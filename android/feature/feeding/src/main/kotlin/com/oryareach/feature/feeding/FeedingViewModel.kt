package com.oryareach.feature.feeding

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryareach.core.database.repository.AppSettingsRepository
import com.oryareach.core.database.repository.VitaminDoseRepository
import com.oryareach.core.database.repository.DiaperChangeRepository
import com.oryareach.core.database.reminder.VitaminReminderRefresher
import com.oryareach.core.database.repository.BabyRepository
import com.oryareach.core.database.repository.FeedingEntryRepository
import com.oryareach.core.domain.feeding.FeedingDay
import com.oryareach.core.domain.feeding.FEED_MILESTONES
import com.oryareach.core.domain.feeding.SUMMARY_WEEK_DAYS
import com.oryareach.core.domain.feeding.doctorSummary
import com.oryareach.core.domain.feeding.feedingTally
import com.oryareach.core.domain.feeding.groupFeedsByDay
import com.oryareach.core.domain.feeding.nextFeedCountdown
import com.oryareach.core.model.AppSettings
import com.oryareach.core.model.Baby
import com.oryareach.core.model.FeedType
import com.oryareach.core.ui.component.DropBurst
import com.oryareach.core.model.FeedingEntry
import com.oryareach.core.network.auth.AuthRepository
import com.oryareach.core.sync.SyncEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant

@Stable
interface FeedingActions {
    fun onLogFeedClick()
    fun onEditFeedClick(feed: FeedingEntry)
    fun onDismissSheet()
    fun onFeedTypeChange(value: FeedType)
    fun onBreastMlChange(value: String)
    fun onFormulaMlChange(value: String)
    fun onToggleUrine()
    fun onToggleStool()
    fun onToggleDiaperChanged()
    fun onNoteChange(value: String)
    fun onOpenDatePicker()
    fun onDismissDatePicker()
    fun onFedDateChange(value: LocalDate)
    fun onOpenTimePicker()
    fun onDismissTimePicker()
    fun onFedTimeChange(value: LocalTime)
    fun onLogFeed()
    fun onDeleteFeedClick(feed: FeedingEntry)
    fun onDismissDeleteFeed()
    fun onConfirmDeleteFeed()
    fun onUndoDelete()
    fun onUndoDismissed()
    fun onHistoryViewChange(value: HistoryView)
    fun onCountdownLongPress()
    fun onDismissNightWatch()
    fun onMilestoneShown()
    fun onMilestoneDismissed()
    fun onVitaminToggle()
    fun onOpenVitaminTimePicker()
    fun onDismissVitaminTimePicker()
    fun onVitaminTimeChange(value: LocalTime)
    fun onClearVitaminTime()
    fun onOpenVitaminHistory()
    fun onDismissVitaminHistory()
    fun onOpenDoctorSummary()
    fun onCloseDoctorSummary()
    fun onRefresh()
}

/**
 * The workspace id is read once, same as every other tab's ViewModel: routing already
 * guarantees a paired, unlocked device by the time this screen is reachable.
 *
 * Everything on the screen hangs off the *active* child. When the switcher points at another
 * child, the feed history and the countdown follow it — which is why the history query is a
 * [flatMapLatest] over that child rather than a one-shot read of an id captured at startup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedingViewModel(
    private val repository: FeedingEntryRepository,
    private val babyRepository: BabyRepository,
    private val settingsRepository: AppSettingsRepository,
    private val vitaminRepository: VitaminDoseRepository,
    private val diaperRepository: DiaperChangeRepository,
    private val vitaminReminders: VitaminReminderRefresher,
    private val auth: AuthRepository,
    private val syncEngine: SyncEngine,
    private val workspaceId: () -> String?,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val ticker: () -> Flow<Unit> = ::secondTicker,
) : ViewModel(), FeedingActions {

    private val _uiState = MutableStateFlow(FeedingUiState())
    val uiState: StateFlow<FeedingUiState> = _uiState.asStateFlow()

    init {
        workspaceId()?.let { id ->
            viewModelScope.launch {
                babyRepository.observeActive(id)
                    .flatMapLatest { baby -> historyFor(id, baby) }
                    .collect { (baby, days) -> set { it.copy(baby = baby, days = days) } }
            }

            // The vitamin card: the day's dose and the fortnight behind it, for the active
            // child. Collected separately from the feed history because it has to re-read when
            // the local day rolls over, not when a feed is logged.
            viewModelScope.launch {
                combine(
                    babyRepository.observeActive(id),
                    settingsRepository.observe(id),
                    ticker(),
                ) { baby, settings, _ -> Triple(baby, settings?.vitaminDMinuteOfDay, todayBounds()) }
                    .distinctUntilChanged()
                    .flatMapLatest { (baby, minuteOfDay, bounds) ->
                        if (baby == null) {
                            flowOf(Triple(minuteOfDay, emptyList(), bounds))
                        } else {
                            vitaminRepository
                                .observeInRange(id, baby.id, bounds.second - HISTORY_WINDOW_MILLIS, bounds.second)
                                .map { doses -> Triple(minuteOfDay, doses, bounds) }
                        }
                    }
                    .collect { (minuteOfDay, doses, bounds) ->
                        set {
                            it.copy(
                                vitaminMinuteOfDay = minuteOfDay,
                                vitaminHistory = doses,
                                vitaminDoseToday = doses.firstOrNull { dose ->
                                    dose.givenAtEpochMillis in bounds.first..bounds.second
                                },
                            )
                        }
                    }
            }

            // The countdown is one second of arithmetic over three inputs — the last feed, the
            // workspace's interval, and the clock. Only the clock ticks on its own, so the
            // ticker is combined in rather than driving a loop that re-reads the database.
            viewModelScope.launch {
                babyRepository.observeActive(id)
                    .flatMapLatest { baby ->
                        if (baby == null) flowOf(null) else repository.observeLatest(id, baby.id)
                    }
                    .let { latestFeed ->
                        combine(latestFeed, settingsRepository.observe(id), ticker()) { feed, settings, _ ->
                            val interval = settings?.feedIntervalMinutes
                                ?: AppSettings.DEFAULT_FEED_INTERVAL_MINUTES
                            interval to nextFeedCountdown(
                                lastFedAtEpochMillis = feed?.fedAtEpochMillis,
                                intervalMinutes = interval,
                                nowEpochMillis = now(),
                            )
                        }
                    }
                    .collect { (interval, countdown) ->
                        set {
                            it.copy(
                                countdown = countdown,
                                intervalMinutes = interval,
                                today = Clock.System.todayIn(timeZone()),
                            )
                        }
                    }
            }
        }
    }

    override fun onLogFeedClick() = set {
        it.copy(
            sheetVisible = true,
            editingFeedId = null,
            formFeedType = FeedType.BREAST_MILK,
            formBreastMl = "",
            formFormulaMl = "",
            formHadUrine = false,
            formHadStool = false,
            formDiaperChanged = true,
            formNote = "",
            // Now, the overwhelmingly common case: the feed just happened. A retroactive entry
            // walks it back from here.
            formFedAtEpochMillis = now(),
        )
    }

    override fun onEditFeedClick(feed: FeedingEntry) = set {
        it.copy(
            sheetVisible = true,
            editingFeedId = feed.id,
            formFeedType = feed.feedType,
            // A feed written before the split has only the legacy amount; it belongs in
            // whichever field its type says it came from, so editing it does not lose it.
            formBreastMl = feed.breastMl?.toString()
                ?: feed.amountMl.takeIf { feed.formulaMl == null && feed.feedType == FeedType.BREAST_MILK }
                    ?.toString().orEmpty(),
            formFormulaMl = feed.formulaMl?.toString()
                ?: feed.amountMl.takeIf { feed.breastMl == null && feed.feedType == FeedType.FORMULA }
                    ?.toString().orEmpty(),
            formHadUrine = feed.hadUrine,
            formHadStool = feed.hadStool,
            formDiaperChanged = feed.diaperChanged,
            formNote = feed.note.orEmpty(),
            formFedAtEpochMillis = feed.fedAtEpochMillis,
        )
    }

    override fun onDismissSheet() = set {
        it.copy(sheetVisible = false, datePickerVisible = false, timePickerVisible = false)
    }
    override fun onFeedTypeChange(value: FeedType) = set { it.copy(formFeedType = value) }

    /** Digits only: the field feeds an Int, and a stray character would silently drop the amount. */
    override fun onBreastMlChange(value: String) = set { it.copy(formBreastMl = value.asAmount()) }

    override fun onFormulaMlChange(value: String) = set { it.copy(formFormulaMl = value.asAmount()) }

    private fun String.asAmount(): String = filter(Char::isDigit).take(MAX_AMOUNT_DIGITS)

    override fun onToggleUrine() = set { it.copy(formHadUrine = !it.formHadUrine) }
    override fun onToggleStool() = set { it.copy(formHadStool = !it.formHadStool) }
    override fun onToggleDiaperChanged() = set { it.copy(formDiaperChanged = !it.formDiaperChanged) }
    override fun onNoteChange(value: String) = set { it.copy(formNote = value) }
    override fun onHistoryViewChange(value: HistoryView) = set { it.copy(historyView = value) }

    override fun onOpenDatePicker() = set { it.copy(datePickerVisible = true) }
    override fun onDismissDatePicker() = set { it.copy(datePickerVisible = false) }
    override fun onOpenTimePicker() = set { it.copy(timePickerVisible = true) }
    override fun onDismissTimePicker() = set { it.copy(timePickerVisible = false) }

    /** Keeps the time of day and moves the date, so picking either one at a time works. */
    override fun onFedDateChange(value: LocalDate) = set {
        it.copy(
            formFedAtEpochMillis = it.formFedAtEpochMillis.movedTo(date = value),
            datePickerVisible = false,
        )
    }

    override fun onFedTimeChange(value: LocalTime) = set {
        it.copy(
            formFedAtEpochMillis = it.formFedAtEpochMillis.movedTo(time = value),
            timePickerVisible = false,
        )
    }

    /**
     * Rebuilds an instant with one half of it replaced, in the device's own zone — the pickers
     * hand back a date or a time, never both, and the other half has to survive.
     */
    private fun Long.movedTo(date: LocalDate? = null, time: LocalTime? = null): Long {
        val zone = timeZone()
        val current = Instant.fromEpochMilliseconds(this).toLocalDateTime(zone)
        return LocalDateTime(date ?: current.date, time ?: current.time)
            .toInstant(zone)
            .toEpochMilliseconds()
    }

    override fun onLogFeed() {
        val workspace = workspaceId() ?: return
        val state = _uiState.value
        val baby = state.baby ?: return
        if (state.busy) return
        set { it.copy(busy = true) }

        viewModelScope.launch {
            val editingId = state.editingFeedId
            if (editingId == null) {
                repository.logFeed(
                    workspaceId = workspace,
                    babyId = baby.id,
                    userId = auth.currentUserId().orEmpty(),
                    feedType = state.resolvedFeedType(),
                    breastMl = state.enteredBreastMl(),
                    formulaMl = state.enteredFormulaMl(),
                    hadUrine = state.formHadUrine,
                    hadStool = state.formHadStool,
                    diaperChanged = state.savedDiaperChanged(),
                    note = state.formNote.ifBlank { null },
                    intervalMinutes = state.intervalMinutes,
                    fedAt = state.formFedAtEpochMillis,
                )
            } else {
                // `fedAt` goes back unchanged: the sheet shows it read-only while editing, and
                // the reminder already scheduled off it stays correct as a result.
                repository.update(
                    id = editingId,
                    feedType = state.resolvedFeedType(),
                    breastMl = state.enteredBreastMl(),
                    formulaMl = state.enteredFormulaMl(),
                    hadUrine = state.formHadUrine,
                    hadStool = state.formHadStool,
                    diaperChanged = state.savedDiaperChanged(),
                    note = state.formNote.ifBlank { null },
                    fedAt = state.formFedAtEpochMillis,
                    intervalMinutes = state.intervalMinutes,
                )
            }
            // Only a brand-new feed can land on a milestone; an edit changes no count. The
            // count comes from the whole log, so the hundredth feed is the hundredth feed and
            // not the hundredth of the last fortnight.
            val milestone = if (editingId == null) crossedMilestone(workspace, baby.id) else null

            set {
                it.copy(
                    busy = false,
                    sheetVisible = false,
                    editingFeedId = null,
                    milestoneBurst = milestone?.let { count -> DropBurst(id = now(), count = DROPS_PER_BURST) },
                    milestoneReached = milestone,
                )
            }
        }
    }

    /** The milestone this feed just landed on, or null — only the feed that lands on it counts. */
    private suspend fun crossedMilestone(workspaceId: String, babyId: String): Int? {
        val total = repository.countByCreator(workspaceId, babyId).sumOf { it.count }
        return FEED_MILESTONES.firstOrNull { it == total }
    }

    override fun onMilestoneShown() = set { it.copy(milestoneBurst = null) }

    override fun onMilestoneDismissed() = set { it.copy(milestoneReached = null) }

    /**
     * Asks first, then still offers the row back — the same as a pumping session, and for the
     * same reason: the trash icon sits where a thumb lands on its way to the snackbar. The dialog
     * names the feed, so it is clear which one goes.
     */
    override fun onDeleteFeedClick(feed: FeedingEntry) = set { it.copy(deleteConfirmFeed = feed) }

    override fun onDismissDeleteFeed() = set { it.copy(deleteConfirmFeed = null) }

    override fun onConfirmDeleteFeed() {
        val id = _uiState.value.deleteConfirmFeed?.id ?: return
        set { it.copy(deleteConfirmFeed = null) }
        viewModelScope.launch {
            repository.delete(id, _uiState.value.intervalMinutes)
            set { it.copy(undoDeleteId = id) }
        }
    }

    override fun onUndoDelete() {
        val id = _uiState.value.undoDeleteId ?: return
        set { it.copy(undoDeleteId = null) }
        viewModelScope.launch { repository.restore(id, _uiState.value.intervalMinutes) }
    }

    override fun onUndoDismissed() = set { it.copy(undoDeleteId = null) }

    /** Same pull-to-refresh contract as Home: await the sync so the spinner stops when it's done. */
    /**
     * Night-watch easter egg. Deliberately silent until at least one feed has been logged
     * between midnight and 6am: a medal for a night nobody sat up through would be a joke at
     * the wrong person's expense.
     *
     * Read from the whole log, not from the fortnight the screen is showing. The panel says
     * "feeds logged in all", and it used to mean "in the last 14 days" — a total that quietly
     * shrank as the log grew. One read on a long press, off the main thread.
     */
    override fun onCountdownLongPress() {
        val workspace = workspaceId() ?: return
        val baby = _uiState.value.baby ?: return

        viewModelScope.launch {
            val all = repository.observeInRange(workspace, baby.id, 0L, Long.MAX_VALUE).first()
            if (all.isEmpty()) return@launch

            val tally = feedingTally(all, timeZone())
            if (tally.nightFeeds == 0) return@launch

            val byCreator = repository.countByCreator(workspace, baby.id)
            val selfId = auth.currentUserId()
            val mine = byCreator.firstOrNull { it.createdBy == selfId }?.count ?: 0
            val theirs = byCreator.filterNot { it.createdBy == selfId }.sumOf { it.count }
            val shared = mine > 0 && theirs > 0

            set {
                it.copy(
                    nightWatchTally = tally,
                    nightWatchMine = mine.takeIf { shared },
                    nightWatchTheirs = theirs.takeIf { shared },
                )
            }
        }
    }

    override fun onDismissNightWatch() =
        set { it.copy(nightWatchTally = null, nightWatchMine = null, nightWatchTheirs = null) }

    /**
     * Ticks today's dose, or takes it back.
     *
     * Taking it back is a real delete rather than a flag, so a mistaken tick leaves nothing
     * behind — and either way the reminder is re-derived, which is what stops the phone asking
     * again this evening for something that has already been given.
     */
    override fun onVitaminToggle() {
        val workspace = workspaceId() ?: return
        val baby = _uiState.value.baby ?: return
        val userId = auth.currentUserId() ?: return
        val existing = _uiState.value.vitaminDoseToday

        viewModelScope.launch {
            if (existing == null) {
                vitaminRepository.logDose(workspace, baby.id, userId)
            } else {
                vitaminRepository.delete(existing.id)
            }
            vitaminReminders.refresh()
        }
    }

    override fun onOpenVitaminTimePicker() = set { it.copy(vitaminTimePickerVisible = true) }

    override fun onDismissVitaminTimePicker() = set { it.copy(vitaminTimePickerVisible = false) }

    override fun onVitaminTimeChange(value: LocalTime) {
        val workspace = workspaceId() ?: return
        set { it.copy(vitaminTimePickerVisible = false) }
        viewModelScope.launch {
            settingsRepository.setVitaminMinuteOfDay(workspace, value.hour * 60 + value.minute)
            vitaminReminders.refresh()
        }
    }

    override fun onClearVitaminTime() {
        val workspace = workspaceId() ?: return
        set { it.copy(vitaminTimePickerVisible = false) }
        viewModelScope.launch {
            settingsRepository.setVitaminMinuteOfDay(workspace, null)
            vitaminReminders.refresh()
        }
    }

    override fun onOpenVitaminHistory() = set { it.copy(vitaminHistoryVisible = true) }

    override fun onDismissVitaminHistory() = set { it.copy(vitaminHistoryVisible = false) }

    /**
     * Reads the week behind today once and hands it to [doctorSummary]. One extra day on the
     * window so the first of the seven complete days is whole in any time zone.
     */
    override fun onOpenDoctorSummary() {
        val workspace = workspaceId() ?: return
        val baby = _uiState.value.baby ?: return

        viewModelScope.launch {
            val nowMillis = now()
            val from = nowMillis - (SUMMARY_WEEK_DAYS + 1) * MILLIS_PER_DAY
            val feeds = repository.observeInRange(workspace, baby.id, from, nowMillis).first()
            val doses = vitaminRepository.observeInRange(workspace, baby.id, from, nowMillis).first()
            // Nappies logged on the nappy page, so the summary counts what that page counts.
            val changes = diaperRepository.observeInRange(workspace, baby.id, from, nowMillis).first()
            // The log's true start, not the window's: a week that begins before anyone was
            // logging must not count those days as feeds that never happened.
            val firstFeed = repository.observeInRange(workspace, baby.id, 0L, nowMillis).first()
                .minOfOrNull { it.fedAtEpochMillis }
            val summary = doctorSummary(
                feeds = feeds,
                doses = doses,
                changes = changes,
                nowEpochMillis = nowMillis,
                timeZone = timeZone(),
                birthDate = baby.birthDate,
                firstFeedEpochMillis = firstFeed,
            )
            set { it.copy(doctorSummary = summary) }
        }
    }

    override fun onCloseDoctorSummary() = set { it.copy(doctorSummary = null) }

    /** The local day the phone is in right now, as the epoch-millis range the queries take. */
    private fun todayBounds(): Pair<Long, Long> {
        val zone = timeZone()
        val today = Clock.System.todayIn(zone)
        val start = today.atStartOfDayIn(zone).toEpochMilliseconds()
        val end = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds() - 1
        return start to end
    }

    override fun onRefresh() {
        if (_uiState.value.refreshing) return
        set { it.copy(refreshing = true) }

        viewModelScope.launch {
            syncEngine.sync()
            set { it.copy(refreshing = false) }
        }
    }

    /**
     * The window has no upper bound: a feed logged a second from now must still land in the
     * list, and an end pinned to subscribe time would quietly drop it.
     */
    private fun historyFor(workspace: String, baby: Baby?): Flow<Pair<Baby?, List<FeedingDay>>> =
        if (baby == null) {
            flowOf(null to emptyList())
        } else {
            repository.observeInRange(workspace, baby.id, now() - HISTORY_WINDOW_MILLIS, Long.MAX_VALUE)
                .map { feeds -> baby to groupFeedsByDay(feeds, timeZone()) }
        }

    private fun set(block: (FeedingUiState) -> FeedingUiState) {
        _uiState.value = block(_uiState.value)
    }

    private companion object {
        const val MAX_AMOUNT_DIGITS = 4

        /** Enough to read as a handful thrown in the air, few enough to be gone in a moment. */
        const val DROPS_PER_BURST = 14

        /** The table view scrolls sideways through days; a fortnight is as far back as it reads. */
        const val HISTORY_WINDOW_MILLIS = 14L * 24 * 60 * 60 * 1000

        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}

/** Drives the countdown text. Cancelled with the collecting scope, so it stops with the screen. */
private fun secondTicker(): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(1_000)
    }
}

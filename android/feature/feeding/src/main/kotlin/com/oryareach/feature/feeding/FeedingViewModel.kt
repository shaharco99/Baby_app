package com.oryareach.feature.feeding

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryareach.core.database.repository.AppSettingsRepository
import com.oryareach.core.database.repository.BabyRepository
import com.oryareach.core.database.repository.FeedingEntryRepository
import com.oryareach.core.domain.feeding.FeedingDay
import com.oryareach.core.domain.feeding.feedingTally
import com.oryareach.core.domain.feeding.groupFeedsByDay
import com.oryareach.core.domain.feeding.nextFeedCountdown
import com.oryareach.core.model.AppSettings
import com.oryareach.core.model.Baby
import com.oryareach.core.model.FeedType
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
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
    fun onAmountChange(value: String)
    fun onToggleUrine()
    fun onToggleStool()
    fun onNoteChange(value: String)
    fun onOpenDatePicker()
    fun onDismissDatePicker()
    fun onFedDateChange(value: LocalDate)
    fun onOpenTimePicker()
    fun onDismissTimePicker()
    fun onFedTimeChange(value: LocalTime)
    fun onLogFeed()
    fun onDeleteFeed(id: String)
    fun onUndoDelete()
    fun onUndoDismissed()
    fun onHistoryViewChange(value: HistoryView)
    fun onCountdownLongPress()
    fun onDismissNightWatch()
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
            formAmountMl = "",
            formHadUrine = false,
            formHadStool = false,
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
            formAmountMl = feed.amountMl?.toString().orEmpty(),
            formHadUrine = feed.hadUrine,
            formHadStool = feed.hadStool,
            formNote = feed.note.orEmpty(),
            formFedAtEpochMillis = feed.fedAtEpochMillis,
        )
    }

    override fun onDismissSheet() = set {
        it.copy(sheetVisible = false, datePickerVisible = false, timePickerVisible = false)
    }
    override fun onFeedTypeChange(value: FeedType) = set { it.copy(formFeedType = value) }

    /** Digits only: the field feeds an Int, and a stray character would silently drop the amount. */
    override fun onAmountChange(value: String) = set {
        it.copy(formAmountMl = value.filter(Char::isDigit).take(MAX_AMOUNT_DIGITS))
    }

    override fun onToggleUrine() = set { it.copy(formHadUrine = !it.formHadUrine) }
    override fun onToggleStool() = set { it.copy(formHadStool = !it.formHadStool) }
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
                    feedType = state.formFeedType,
                    amountMl = state.formAmountMl.toIntOrNull(),
                    hadUrine = state.formHadUrine,
                    hadStool = state.formHadStool,
                    note = state.formNote.ifBlank { null },
                    intervalMinutes = state.intervalMinutes,
                    fedAt = state.formFedAtEpochMillis,
                )
            } else {
                // `fedAt` goes back unchanged: the sheet shows it read-only while editing, and
                // the reminder already scheduled off it stays correct as a result.
                repository.update(
                    id = editingId,
                    feedType = state.formFeedType,
                    amountMl = state.formAmountMl.toIntOrNull(),
                    hadUrine = state.formHadUrine,
                    hadStool = state.formHadStool,
                    note = state.formNote.ifBlank { null },
                    fedAt = state.formFedAtEpochMillis,
                )
            }
            set { it.copy(busy = false, sheetVisible = false, editingFeedId = null) }
        }
    }

    /**
     * Deletes straight away and offers the row back, rather than asking first: the row is only
     * soft-deleted, so undoing it is cheap, and a confirmation dialog on every delete is its own
     * kind of annoying at four in the morning.
     */
    override fun onDeleteFeed(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            set { it.copy(undoDeleteId = id) }
        }
    }

    override fun onUndoDelete() {
        val id = _uiState.value.undoDeleteId ?: return
        set { it.copy(undoDeleteId = null) }
        viewModelScope.launch { repository.restore(id) }
    }

    override fun onUndoDismissed() = set { it.copy(undoDeleteId = null) }

    /** Same pull-to-refresh contract as Home: await the sync so the spinner stops when it's done. */
    /**
     * Night-watch easter egg. Deliberately silent until at least one feed has been logged
     * between midnight and 6am: a medal for a night nobody sat up through would be a joke at
     * the wrong person's expense.
     */
    override fun onCountdownLongPress() {
        val days = _uiState.value.days
        if (days.isEmpty()) return

        val tally = feedingTally(days.flatMap { it.feeds }, timeZone())
        if (tally.nightFeeds == 0) return
        set { it.copy(nightWatchTally = tally) }
    }

    override fun onDismissNightWatch() = set { it.copy(nightWatchTally = null) }

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

        /** The table view scrolls sideways through days; a fortnight is as far back as it reads. */
        const val HISTORY_WINDOW_MILLIS = 14L * 24 * 60 * 60 * 1000
    }
}

/** Drives the countdown text. Cancelled with the collecting scope, so it stops with the screen. */
private fun secondTicker(): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(1_000)
    }
}

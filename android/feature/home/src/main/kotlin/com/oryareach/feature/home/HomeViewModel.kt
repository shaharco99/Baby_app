package com.oryareach.feature.home

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryareach.core.database.repository.AppSettingsRepository
import com.oryareach.core.database.repository.BabyRepository
import com.oryareach.core.database.repository.FeedingEntryRepository
import com.oryareach.core.database.repository.PumpSessionRepository
import com.oryareach.core.database.repository.ShoppingItemRepository
import com.oryareach.core.database.repository.TaskRepository
import com.oryareach.core.domain.baby.babyAge
import com.oryareach.core.domain.feeding.nextFeedCountdown
import com.oryareach.core.domain.pregnancy.dueDateFromLastPeriod
import com.oryareach.core.domain.pregnancy.getPregnancyProgress
import com.oryareach.core.domain.pregnancy.lastPeriodFromDueDate
import com.oryareach.core.domain.shopping.calculateBudget
import com.oryareach.core.network.auth.AuthRepository
import com.oryareach.core.model.AppSettings
import com.oryareach.core.sync.PartnerPresence
import com.oryareach.core.sync.SyncEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.todayIn
import kotlin.time.Clock

@Stable
interface HomeActions {
    fun onEditDueDate()
    fun onDismissSheet()
    fun onOpenDatePicker()
    fun onDismissDatePicker()
    fun onLastPeriodChange(value: LocalDate)
    fun onBabyNameChange(value: String)
    fun onPartnerOneNameChange(value: String)
    fun onPartnerTwoNameChange(value: String)
    fun onSubmit()
    fun onRefresh()
    fun onMoonLongPress()
    fun onDismissBookOfLove()
    fun onEditBirthDetails()
    fun onDismissBirthSheet()
    fun onOpenBirthDatePicker()
    fun onDismissBirthDatePicker()
    fun onOpenBirthTimePicker()
    fun onDismissBirthTimePicker()
    fun onBirthDateChange(value: LocalDate)
    fun onBirthTimeChange(value: LocalTime)
    fun onBirthWeightChange(value: String)
    fun onBirthPlaceChange(value: String)
    fun onSubmitBirthDetails()
}

/**
 * The workspace id is read once, same as every other tab's ViewModel: routing already
 * guarantees a paired, unlocked device by the time this screen is reachable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val settingsRepository: AppSettingsRepository,
    private val babyRepository: BabyRepository,
    private val feedingRepository: FeedingEntryRepository,
    private val pumpRepository: PumpSessionRepository,
    private val taskRepository: TaskRepository,
    private val shoppingRepository: ShoppingItemRepository,
    private val auth: AuthRepository,
    private val presence: PartnerPresence,
    private val syncEngine: SyncEngine,
    private val workspaceId: () -> String?,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val ticker: () -> Flow<Unit> = ::secondTicker,
) : ViewModel(), HomeActions {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        workspaceId()?.let { id ->
            viewModelScope.launch {
                combine(
                    settingsRepository.observe(id),
                    babyRepository.observeAll(id),
                    babyRepository.observeActive(id),
                    taskRepository.observeAll(id),
                    shoppingRepository.observeAll(id),
                ) { settings, children, activeBaby, tasks, items ->
                    val budget = calculateBudget(items)
                    HomeUiState(
                        dueDate = settings?.dueDate,
                        babyName = settings?.babyName,
                        partnerOneName = settings?.partnerOneName,
                        partnerTwoName = settings?.partnerTwoName,
                        children = children,
                        activeBaby = activeBaby,
                        openTaskCount = tasks.count { !it.done },
                        budgetEstimated = budget.estimatedTotal,
                        budgetSpent = budget.spentTotal,
                        budgetSpentByUs = budget.spentByUs,
                        budgetSpentByOthers = budget.spentByOthers,
                        progress = settings?.dueDate?.let { getPregnancyProgress(it, today()) },
                        babyAge = activeBaby?.birthDate?.let { babyAge(it, today()) },
                    )
                }.collect { computed ->
                    set { current ->
                        computed.copy(
                            isLoaded = true,
                            sheetVisible = current.sheetVisible,
                            datePickerVisible = current.datePickerVisible,
                            editingLastPeriodDate = current.editingLastPeriodDate,
                            editingBabyName = current.editingBabyName,
                            editingPartnerOneName = current.editingPartnerOneName,
                            editingPartnerTwoName = current.editingPartnerTwoName,
                            birthSheetVisible = current.birthSheetVisible,
                            birthDatePickerVisible = current.birthDatePickerVisible,
                            birthTimePickerVisible = current.birthTimePickerVisible,
                            editingBirthDate = current.editingBirthDate,
                            editingBirthTime = current.editingBirthTime,
                            editingBirthWeightGrams = current.editingBirthWeightGrams,
                            editingBirthPlace = current.editingBirthPlace,
                            feedCountdown = current.feedCountdown,
                            lastFedAtEpochMillis = current.lastFedAtEpochMillis,
                            sinceLastFeedMillis = current.sinceLastFeedMillis,
                            todayFeedCount = current.todayFeedCount,
                            todayFeedMl = current.todayFeedMl,
                            pumpCountdown = current.pumpCountdown,
                            pumpRunning = current.pumpRunning,
                            pumpElapsedMillis = current.pumpElapsedMillis,
                        )
                    }
                }
            }

            // Baby mode's countdown. Deliberately reads the feeding repository directly
            // rather than reaching for :feature:feeding — feature modules never depend on
            // each other, and the shared seam for this is :core:domain's arithmetic.
            viewModelScope.launch {
                babyRepository.observeActive(id)
                    .flatMapLatest { baby ->
                        if (baby == null) flowOf(null) else feedingRepository.observeLatest(id, baby.id)
                    }
                    .let { latestFeed ->
                        combine(latestFeed, settingsRepository.observe(id), ticker()) { feed, settings, _ ->
                            feed?.fedAtEpochMillis to nextFeedCountdown(
                                lastFedAtEpochMillis = feed?.fedAtEpochMillis,
                                intervalMinutes = settings?.feedIntervalMinutes
                                    ?: AppSettings.DEFAULT_FEED_INTERVAL_MINUTES,
                                nowEpochMillis = now(),
                            )
                        }
                    }
                    .collect { (lastFedAt, countdown) ->
                        set {
                            it.copy(
                                feedCountdown = countdown,
                                lastFedAtEpochMillis = lastFedAt,
                                sinceLastFeedMillis = lastFedAt?.let { fedAt -> (now() - fedAt).coerceAtLeast(0) } ?: 0,
                            )
                        }
                    }
            }

            // Today's running tally under the timer. Re-subscribes when the local day rolls over,
            // which is why the day's start is derived from the ticker rather than read once.
            viewModelScope.launch {
                combine(
                    babyRepository.observeActive(id),
                    ticker().map { today() }.distinctUntilChanged(),
                ) { baby, day -> baby to day }
                    .flatMapLatest { (baby, day) ->
                        if (baby == null) {
                            flowOf(emptyList())
                        } else {
                            val start = day.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                            feedingRepository.observeInRange(id, baby.id, start, Long.MAX_VALUE)
                        }
                    }
                    .collect { feeds ->
                        set {
                            it.copy(
                                todayFeedCount = feeds.size,
                                todayFeedMl = feeds.mapNotNull { feed -> feed.totalMl }.takeIf { ml -> ml.isNotEmpty() }?.sum(),
                            )
                        }
                    }
            }

            // The pump countdown, on the same terms as the feed one: the repository directly,
            // never :feature:pumping. It is not inside the baby-mode branch because pumping is
            // workspace-scoped — it has no child, and it can start before the birth.
            viewModelScope.launch {
                combine(
                    pumpRepository.observeRunning(id),
                    pumpRepository.observeLatest(id),
                    settingsRepository.observe(id),
                    ticker(),
                ) { running, latest, settings, _ ->
                    Triple(
                        running,
                        running?.elapsedMillisAt(now()) ?: 0L,
                        nextFeedCountdown(
                            lastFedAtEpochMillis = latest?.startedAtEpochMillis,
                            intervalMinutes = settings?.pumpIntervalMinutes
                                ?: AppSettings.DEFAULT_PUMP_INTERVAL_MINUTES,
                            nowEpochMillis = now(),
                        ),
                    )
                }.collect { (running, elapsed, countdown) ->
                    set {
                        it.copy(
                            pumpRunning = running,
                            pumpElapsedMillis = elapsed,
                            pumpCountdown = countdown,
                        )
                    }
                }
            }

            // An install that predates per-child records has its pregnancy on `app_settings`
            // and no child at all. The seed needs those settings, which arrive asynchronously
            // (locally or by sync), so it waits for the first non-null emission rather than
            // running once against an empty database and giving up.
            viewModelScope.launch {
                settingsRepository.observe(id).first { it != null }
                babyRepository.seedFromSettingsIfNeeded(id, auth.currentUserId().orEmpty())
            }
        }
    }

    override fun onEditDueDate() = set {
        it.copy(
            sheetVisible = true,
            editingLastPeriodDate = it.dueDate?.let(::lastPeriodFromDueDate),
            editingBabyName = it.babyName.orEmpty(),
            editingPartnerOneName = it.partnerOneName.orEmpty(),
            editingPartnerTwoName = it.partnerTwoName.orEmpty(),
        )
    }

    override fun onDismissSheet() = set { it.copy(sheetVisible = false) }
    override fun onOpenDatePicker() = set { it.copy(datePickerVisible = true) }
    override fun onDismissDatePicker() = set { it.copy(datePickerVisible = false) }
    override fun onLastPeriodChange(value: LocalDate) = set {
        it.copy(editingLastPeriodDate = value, datePickerVisible = false)
    }
    override fun onBabyNameChange(value: String) = set { it.copy(editingBabyName = value) }
    override fun onPartnerOneNameChange(value: String) = set { it.copy(editingPartnerOneName = value) }
    override fun onPartnerTwoNameChange(value: String) = set { it.copy(editingPartnerTwoName = value) }

    override fun onSubmit() {
        val state = _uiState.value
        val workspace = workspaceId() ?: return
        val lastPeriodDate = state.editingLastPeriodDate ?: return

        viewModelScope.launch {
            settingsRepository.save(
                workspaceId = workspace,
                userId = auth.currentUserId().orEmpty(),
                dueDate = dueDateFromLastPeriod(lastPeriodDate),
                babyName = state.editingBabyName.ifBlank { null },
                partnerOneName = state.editingPartnerOneName.ifBlank { null },
                partnerTwoName = state.editingPartnerTwoName.ifBlank { null },
            )
            // The due date and name live on the active child too, and the moon page reads the
            // settings row — writing only one of the two would leave them disagreeing.
            state.activeBaby?.let { baby ->
                babyRepository.update(
                    id = baby.id,
                    name = state.editingBabyName.ifBlank { null },
                    dueDate = dueDateFromLastPeriod(lastPeriodDate),
                    birthDate = baby.birthDate,
                    birthTime = baby.birthTime,
                    birthWeightGrams = baby.birthWeightGrams,
                    birthPlace = baby.birthPlace,
                )
            }
            set { it.copy(sheetVisible = false) }
        }
    }

    override fun onEditBirthDetails() = set {
        val baby = it.activeBaby
        it.copy(
            birthSheetVisible = true,
            editingBirthDate = baby?.birthDate ?: today(),
            editingBirthTime = baby?.birthTime,
            editingBirthWeightGrams = baby?.birthWeightGrams?.toString().orEmpty(),
            editingBirthPlace = baby?.birthPlace.orEmpty(),
        )
    }

    override fun onDismissBirthSheet() = set { it.copy(birthSheetVisible = false) }
    override fun onOpenBirthDatePicker() = set { it.copy(birthDatePickerVisible = true) }
    override fun onDismissBirthDatePicker() = set { it.copy(birthDatePickerVisible = false) }
    override fun onOpenBirthTimePicker() = set { it.copy(birthTimePickerVisible = true) }
    override fun onDismissBirthTimePicker() = set { it.copy(birthTimePickerVisible = false) }

    override fun onBirthDateChange(value: LocalDate) = set {
        it.copy(editingBirthDate = value, birthDatePickerVisible = false)
    }

    override fun onBirthTimeChange(value: LocalTime) = set {
        it.copy(editingBirthTime = value, birthTimePickerVisible = false)
    }

    /** Digits only: the field feeds an Int, and a stray character would silently drop the weight. */
    override fun onBirthWeightChange(value: String) = set {
        it.copy(editingBirthWeightGrams = value.filter(Char::isDigit).take(MAX_WEIGHT_DIGITS))
    }

    override fun onBirthPlaceChange(value: String) = set { it.copy(editingBirthPlace = value) }

    /** Writing a birth date is what moves the home page from the moon countdown to baby mode. */
    override fun onSubmitBirthDetails() {
        val state = _uiState.value
        val baby = state.activeBaby ?: return
        val birthDate = state.editingBirthDate ?: return

        viewModelScope.launch {
            babyRepository.update(
                id = baby.id,
                name = baby.name,
                dueDate = baby.dueDate,
                birthDate = birthDate,
                birthTime = state.editingBirthTime,
                birthWeightGrams = state.editingBirthWeightGrams.toIntOrNull(),
                birthPlace = state.editingBirthPlace.ifBlank { null },
            )
            set { it.copy(birthSheetVisible = false) }
        }
    }

    /**
     * Book of Love easter egg: only surfaces when both of you have the app open at the same
     * moment, which is the whole point of it — a tip meant for two people reading it together.
     *
     * It used to ask whether the partner had *edited* something in the last five minutes, which
     * is a different question: it said yes long after they had put the phone down, and no while
     * they sat reading the app without touching anything. [PartnerPresence] answers the real
     * one, from a heartbeat each phone writes while its app is on screen.
     */
    override fun onMoonLongPress() {
        if (!presence.isPartnerHere.value) return
        set { it.copy(bookOfLoveVisible = true) }
    }

    override fun onDismissBookOfLove() = set { it.copy(bookOfLoveVisible = false) }

    /**
     * Pull-to-refresh: the app otherwise only syncs on a 6-hour periodic worker or right after
     * a local write, so there was no way to ask "check the partner's changes now" from the UI.
     * Calls SyncEngine directly rather than going through WorkManager/SyncTrigger, since this
     * needs to await completion to know when to stop spinning.
     */
    override fun onRefresh() {
        if (_uiState.value.refreshing) return
        set { it.copy(refreshing = true) }

        viewModelScope.launch {
            syncEngine.sync()
            set { it.copy(refreshing = false) }
        }
    }

    private fun set(block: (HomeUiState) -> HomeUiState) {
        _uiState.value = block(_uiState.value)
    }

    private companion object {
        const val MAX_WEIGHT_DIGITS = 5
    }
}

/** Drives the feed countdown text. Cancelled with the collecting scope, so it stops with the screen. */
private fun secondTicker(): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(1_000)
    }
}

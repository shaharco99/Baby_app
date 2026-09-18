package com.oryareach.feature.feeding

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryareach.core.database.repository.AppSettingsRepository
import com.oryareach.core.database.repository.BabyRepository
import com.oryareach.core.database.repository.FeedingEntryRepository
import com.oryareach.core.domain.feeding.FeedingDay
import com.oryareach.core.domain.feeding.groupFeedsByDay
import com.oryareach.core.domain.feeding.nextFeedCountdown
import com.oryareach.core.model.AppSettings
import com.oryareach.core.model.Baby
import com.oryareach.core.model.FeedType
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
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

@Stable
interface FeedingActions {
    fun onLogFeedClick()
    fun onDismissSheet()
    fun onFeedTypeChange(value: FeedType)
    fun onAmountChange(value: String)
    fun onToggleUrine()
    fun onToggleStool()
    fun onNoteChange(value: String)
    fun onLogFeed()
    fun onDeleteFeed(id: String)
    fun onHistoryViewChange(value: HistoryView)
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
                        set { it.copy(countdown = countdown, intervalMinutes = interval) }
                    }
            }
        }
    }

    override fun onLogFeedClick() = set {
        it.copy(
            sheetVisible = true,
            formFeedType = FeedType.BREAST_MILK,
            formAmountMl = "",
            formHadUrine = false,
            formHadStool = false,
            formNote = "",
        )
    }

    override fun onDismissSheet() = set { it.copy(sheetVisible = false) }
    override fun onFeedTypeChange(value: FeedType) = set { it.copy(formFeedType = value) }

    /** Digits only: the field feeds an Int, and a stray character would silently drop the amount. */
    override fun onAmountChange(value: String) = set {
        it.copy(formAmountMl = value.filter(Char::isDigit).take(MAX_AMOUNT_DIGITS))
    }

    override fun onToggleUrine() = set { it.copy(formHadUrine = !it.formHadUrine) }
    override fun onToggleStool() = set { it.copy(formHadStool = !it.formHadStool) }
    override fun onNoteChange(value: String) = set { it.copy(formNote = value) }
    override fun onHistoryViewChange(value: HistoryView) = set { it.copy(historyView = value) }

    override fun onLogFeed() {
        val workspace = workspaceId() ?: return
        val state = _uiState.value
        val baby = state.baby ?: return
        if (state.busy) return
        set { it.copy(busy = true) }

        viewModelScope.launch {
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
            )
            set { it.copy(busy = false, sheetVisible = false) }
        }
    }

    override fun onDeleteFeed(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    /** Same pull-to-refresh contract as Home: await the sync so the spinner stops when it's done. */
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

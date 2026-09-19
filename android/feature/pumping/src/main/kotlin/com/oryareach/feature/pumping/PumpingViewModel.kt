package com.oryareach.feature.pumping

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryareach.core.database.repository.AppSettingsRepository
import com.oryareach.core.database.repository.PumpSessionRepository
import com.oryareach.core.domain.feeding.nextFeedCountdown
import com.oryareach.core.domain.pumping.groupPumpsByDay
import com.oryareach.core.domain.pumping.milkStash
import com.oryareach.core.model.AppSettings
import com.oryareach.core.model.PumpSession
import com.oryareach.core.model.PumpSide
import com.oryareach.core.network.auth.AuthRepository
import com.oryareach.core.sync.SyncEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
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
interface PumpingActions {
    fun onStartClick()
    fun onPauseClick()
    fun onResumeClick()
    fun onStopClick()
    fun onTimerLongPress()
    fun onDismissStash()
    fun onDropsShown()
    fun onLogPastClick()
    fun onEditClick(session: PumpSession)
    fun onDismissSheet()
    fun onSideChange(value: PumpSide)
    fun onPendingSideChange(value: PumpSide)
    fun onMinutesChange(value: String)
    fun onAmountChange(value: String)
    fun onNoteChange(value: String)
    fun onOpenDatePicker()
    fun onDismissDatePicker()
    fun onStartedDateChange(value: LocalDate)
    fun onOpenTimePicker()
    fun onDismissTimePicker()
    fun onStartedTimeChange(value: LocalTime)
    fun onSave()
    fun onDiscard()
    fun onDeleteSession(id: String)
    fun onUndoDelete()
    fun onUndoDismissed()
    fun onHistoryViewChange(value: PumpHistoryView)
    fun onRefresh()
}

/**
 * Pumping is workspace-scoped, not baby-scoped: there is no active-child lookup here, and the
 * screen works before the birth.
 *
 * The timer is not a field on this ViewModel. It is a row in the database with no end time, which
 * is why leaving the screen, force-stopping the app or rebooting the phone does not lose a session
 * in progress — and why the partner's device shows it too.
 */
class PumpingViewModel(
    private val repository: PumpSessionRepository,
    private val settingsRepository: AppSettingsRepository,
    private val auth: AuthRepository,
    private val syncEngine: SyncEngine,
    private val workspaceId: () -> String?,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val ticker: () -> Flow<Unit> = ::secondTicker,
) : ViewModel(), PumpingActions {

    private val _uiState = MutableStateFlow(PumpingUiState())
    val uiState: StateFlow<PumpingUiState> = _uiState.asStateFlow()

    init {
        workspaceId()?.let { id ->
            viewModelScope.launch {
                repository.observeInRange(id, now() - HISTORY_WINDOW_MILLIS, Long.MAX_VALUE)
                    .map { sessions -> groupPumpsByDay(sessions, timeZone()) }
                    .collect { days -> set { it.copy(days = days) } }
            }

            // One clock for both numbers on the screen: the elapsed time of a running session and
            // the countdown to the next one. Combining the ticker in rather than looping keeps the
            // database out of the once-a-second path.
            viewModelScope.launch {
                combine(
                    repository.observeRunning(id),
                    repository.observeLatest(id),
                    settingsRepository.observe(id),
                    ticker(),
                ) { running, latest, settings, _ ->
                    val interval = settings?.pumpIntervalMinutes
                        ?: AppSettings.DEFAULT_PUMP_INTERVAL_MINUTES
                    Snapshot(
                        running = running,
                        // The session knows how to do this: a pause has to come out of the
                        // number, and while paused it stops moving altogether.
                        elapsedMillis = running?.elapsedMillisAt(now()) ?: 0,
                        countdown = nextFeedCountdown(
                            lastFedAtEpochMillis = latest?.startedAtEpochMillis,
                            intervalMinutes = interval,
                            nowEpochMillis = now(),
                        ),
                        intervalMinutes = interval,
                        today = Clock.System.todayIn(timeZone()),
                    )
                }.collect { snapshot ->
                    set {
                        it.copy(
                            running = snapshot.running,
                            elapsedMillis = snapshot.elapsedMillis,
                            countdown = snapshot.countdown,
                            intervalMinutes = snapshot.intervalMinutes,
                            today = snapshot.today,
                        )
                    }
                }
            }
        }
    }

    override fun onStartClick() {
        val workspace = workspaceId() ?: return
        if (_uiState.value.isRunning) return

        viewModelScope.launch {
            repository.start(
                workspaceId = workspace,
                userId = auth.currentUserId().orEmpty(),
                side = _uiState.value.pendingSide,
            )
        }
    }

    /**
     * Pause and resume are writes to the row, not screen state: the break is visible on the
     * partner's phone, survives a force-stop, and comes out of the duration on its own — so a
     * session interrupted to answer the door does not have to be corrected by hand afterwards.
     */
    override fun onPauseClick() {
        val running = _uiState.value.running ?: return
        if (running.isPaused) return
        viewModelScope.launch { repository.pause(running.id) }
    }

    override fun onResumeClick() {
        val running = _uiState.value.running ?: return
        if (!running.isPaused) return
        viewModelScope.launch { repository.resume(running.id) }
    }

    /**
     * Stops the clock first and asks questions second: the end time is written immediately, so a
     * dismissed sheet still leaves a correct session behind rather than one that keeps running.
     * The sheet that opens is the edit sheet, over the row that was just closed.
     */
    override fun onStopClick() {
        val running = _uiState.value.running ?: return
        val state = _uiState.value

        viewModelScope.launch {
            repository.stop(
                id = running.id,
                side = running.side,
                amountMl = null,
                note = null,
                intervalMinutes = state.intervalMinutes,
            )
            val stopped = repository.findLatest(workspaceId() ?: return@launch)
            set {
                it.copy(
                    sheetVisible = true,
                    editingSessionId = running.id,
                    discardable = true,
                    formSide = running.side,
                    formMinutes = (stopped?.durationMinutes ?: 0).toString(),
                    minutesTouched = false,
                    editingOriginalAmountMl = null,
                    formAmountMl = "",
                    formNote = "",
                    formStartedAtEpochMillis = running.startedAtEpochMillis,
                )
            }
        }
    }

    override fun onLogPastClick() = set {
        it.copy(
            sheetVisible = true,
            editingSessionId = null,
            discardable = false,
            formSide = it.pendingSide,
            formMinutes = "",
            minutesTouched = false,
            editingOriginalAmountMl = null,
            formAmountMl = "",
            formNote = "",
            // Now, then walked back: a session typed in later is usually one from earlier today.
            formStartedAtEpochMillis = now(),
        )
    }

    /**
     * A running session has no end time for the sheet to correct, so tapping its row stops it —
     * which is what anyone tapping the row that says "in progress" is trying to do anyway.
     */
    override fun onEditClick(session: PumpSession) {
        if (session.isRunning) {
            onStopClick()
            return
        }
        setEditing(session)
    }

    private fun setEditing(session: PumpSession) = set {
        it.copy(
            sheetVisible = true,
            editingSessionId = session.id,
            discardable = false,
            formSide = session.side,
            formMinutes = session.durationMinutes?.toString().orEmpty(),
            minutesTouched = false,
            editingOriginalAmountMl = session.amountMl,
            formAmountMl = session.amountMl?.toString().orEmpty(),
            formNote = session.note.orEmpty(),
            formStartedAtEpochMillis = session.startedAtEpochMillis,
        )
    }

    override fun onDismissSheet() = set {
        it.copy(sheetVisible = false, datePickerVisible = false, timePickerVisible = false)
    }

    override fun onSideChange(value: PumpSide) = set { it.copy(formSide = value) }

    /** The card's own choice, which the sheet never touches. */
    override fun onPendingSideChange(value: PumpSide) = set { it.copy(pendingSide = value) }

    /** Digits only, for the same reason as the amount: the field feeds an Int. */
    override fun onMinutesChange(value: String) = set {
        it.copy(
            formMinutes = value.filter(Char::isDigit).take(MAX_MINUTES_DIGITS),
            minutesTouched = true,
        )
    }

    override fun onAmountChange(value: String) = set {
        it.copy(formAmountMl = value.filter(Char::isDigit).take(MAX_AMOUNT_DIGITS))
    }

    override fun onNoteChange(value: String) = set { it.copy(formNote = value) }
    override fun onHistoryViewChange(value: PumpHistoryView) = set { it.copy(historyView = value) }

    override fun onOpenDatePicker() = set { it.copy(datePickerVisible = true) }
    override fun onDismissDatePicker() = set { it.copy(datePickerVisible = false) }
    override fun onOpenTimePicker() = set { it.copy(timePickerVisible = true) }
    override fun onDismissTimePicker() = set { it.copy(timePickerVisible = false) }

    /** Keeps the time of day and moves the date, so picking either one at a time works. */
    override fun onStartedDateChange(value: LocalDate) = set {
        it.copy(
            formStartedAtEpochMillis = it.formStartedAtEpochMillis.movedTo(date = value),
            datePickerVisible = false,
        )
    }

    override fun onStartedTimeChange(value: LocalTime) = set {
        it.copy(
            formStartedAtEpochMillis = it.formStartedAtEpochMillis.movedTo(time = value),
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

    override fun onSave() {
        val workspace = workspaceId() ?: return
        val state = _uiState.value
        val minutes = state.formMinutes.toIntOrNull()
        if (minutes == null || minutes <= 0) {
            set { it.copy(minutesTouched = true) }
            return
        }
        if (state.busy) return
        set { it.copy(busy = true) }

        viewModelScope.launch {
            val editingId = state.editingSessionId
            val amountMl = state.formAmountMl.toIntOrNull()
            if (editingId == null) {
                repository.logManual(
                    workspaceId = workspace,
                    userId = auth.currentUserId().orEmpty(),
                    side = state.formSide,
                    startedAt = state.formStartedAtEpochMillis,
                    durationMinutes = minutes,
                    amountMl = amountMl,
                    note = state.formNote.ifBlank { null },
                    intervalMinutes = state.intervalMinutes,
                )
            } else {
                // The start goes nowhere: the sheet shows it read-only, and the reminder already
                // scheduled off it stays correct as a result. Only the end moves.
                repository.update(
                    id = editingId,
                    side = state.formSide,
                    durationMinutes = minutes,
                    amountMl = amountMl,
                    note = state.formNote.ifBlank { null },
                )
            }
            // Drops mark a session being put away — and measuring one afterwards, which is the
            // same moment arriving late: she stops, saves, pours it into the bottle, then comes
            // back and fills the amount in. A correction that leaves the amount alone gets
            // nothing, so a re-save of an old row does not set it off.
            val amountWasMeasured = amountMl != null && amountMl != state.editingOriginalAmountMl
            val celebrate = editingId == null || state.discardable || amountWasMeasured
            set {
                it.copy(
                    busy = false,
                    sheetVisible = false,
                    editingSessionId = null,
                    discardable = false,
                    milkDrops = if (celebrate) {
                        MilkDrops(id = now(), count = dropCount(state.formAmountMl.toIntOrNull()))
                    } else {
                        it.milkDrops
                    },
                )
            }
        }
    }

    override fun onDropsShown() = set { it.copy(milkDrops = null) }

    /**
     * The stash easter egg: what the pumping has actually come to. Silent until something has
     * been measured, on the same principle as the feeding log's night watch — a panel reading
     * zero millilitres is worse than no panel.
     */
    override fun onTimerLongPress() {
        val sessions = _uiState.value.days.flatMap { it.sessions }
        val stash = milkStash(sessions, timeZone()) ?: return
        set { it.copy(stash = stash) }
    }

    override fun onDismissStash() = set { it.copy(stash = null) }

    /** For a session that was started by accident: the row is thrown away rather than kept at 0. */
    override fun onDiscard() {
        val id = _uiState.value.editingSessionId ?: return
        viewModelScope.launch {
            repository.delete(id)
            set {
                it.copy(sheetVisible = false, editingSessionId = null, discardable = false)
            }
        }
    }

    /**
     * Deletes straight away and offers the row back, rather than asking first: the row is only
     * soft-deleted, so undoing it is cheap, and a confirmation dialog on every delete is its own
     * kind of annoying at four in the morning.
     */
    override fun onDeleteSession(id: String) {
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

    /** Same pull-to-refresh contract as the other tabs: await the sync so the spinner ends with it. */
    override fun onRefresh() {
        if (_uiState.value.refreshing) return
        set { it.copy(refreshing = true) }

        viewModelScope.launch {
            syncEngine.sync()
            set { it.copy(refreshing = false) }
        }
    }

    private fun set(block: (PumpingUiState) -> PumpingUiState) {
        _uiState.value = block(_uiState.value)
    }

    private data class Snapshot(
        val running: PumpSession?,
        val elapsedMillis: Long,
        val countdown: com.oryareach.core.domain.feeding.FeedCountdown?,
        val intervalMinutes: Int,
        val today: LocalDate,
    )

    private companion object {
        const val MAX_MINUTES_DIGITS = 3
        const val MAX_AMOUNT_DIGITS = 4

        /**
         * Roughly one drop per 20 ml, clamped: enough that a good session visibly rains and a
         * small one still gets something, without either turning into weather.
         */
        fun dropCount(amountMl: Int?): Int =
            amountMl?.let { (it / 20).coerceIn(5, 18) } ?: 7

        /** The table view scrolls through days; a fortnight is as far back as it reads. */
        const val HISTORY_WINDOW_MILLIS = 14L * 24 * 60 * 60 * 1000
    }
}

/** Drives the elapsed timer and the countdown. Cancelled with the scope, so it stops with the screen. */
private fun secondTicker(): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(1_000)
    }
}

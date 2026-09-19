package com.oryareach.feature.pumping

import androidx.compose.runtime.Immutable
import com.oryareach.core.domain.feeding.FeedCountdown
import com.oryareach.core.domain.pumping.PumpingDay
import com.oryareach.core.model.AppSettings
import com.oryareach.core.model.PumpSession
import com.oryareach.core.model.PumpSide

/** The history has two shapes; the toggle above it picks which one is drawn. */
enum class PumpHistoryView { LIST, TABLE }

@Immutable
data class PumpingUiState(
    // Persisted snapshot: the live data from Room, already decrypted.
    val days: List<PumpingDay> = emptyList(),
    /** The session in progress, if any — a row with no end, not in-memory state. */
    val running: PumpSession? = null,

    // Derived on every tick.
    /** How long the running session has gone on. Zero when nothing is running. */
    val elapsedMillis: Long = 0,
    val countdown: FeedCountdown? = null,
    /** Shared setting, kept here so finishing a session can schedule the reminder off it. */
    val intervalMinutes: Int = AppSettings.DEFAULT_PUMP_INTERVAL_MINUTES,

    // The sheet. It has three ways in — a session that was just stopped, an older session being
    // corrected, and one typed in from scratch — and [editingSessionId] plus [discardable] are
    // what tell them apart.
    val sheetVisible: Boolean = false,
    /** Null while typing in a past session, the row's id when the sheet is over an existing one. */
    val editingSessionId: String? = null,
    /**
     * True only for the session that was *just* stopped: the row exists but nobody has confirmed
     * it yet, so the sheet offers to throw it away. Editing an older session never does.
     */
    val discardable: Boolean = false,
    val formSide: PumpSide = PumpSide.BOTH,
    val formMinutes: String = "",
    val formAmountMl: String = "",
    val formNote: String = "",
    /** When the session started. Defaults to now; a session typed in later moves it back. */
    val formStartedAtEpochMillis: Long = 0,
    val datePickerVisible: Boolean = false,
    val timePickerVisible: Boolean = false,

    // Transient UI-only.
    val historyView: PumpHistoryView = PumpHistoryView.LIST,
    val busy: Boolean = false,
    val refreshing: Boolean = false,
) {
    val isRunning: Boolean get() = running != null

    /**
     * The start of an existing session is read-only, for the same reason a feed's time is: the
     * whole log is arranged by it, the reminder was scheduled off it, and a correction is about
     * how long the session was, not when it began. Only a session typed in from scratch picks it.
     */
    val isEditing: Boolean get() = editingSessionId != null

    /** Duration is the one field that has to be there — it is the point of the record. */
    val canSave: Boolean get() = !busy && formMinutes.toIntOrNull()?.let { it > 0 } == true
}

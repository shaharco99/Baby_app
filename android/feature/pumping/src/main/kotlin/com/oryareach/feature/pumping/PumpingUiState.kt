package com.oryareach.feature.pumping

import androidx.compose.runtime.Immutable
import com.oryareach.core.ui.component.DropBurst
import com.oryareach.core.domain.feeding.FeedCountdown
import com.oryareach.core.domain.pumping.MilkStash
import com.oryareach.core.domain.pumping.PumpingDay
import com.oryareach.core.model.AppSettings
import com.oryareach.core.model.PumpSession
import com.oryareach.core.model.PumpSide
import kotlinx.datetime.LocalDate

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
    /**
     * Today, in the viewer's zone, carried in state rather than read during composition: the day
     * headers name today and yesterday, and a label that decides that for itself would go stale
     * without anything telling it to redraw.
     */
    val today: LocalDate? = null,
    /**
     * Which side the card will start with. Deliberately *not* [formSide]: the sheet's picker and
     * the card's picker used to be one field, so dismissing a sheet quietly rewrote what the card
     * was set to.
     */
    val pendingSide: PumpSide = PumpSide.BOTH,

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
    /** Set once the minutes field has been typed in, so the error only shows after a real attempt. */
    val minutesTouched: Boolean = false,
    /**
     * What the amount was when the sheet opened, so saving can tell a real change from a re-save.
     * Null both when the sheet is new and when the session genuinely had no amount.
     */
    val editingOriginalAmountMl: Int? = null,
    /** When the session started. Defaults to now; a session typed in later moves it back. */
    val formStartedAtEpochMillis: Long = 0,
    val datePickerVisible: Boolean = false,
    val timePickerVisible: Boolean = false,

    // Transient UI-only.
    val historyView: PumpHistoryView = PumpHistoryView.LIST,
    val busy: Boolean = false,
    val refreshing: Boolean = false,
    /** Set for one burst of falling drops after a session is put away, then cleared. */
    val milkDrops: DropBurst? = null,
    /**
     * The id of a session just deleted, while the undo is still on offer. The row is soft-deleted
     * either way — this is only what keeps the snackbar on screen.
     */
    val undoDeleteId: String? = null,
    /** The session whose trash icon was tapped, while the are-you-sure dialog is up. */
    val deleteConfirmSession: PumpSession? = null,
    /** The stash panel, when it has been asked for and there is something in it. */
    val stash: MilkStash? = null,
) {
    val isRunning: Boolean get() = running != null

    /** Paused is a running session holding still, so both of these are true at once. */
    val isPaused: Boolean get() = running?.isPaused == true

    /**
     * The start of an existing session is read-only, for the same reason a feed's time is: the
     * whole log is arranged by it, the reminder was scheduled off it, and a correction is about
     * how long the session was, not when it began. Only a session typed in from scratch picks it.
     */
    val isEditing: Boolean get() = editingSessionId != null

    /** Duration is the one field that has to be there — it is the point of the record. */
    val canSave: Boolean get() = !busy && hasMinutes

    private val hasMinutes: Boolean get() = formMinutes.toIntOrNull()?.let { it > 0 } == true

    /**
     * Marks the minutes field rather than leaving Save dead with no explanation. Only after the
     * field has been touched: an untouched form is not yet wrong, it is just empty.
     */
    val minutesError: Boolean get() = minutesTouched && !hasMinutes
}

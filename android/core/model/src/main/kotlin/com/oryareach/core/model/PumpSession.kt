package com.oryareach.core.model

import kotlinx.serialization.Serializable

/** Which breast was pumped. [BOTH] covers a double pump, the common case with an electric pump. */
enum class PumpSide {
    LEFT,
    RIGHT,
    BOTH,
}

/**
 * One pumping session.
 *
 * Unlike [FeedingEntry] there is no `babyId`: this is the mother's record, it can start before the
 * birth, and it does not change meaning when the active-child switcher moves.
 *
 * A session is *running* while [endedAtEpochMillis] is null — that is the timer's entire state,
 * kept in the database rather than in memory so it survives leaving the screen, a force-stop and a
 * reboot, and so the other partner's device can see a pump in progress.
 *
 * The duration is derived rather than stored, which keeps one source of truth: a session timed by
 * the on-screen timer and one typed in after the fact are the same row, differing only in how
 * [endedAtEpochMillis] got its value.
 *
 * Pausing is what makes the derivation more than a subtraction. [pausedMillis] is the time already
 * spent paused and [pausedAtEpochMillis] is when the current pause began, so the pumping time is
 * always wall time *minus* what was paused — and a pause taken to answer the door does not have to
 * be corrected by hand afterwards.
 */
@Serializable
data class PumpSession(
    val id: String,
    val startedAtEpochMillis: Long,
    /** Null while the session is still running. */
    val endedAtEpochMillis: Long? = null,
    val side: PumpSide = PumpSide.BOTH,
    /** Null when the output was not measured. */
    val amountMl: Int? = null,
    val note: String? = null,
    /**
     * Time already spent paused, across every pause that has been closed again. A pause still open
     * is not in here — it is in [pausedAtEpochMillis] until the session resumes or stops.
     */
    val pausedMillis: Long = 0,
    /** When the current pause began. Null unless the session is paused right now. */
    val pausedAtEpochMillis: Long? = null,
) {
    val isRunning: Boolean get() = endedAtEpochMillis == null

    /** Paused is a state a *running* session is in, not an alternative to running. */
    val isPaused: Boolean get() = isRunning && pausedAtEpochMillis != null

    /**
     * Pumping time so far, with the paused stretches taken out. The one piece of arithmetic every
     * caller needs: the live timer, the saved duration and the day totals all come through here.
     *
     * While paused it is frozen at the moment the pause began, so the number on screen stops moving
     * rather than quietly counting the break.
     */
    fun elapsedMillisAt(nowEpochMillis: Long): Long {
        val until = pausedAtEpochMillis ?: endedAtEpochMillis ?: nowEpochMillis
        return (until - startedAtEpochMillis - pausedMillis).coerceAtLeast(0)
    }

    /** Null while running. Rounded down: a 90-second session reads as one minute, not two. */
    val durationMinutes: Int?
        get() = endedAtEpochMillis?.let { (elapsedMillisAt(it) / MILLIS_PER_MINUTE).toInt() }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

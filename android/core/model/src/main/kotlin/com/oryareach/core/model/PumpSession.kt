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
) {
    val isRunning: Boolean get() = endedAtEpochMillis == null

    /** Null while running. Rounded down: a 90-second session reads as one minute, not two. */
    val durationMinutes: Int?
        get() = endedAtEpochMillis?.let { ((it - startedAtEpochMillis) / MILLIS_PER_MINUTE).toInt() }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

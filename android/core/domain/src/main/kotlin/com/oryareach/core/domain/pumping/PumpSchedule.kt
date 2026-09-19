package com.oryareach.core.domain.pumping

import com.oryareach.core.model.PumpSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * How long until the next pump is due, and the day grouping the history is drawn from.
 *
 * The countdown itself is not redefined here: `nextFeedCountdown` and `formatCountdown` in
 * [com.oryareach.core.domain.feeding] take raw epoch millis and an interval, with nothing
 * feed-specific about them, so the pumping screen reuses both — `formatCountdown` also renders the
 * live elapsed timer of a running session.
 */

/** One calendar day's sessions — a column group in the table view, a date header in the list. */
data class PumpingDay(
    val date: LocalDate,
    /** Ascending by start time, so the table's thin columns read left-to-right through the day. */
    val sessions: List<PumpSession>,
) {
    /** Null when nothing that day was measured; a partial day sums only what was. */
    val totalMl: Int? get() = sessions.mapNotNull { it.amountMl }.takeIf { it.isNotEmpty() }?.sum()

    /** Null when nothing that day has finished — a day holding only a running session. */
    val totalMinutes: Int?
        get() = sessions.mapNotNull { it.durationMinutes }.takeIf { it.isNotEmpty() }?.sum()
}

/**
 * Groups sessions into calendar days in the viewer's time zone, newest day first.
 *
 * A session is filed under the day it *started*, even one that runs past midnight: that is the
 * night it belongs to, and it keeps a session from appearing twice.
 */
fun groupPumpsByDay(sessions: List<PumpSession>, timeZone: TimeZone): List<PumpingDay> =
    sessions
        .groupBy { session ->
            Instant.fromEpochMilliseconds(session.startedAtEpochMillis).toLocalDateTime(timeZone).date
        }
        .map { (date, daySessions) ->
            PumpingDay(date = date, sessions = daySessions.sortedBy { it.startedAtEpochMillis })
        }
        .sortedByDescending { it.date }

/**
 * What the pumping added up to over the window on screen.
 *
 * A running session contributes to [totalSessions] but not to [totalMinutes] — it has no duration
 * yet, and inventing one would make the number tick upward while nobody is looking at it.
 */
data class PumpingTally(
    val totalSessions: Int,
    val totalMinutes: Int?,
    val totalMl: Int?,
)

fun pumpingTally(sessions: List<PumpSession>): PumpingTally {
    val minutes = sessions.mapNotNull { it.durationMinutes }
    val measured = sessions.mapNotNull { it.amountMl }
    return PumpingTally(
        totalSessions = sessions.size,
        totalMinutes = minutes.takeIf { it.isNotEmpty() }?.sum(),
        totalMl = measured.takeIf { it.isNotEmpty() }?.sum(),
    )
}

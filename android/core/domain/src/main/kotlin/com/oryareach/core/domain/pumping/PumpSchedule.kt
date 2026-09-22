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

/**
 * What the pumping has actually produced — the numbers behind the milk-drop panel.
 *
 * [feedsCovered] is the one figure here that is not arithmetic on the log: it turns millilitres
 * into something recognisable, because "1,440 ml" means less at 4am than "twelve feeds' worth".
 */
data class MilkStash(
    val totalMl: Int,
    val sessions: Int,
    val totalMinutes: Int,
    /** The best single day, by measured output. */
    val bestDayMl: Int,
    val feedsCovered: Int,
    /** The highest of [STASH_MILESTONES_ML] reached, in millilitres, or null before the first. */
    val milestoneMl: Int?,
)

/**
 * Round volumes worth a line. Half a litre is the first: it is roughly a day's feeds for a
 * newborn, and the first one that sounds like a quantity rather than a bottle.
 */
val STASH_MILESTONES_ML = listOf(500, 1_000, 2_000, 5_000, 10_000)

/**
 * Null until at least one session has had its output measured: a stash panel showing zero
 * millilitres would be a worse thing to show than nothing at all.
 */
fun milkStash(sessions: List<PumpSession>, timeZone: TimeZone): MilkStash? {
    val measured = sessions.filter { it.amountMl != null }
    if (measured.isEmpty()) return null

    val totalMl = measured.sumOf { it.amountMl ?: 0 }
    val perDay = measured.groupBy {
        Instant.fromEpochMilliseconds(it.startedAtEpochMillis).toLocalDateTime(timeZone).date
    }
    return MilkStash(
        totalMl = totalMl,
        sessions = sessions.size,
        totalMinutes = sessions.sumOf { it.durationMinutes ?: 0 },
        bestDayMl = perDay.values.maxOf { day -> day.sumOf { it.amountMl ?: 0 } },
        feedsCovered = totalMl / ML_PER_FEED,
        milestoneMl = STASH_MILESTONES_ML.lastOrNull { it <= totalMl },
    )
}

/** A middling bottle for a newborn. Only ever used to make a total legible, never as advice. */
private const val ML_PER_FEED = 120

fun pumpingTally(sessions: List<PumpSession>): PumpingTally {
    val minutes = sessions.mapNotNull { it.durationMinutes }
    val measured = sessions.mapNotNull { it.amountMl }
    return PumpingTally(
        totalSessions = sessions.size,
        totalMinutes = minutes.takeIf { it.isNotEmpty() }?.sum(),
        totalMl = measured.takeIf { it.isNotEmpty() }?.sum(),
    )
}

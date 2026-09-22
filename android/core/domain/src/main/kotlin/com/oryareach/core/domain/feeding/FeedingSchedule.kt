package com.oryareach.core.domain.feeding

import com.oryareach.core.model.FeedingEntry
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * How long until the next feed is due.
 *
 * [remainingMillis] goes negative once the feed is overdue rather than clamping at zero: the
 * screen shows how *late* it is, which is the number that matters at 4am.
 */
data class FeedCountdown(
    val dueAtEpochMillis: Long,
    val remainingMillis: Long,
) {
    val isOverdue: Boolean get() = remainingMillis < 0
}

/**
 * Null when there is nothing to count from — a child with no logged feed yet. The caller shows
 * "log the first feed" rather than a timer counting down from an invented starting point.
 */
fun nextFeedCountdown(
    lastFedAtEpochMillis: Long?,
    intervalMinutes: Int,
    nowEpochMillis: Long,
): FeedCountdown? {
    if (lastFedAtEpochMillis == null) return null
    val dueAt = lastFedAtEpochMillis + intervalMinutes * MILLIS_PER_MINUTE
    return FeedCountdown(dueAtEpochMillis = dueAt, remainingMillis = dueAt - nowEpochMillis)
}

/** One calendar day's feeds — a column group in the table view, a date header in the list. */
data class FeedingDay(
    val date: LocalDate,
    /** Ascending by time, so the table's thin columns read left-to-right through the day. */
    val feeds: List<FeedingEntry>,
) {
    /** Null when no feed that day was measured; a partial day sums only what was. */
    val totalMl: Int? get() = feeds.mapNotNull { it.totalMl }.takeIf { it.isNotEmpty() }?.sum()

    /**
     * What came from each source that day, each null when that source was not used — which is
     * what lets the day line show a breakdown only when there is one to show. A day fed from
     * one source has nothing to break down and reads as a single total.
     */
    val breastMl: Int? get() = feeds.mapNotNull { it.breastAmountMl }.takeIf { it.isNotEmpty() }?.sum()

    val formulaMl: Int? get() = feeds.mapNotNull { it.formulaAmountMl }.takeIf { it.isNotEmpty() }?.sum()

    /**
     * True only when both sources contributed. A day of breast feeds alone still has a
     * [breastMl] equal to its total, and repeating that number beside itself says nothing.
     */
    val hasSourceBreakdown: Boolean get() = breastMl != null && formulaMl != null
}

/**
 * Groups feeds into calendar days in the viewer's time zone, newest day first.
 *
 * The day boundary is local, not UTC: a 1am feed belongs to the night it happened in, which is
 * how the paper day-sheet this mirrors is filled in.
 */
fun groupFeedsByDay(entries: List<FeedingEntry>, timeZone: TimeZone): List<FeedingDay> =
    entries
        .groupBy { entry ->
            Instant.fromEpochMilliseconds(entry.fedAtEpochMillis).toLocalDateTime(timeZone).date
        }
        .map { (date, feeds) -> FeedingDay(date = date, feeds = feeds.sortedBy { it.fedAtEpochMillis }) }
        .sortedByDescending { it.date }

/**
 * `H:MM:SS`, unsigned — an overdue countdown reads the same as a pending one, and the label
 * beside it says which. Shared so the home page and the feeding screen can't drift apart.
 */
fun formatCountdown(millis: Long): String {
    val totalSeconds = kotlin.math.abs(millis) / 1000
    return "%d:%02d:%02d".format(totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
}

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * What the night shift actually added up to.
 *
 * [nightFeeds] counts feeds whose local hour falls in [NIGHT_START_HOUR, NIGHT_END_HOUR) — the
 * stretch nobody volunteers for. The rest are the milestones worth saying out loud: how many
 * feeds in all, how much milk that came to, how many days the log has been kept, and which
 * round number was last passed.
 *
 * Every figure is over the whole log, not a window of it. A panel that says "in all" and means
 * "in the last fortnight" gets quietly wronger the longer the log runs.
 */
data class FeedingTally(
    val totalFeeds: Int,
    val nightFeeds: Int,
    val totalMl: Int?,
    /** Distinct local days with at least one feed — not a streak, just how many days are in. */
    val daysLogged: Int,
    /** When the log starts; null only for an empty log. */
    val firstFeedEpochMillis: Long?,
    /** The highest of [FEED_MILESTONES] reached, or null before the first one. */
    val milestone: Int?,
)

/** Round numbers worth a line. Nothing below fifty: the first week alone passes ten. */
val FEED_MILESTONES = listOf(50, 100, 250, 500, 1000, 2000)

fun feedingTally(entries: List<FeedingEntry>, timeZone: TimeZone): FeedingTally {
    val byTime = entries.sortedBy { it.fedAtEpochMillis }
    val measured = byTime.mapNotNull { it.totalMl }

    val nightFeeds = byTime.count { entry ->
        val hour = Instant.fromEpochMilliseconds(entry.fedAtEpochMillis)
            .toLocalDateTime(timeZone).hour
        hour in NIGHT_START_HOUR until NIGHT_END_HOUR
    }

    val daysLogged = byTime
        .map { Instant.fromEpochMilliseconds(it.fedAtEpochMillis).toLocalDateTime(timeZone).date }
        .distinct()
        .size

    return FeedingTally(
        totalFeeds = byTime.size,
        nightFeeds = nightFeeds,
        totalMl = measured.takeIf { it.isNotEmpty() }?.sum(),
        daysLogged = daysLogged,
        firstFeedEpochMillis = byTime.firstOrNull()?.fedAtEpochMillis,
        milestone = FEED_MILESTONES.lastOrNull { it <= byTime.size },
    )
}

private const val NIGHT_START_HOUR = 0
private const val NIGHT_END_HOUR = 6

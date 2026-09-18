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
    val totalMl: Int? get() = feeds.mapNotNull { it.amountMl }.takeIf { it.isNotEmpty() }?.sum()
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

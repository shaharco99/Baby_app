package com.oryareach.core.domain.diaper

import com.oryareach.core.model.DiaperChange
import com.oryareach.core.model.FeedingEntry
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * One nappy change, wherever it was logged.
 *
 * The nappy log is a read over two sources: changes typed into the nappy screen
 * ([DiaperChange]) and feeds with urine or stool marked on them ([FeedingEntry]). Neither is
 * copied into the other, so a feed edited or deleted on the feeding screen moves here with it.
 * [fromFeed] says which one a row is, because only the screen that owns a row can edit it.
 */
data class DiaperEvent(
    val id: String,
    val atEpochMillis: Long,
    val hadUrine: Boolean,
    val hadStool: Boolean,
    val note: String?,
    val fromFeed: Boolean,
) {
    val isDry: Boolean get() = !hadUrine && !hadStool
}

/** One local calendar day of the nappy log, oldest change first. */
data class DiaperDay(
    val date: LocalDate,
    val events: List<DiaperEvent>,
) {
    /** Nappies changed that day — every event, dry ones included. */
    val changeCount: Int get() = events.size

    val urineCount: Int get() = events.count { it.hadUrine }

    val stoolCount: Int get() = events.count { it.hadStool }
}

/**
 * Merges feeds and stand-alone changes into days in the viewer's zone, newest day first.
 *
 * A feed only counts when something was marked on it: a feed with neither mark says nothing
 * about a nappy, and counting it would make every feed a change.
 */
fun diaperDays(
    feeds: List<FeedingEntry>,
    changes: List<DiaperChange>,
    timeZone: TimeZone,
): List<DiaperDay> =
    diaperEvents(feeds, changes)
        .groupBy { Instant.fromEpochMilliseconds(it.atEpochMillis).toLocalDateTime(timeZone).date }
        .map { (date, events) -> DiaperDay(date = date, events = events.sortedBy { it.atEpochMillis }) }
        .sortedByDescending { it.date }

/**
 * Every nappy change in [feeds] and [changes], oldest first — the same rows the nappy page shows,
 * ungrouped, so the doctor summary counts exactly what that page counts.
 */
fun diaperEvents(feeds: List<FeedingEntry>, changes: List<DiaperChange>): List<DiaperEvent> {
    val fromFeeds = feeds
        .filter { it.hadUrine || it.hadStool }
        .map {
            DiaperEvent(
                id = it.id,
                atEpochMillis = it.fedAtEpochMillis,
                hadUrine = it.hadUrine,
                hadStool = it.hadStool,
                note = null,
                fromFeed = true,
            )
        }
    val own = changes.map {
        DiaperEvent(
            id = it.id,
            atEpochMillis = it.changedAtEpochMillis,
            hadUrine = it.hadUrine,
            hadStool = it.hadStool,
            note = it.note,
            fromFeed = false,
        )
    }
    return (fromFeeds + own).sortedBy { it.atEpochMillis }
}

/** Nappies changed on [from] and every day after it, for the "last 7 days" line. */
fun changesSince(days: List<DiaperDay>, from: LocalDate): Int =
    days.filter { it.date >= from }.sumOf { it.changeCount }

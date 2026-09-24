package com.oryareach.core.domain.feeding

import com.oryareach.core.model.FeedingEntry
import com.oryareach.core.model.VitaminDose
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * What a stretch of the feeding log adds up to — the figures a doctor or nurse asks for.
 *
 * Amounts are null when nothing in the stretch was measured, the same as a day's total: "0 ml"
 * would claim the baby drank nothing, when the truth is nobody wrote it down.
 */
data class FeedingStretch(
    val feeds: Int,
    val totalMl: Int?,
    val breastMl: Int?,
    val formulaMl: Int?,
    val urineCount: Int,
    val stoolCount: Int,
    /** Mean minutes between consecutive feeds; null with fewer than two feeds. */
    val averageGapMinutes: Int?,
)

/**
 * The quick look shown before a checkup: the last 24 hours as they happened, and the complete
 * days before today, one by one and averaged.
 *
 * The week is complete days only — yesterday and the six before it, never today. Today is still
 * being logged, and folding a half day into a daily average drags it down every morning. Days
 * before the birth, and before the first feed was ever logged, are left out rather than counted
 * as empty: a day nobody was logging yet is not a day the baby went unfed.
 */
data class DoctorSummary(
    val last24Hours: FeedingStretch,
    /** Oldest first, one entry per day even when nothing was logged, so a gap shows as a gap. */
    val days: List<FeedingDay>,
    val week: FeedingStretch,
    /** Of [days], how many had the vitamin given. */
    val vitaminDays: Int,
) {
    val dayCount: Int get() = days.size

    /** Per-day averages over [days]; null when there are no complete days yet. */
    val averageFeedsPerDay: Double? get() = perDay(week.feeds)
    val averageMlPerDay: Double? get() = week.totalMl?.let(::perDay)
    val averageUrinePerDay: Double? get() = perDay(week.urineCount)
    val averageStoolPerDay: Double? get() = perDay(week.stoolCount)

    private fun perDay(value: Int): Double? = dayCount.takeIf { it > 0 }?.let { value.toDouble() / it }
}

fun summarizeFeeds(entries: List<FeedingEntry>): FeedingStretch {
    val byTime = entries.sortedBy { it.fedAtEpochMillis }
    val gaps = byTime.zipWithNext { a, b -> b.fedAtEpochMillis - a.fedAtEpochMillis }
    return FeedingStretch(
        feeds = byTime.size,
        totalMl = byTime.mapNotNull { it.totalMl }.takeIf { it.isNotEmpty() }?.sum(),
        breastMl = byTime.mapNotNull { it.breastAmountMl }.takeIf { it.isNotEmpty() }?.sum(),
        formulaMl = byTime.mapNotNull { it.formulaAmountMl }.takeIf { it.isNotEmpty() }?.sum(),
        urineCount = byTime.count { it.hadUrine },
        stoolCount = byTime.count { it.hadStool },
        averageGapMinutes = gaps.takeIf { it.isNotEmpty() }?.let { (it.sum() / it.size / MILLIS_PER_MINUTE).toInt() },
    )
}

/**
 * Builds the summary from the raw log. [feeds] and [doses] may cover more than the week; only
 * what falls in each window is counted.
 */
fun doctorSummary(
    feeds: List<FeedingEntry>,
    doses: List<VitaminDose>,
    nowEpochMillis: Long,
    timeZone: TimeZone,
    birthDate: LocalDate?,
    /** When the log begins — the first feed ever logged, not the first in [feeds]. */
    firstFeedEpochMillis: Long?,
    weekDays: Int = SUMMARY_WEEK_DAYS,
): DoctorSummary {
    fun Long.localDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(timeZone).date

    val today = nowEpochMillis.localDate()
    val logStart = firstFeedEpochMillis?.localDate()
    val dates = (weekDays downTo 1)
        .map { today.minus(it, DateTimeUnit.DAY) }
        .filter { birthDate == null || it >= birthDate }
        .filter { logStart == null || it >= logStart }

    val byDate = feeds.groupBy { it.fedAtEpochMillis.localDate() }
    val days = dates.map { date ->
        FeedingDay(date = date, feeds = byDate[date].orEmpty().sortedBy { it.fedAtEpochMillis })
    }
    val dosedDates = doses.map { it.givenAtEpochMillis.localDate() }.toSet()

    val dayStart = nowEpochMillis - MILLIS_PER_DAY
    return DoctorSummary(
        last24Hours = summarizeFeeds(feeds.filter { it.fedAtEpochMillis in dayStart..nowEpochMillis }),
        days = days,
        week = summarizeFeeds(days.flatMap { it.feeds }),
        vitaminDays = dates.count { it in dosedDates },
    )
}

/** A week of complete days: long enough to smooth one bad night, short enough to be current. */
const val SUMMARY_WEEK_DAYS = 7

private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_DAY = 24 * 60 * MILLIS_PER_MINUTE

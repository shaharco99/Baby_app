package com.oryareach.core.domain.log

/**
 * How many days of a log stay open at the top of the screen.
 *
 * Two, so "today" always has "yesterday" under it. Overnight that matters more than it sounds:
 * at two in the morning the feed being compared against happened on yesterday's date, and a log
 * that showed only today would open on an almost-empty screen.
 */
const val RECENT_LOG_DAYS = 2

/**
 * A log split into the days shown in full and the days folded into a drawer.
 *
 * Generic over the day type because the feeding and pumping logs group into their own shapes
 * ([com.oryareach.core.domain.feeding.FeedingDay] and
 * [com.oryareach.core.domain.pumping.PumpingDay]) but fold away identically.
 */
data class LogDays<T>(
    val recent: List<T>,
    val older: List<T>,
)

/**
 * Splits a newest-first list of days into the ones to show and the ones to fold away.
 *
 * Both callers group newest day first, and this trusts that rather than re-sorting: the day
 * types have no common supertype to read a date off, and the two groupers are the only things
 * that ever build these lists.
 *
 * A [recentCount] of zero or less folds everything away, which is what an argument of nothing
 * should mean; nothing is ever dropped, so `recent + older` is always the whole log.
 */
fun <T> splitLogDays(days: List<T>, recentCount: Int = RECENT_LOG_DAYS): LogDays<T> {
    val kept = recentCount.coerceIn(0, days.size)
    return LogDays(recent = days.take(kept), older = days.drop(kept))
}

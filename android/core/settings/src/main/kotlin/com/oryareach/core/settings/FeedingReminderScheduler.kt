package com.oryareach.core.settings

/**
 * Schedules or cancels the one-shot notification for the next feed. Same seam as
 * [ReminderScheduler]: the alarm-backed implementation lives in `:app`, so the modules
 * that trigger it — `:core:database`'s feeding repository, `:feature:settings` — never need to
 * depend on AlarmManager.
 *
 * Unlike [ReminderScheduler]'s periodic daily nag, this is a single alarm that moves every time
 * a feed is logged: scheduling replaces whatever was pending rather than adding to it.
 */
interface FeedingReminderScheduler {
    /** Fires [intervalMinutes] after [fedAtEpochMillis]. A time already past is not rung: the app's countdown already reads "overdue". */
    fun scheduleNext(fedAtEpochMillis: Long, intervalMinutes: Int)

    fun cancel()
}

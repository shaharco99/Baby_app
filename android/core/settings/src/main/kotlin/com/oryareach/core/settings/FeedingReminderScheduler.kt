package com.oryareach.core.settings

/**
 * Schedules or cancels the one-shot notification for the next feed. Same seam as
 * [ReminderScheduler]: the WorkManager-backed implementation lives in `:app`, so the modules
 * that trigger it — `:core:database`'s feeding repository, `:feature:settings` — never need to
 * depend on WorkManager.
 *
 * Unlike [ReminderScheduler]'s periodic daily nag, this is a single alarm that moves every time
 * a feed is logged: scheduling replaces whatever was pending rather than adding to it.
 */
interface FeedingReminderScheduler {
    /** Fires [intervalMinutes] after [fedAtEpochMillis]; a time already past fires promptly. */
    fun scheduleNext(fedAtEpochMillis: Long, intervalMinutes: Int)

    fun cancel()
}

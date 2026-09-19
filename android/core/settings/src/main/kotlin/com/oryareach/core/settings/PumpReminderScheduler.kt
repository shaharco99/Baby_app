package com.oryareach.core.settings

/**
 * Schedules or cancels the one-shot notification for the next pumping session. Same seam as
 * [FeedingReminderScheduler], and a separate one on purpose: the two reminders have their own
 * intervals, their own notification channel and their own pending alarm, so silencing or moving
 * one never touches the other.
 *
 * Counted from when a session *starts*, not when it ends — that is how a pump schedule is spaced.
 */
interface PumpReminderScheduler {
    /** Fires [intervalMinutes] after [startedAtEpochMillis]; a time already past fires promptly. */
    fun scheduleNext(startedAtEpochMillis: Long, intervalMinutes: Int)

    fun cancel()
}

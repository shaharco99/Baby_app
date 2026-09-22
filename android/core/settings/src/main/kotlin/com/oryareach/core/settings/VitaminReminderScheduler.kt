package com.oryareach.core.settings

/**
 * Schedules or cancels the daily supplement reminder. Same seam as [FeedingReminderScheduler]:
 * the alarm-backed implementation lives in `:app`, so `:core:database` and the feature modules
 * never need to depend on AlarmManager.
 *
 * Unlike the feed and pump reminders, this one is a fixed wall-clock time rather than an
 * interval after something that happened, and it repeats. The implementation re-arms the next
 * day's alarm as soon as one rings, so a phone that is never opened keeps being reminded.
 */
interface VitaminReminderScheduler {
    /**
     * Rings at [dueAtEpochMillis], and thereafter each day at [minuteOfDay] — minutes past local
     * midnight, which is what the re-arm needs to survive a daylight-saving change.
     */
    fun scheduleAt(dueAtEpochMillis: Long, minuteOfDay: Int)

    /** Stops the reminder and forgets the hour, so nothing re-arms it. */
    fun cancel()

    /** The next moment the local clock reads [minuteOfDay]: today if still ahead, else tomorrow. */
    fun nextOccurrence(minuteOfDay: Int): Long
}

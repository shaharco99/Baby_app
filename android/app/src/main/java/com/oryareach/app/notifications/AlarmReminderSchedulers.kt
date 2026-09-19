package com.oryareach.app.notifications

import android.content.Context
import com.oryareach.core.settings.FeedingReminderScheduler
import com.oryareach.core.settings.PumpReminderScheduler

private const val MILLIS_PER_MINUTE = 60_000L

class AlarmFeedingReminderScheduler(private val context: Context) : FeedingReminderScheduler {
    override fun scheduleNext(fedAtEpochMillis: Long, intervalMinutes: Int) =
        ReminderAlarms.schedule(context, ReminderKind.FEEDING, fedAtEpochMillis + intervalMinutes * MILLIS_PER_MINUTE)

    override fun cancel() = ReminderAlarms.cancel(context, ReminderKind.FEEDING)
}

class AlarmPumpReminderScheduler(private val context: Context) : PumpReminderScheduler {
    override fun scheduleNext(startedAtEpochMillis: Long, intervalMinutes: Int) =
        ReminderAlarms.schedule(context, ReminderKind.PUMP, startedAtEpochMillis + intervalMinutes * MILLIS_PER_MINUTE)

    override fun cancel() = ReminderAlarms.cancel(context, ReminderKind.PUMP)
}

package com.oryareach.app.notifications

import android.content.Context
import com.oryareach.app.widget.FeedWidgetStore
import com.oryareach.core.settings.FeedingReminderScheduler
import com.oryareach.core.settings.PumpReminderScheduler
import com.oryareach.core.settings.VitaminReminderScheduler

private const val MILLIS_PER_MINUTE = 60_000L

/** How long before a feed is due the heads-up rings. */
private const val FEED_HEADS_UP_MINUTES = 10

/**
 * Also the feed widget's only writer: every path that moves the feed reminder — a feed logged
 * here, one pulled from the partner, the interval changed — comes through here, so the widget
 * moves with it.
 */
class AlarmFeedingReminderScheduler(private val context: Context) : FeedingReminderScheduler {
    override fun scheduleNext(fedAtEpochMillis: Long, intervalMinutes: Int) {
        val dueAt = fedAtEpochMillis + intervalMinutes * MILLIS_PER_MINUTE
        ReminderAlarms.schedule(context, ReminderKind.FEEDING, dueAt)
        ReminderAlarms.schedule(context, ReminderKind.FEEDING_SOON, dueAt - FEED_HEADS_UP_MINUTES * MILLIS_PER_MINUTE)
        FeedWidgetStore.save(context, lastFedAt = fedAtEpochMillis, dueAt = dueAt)
    }

    override fun cancel() {
        ReminderAlarms.cancel(context, ReminderKind.FEEDING)
        ReminderAlarms.cancel(context, ReminderKind.FEEDING_SOON)
        FeedWidgetStore.clear(context)
    }
}

class AlarmPumpReminderScheduler(private val context: Context) : PumpReminderScheduler {
    override fun scheduleNext(startedAtEpochMillis: Long, intervalMinutes: Int) =
        ReminderAlarms.schedule(context, ReminderKind.PUMP, startedAtEpochMillis + intervalMinutes * MILLIS_PER_MINUTE)

    override fun cancel() = ReminderAlarms.cancel(context, ReminderKind.PUMP)
}

class AlarmVitaminReminderScheduler(private val context: Context) : VitaminReminderScheduler {
    override fun scheduleAt(dueAtEpochMillis: Long, minuteOfDay: Int) =
        ReminderAlarms.scheduleDaily(context, dueAtEpochMillis, minuteOfDay)

    override fun cancel() = ReminderAlarms.cancelDaily(context)

    override fun nextOccurrence(minuteOfDay: Int): Long = ReminderAlarms.nextOccurrence(minuteOfDay)
}

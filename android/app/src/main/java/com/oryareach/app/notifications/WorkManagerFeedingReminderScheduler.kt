package com.oryareach.app.notifications

import android.content.Context
import com.oryareach.core.settings.FeedingReminderScheduler

class WorkManagerFeedingReminderScheduler(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) : FeedingReminderScheduler {

    override fun scheduleNext(fedAtEpochMillis: Long, intervalMinutes: Int) {
        val dueAt = fedAtEpochMillis + intervalMinutes * MILLIS_PER_MINUTE
        FeedingReminderWorker.scheduleAfter(context, dueAt - now())
    }

    override fun cancel() = FeedingReminderWorker.cancel(context)

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

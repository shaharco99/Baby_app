package com.oryareach.app.notifications

import android.content.Context
import com.oryareach.core.settings.PumpReminderScheduler

class WorkManagerPumpReminderScheduler(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) : PumpReminderScheduler {

    override fun scheduleNext(startedAtEpochMillis: Long, intervalMinutes: Int) {
        val dueAt = startedAtEpochMillis + intervalMinutes * MILLIS_PER_MINUTE
        PumpReminderWorker.scheduleAfter(context, dueAt - now())
    }

    override fun cancel() = PumpReminderWorker.cancel(context)

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

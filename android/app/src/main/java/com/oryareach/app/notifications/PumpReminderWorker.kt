package com.oryareach.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * One-shot, not periodic, for the same reason as [FeedingReminderWorker]: the next pump is a
 * moving target, so each finished session replaces the pending alarm instead of queueing another
 * behind it. Its own unique name keeps it clear of the feeding reminder's.
 */
class PumpReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        PumpReminderNotifier.show(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK = "pump-reminder-next"

        fun scheduleAfter(context: Context, delayMillis: Long) {
            PumpReminderNotifier.ensureChannel(context)
            val request = OneTimeWorkRequestBuilder<PumpReminderWorker>()
                .setInitialDelay(delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        }
    }
}

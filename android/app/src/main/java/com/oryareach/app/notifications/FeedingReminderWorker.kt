package com.oryareach.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * One-shot, not periodic: the next feed is a moving target, so every logged feed replaces the
 * pending alarm rather than queueing another one behind it. `ExistingWorkPolicy.REPLACE` on a
 * single unique name is what makes that automatic.
 */
class FeedingReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        FeedingReminderNotifier.show(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK = "feeding-reminder-next"

        fun scheduleAfter(context: Context, delayMillis: Long) {
            FeedingReminderNotifier.ensureChannel(context)
            val request = OneTimeWorkRequestBuilder<FeedingReminderWorker>()
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

package com.oryareach.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.oryareach.core.common.AppResult
import com.oryareach.core.database.reminder.FeedingReminderRefresher
import com.oryareach.core.database.reminder.PumpReminderRefresher
import com.oryareach.core.database.reminder.VitaminReminderRefresher
import com.oryareach.core.sync.SyncEngine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Runs one sync cycle in the background.
 *
 * Retries only for transient failures. A conflict is not retried: replaying the same write
 * would conflict again forever, and the record is already parked for a person to resolve.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val engine: SyncEngine by inject()
    private val feedingReminders: FeedingReminderRefresher by inject()
    private val pumpReminders: PumpReminderRefresher by inject()
    private val vitaminReminders: VitaminReminderRefresher by inject()

    override suspend fun doWork(): Result = when (val outcome = engine.sync()) {
        is AppResult.Success -> {
            // A feed or pump logged on the partner's phone arrives here, never through this
            // device's repositories — so this is where it moves this device's alarm.
            if (outcome.data.pulled > 0) {
                feedingReminders.refresh()
                pumpReminders.refresh()
                // A dose ticked off on the partner's phone silences this one's reminder too.
                vitaminReminders.refresh()
            }
            if (outcome.data.shouldRetry) Result.retry() else Result.success()
        }

        // A hard failure is still worth another attempt with backoff; the outbox is intact.
        is AppResult.Failure -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        private const val MAX_ATTEMPTS = 5
        private const val ONE_SHOT = "sync-now"
        private const val PERIODIC = "sync-periodic"
        private const val POLL = "sync-foreground-poll"

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Called after a local write, so a change reaches the other device promptly. */
        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                // APPEND_OR_REPLACE, not KEEP: KEEP silently drops a syncNow() call that
                // arrives while a run is already in flight, with nothing to catch up
                // afterward — a burst of local writes (e.g. importing a snapshot, seeding
                // the hospital-bag preset) would then permanently strand whatever was
                // created after the in-flight run had already read the outbox. APPEND
                // guarantees at least one more run after the current one finishes; since
                // each run drains the outbox until empty, the extra runs a burst produces
                // are cheap no-ops rather than duplicate work.
                .enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        /**
         * The foreground poll. KEEP, unlike [syncNow]: a poll only needs *a* run to be pending,
         * and appending one every tick while offline would stack a long queue for reconnect.
         */
        fun pollNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(POLL, ExistingWorkPolicy.KEEP, request)
        }

        /**
         * Safety net for changes made on the other device while this one was idle.
         *
         * Hourly rather than six-hourly since the push wake-up landed: push is now the thing
         * that keeps the reminder honest, and this exists to bound how long a *dropped* push
         * can leave an alarm wrong. Six hours was a reasonable period for the only background
         * path there was; it is far too long for a backstop to one.
         */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                // UPDATE, not KEEP: KEEP would leave an existing installation on whatever
                // period it was registered with, so the shortened interval would only ever
                // reach a fresh install.
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}

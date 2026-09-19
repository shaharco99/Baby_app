package com.oryareach.core.database.reminder

import com.oryareach.core.database.repository.AppSettingsRepository
import com.oryareach.core.database.repository.PumpSessionRepository
import com.oryareach.core.model.AppSettings
import com.oryareach.core.settings.PumpReminderScheduler
import kotlinx.coroutines.flow.first

/**
 * Re-derives the pending pump reminder from what is actually in the database, for the same reasons
 * as [FeedingReminderRefresher]: a reinstall drops WorkManager's queue, a changed interval
 * invalidates the pending alarm, and a session logged on the partner's device arrives by sync
 * without ever passing through this device's repository.
 *
 * No child checks here — pumping is not scoped to a baby, and can start before the birth.
 */
class PumpReminderRefresher(
    private val sessions: PumpSessionRepository,
    private val settings: AppSettingsRepository,
    private val scheduler: PumpReminderScheduler,
    private val workspaceId: () -> String?,
) {
    suspend fun refresh() {
        val workspace = workspaceId() ?: return

        // Nothing to remind about before the first session: the session that starts the log
        // schedules the reminder itself.
        val latest = sessions.findLatest(workspace)
        if (latest == null) {
            scheduler.cancel()
            return
        }

        val interval = settings.observe(workspace).first()?.pumpIntervalMinutes
            ?: AppSettings.DEFAULT_PUMP_INTERVAL_MINUTES
        scheduler.scheduleNext(latest.startedAtEpochMillis, interval)
    }
}

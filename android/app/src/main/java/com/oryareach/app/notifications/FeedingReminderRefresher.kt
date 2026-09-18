package com.oryareach.app.notifications

import com.oryareach.core.database.repository.AppSettingsRepository
import com.oryareach.core.database.repository.BabyRepository
import com.oryareach.core.database.repository.FeedingEntryRepository
import com.oryareach.core.model.AppSettings
import com.oryareach.core.settings.FeedingReminderScheduler
import kotlinx.coroutines.flow.first

/**
 * Re-derives the pending feed reminder from what is actually in the database.
 *
 * A reminder is scheduled when a feed is logged, but WorkManager's queue does not survive
 * everything: a reinstall drops it, a changed interval invalidates it, and a feed logged on the
 * partner's device arrives by sync without ever going through this device's repository. Running
 * this at startup — and after the interval changes in Settings — puts it back.
 */
class FeedingReminderRefresher(
    private val babies: BabyRepository,
    private val feeds: FeedingEntryRepository,
    private val settings: AppSettingsRepository,
    private val scheduler: FeedingReminderScheduler,
    private val workspaceId: () -> String?,
) {
    suspend fun refresh() {
        val workspace = workspaceId() ?: return
        val baby = babies.observeActive(workspace).first()

        // Nothing to remind about before the child is born or before the first feed: the feed
        // that starts the log schedules the reminder itself.
        if (baby == null || !baby.isBorn) {
            scheduler.cancel()
            return
        }

        val lastFeed = feeds.findLatest(workspace, baby.id)
        if (lastFeed == null) {
            scheduler.cancel()
            return
        }

        val interval = settings.observe(workspace).first()?.feedIntervalMinutes
            ?: AppSettings.DEFAULT_FEED_INTERVAL_MINUTES
        scheduler.scheduleNext(lastFeed.fedAtEpochMillis, interval)
    }
}

package com.oryareach.app.watch

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.oryareach.core.database.repository.AppSettingsRepository
import com.oryareach.core.database.repository.BabyRepository
import com.oryareach.core.database.repository.FeedingEntryRepository
import com.oryareach.core.database.repository.PumpSessionRepository
import com.oryareach.core.model.AppSettings
import com.oryareach.core.model.FeedingEntry
import com.oryareach.core.watch.RunningTimer
import com.oryareach.core.watch.WatchTimers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

private const val MILLIS_PER_MINUTE = 60_000L
private const val TAG = "WatchTimers"

/**
 * Hands the feed and pump clocks to the watch apps: Wear OS over the Wearable Data Layer, Garmin
 * through Garmin Connect ([GarminTimersSink]).
 *
 * Two ways in, for the same reason the reminders have two: [follow] runs while a workspace is
 * open in this process and catches every Room write — a feed logged here, a pump paused, a row
 * pulled in by sync — the moment it lands; [publishOnce] is for the background sync worker, which
 * pulls a partner's feed into a process where nothing is following.
 *
 * Only timestamps leave the phone; see [WatchTimers]. A phone with no watch, or no Play services,
 * fails the put — logged and otherwise ignored, never surfaced: the watch is an extra.
 */
class WatchTimerPublisher(
    private val context: Context,
    private val babies: BabyRepository,
    private val feeds: FeedingEntryRepository,
    private val pumps: PumpSessionRepository,
    private val settings: AppSettingsRepository,
    private val garmin: GarminTimersSink,
    private val workspaceId: () -> String?,
) {
    /** Never returns while [workspace] stays open; cancel the collecting job to stop. */
    suspend fun follow(workspace: String) {
        timers(workspace).distinctUntilChanged().collectLatest { put(it) }
    }

    suspend fun publishOnce() {
        val workspace = workspaceId() ?: return
        put(timers(workspace).first())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun timers(workspace: String): Flow<WatchTimers> {
        val feedSide: Flow<Pair<FeedingEntry?, FeedingEntry?>> =
            babies.observeActive(workspace).flatMapLatest { baby ->
                // Before the birth there is no feed clock at all — the same rule the feed
                // reminder follows.
                if (baby == null || !baby.isBorn) {
                    flowOf(null to null)
                } else {
                    combine(
                        feeds.observeLatest(workspace, baby.id),
                        feeds.observeRunningNursing(workspace, baby.id),
                    ) { latest, nursing -> latest to nursing }
                }
            }

        return combine(
            feedSide,
            pumps.observeLatest(workspace),
            pumps.observeRunning(workspace),
            settings.observe(workspace),
        ) { (lastFeed, nursing), lastPump, runningPump, appSettings ->
            val feedInterval = appSettings?.feedIntervalMinutes ?: AppSettings.DEFAULT_FEED_INTERVAL_MINUTES
            val pumpInterval = appSettings?.pumpIntervalMinutes ?: AppSettings.DEFAULT_PUMP_INTERVAL_MINUTES
            WatchTimers(
                lastFedAt = lastFeed?.fedAtEpochMillis,
                feedDueAt = lastFeed?.let { it.fedAtEpochMillis + feedInterval * MILLIS_PER_MINUTE },
                nursing = nursing?.let {
                    RunningTimer(
                        startedAt = it.fedAtEpochMillis,
                        pausedMillis = it.nursingPausedMillis,
                        pausedAt = it.nursingPausedAtEpochMillis,
                    )
                },
                lastPumpAt = lastPump?.startedAtEpochMillis,
                pumpDueAt = lastPump?.let { it.startedAtEpochMillis + pumpInterval * MILLIS_PER_MINUTE },
                pumping = runningPump?.let {
                    RunningTimer(
                        startedAt = it.startedAtEpochMillis,
                        pausedMillis = it.pausedMillis,
                        pausedAt = it.pausedAtEpochMillis,
                    )
                },
            )
        }
    }

    private suspend fun put(timers: WatchTimers) {
        putOnWear(timers)
        garmin.put(timers)
    }

    private suspend fun putOnWear(timers: WatchTimers) {
        val request = PutDataMapRequest.create(WatchTimers.PATH).apply {
            dataMap.putAll(timers.toDataMap())
        }.asPutDataRequest().setUrgent()
        try {
            Wearable.getDataClient(context).putDataItem(request).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // ApiException when the phone has no Wear OS companion at all; nothing to do.
            Log.i(TAG, "No watch to update: ${e.message}")
        }
    }
}

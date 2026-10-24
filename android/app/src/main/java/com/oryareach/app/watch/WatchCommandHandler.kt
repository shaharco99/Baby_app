package com.oryareach.app.watch

import android.util.Log
import com.oryareach.core.database.repository.AppSettingsRepository
import com.oryareach.core.database.repository.BabyRepository
import com.oryareach.core.database.repository.FeedingEntryRepository
import com.oryareach.core.model.AppSettings
import com.oryareach.core.model.FeedType
import com.oryareach.core.model.PumpSide
import com.oryareach.core.network.auth.AuthRepository
import com.oryareach.core.watch.CommandResult
import com.oryareach.core.watch.NursingSide
import com.oryareach.core.watch.WatchCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val TAG = "WatchCommand"

/** A second "log feed" inside this window is the same tap arriving twice, not a second feed. */
private const val DUPLICATE_WINDOW_MILLIS = 20_000L

/**
 * Does what the watch asked, through the same repositories the phone's own screens use — so the
 * feed reminder, the home-screen widget, sync and the partner's phone all follow as they would
 * for a tap on the phone.
 *
 * Works with the app closed or locked, like background sync: the lock is on the UI, and the
 * workspace is found the same way the reminder refreshers find it ([workspaceId]).
 */
class WatchCommandHandler(
    private val babies: BabyRepository,
    private val feeds: FeedingEntryRepository,
    private val settings: AppSettingsRepository,
    private val auth: AuthRepository,
    private val publisher: WatchTimerPublisher,
    private val workspaceId: () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun handle(command: WatchCommand): CommandResult {
        val result = try {
            run(command)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Watch command failed: ${e.message}")
            CommandResult.FAILED
        }
        // This process may have no follow running (app closed): push the new clocks explicitly.
        if (result == CommandResult.DONE) publisher.publishOnce()
        return result
    }

    private suspend fun run(command: WatchCommand): CommandResult {
        val workspace = workspaceId() ?: return CommandResult.NOTHING_TO_LOG
        val baby = babies.observeActive(workspace).first()?.takeIf { it.isBorn }
            ?: return CommandResult.NOTHING_TO_LOG
        val interval = settings.observe(workspace).first()?.feedIntervalMinutes
            ?: AppSettings.DEFAULT_FEED_INTERVAL_MINUTES
        val userId = auth.currentUserId().orEmpty()

        return when (command) {
            WatchCommand.LogFeed -> {
                val latest = feeds.findLatest(workspace, baby.id)
                if (latest != null && now() - latest.fedAtEpochMillis in 0 until DUPLICATE_WINDOW_MILLIS) {
                    return CommandResult.DONE
                }
                feeds.logFeed(
                    workspaceId = workspace,
                    babyId = baby.id,
                    userId = userId,
                    feedType = FeedType.BREAST_MILK,
                    breastMl = null,
                    formulaMl = null,
                    hadUrine = false,
                    hadStool = false,
                    note = null,
                    intervalMinutes = interval,
                )
                CommandResult.DONE
            }
            is WatchCommand.StartNursing -> {
                // Returns the one already running if there is one, so a repeat tap is harmless.
                feeds.startNursing(
                    workspaceId = workspace,
                    babyId = baby.id,
                    userId = userId,
                    side = command.side.toPumpSide(),
                    intervalMinutes = interval,
                )
                CommandResult.DONE
            }
            WatchCommand.PauseNursing -> withRunning(workspace, baby.id) { feeds.pauseNursing(it) }
            WatchCommand.ResumeNursing -> withRunning(workspace, baby.id) { feeds.resumeNursing(it) }
            WatchCommand.StopNursing -> withRunning(workspace, baby.id) { feeds.stopNursing(it) }
        }
    }

    private suspend fun withRunning(workspace: String, babyId: String, act: suspend (String) -> Unit): CommandResult {
        val running = feeds.observeRunningNursing(workspace, babyId).first() ?: return CommandResult.NOT_RUNNING
        act(running.id)
        return CommandResult.DONE
    }

    private fun NursingSide.toPumpSide() = when (this) {
        NursingSide.LEFT -> PumpSide.LEFT
        NursingSide.RIGHT -> PumpSide.RIGHT
        NursingSide.BOTH -> PumpSide.BOTH
    }
}

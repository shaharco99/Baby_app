package com.oryareach.app.sync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.oryareach.app.di.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pulls the partner's changes while the app is on screen.
 *
 * Before this, a change from the other phone only arrived when this one wrote something itself,
 * reopened, or hit the six-hourly background sync. With both phones open side by side, a feed
 * logged on one did not show on the other at all. Now: a sync every time the app comes back to the
 * foreground, then every [INTERVAL_MILLIS] while it stays there. A sync with nothing new is one
 * small request, and nothing runs once the app is in the background.
 *
 * Process-wide foreground, not an Activity's, for the same reason as `AutoLockController`.
 */
class ForegroundSyncController(
    private val session: SessionState,
    private val poll: () -> Unit,
    private val refreshReminders: suspend () -> Unit,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null

    override fun onStart(owner: LifecycleOwner) {
        loop?.cancel()
        loop = scope.launch {
            // The pending alarms are re-derived once on the way back in, before the first poll.
            // A workspace that was already open when the app went away never emits on
            // `workspaceIdFlow` again, so nothing else puts an alarm back that drifted while
            // the app was in the background — a feed the partner logged and this device pulled
            // on its own, say, or a reboot that rearmed a due time since superseded.
            if (session.isUnlocked) refreshReminders()
            while (isActive) {
                if (session.isUnlocked) poll()
                delay(INTERVAL_MILLIS)
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        loop?.cancel()
        loop = null
    }

    private companion object {
        const val INTERVAL_MILLIS = 30_000L
    }
}

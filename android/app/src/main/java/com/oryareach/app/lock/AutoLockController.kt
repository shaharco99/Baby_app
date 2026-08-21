package com.oryareach.app.lock

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.oryareach.app.di.SessionState
import com.oryareach.core.settings.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Locks the session after the app has been backgrounded for the configured timeout, but only
 * when biometric unlock is on — see [SettingsPreferences]'s doc comment for why a lock with no
 * way back in would just strand the user. Registered against [androidx.lifecycle.ProcessLifecycleOwner]
 * in [com.oryareach.app.TakesTwoApplication], not the Activity's own lifecycle: a rotation or a
 * transient multi-window state change stops and restarts an Activity without the app actually
 * leaving the foreground, and locking on those would be wrong.
 */
class AutoLockController(
    private val session: SessionState,
    private val preferences: SettingsPreferences,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lockJob: Job? = null

    override fun onStop(owner: LifecycleOwner) {
        lockJob?.cancel()
        if (!session.isUnlocked) return

        lockJob = scope.launch {
            if (!preferences.biometricUnlockEnabled.first()) return@launch
            val minutes = preferences.autoLockTimeoutMinutes.first()
            delay(minutes.coerceAtLeast(0) * MILLIS_PER_MINUTE)
            session.lock()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        lockJob?.cancel()
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

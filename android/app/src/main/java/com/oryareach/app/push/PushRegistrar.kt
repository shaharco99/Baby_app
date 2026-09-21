package com.oryareach.app.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.oryareach.core.network.push.PushTokenRepository
import com.oryareach.core.security.DeviceIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Keeps this device's FCM token on the server for as long as it belongs to a workspace.
 *
 * Registered whenever a workspace opens, and again whenever the platform hands over a new
 * token. Both are needed: the first covers a fresh install and a device that was paired after
 * the token already existed, the second covers rotation, which happens without warning and
 * would otherwise leave this phone unwakeable.
 */
class PushRegistrar(
    private val context: Context,
    private val identity: DeviceIdentity,
    private val tokens: PushTokenRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Called when a workspace opens. A no-op when push was never configured for this build. */
    fun onWorkspaceOpened(workspaceId: String) {
        if (!PushConfig.isConfigured) return
        scope.launch {
            val token = currentToken() ?: return@launch
            tokens.register(workspaceId, identity.pushDeviceId, token)
        }
    }

    /** Called by [PushMessagingService] when the platform rotates the token. */
    fun onTokenChanged(token: String) {
        val workspaceId = identity.workspaceId ?: return
        scope.launch { tokens.register(workspaceId, identity.pushDeviceId, token) }
    }

    /**
     * Called on sign-out. Leaving the row behind would have the edge function pushing to a
     * device that is no longer in the workspace, every time the remaining phone writes anything.
     */
    fun onSignedOut() {
        if (!PushConfig.isConfigured) return
        val deviceId = identity.pushDeviceId
        scope.launch { tokens.unregister(deviceId) }
    }

    private suspend fun currentToken(): String? = try {
        PushConfig.initialize(context)
        suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
    } catch (_: Exception) {
        // No Play Services, no network, a misconfigured project: push is an optimisation over
        // the poll that is already there, so this stays quiet rather than surfacing an error
        // about a feature the person never asked for.
        null
    }
}

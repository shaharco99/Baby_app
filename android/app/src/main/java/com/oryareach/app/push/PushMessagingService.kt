package com.oryareach.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.oryareach.app.sync.SyncWorker
import org.koin.android.ext.android.inject

/**
 * Receives the wake-up the partner's phone asked for, and syncs.
 *
 * The message carries nothing but the workspace id — it cannot carry anything else, because the
 * server has never been able to read what changed. Nothing is shown here: this device syncs,
 * decrypts what arrived itself, and lets the reminder re-derive from its own database. What the
 * person sees is their feed alarm quietly moving to the right time.
 *
 * This is the piece that makes a reminder correct on a phone whose app is closed. Everything
 * else already worked while the app was open; nothing woke it when it was not.
 */
class PushMessagingService : FirebaseMessagingService() {

    private val registrar: PushRegistrar by inject()

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] != TYPE_WORKSPACE_CHANGED) return
        // Through the worker rather than syncing inline: the service's lifetime is short and not
        // ours to extend, and SyncWorker already knows how to retry, back off, and refresh the
        // alarms afterwards.
        SyncWorker.syncNow(applicationContext)
    }

    /**
     * The platform rotates tokens on its own schedule — on a restore to a new phone, after
     * clearing app data, or when Firebase decides to. A rotated token that is not re-registered
     * is a device that silently stops being woken, which is the failure this whole path exists
     * to remove.
     */
    override fun onNewToken(token: String) {
        registrar.onTokenChanged(token)
    }

    private companion object {
        const val TYPE_WORKSPACE_CHANGED = "workspace-changed"
    }
}

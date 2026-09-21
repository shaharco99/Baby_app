package com.oryareach.app.di

import com.oryareach.core.crypto.WorkspaceKey
import com.oryareach.core.security.SessionController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the app knows about the current session: which workspace is open, and whether the
 * workspace key is unlocked.
 *
 * Held in memory only. The key is never written to disk in this form — the only persisted copy
 * is wrapped by an Android Keystore key, and it is dropped here whenever the app locks.
 *
 * What that buys is a lock on the UI, not on the data at rest, and it is worth being exact
 * about which. The Keystore-wrapped copy is readable by this app without any user
 * authentication (see `KeystoreSealedBox`), as is the SQLCipher passphrase
 * (`KeystoreDatabasePassphrase`) — so background sync deliberately falls back to
 * `DeviceIdentity` when this holder is empty, which is what lets a push wake the app and move
 * a stale reminder while it is locked. See docs/architecture/012-push-wake-up.md.
 */
class SessionState : SessionController {

    private val _workspaceId = MutableStateFlow<String?>(null)
    val workspaceIdFlow: StateFlow<String?> = _workspaceId.asStateFlow()

    val workspaceId: String? get() = _workspaceId.value

    @Volatile
    private var key: WorkspaceKey? = null

    val isUnlocked: Boolean get() = key != null

    /**
     * Distinct from [isUnlocked]: a deliberate lock (auto-lock timeout, manual "Lock now")
     * that must survive a trip through the pairing screen's key-recovery path. Without this
     * flag, [lock] alone is not a real lock — [com.oryareach.feature.pairing.PairingViewModel]
     * re-derives the key from the device's own Keystore-sealed copy on every `init` and would
     * silently reopen the session the moment the app fell through to the pairing route.
     */
    private val _locked = MutableStateFlow(false)
    val lockedFlow: StateFlow<Boolean> = _locked.asStateFlow()
    val isLocked: Boolean get() = _locked.value

    fun open(workspaceId: String, workspaceKey: WorkspaceKey) {
        _workspaceId.value = workspaceId
        key = workspaceKey
        _locked.value = false
    }

    /** Re-arms an already-open session after a deliberate lock, without going through pairing
     * again — the caller (a biometric/device-credential prompt) already knows [workspaceKey]. */
    fun unlock(workspaceKey: WorkspaceKey) {
        key = workspaceKey
        _locked.value = false
    }

    override fun lock() {
        key?.destroy()
        key = null
        if (_workspaceId.value != null) _locked.value = true
    }

    override fun signOut() {
        key?.destroy()
        key = null
        _locked.value = false
        _workspaceId.value = null
    }

    fun keyProvider(): com.oryareach.core.sync.WorkspaceKeyProvider =
        com.oryareach.core.sync.WorkspaceKeyProvider { key }
}

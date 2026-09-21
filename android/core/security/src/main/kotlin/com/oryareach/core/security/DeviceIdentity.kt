package com.oryareach.core.security

import com.oryareach.core.crypto.DeviceKeyPair
import com.oryareach.core.crypto.KeyWrap
import com.oryareach.core.crypto.WorkspaceKey

/**
 * This device's long-lived X25519 identity, plus the workspace key once it has been received.
 *
 * The private half and the workspace key are both stored only as Keystore-sealed blobs. The
 * public half is not sensitive — it is what the partner's device seals the workspace key to.
 */
class DeviceIdentity(
    private val store: KeystoreBlobStore,
    private val keyWrap: KeyWrap = KeyWrap(),
) {

    /** Generates the keypair on first call and reuses it forever after. */
    @Synchronized
    fun keyPair(): DeviceKeyPair {
        val storedPublic = store.get(KEY_PUBLIC)
        val storedPrivate = store.get(KEY_PRIVATE)

        if (storedPublic != null && storedPrivate != null) {
            return DeviceKeyPair(publicKey = storedPublic, privateKey = storedPrivate)
        }

        return keyWrap.generateDeviceKeyPair().also { generated ->
            store.put(KEY_PUBLIC, generated.publicKey)
            store.put(KEY_PRIVATE, generated.privateKeyBytes())
        }
    }

    /** The server-side id of this device's published key, once it has been registered. */
    var registeredKeyId: String?
        get() = store.getString(KEY_REGISTERED_ID)
        set(value) {
            if (value == null) store.remove(KEY_REGISTERED_ID) else store.putString(KEY_REGISTERED_ID, value)
        }

    /**
     * A stable id for this installation, generated on first use.
     *
     * Its own value rather than [registeredKeyId]: that one only exists once the device has
     * been paired, and the push registration has to survive being unpaired and paired again
     * without stranding a row on the server that nothing will ever clean up.
     */
    @get:Synchronized
    val pushDeviceId: String
        get() = store.getString(KEY_PUSH_DEVICE_ID)
            ?: java.util.UUID.randomUUID().toString().also { store.putString(KEY_PUSH_DEVICE_ID, it) }

    fun saveWorkspaceKey(key: WorkspaceKey) {
        store.put(KEY_WORKSPACE, key.bytes())
    }

    fun workspaceKey(): WorkspaceKey? = store.get(KEY_WORKSPACE)?.let(::WorkspaceKey)

    var workspaceId: String?
        get() = store.getString(KEY_WORKSPACE_ID)
        set(value) {
            if (value == null) store.remove(KEY_WORKSPACE_ID) else store.putString(KEY_WORKSPACE_ID, value)
        }

    /**
     * Clears everything tied to the account. The device keypair goes too: reusing an identity
     * across accounts would let the server correlate them.
     */
    fun forget() {
        store.clear()
    }

    private companion object {
        const val KEY_PUBLIC = "device-public"
        const val KEY_PRIVATE = "device-private"
        const val KEY_REGISTERED_ID = "device-key-id"
        const val KEY_WORKSPACE = "workspace-key"
        const val KEY_WORKSPACE_ID = "workspace-id"
        const val KEY_PUSH_DEVICE_ID = "push-device-id"
    }
}

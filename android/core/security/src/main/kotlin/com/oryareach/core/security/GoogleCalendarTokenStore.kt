package com.oryareach.core.security

/**
 * Device-local storage for the Google Calendar OAuth access token.
 *
 * This is a device credential, not couple-shared workspace data: unlike everything in
 * `:core:database`/`:core:sync`, it must never reach Supabase — each partner's device connects
 * to *their own* Google account independently. Same Keystore-sealed-blob treatment as
 * [DeviceIdentity]'s device keypair and workspace key, via the same [KeystoreBlobStore], just a
 * separate store/prefs file so wiping the workspace identity (sign-out) does not implicitly
 * touch a Google credential that has nothing to do with workspace pairing, and vice versa.
 *
 * The Identity Authorization API (see [GoogleCalendarAuthManager]) does not hand back a
 * long-lived refresh token for installed apps the way a server-side OAuth flow would — instead,
 * re-calling `authorize()` for a scope already granted typically succeeds silently and mints a
 * fresh short-lived access token. So only the access token and its expiry are cached here; there
 * is no refresh token to protect.
 */
class GoogleCalendarTokenStore(context: android.content.Context) {

    private val store = KeystoreBlobStore(context, name = STORE_NAME)

    var accessToken: String?
        get() = store.getString(KEY_ACCESS_TOKEN)
        set(value) {
            if (value == null) store.remove(KEY_ACCESS_TOKEN) else store.putString(KEY_ACCESS_TOKEN, value)
        }

    /** Epoch seconds. Null means "unknown expiry" — treated as expired so a fresh token is
     * always requested rather than risking use of a stale one. */
    var accessTokenExpiresAtEpochSeconds: Long?
        get() = store.getString(KEY_EXPIRES_AT)?.toLongOrNull()
        set(value) {
            if (value == null) store.remove(KEY_EXPIRES_AT) else store.putString(KEY_EXPIRES_AT, value.toString())
        }

    /** The connected Google account's email, shown in the UI so the user knows which account is
     * feeding the calendar view. Not itself a secret, but kept in the same sealed store as the
     * token it is associated with rather than a second, unencrypted place to read from. */
    var connectedAccountEmail: String?
        get() = store.getString(KEY_ACCOUNT_EMAIL)
        set(value) {
            if (value == null) store.remove(KEY_ACCOUNT_EMAIL) else store.putString(KEY_ACCOUNT_EMAIL, value)
        }

    fun isConnected(): Boolean = connectedAccountEmail != null

    fun clear() = store.clear()

    private companion object {
        const val STORE_NAME = "sahar-google-calendar"
        const val KEY_ACCESS_TOKEN = "access-token"
        const val KEY_EXPIRES_AT = "access-token-expires-at"
        const val KEY_ACCOUNT_EMAIL = "account-email"
    }
}

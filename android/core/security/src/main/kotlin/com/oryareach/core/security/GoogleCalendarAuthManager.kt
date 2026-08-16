package com.oryareach.core.security

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Result of attempting to connect (or silently refresh) the Google Calendar connection. */
sealed interface GoogleCalendarConnectResult {
    data object Connected : GoogleCalendarConnectResult

    /** First-time consent (or a scope not yet granted) needs the user to see Google's account
     * picker / consent screen. The caller (a Composable, which owns an
     * `ActivityResultLauncher<IntentSenderRequest>` — see `:core:scanner`'s `DocumentScanner`
     * for the identical pattern already used in this app) must launch this and feed the
     * `resultCode`/`data` it gets back into [GoogleCalendarAuthManager.completeResolution]. */
    data class ResolutionRequired(val intentSender: IntentSender) : GoogleCalendarConnectResult

    data class Failed(val message: String) : GoogleCalendarConnectResult
}

/**
 * Signs the user into their Google account (Credential Manager / One Tap — not the deprecated
 * `GoogleSignInClient`) and authorizes read-only Calendar access (the Identity Authorization
 * API — also not `GoogleSignInClient`), then keeps a short-lived access token cached in
 * [GoogleCalendarTokenStore] for `:core:calendar` to use.
 *
 * Entirely independent of the app's workspace pairing/session: this is one partner's personal
 * Google account, connected per-device, and never touches Supabase — see
 * [GoogleCalendarTokenStore]'s doc comment.
 */
interface GoogleCalendarAuthManager {
    fun isConnected(): Boolean
    fun connectedAccountEmail(): String?

    /** Starts (or silently refreshes) the connection. On a device that has already granted the
     * scope, this typically resolves immediately with [GoogleCalendarConnectResult.Connected]
     * and no UI. On first connect, expect [GoogleCalendarConnectResult.ResolutionRequired]. */
    suspend fun connect(context: Context): GoogleCalendarConnectResult

    /** Call after launching a [GoogleCalendarConnectResult.ResolutionRequired] intent sender,
     * with the `Activity.RESULT_OK`/data pair the launcher returned. */
    suspend fun completeResolution(resultCode: Int, data: Intent?): GoogleCalendarConnectResult

    /** The token `:core:calendar` sends as the `Authorization: Bearer` header. Returns null if
     * never connected, or if a silent refresh attempt failed — the caller should prompt
     * [connect] again in that case; this never launches UI itself. */
    suspend fun currentAccessToken(): String?

    fun disconnect()
}

class GoogleCalendarAuthManagerImpl(
    private val appContext: Context,
    private val tokenStore: GoogleCalendarTokenStore,
) : GoogleCalendarAuthManager {

    private val credentialManager by lazy { CredentialManager.create(appContext) }
    private val authorizationClient by lazy { Identity.getAuthorizationClient(appContext) }
    private val requestedScope = Scope(CALENDAR_READONLY_SCOPE)

    override fun isConnected(): Boolean = tokenStore.isConnected()

    override fun connectedAccountEmail(): String? = tokenStore.connectedAccountEmail

    override suspend fun connect(context: Context): GoogleCalendarConnectResult {
        val clientId = BuildConfig.GOOGLE_CALENDAR_OAUTH_CLIENT_ID
        if (clientId.isBlank()) {
            return GoogleCalendarConnectResult.Failed(
                "No Google OAuth client id configured — see core/security/build.gradle.kts's " +
                    "GOOGLE_CALENDAR_OAUTH_CLIENT_ID TODO.",
            )
        }

        runCatching { signIn(context, clientId) }
            .onFailure { return GoogleCalendarConnectResult.Failed(it.message ?: "Google sign-in failed") }

        val request = AuthorizationRequest.Builder().setRequestedScopes(listOf(requestedScope)).build()
        val result = runCatching { awaitAuthorization(request) }
            .getOrElse { return GoogleCalendarConnectResult.Failed(it.message ?: "Authorization failed") }
        return applyAuthorizationResult(result)
    }

    private suspend fun signIn(context: Context, clientId: String) {
        val option = GetSignInWithGoogleOption.Builder(clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        // Only proves identity (which Google account) — Calendar access itself is authorized
        // separately below via the Identity Authorization API.
        val response = credentialManager.getCredential(context, request)
        val credential = GoogleIdTokenCredential.createFrom(response.credential.data)
        tokenStore.connectedAccountEmail = credential.id
    }

    override suspend fun completeResolution(resultCode: Int, data: Intent?): GoogleCalendarConnectResult {
        if (resultCode != android.app.Activity.RESULT_OK || data == null) {
            return GoogleCalendarConnectResult.Failed("Google consent was cancelled")
        }
        val result = runCatching { authorizationClient.getAuthorizationResultFromIntent(data) }
            .getOrElse { return GoogleCalendarConnectResult.Failed(it.message ?: "Authorization failed") }
        return applyAuthorizationResult(result)
    }

    private fun applyAuthorizationResult(result: AuthorizationResult): GoogleCalendarConnectResult {
        if (result.hasResolution()) {
            val intentSender = result.pendingIntent?.intentSender
                ?: return GoogleCalendarConnectResult.Failed("Google returned a resolution with no intent")
            return GoogleCalendarConnectResult.ResolutionRequired(intentSender)
        }

        val accessToken = result.accessToken
            ?: return GoogleCalendarConnectResult.Failed("Google granted no access token")

        tokenStore.accessToken = accessToken
        // The Identity Authorization API does not report an explicit expiry; Google access
        // tokens are conventionally valid ~1 hour. Assuming a shorter lifetime is safe — worst
        // case it just costs one extra silent authorize() call, never a failure.
        tokenStore.accessTokenExpiresAtEpochSeconds = nowEpochSeconds() + ACCESS_TOKEN_ASSUMED_LIFETIME_SECONDS
        return GoogleCalendarConnectResult.Connected
    }

    override suspend fun currentAccessToken(): String? {
        if (!tokenStore.isConnected()) return null

        val expiresAt = tokenStore.accessTokenExpiresAtEpochSeconds
        val cached = tokenStore.accessToken
        if (cached != null && expiresAt != null && nowEpochSeconds() < expiresAt - TOKEN_REFRESH_SKEW_SECONDS) {
            return cached
        }

        // Silent refresh: re-authorizing a scope already granted does not show UI as long as
        // consent was already given once via connect().
        val clientId = BuildConfig.GOOGLE_CALENDAR_OAUTH_CLIENT_ID
        if (clientId.isBlank()) return null
        val request = AuthorizationRequest.Builder().setRequestedScopes(listOf(requestedScope)).build()
        val result = runCatching { awaitAuthorization(request) }.getOrNull() ?: return cached
        if (result.hasResolution()) return cached // needs UI; hand back whatever is cached (may be stale/null)
        val refreshed = result.accessToken ?: return cached
        tokenStore.accessToken = refreshed
        tokenStore.accessTokenExpiresAtEpochSeconds = nowEpochSeconds() + ACCESS_TOKEN_ASSUMED_LIFETIME_SECONDS
        return refreshed
    }

    override fun disconnect() {
        tokenStore.clear()
    }

    private suspend fun awaitAuthorization(request: AuthorizationRequest): AuthorizationResult =
        suspendCancellableCoroutine { continuation ->
            authorizationClient.authorize(request)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }

    private fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000

    private companion object {
        const val CALENDAR_READONLY_SCOPE = "https://www.googleapis.com/auth/calendar.readonly"
        const val ACCESS_TOKEN_ASSUMED_LIFETIME_SECONDS = 3300L // ~55 minutes, under Google's ~1h
        const val TOKEN_REFRESH_SKEW_SECONDS = 120L
    }
}

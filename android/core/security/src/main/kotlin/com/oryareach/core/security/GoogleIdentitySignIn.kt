package com.oryareach.core.security

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Just identity — the raw Google ID token proving which Google account the user picked, via
 * Credential Manager (not the deprecated `GoogleSignInClient`). Returns the token itself, not
 * the whole [GoogleIdTokenCredential], so callers outside this module never need `googleid` on
 * their own classpath — that dependency is `implementation`, not `api`, in this module's
 * `build.gradle.kts`, deliberately not exposed further. The caller hands the token straight to
 * `AuthRepository.signInWithGoogle`, which Supabase verifies server-side against the Google
 * provider configured in its dashboard.
 *
 * Deliberately separate from [GoogleCalendarAuthManager]: that one also authorizes Calendar
 * read access and manages its own long-lived token, neither of which app login needs.
 */
object GoogleIdentitySignIn {
    suspend fun getIdToken(context: Context, webClientId: String): Result<String> = runCatching {
        val credentialManager = CredentialManager.create(context)
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = credentialManager.getCredential(context, request)
        GoogleIdTokenCredential.createFrom(response.credential.data).idToken
    }
}

package com.oryareach.core.network.auth

import android.content.Intent
import com.oryareach.core.common.AppError
import com.oryareach.core.common.AppResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Scheme+host this app registers in `AndroidManifest.xml` for the password-recovery email
 * link to land on. Must also be added to Supabase's dashboard under Authentication → URL
 * Configuration → Redirect URLs — Supabase silently falls back to the project's default Site
 * URL for any `redirectTo` that isn't allow-listed there, which would send the link to a
 * generic web page nobody's set up instead of back into this app. */
internal const val PASSWORD_RECOVERY_REDIRECT_URL = "com.oryareach.app://reset-password"

/**
 * Whether anyone is signed in on this device.
 *
 * `Unknown` is deliberately distinct from `SignedOut`: at cold start the SDK has not finished
 * loading the stored session, and treating that moment as signed-out would bounce the user to
 * the login screen on every launch.
 */
enum class AuthState { Unknown, SignedIn, SignedOut }

interface AuthRepository {
    val state: Flow<AuthState>
    fun currentUserId(): String?
    suspend fun signUp(email: String, password: String): AppResult<Unit>

    /**
     * [rememberMe] controls whether the session survives a restart. It defaults to true —
     * this is what "remember me" being checked by default on the sign-in form maps to.
     */
    suspend fun signIn(email: String, password: String, rememberMe: Boolean = true): AppResult<Unit>

    /** [idToken] is the raw Google ID token obtained via Credential Manager (see
     * `:core:security`'s `GoogleIdentitySignIn`) — Supabase verifies it server-side against
     * the Google provider configured in its dashboard. Always "remembered": there is no
     * password-based session here to distinguish a throwaway sign-in from a lasting one. */
    suspend fun signInWithGoogle(idToken: String): AppResult<Unit>

    /** Whether the signed-in user already has a Google identity attached — either because they
     * originally signed in with Google, or linked it later via [linkGoogleIdentity]. */
    fun isGoogleLinked(): Boolean

    /** Attaches a Google identity to the *currently signed-in* user, via the same kind of
     * [idToken] [signInWithGoogle] takes. Unlike [signInWithGoogle] this never creates or
     * resolves a different user — it's Supabase's identity-linking endpoint, so it's additive:
     * the account, its workspace, and all its data stay exactly what they were: someone who
     * signed up with email/password can add Google as an additional way to sign in later
     * without losing anything. The workspace encryption key is unaffected either way — it lives
     * device-local (Keystore/pairing), never derived from how the Supabase session authenticated
     * (see `docs/architecture/007-encryption.md`). */
    suspend fun linkGoogleIdentity(idToken: String): AppResult<Unit>
    suspend fun signOut(): AppResult<Unit>

    /** Emails [email] a password-recovery link (via Supabase's built-in flow — no separate
     * template or server code of our own). Always reports success regardless of whether the
     * address has an account, same account-enumeration reasoning as [toAuthError]. */
    suspend fun sendPasswordResetEmail(email: String): AppResult<Unit>

    /** Cheap, synchronous check of whether [intent] is the password-recovery link Supabase
     * emailed — before doing any of the async work [consumePasswordRecoveryLink] does. Lets the
     * caller (`MainActivity`) skip that work entirely for an ordinary cold-start intent. */
    fun isPasswordRecoveryLink(intent: Intent): Boolean

    /** Parses the recovery tokens out of [intent] and establishes them as the current session,
     * so the signed-in user is now the account that requested the reset — the caller should
     * route to a "set a new password" screen instead of the normal signed-in app until
     * [updatePassword] completes. Only call after [isPasswordRecoveryLink] returns true. */
    suspend fun consumePasswordRecoveryLink(intent: Intent): AppResult<Unit>

    /** Sets a new password on the *currently signed-in* session — used right after
     * [consumePasswordRecoveryLink] establishes a recovery session, but works for any signed-in
     * user (e.g. a deliberate password change from Settings later). */
    suspend fun updatePassword(newPassword: String): AppResult<Unit>
}

class SupabaseAuthRepository(
    private val client: SupabaseClient,
    private val sessionManager: EncryptedSessionManager,
) : AuthRepository {

    override val state: Flow<AuthState> = client.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> AuthState.SignedIn
            is SessionStatus.NotAuthenticated -> AuthState.SignedOut
            // A failed background token refresh does not mean the cached session is gone —
            // the SDK keeps it in place and will retry. Treating this the same as
            // `Initializing` (mapped to Unknown below) would unmount the entire signed-in UI
            // tree — see `TakesTwoApp`'s routing `when`, which renders nothing for `Unknown` —
            // on a transient network hiccup, e.g. right as the app resumes from backgrounding
            // for a system file/document picker. That contradicts this app's offline-first
            // design (Room-first reads, background sync) and was the leading suspect for a
            // live-tested bug where returning from the SAF import picker reset the bottom-nav
            // tab and silently dropped the in-flight `ActivityResultLauncher` callback.
            is SessionStatus.RefreshFailure -> AuthState.SignedIn
            else -> AuthState.Unknown
        }
    }

    override fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    override suspend fun signUp(email: String, password: String): AppResult<Unit> = attempt {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signIn(email: String, password: String, rememberMe: Boolean): AppResult<Unit> = attempt {
        sessionManager.persistNextSession = rememberMe
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signInWithGoogle(idToken: String): AppResult<Unit> = attempt {
        sessionManager.persistNextSession = true
        client.auth.signInWith(IDToken) {
            this.idToken = idToken
            this.provider = Google
        }
    }

    override fun isGoogleLinked(): Boolean =
        client.auth.currentUserOrNull()?.identities?.any { it.provider == "google" } == true

    override suspend fun linkGoogleIdentity(idToken: String): AppResult<Unit> = attempt {
        client.auth.linkIdentityWithIdToken(Google, idToken) {}
    }

    override suspend fun signOut(): AppResult<Unit> = attempt { client.auth.signOut() }

    override suspend fun sendPasswordResetEmail(email: String): AppResult<Unit> = attempt {
        client.auth.resetPasswordForEmail(email = email, redirectUrl = PASSWORD_RECOVERY_REDIRECT_URL)
    }

    override fun isPasswordRecoveryLink(intent: Intent): Boolean {
        val data = intent.data ?: return false
        if (data.scheme != "com.oryareach.app" || data.host != "reset-password") return false
        // Supabase encodes which kind of link this is ("recovery", "signup", "magiclink", ...)
        // as a query param on newer (PKCE-style) links, or inside the URL fragment on older
        // (implicit-flow) ones — check both rather than assume which one this project's
        // Supabase version emits.
        val type = data.getQueryParameter("type")
            ?: data.fragment
                ?.split('&')
                ?.firstOrNull { it.startsWith("type=") }
                ?.substringAfter('=')
        return type == "recovery"
    }

    override suspend fun consumePasswordRecoveryLink(intent: Intent): AppResult<Unit> =
        suspendCancellableCoroutine { continuation ->
            client.handleDeeplinks(
                intent,
                { continuation.resume(AppResult.Success(Unit)) },
                { error ->
                    val appError = (error as? Exception)?.toAuthError()
                        ?: AppError.Unexpected(error.message ?: error::class.simpleName.orEmpty())
                    continuation.resume(AppResult.Failure(appError))
                },
            )
        }

    override suspend fun updatePassword(newPassword: String): AppResult<Unit> = attempt {
        client.auth.updateUser { password = newPassword }
    }

    private inline fun attempt(block: () -> Unit): AppResult<Unit> = try {
        block()
        AppResult.Success(Unit)
    } catch (e: Exception) {
        AppResult.Failure(e.toAuthError())
    }
}

/**
 * Maps SDK failures to the domain error type. Credential problems are reported as
 * `Unauthorized` without echoing which part was wrong — telling a caller that an email exists
 * but the password is wrong is an account-enumeration oracle.
 */
internal fun Exception.toAuthError(): AppError {
    val text = message.orEmpty().lowercase()
    return when {
        this is java.net.UnknownHostException -> AppError.Network.Offline
        this is java.net.SocketTimeoutException -> AppError.Network.Timeout
        "invalid" in text || "credential" in text || "password" in text ->
            AppError.Network.Unauthorized
        "already registered" in text || "already exists" in text ->
            AppError.Network.Server(status = 409)
        else -> AppError.Unexpected(message ?: this::class.simpleName.orEmpty())
    }
}

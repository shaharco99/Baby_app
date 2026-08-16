package com.oryareach.feature.auth

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

enum class AuthMode { SignIn, SignUp }

@Immutable
data class AuthUiState(
    // Editable input
    val mode: AuthMode = AuthMode.SignIn,
    val email: String = "",
    val password: String = "",
    val rememberMe: Boolean = true,

    // Transient UI-only: must not survive the screen
    val submitting: Boolean = false,
    val googleSigningIn: Boolean = false,
    val passwordVisible: Boolean = false,
    @StringRes val errorMessage: Int? = null,

    // Persisted outcome: "we sent you an email" must still be on screen after a rotation,
    // so it is state rather than a one-shot effect.
    @StringRes val infoMessage: Int? = null,
) {
    // Derived as getters, not constructor params, so no copy() can leave them inconsistent
    // with the inputs they describe.
    val emailValid: Boolean get() = email.contains('@') && email.substringAfter('@').contains('.')

    val passwordValid: Boolean get() = password.length >= MIN_PASSWORD_LENGTH

    val canSubmit: Boolean get() = emailValid && passwordValid && !submitting && !googleSigningIn

    companion object {
        /** Matches the minimum enforced by the Supabase project. */
        const val MIN_PASSWORD_LENGTH = 8
    }
}

sealed interface AuthEffect {
    data object SignedIn : AuthEffect
}

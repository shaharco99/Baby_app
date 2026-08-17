package com.oryareach.feature.auth

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

/**
 * Set-a-new-password screen shown after opening the link Supabase emailed for
 * [AuthActions.onForgotPasswordSubmit] — see `AuthRepository.consumePasswordRecoveryLink`'s
 * doc comment for how the app gets here.
 */
@Immutable
data class ResetPasswordUiState(
    val password: String = "",
    val confirmPassword: String = "",
    val passwordVisible: Boolean = false,
    val submitting: Boolean = false,
    @StringRes val errorMessage: Int? = null,
) {
    val passwordValid: Boolean get() = password.length >= AuthUiState.MIN_PASSWORD_LENGTH
    val passwordsMatch: Boolean get() = password == confirmPassword
    val canSubmit: Boolean get() = passwordValid && passwordsMatch && !submitting
}

sealed interface ResetPasswordEffect {
    data object PasswordUpdated : ResetPasswordEffect
}

package com.oryareach.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryareach.core.common.AppResult
import com.oryareach.core.network.auth.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@androidx.compose.runtime.Stable
interface ResetPasswordActions {
    fun onPasswordChange(value: String)
    fun onConfirmPasswordChange(value: String)
    fun onTogglePasswordVisibility()
    fun onSubmit()
}

class ResetPasswordViewModel(private val auth: AuthRepository) : ViewModel(), ResetPasswordActions {

    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ResetPasswordEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    override fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    override fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }
    }

    override fun onTogglePasswordVisibility() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    override fun onSubmit() {
        val state = _uiState.value
        if (!state.canSubmit) return

        _uiState.update { it.copy(submitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = auth.updatePassword(state.password)) {
                is AppResult.Success -> _effects.trySend(ResetPasswordEffect.PasswordUpdated)
                is AppResult.Failure -> _uiState.update {
                    it.copy(submitting = false, errorMessage = result.error.toMessageRes())
                }
            }
        }
    }
}

private fun MutableStateFlow<ResetPasswordUiState>.update(block: (ResetPasswordUiState) -> ResetPasswordUiState) {
    value = block(value)
}

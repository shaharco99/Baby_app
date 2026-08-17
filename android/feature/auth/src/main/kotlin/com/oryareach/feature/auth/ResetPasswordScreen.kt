package com.oryareach.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oryareach.core.ui.theme.OrYareachTheme

/** Shown instead of the normal signed-in app right after opening a password-recovery link —
 * see `SaharApp`'s routing `when` and `AuthRepository.consumePasswordRecoveryLink`'s doc
 * comment for how the app decides to render this. */
@Composable
fun ResetPasswordScreen(
    uiState: ResetPasswordUiState,
    actions: ResetPasswordActions,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.reset_password_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )

            Text(
                text = stringResource(R.string.reset_password_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            val visualTransformation = if (uiState.passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            }

            OutlinedTextField(
                value = uiState.password,
                onValueChange = actions::onPasswordChange,
                label = { Text(stringResource(R.string.reset_password_new_password)) },
                singleLine = true,
                isError = uiState.password.isNotEmpty() && !uiState.passwordValid,
                supportingText = {
                    Text(stringResource(R.string.auth_password_hint, AuthUiState.MIN_PASSWORD_LENGTH))
                },
                visualTransformation = visualTransformation,
                trailingIcon = {
                    TextButton(onClick = actions::onTogglePasswordVisibility) {
                        Text(
                            stringResource(
                                if (uiState.passwordVisible) R.string.auth_hide_password else R.string.auth_show_password,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.confirmPassword,
                onValueChange = actions::onConfirmPasswordChange,
                label = { Text(stringResource(R.string.reset_password_confirm_password)) },
                singleLine = true,
                isError = uiState.confirmPassword.isNotEmpty() && !uiState.passwordsMatch,
                supportingText = if (uiState.confirmPassword.isNotEmpty() && !uiState.passwordsMatch) {
                    { Text(stringResource(R.string.reset_password_mismatch)) }
                } else {
                    null
                },
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { actions.onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )

            uiState.errorMessage?.let { message ->
                Text(
                    text = stringResource(message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = actions::onSubmit,
                enabled = uiState.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.reset_password_submit))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ResetPasswordPreview() {
    OrYareachTheme {
        ResetPasswordScreen(uiState = ResetPasswordUiState(), actions = NoopResetPasswordActions)
    }
}

private object NoopResetPasswordActions : ResetPasswordActions {
    override fun onPasswordChange(value: String) = Unit
    override fun onConfirmPasswordChange(value: String) = Unit
    override fun onTogglePasswordVisibility() = Unit
    override fun onSubmit() = Unit
}

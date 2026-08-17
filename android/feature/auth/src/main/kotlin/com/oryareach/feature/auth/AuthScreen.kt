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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oryareach.core.security.BuildConfig as SecurityBuildConfig
import com.oryareach.core.security.GoogleIdentitySignIn
import com.oryareach.core.ui.theme.OrYareachTheme
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    uiState: AuthUiState,
    actions: AuthActions,
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
                text = stringResource(
                    if (uiState.mode == AuthMode.SignIn) {
                        R.string.auth_title_sign_in
                    } else {
                        R.string.auth_title_sign_up
                    },
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )

            Text(
                text = stringResource(R.string.auth_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.email,
                onValueChange = actions::onEmailChange,
                label = { Text(stringResource(R.string.auth_email)) },
                singleLine = true,
                isError = uiState.email.isNotEmpty() && !uiState.emailValid,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.password,
                onValueChange = actions::onPasswordChange,
                label = { Text(stringResource(R.string.auth_password)) },
                singleLine = true,
                isError = uiState.password.isNotEmpty() && !uiState.passwordValid,
                supportingText = {
                    Text(
                        stringResource(
                            R.string.auth_password_hint,
                            AuthUiState.MIN_PASSWORD_LENGTH,
                        ),
                    )
                },
                visualTransformation = if (uiState.passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    TextButton(onClick = actions::onTogglePasswordVisibility) {
                        Text(
                            stringResource(
                                if (uiState.passwordVisible) {
                                    R.string.auth_hide_password
                                } else {
                                    R.string.auth_show_password
                                },
                            ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { actions.onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )

            if (uiState.mode == AuthMode.SignIn) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { actions.onRememberMeChange(!uiState.rememberMe) },
                    ) {
                        Checkbox(checked = uiState.rememberMe, onCheckedChange = actions::onRememberMeChange)
                        Text(
                            stringResource(R.string.auth_remember_me),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    TextButton(onClick = actions::onForgotPasswordClick) {
                        Text(stringResource(R.string.auth_forgot_password))
                    }
                }
            }

            uiState.infoMessage?.let { message ->
                Text(
                    text = stringResource(message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // The error is a field in state, not an effect: it must survive a rotation.
            uiState.errorMessage?.let { message ->
                Text(
                    text = stringResource(message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { },
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
                    Text(
                        stringResource(
                            if (uiState.mode == AuthMode.SignIn) {
                                R.string.auth_sign_in
                            } else {
                                R.string.auth_sign_up
                            },
                        ),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.auth_or_divider),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            GoogleSignInButton(uiState = uiState, actions = actions)

            TextButton(
                onClick = {
                    actions.onModeChange(
                        if (uiState.mode == AuthMode.SignIn) AuthMode.SignUp else AuthMode.SignIn,
                    )
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    stringResource(
                        if (uiState.mode == AuthMode.SignIn) {
                            R.string.auth_switch_to_sign_up
                        } else {
                            R.string.auth_switch_to_sign_in
                        },
                    ),
                )
            }
        }
    }

    if (uiState.forgotPasswordVisible) {
        ForgotPasswordDialog(uiState = uiState, actions = actions)
    }
}

@Composable
private fun ForgotPasswordDialog(uiState: AuthUiState, actions: AuthActions) {
    AlertDialog(
        onDismissRequest = actions::onDismissForgotPassword,
        title = { Text(stringResource(R.string.auth_forgot_password_title)) },
        text = {
            if (uiState.forgotPasswordSent) {
                Text(stringResource(R.string.auth_forgot_password_sent))
            } else {
                OutlinedTextField(
                    value = uiState.forgotPasswordEmail,
                    onValueChange = actions::onForgotPasswordEmailChange,
                    label = { Text(stringResource(R.string.auth_email)) },
                    singleLine = true,
                    isError = uiState.forgotPasswordEmail.isNotEmpty() && !uiState.forgotPasswordEmailValid,
                    enabled = !uiState.forgotPasswordSubmitting,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { actions.onForgotPasswordSubmit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            if (uiState.forgotPasswordSent) {
                TextButton(onClick = actions::onDismissForgotPassword) {
                    Text(stringResource(R.string.auth_forgot_password_done))
                }
            } else {
                TextButton(
                    onClick = actions::onForgotPasswordSubmit,
                    enabled = uiState.forgotPasswordEmailValid && !uiState.forgotPasswordSubmitting,
                ) {
                    if (uiState.forgotPasswordSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.auth_forgot_password_submit))
                    }
                }
            }
        },
        dismissButton = if (uiState.forgotPasswordSent) {
            null
        } else {
            {
                TextButton(onClick = actions::onDismissForgotPassword) {
                    Text(stringResource(R.string.auth_forgot_password_cancel))
                }
            }
        },
    )
}

/**
 * Owns the Credential Manager call itself — it needs an Android `Context`, which [AuthViewModel]
 * must not hold — and only ever hands the ViewModel a finished result: the raw ID token on
 * success, via [AuthActions.onGoogleIdToken], or a failure via [AuthActions.onGoogleSignInFailed].
 * A user simply dismissing the account picker is treated as silence, not an error — Credential
 * Manager throws a `GetCredentialCancellationException` for that, which the caller doesn't need
 * to see as "something went wrong".
 */
@Composable
private fun GoogleSignInButton(uiState: AuthUiState, actions: AuthActions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    OutlinedButton(
        onClick = {
            actions.onGoogleSignInStarted()
            scope.launch {
                val clientId = SecurityBuildConfig.GOOGLE_WEB_CLIENT_ID
                if (clientId.isBlank()) {
                    actions.onGoogleSignInFailed("No Google OAuth client id configured")
                    return@launch
                }
                GoogleIdentitySignIn.getIdToken(context, clientId).fold(
                    onSuccess = { idToken -> actions.onGoogleIdToken(idToken) },
                    onFailure = { error ->
                        if (error::class.simpleName == "GetCredentialCancellationException") {
                            actions.onGoogleSignInFailed(null)
                        } else {
                            actions.onGoogleSignInFailed(error.message)
                        }
                    },
                )
            }
        },
        enabled = !uiState.submitting && !uiState.googleSigningIn,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (uiState.googleSigningIn) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(R.string.auth_continue_with_google))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthPreview() {
    OrYareachTheme {
        AuthScreen(uiState = AuthUiState(email = "shahar@example.com"), actions = NoopAuthActions)
    }
}

private object NoopAuthActions : AuthActions {
    override fun onEmailChange(value: String) = Unit
    override fun onPasswordChange(value: String) = Unit
    override fun onTogglePasswordVisibility() = Unit
    override fun onRememberMeChange(value: Boolean) = Unit
    override fun onModeChange(mode: AuthMode) = Unit
    override fun onSubmit() = Unit
    override fun onGoogleIdToken(idToken: String) = Unit
    override fun onGoogleSignInStarted() = Unit
    override fun onGoogleSignInFailed(message: String?) = Unit
    override fun onForgotPasswordClick() = Unit
    override fun onForgotPasswordEmailChange(value: String) = Unit
    override fun onForgotPasswordSubmit() = Unit
    override fun onDismissForgotPassword() = Unit
}

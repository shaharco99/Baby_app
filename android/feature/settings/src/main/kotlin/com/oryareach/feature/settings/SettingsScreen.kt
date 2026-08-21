package com.oryareach.feature.settings

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oryareach.core.ui.theme.OrYareachTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    /** Slot for host-app content this module cannot own itself — e.g. "Check for updates",
     * which lives in `:feature:update` and feature modules must not depend on each other. */
    footer: @Composable () -> Unit = {},
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    var titleTapCount by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val easterEggMessage = stringResource(R.string.settings_easter_egg_message)

    Surface(modifier = modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .semantics { heading() }
                        .clickable {
                            titleTapCount++
                            if (titleTapCount >= 7) {
                                titleTapCount = 0
                                Toast.makeText(context, easterEggMessage, Toast.LENGTH_LONG).show()
                            }
                        },
                )
            }

            item { AccountSection(uiState = uiState, actions = actions) }
            item { SecuritySection(uiState = uiState, actions = actions) }
            item { NotificationsSection(uiState = uiState, actions = actions) }
            item { RecoverySection(actions = actions) }
            item { DevicesSection(actions = actions) }
            item { GoogleCalendarSection(uiState = uiState, actions = actions) }
            item { footer() }

            item {
                OutlinedButton(
                    onClick = { confirmSignOut = true },
                    enabled = !uiState.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_sign_out))
                }
            }
        }
    }

    uiState.recoveryPhrase?.let { words ->
        val clipboard = LocalClipboard.current
        val scope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = actions::onDismissRecoveryPhrase,
            title = { Text(stringResource(R.string.settings_recovery_phrase_title)) },
            text = {
                Text(
                    text = words.joinToString(" "),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = actions::onDismissRecoveryPhrase) {
                    Text(stringResource(R.string.settings_close))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        clipboard.setClipEntry(
                            ClipEntry(ClipData.newPlainText("recovery-phrase", words.joinToString(" "))),
                        )
                    }
                }) {
                    Text(stringResource(R.string.settings_recovery_phrase_copy))
                }
            },
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.settings_sign_out_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_body)) },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; actions.onSignOutClick() }) {
                    Text(stringResource(R.string.settings_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun AccountSection(uiState: SettingsUiState, actions: SettingsActions) {
    val context = LocalContext.current

    SectionCard(title = stringResource(R.string.settings_account_title)) {
        Text(
            text = stringResource(R.string.settings_account_google_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (uiState.googleAccountLinkError) {
            Text(
                stringResource(R.string.settings_account_google_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (uiState.googleAccountLinked) {
            Text(
                stringResource(R.string.settings_account_google_connected),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Button(
                onClick = { actions.onConnectGoogleAccountClick(context) },
                enabled = !uiState.googleAccountLinkBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.googleAccountLinkBusy) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.settings_account_google_connect))
                }
            }
        }
    }
}

@Composable
private fun SecuritySection(uiState: SettingsUiState, actions: SettingsActions) {
    SectionCard(title = stringResource(R.string.settings_security_title)) {
        SwitchRow(
            label = stringResource(R.string.settings_biometric_unlock),
            checked = uiState.biometricUnlockEnabled,
            onCheckedChange = actions::onBiometricToggle,
        )

        if (uiState.biometricUnlockEnabled) {
            AutoLockDropdown(uiState = uiState, actions = actions)
        }

        SwitchRow(
            label = stringResource(R.string.settings_block_screenshots),
            checked = uiState.screenshotsBlocked,
            onCheckedChange = actions::onScreenshotsToggle,
        )

        Button(
            onClick = actions::onLockNowClick,
            enabled = uiState.biometricUnlockEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_lock_now))
        }
    }
}

@Composable
private fun AutoLockDropdown(uiState: SettingsUiState, actions: SettingsActions) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_auto_lock_timeout, uiState.autoLockTimeoutMinutes))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            uiState.autoLockOptionMinutes.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_auto_lock_minutes, minutes)) },
                    onClick = {
                        actions.onAutoLockMinutesChange(minutes)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun NotificationsSection(uiState: SettingsUiState, actions: SettingsActions) {
    SectionCard(title = stringResource(R.string.settings_notifications_title)) {
        SwitchRow(
            label = stringResource(R.string.settings_notifications_enabled),
            checked = uiState.notificationsEnabled,
            onCheckedChange = actions::onNotificationsToggle,
        )
        Text(
            text = stringResource(R.string.settings_notifications_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecoverySection(actions: SettingsActions) {
    SectionCard(title = stringResource(R.string.settings_recovery_title)) {
        Text(
            text = stringResource(R.string.settings_recovery_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = actions::onShowRecoveryPhraseClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_show_recovery_phrase))
        }
    }
}

@Composable
private fun DevicesSection(actions: SettingsActions) {
    SectionCard(title = stringResource(R.string.settings_devices_title)) {
        OutlinedButton(onClick = actions::onManageDevicesClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_manage_devices))
        }
    }
}

@Composable
private fun GoogleCalendarSection(uiState: SettingsUiState, actions: SettingsActions) {
    val context = LocalContext.current

    SectionCard(title = stringResource(R.string.settings_google_calendar_title)) {
        Text(
            text = stringResource(R.string.settings_google_calendar_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        uiState.googleCalendarError?.let { error ->
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        if (uiState.googleCalendarConnected) {
            uiState.googleCalendarAccountEmail?.let { email ->
                Text(
                    stringResource(R.string.settings_google_calendar_connected_as, email),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedButton(
                onClick = actions::onOpenCalendarPickerClick,
                enabled = !uiState.googleCalendarBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_google_calendar_choose_calendars))
            }
            TextButton(onClick = actions::onDisconnectGoogleCalendarClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_google_calendar_disconnect))
            }
        } else {
            Button(
                onClick = { actions.onConnectGoogleCalendarClick(context) },
                enabled = !uiState.googleCalendarBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_google_calendar_connect))
            }
        }

        if (uiState.googleCalendarBusy) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 4.dp))
        }
    }

    if (uiState.calendarPickerVisible) {
        AlertDialog(
            onDismissRequest = actions::onDismissCalendarPicker,
            title = { Text(stringResource(R.string.settings_google_calendar_picker_title)) },
            text = {
                if (uiState.availableGoogleCalendars.isEmpty() && !uiState.googleCalendarBusy) {
                    Text(
                        stringResource(R.string.settings_google_calendar_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Column {
                        uiState.availableGoogleCalendars.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = option.selected,
                                        onValueChange = { actions.onToggleCalendarSelection(option.id) },
                                        role = Role.Checkbox,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = option.selected, onCheckedChange = null)
                                Text(option.summary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = actions::onDismissCalendarPicker) {
                    Text(stringResource(R.string.settings_close))
                }
            },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                role = Role.Switch,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    OrYareachTheme {
        SettingsScreen(uiState = SettingsUiState(), actions = NoopSettingsActions)
    }
}

private object NoopSettingsActions : SettingsActions {
    override fun onBiometricToggle(enabled: Boolean) = Unit
    override fun onAutoLockMinutesChange(minutes: Int) = Unit
    override fun onScreenshotsToggle(blocked: Boolean) = Unit
    override fun onNotificationsToggle(enabled: Boolean) = Unit
    override fun onNotificationPermissionResult(granted: Boolean) = Unit
    override fun onLockNowClick() = Unit
    override fun onShowRecoveryPhraseClick() = Unit
    override fun onDismissRecoveryPhrase() = Unit
    override fun onManageDevicesClick() = Unit
    override fun onSignOutClick() = Unit
    override fun onConnectGoogleAccountClick(context: android.content.Context) = Unit
    override fun onConnectGoogleCalendarClick(context: android.content.Context) = Unit
    override fun onGoogleCalendarResolutionResult(resultCode: Int, data: android.content.Intent?) = Unit
    override fun onOpenCalendarPickerClick() = Unit
    override fun onDismissCalendarPicker() = Unit
    override fun onToggleCalendarSelection(calendarId: String) = Unit
    override fun onDisconnectGoogleCalendarClick() = Unit
}

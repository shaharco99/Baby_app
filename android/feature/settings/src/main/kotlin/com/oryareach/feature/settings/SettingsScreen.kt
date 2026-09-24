package com.oryareach.feature.settings

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.oryareach.core.model.Baby
import com.oryareach.core.ui.component.DrawerHeader
import com.oryareach.core.ui.text.dateLabel
import com.oryareach.core.ui.theme.OrYareachTheme
import com.oryareach.core.ui.text.confirmCopied
import com.oryareach.core.ui.text.sensitiveClipEntry
import com.oryareach.core.ui.component.BusyLabel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
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
                    // A raw tap detector, not `clickable`: the easter egg must not give the
                    // heading a ripple or make TalkBack announce it as a button.
                    modifier = Modifier
                        .semantics { heading() }
                        .pointerInput(easterEggMessage) {
                            detectTapGestures {
                                titleTapCount++
                                if (titleTapCount >= 7) {
                                    titleTapCount = 0
                                    Toast.makeText(context, easterEggMessage, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                )
            }

            item { AccountSection(uiState = uiState, actions = actions) }
            item { SecuritySection(uiState = uiState, actions = actions) }
            item { ChildrenSection(uiState = uiState, actions = actions) }
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
                    // Sign-out waits on the server (push token, device row) before it leaves.
                    BusyLabel(stringResource(R.string.settings_sign_out), busy = uiState.busy)
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
                        clipboard.setClipEntry(sensitiveClipEntry("recovery-phrase", words.joinToString(" ")))
                        context.confirmCopied()
                    }
                }) {
                    Text(stringResource(R.string.settings_recovery_phrase_copy))
                }
            },
        )
    }

    if (uiState.addChildVisible) {
        ChildFormDialog(
            baby = null,
            onDismiss = actions::onDismissAddChild,
            onSubmit = { name, dueDate, _, _, _, _, makeActive ->
                actions.onAddChild(name, dueDate, makeActive)
            },
        )
    }

    uiState.editingChild?.let { child ->
        ChildFormDialog(
            baby = child,
            onDismiss = actions::onDismissEditChild,
            onSubmit = { name, dueDate, birthDate, birthTime, weight, place, _ ->
                actions.onUpdateChildBirthDetails(child.id, name, dueDate, birthDate, birthTime, weight, place)
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

/**
 * The couple's children and how often the baby feeds. Adding a child lives here rather than on
 * the home page so a stray tap can't start a new pregnancy record by accident.
 */
@Composable
private fun ChildrenSection(uiState: SettingsUiState, actions: SettingsActions) {
    SectionCard(title = stringResource(R.string.settings_children_title)) {
        if (uiState.children.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_children_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        uiState.children.forEach { child ->
            ChildRow(
                child = child,
                active = child.id == uiState.activeBabyId,
                onSetActive = { actions.onSetActiveChild(child.id) },
                onEdit = { actions.onEditChildClick(child) },
            )
        }

        OutlinedButton(onClick = actions::onAddChildClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_add_child))
        }

        IntervalRow(
            labelRes = R.string.settings_feed_interval,
            minutes = uiState.feedIntervalMinutes,
            options = uiState.feedIntervalOptionMinutes,
            onChange = actions::onFeedIntervalChange,
        )

        IntervalRow(
            labelRes = R.string.settings_pump_interval,
            minutes = uiState.pumpIntervalMinutes,
            options = uiState.pumpIntervalOptionMinutes,
            onChange = actions::onPumpIntervalChange,
        )
    }
}

@Composable
private fun ChildRow(child: Baby, active: Boolean, onSetActive: () -> Unit, onEdit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSetActive).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = child.name ?: stringResource(R.string.settings_child_unnamed),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                // The born cases both carry the date. The active one used to end at the word
                // "born", so Emily's row read "Active · born" and stopped mid-sentence, while
                // the inactive row right above it gave a date. A birth date can still be missing,
                // and then there is genuinely nothing to add.
                text = when {
                    active && child.isBorn -> child.birthDate
                        ?.let { stringResource(R.string.settings_child_active_born_on, dateLabel(it)) }
                        ?: stringResource(R.string.settings_child_active_born)
                    active -> stringResource(R.string.settings_child_active_expected)
                    child.isBorn -> child.birthDate
                        ?.let { stringResource(R.string.settings_child_born, dateLabel(it)) }
                        ?: stringResource(R.string.settings_child_born_unknown)
                    else -> stringResource(R.string.settings_child_expected)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onEdit) { Text(stringResource(R.string.settings_child_edit)) }
    }
}

/** Shared by the feed and pump cadences — same control, different setting behind it. */
@Composable
private fun IntervalRow(
    @StringRes labelRes: Int,
    minutes: Int,
    options: List<Int>,
    onChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(stringResource(R.string.settings_interval_value, minutes))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_interval_value, option)) },
                        onClick = { expanded = false; onChange(option) },
                    )
                }
            }
        }
    }
}

/**
 * One dialog for both "add a child" and "edit this child's details" — the fields are the same
 * set, and a child added before the birth simply leaves the birth half empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildFormDialog(
    baby: Baby?,
    onDismiss: () -> Unit,
    onSubmit: (
        name: String,
        dueDate: LocalDate?,
        birthDate: LocalDate?,
        birthTime: kotlinx.datetime.LocalTime?,
        birthWeightGrams: Int?,
        birthPlace: String?,
        makeActive: Boolean,
    ) -> Unit,
) {
    var name by remember { mutableStateOf(baby?.name.orEmpty()) }
    var dueDate by remember { mutableStateOf(baby?.dueDate) }
    var birthDate by remember { mutableStateOf(baby?.birthDate) }
    var weight by remember { mutableStateOf(baby?.birthWeightGrams?.toString().orEmpty()) }
    var place by remember { mutableStateOf(baby?.birthPlace.orEmpty()) }
    var makeActive by remember { mutableStateOf(baby == null) }
    var pickingDueDate by remember { mutableStateOf(false) }
    var pickingBirthDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (baby == null) R.string.settings_add_child else R.string.settings_child_edit_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_child_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { pickingDueDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(dueDate?.let { dateLabel(it) } ?: stringResource(R.string.settings_child_due_date))
                }
                if (baby != null) {
                    OutlinedButton(onClick = { pickingBirthDate = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(birthDate?.let { dateLabel(it) } ?: stringResource(R.string.settings_child_birth_date))
                    }
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { value -> weight = value.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(R.string.settings_child_birth_weight)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = place,
                        onValueChange = { place = it },
                        label = { Text(stringResource(R.string.settings_child_birth_place)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (baby == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = makeActive, onCheckedChange = { makeActive = it })
                        Text(stringResource(R.string.settings_child_make_active))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // The birth time is edited on the home page's birth sheet, which has a time
                // picker; this dialog leaves whatever is already stored alone.
                onSubmit(name, dueDate, birthDate, baby?.birthTime, weight.toIntOrNull(), place.ifBlank { null }, makeActive)
            }) { Text(stringResource(R.string.settings_child_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )

    if (pickingDueDate) {
        DatePickerDialogFor(
            initial = dueDate,
            onDismiss = { pickingDueDate = false },
            onPicked = { dueDate = it; pickingDueDate = false },
        )
    }

    if (pickingBirthDate) {
        DatePickerDialogFor(
            initial = birthDate,
            onDismiss = { pickingBirthDate = false },
            onPicked = { birthDate = it; pickingBirthDate = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialogFor(initial: LocalDate?, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial?.let { Instant.parse("${it}T00:00:00Z").toEpochMilliseconds() },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { millis ->
                    onPicked(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date)
                }
            }) { Text(stringResource(R.string.settings_child_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    ) {
        DatePicker(state = pickerState)
    }
}

/**
 * One group of settings.
 *
 * [collapsible] folds the group shut by default, for the ones nobody opens this screen to get
 * to — the paired devices, the recovery phrase, the calendar connection. They were all drawn in
 * full, so the settings anyone actually changes sat below four screens of things they don't.
 * A collapsible group keeps its own title as the tap target, so nothing moves or disappears:
 * the heading is where it always was, with a chevron on it.
 */
@Composable
private fun SectionCard(
    title: String,
    collapsible: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(!collapsible) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        if (!collapsible) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                content()
            }
            return@Card
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            DrawerHeader(
                title = title,
                count = null,
                expanded = expanded,
                onToggle = { expanded = !expanded },
            )
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = { content() },
                )
            }
        }
    }
}

@Composable
private fun AccountSection(uiState: SettingsUiState, actions: SettingsActions) {
    val context = LocalContext.current

    SectionCard(title = stringResource(R.string.settings_account_title), collapsible = true) {
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
                BusyLabel(
                    stringResource(R.string.settings_account_google_connect),
                    busy = uiState.googleAccountLinkBusy,
                )
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
    SectionCard(title = stringResource(R.string.settings_recovery_title), collapsible = true) {
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
    SectionCard(title = stringResource(R.string.settings_devices_title), collapsible = true) {
        OutlinedButton(onClick = actions::onManageDevicesClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_manage_devices))
        }
    }
}

@Composable
private fun GoogleCalendarSection(uiState: SettingsUiState, actions: SettingsActions) {
    val context = LocalContext.current

    SectionCard(title = stringResource(R.string.settings_google_calendar_title), collapsible = true) {
        Text(
            text = stringResource(R.string.settings_google_calendar_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        uiState.googleCalendarError?.let { error ->
            Text(stringResource(error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
                // Four states, not two: while the list loads the dialog used to sit blank (the
                // spinner is on the card behind it), and a failed fetch claimed the account had
                // no calendars at all.
                val pickerError = uiState.googleCalendarError
                if (uiState.availableGoogleCalendars.isEmpty() && uiState.googleCalendarBusy) {
                    CircularProgressIndicator()
                } else if (uiState.availableGoogleCalendars.isEmpty() && pickerError != null) {
                    Text(
                        stringResource(pickerError),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (uiState.availableGoogleCalendars.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_google_calendar_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Column {
                        uiState.availableGoogleCalendars.forEach { option ->
                            Row(
                                // 48dp rows: with the checkbox's own tap target switched off
                                // (onCheckedChange = null) each row was one line of text tall,
                                // ~13dp on the Pixel, with the box touching the name.
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .toggleable(
                                        value = option.selected,
                                        onValueChange = { actions.onToggleCalendarSelection(option.id) },
                                        role = Role.Checkbox,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = option.selected, onCheckedChange = null)
                                Spacer(Modifier.width(12.dp))
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
        // Spelled out rather than taking the defaults. Material reads the unchecked track from
        // `surfaceContainerHighest`, which in this palette is the card colour — the same colour
        // this switch sits on, so an off switch became an invisible one. Off is now the page
        // background, which reads as a slot cut into the card, and the thumb is the text colour
        // rather than the border token so it is visible in both themes.
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                uncheckedTrackColor = MaterialTheme.colorScheme.background,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
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
    override fun onSetActiveChild(babyId: String) = Unit
    override fun onAddChildClick() = Unit
    override fun onDismissAddChild() = Unit
    override fun onAddChild(name: String, dueDate: LocalDate?, makeActive: Boolean) = Unit
    override fun onEditChildClick(baby: Baby) = Unit
    override fun onDismissEditChild() = Unit
    override fun onUpdateChildBirthDetails(
        babyId: String,
        name: String,
        dueDate: LocalDate?,
        birthDate: LocalDate?,
        birthTime: kotlinx.datetime.LocalTime?,
        birthWeightGrams: Int?,
        birthPlace: String?,
    ) = Unit
    override fun onFeedIntervalChange(minutes: Int) = Unit
    override fun onPumpIntervalChange(minutes: Int) = Unit
}

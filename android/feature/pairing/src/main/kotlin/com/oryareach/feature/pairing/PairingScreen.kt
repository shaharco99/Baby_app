package com.oryareach.feature.pairing

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oryareach.core.scanner.InvitationQrCode
import com.oryareach.core.scanner.rememberInvitationCodeScanner
import com.oryareach.core.security.InvitationToken
import com.oryareach.core.ui.text.asLtrIsolate
import com.oryareach.core.ui.theme.OrYareachTheme
import kotlinx.coroutines.launch

@Composable
fun PairingScreen(
    uiState: PairingUiState,
    actions: PairingActions,
    modifier: Modifier = Modifier,
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    val stage = uiState.stage

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (stage) {
                PairingStage.Loading -> LoadingStage()
                PairingStage.Choose -> ChooseStage(uiState, actions)
                is PairingStage.ShowRecoveryPhrase -> RecoveryPhraseStage(stage, uiState, actions)
                PairingStage.EnterCode -> EnterCodeStage(uiState, actions)
                PairingStage.AwaitingKey -> AwaitingKeyStage(uiState, actions)
                PairingStage.EnterRecoveryPhrase -> EnterRecoveryPhraseStage(uiState, actions)
                is PairingStage.Ready -> ReadyStage(stage, uiState, actions)
            }

            uiState.errorMessage?.let { message ->
                Text(
                    text = stringResource(message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // The wrong-account escape hatch — see `PairingStage.allowsSignOut`'s doc comment
            // for exactly which stages this covers and why.
            if (stage.allowsSignOut) {
                TextButton(
                    onClick = { confirmSignOut = true },
                    enabled = !uiState.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.pairing_sign_out)) }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.pairing_sign_out_title)) },
            text = { Text(stringResource(R.string.pairing_sign_out_body)) },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; actions.onSignOut() }) {
                    Text(stringResource(R.string.pairing_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) {
                    Text(stringResource(R.string.pairing_recovery_entry_cancel))
                }
            },
        )
    }
}

@Composable
private fun LoadingStage() {
    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ChooseStage(uiState: PairingUiState, actions: PairingActions) {
    Heading(R.string.pairing_choose_title, R.string.pairing_choose_body)

    Button(
        onClick = actions::onCreateWorkspace,
        enabled = !uiState.busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.pairing_create)) }

    OutlinedButton(
        onClick = actions::onChooseJoin,
        enabled = !uiState.busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.pairing_join)) }

    TextButton(
        onClick = actions::onShowRecoveryPhraseEntry,
        enabled = !uiState.busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.pairing_recover_with_phrase)) }
}

@Composable
private fun RecoveryPhraseStage(
    stage: PairingStage.ShowRecoveryPhrase,
    uiState: PairingUiState,
    actions: PairingActions,
) {
    Heading(R.string.pairing_phrase_title, R.string.pairing_phrase_body)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Numbered and isolated: the words are Latin inside an otherwise RTL layout, and
            // order is the whole point of a recovery phrase.
            stage.words.forEachIndexed { index, word ->
                Text(
                    text = "${index + 1}. $word".asLtrIsolate(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    OutlinedButton(
        onClick = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("recovery-phrase", stage.words.joinToString(" "))))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.pairing_phrase_copy))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = uiState.phraseConfirmed, onCheckedChange = actions::onPhraseConfirmedChange)
        Text(
            text = stringResource(R.string.pairing_phrase_confirm),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }

    Button(
        onClick = actions::onFinishRecoveryPhrase,
        // Gated on the checkbox: this screen is the only time the phrase is ever shown.
        enabled = uiState.phraseConfirmed && !uiState.busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.pairing_phrase_continue)) }
}

/**
 * Groups the raw code into dashed chunks for display without touching the field's underlying
 * text/cursor state — keeping [OutlinedTextField]'s value as the raw (undashed) code and
 * transforming only what's shown avoids the cursor-jump bugs that come from feeding a
 * pre-formatted string back through onValueChange (each keystroke's length delta from dash
 * insertion made Compose's own cursor-position diffing land in the wrong place, especially
 * under fast/programmatic input).
 */
private const val INVITE_CODE_GROUP = 5

private val inviteCodeDashTransformation = VisualTransformation { text ->
    val raw = text.text
    val transformed = InvitationToken.forDisplay(raw)
    val offsetMapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            val dashes = if (offset <= 0) 0 else (offset - 1) / INVITE_CODE_GROUP
            return (offset + dashes).coerceIn(0, transformed.length)
        }

        override fun transformedToOriginal(offset: Int): Int {
            val dashes = transformed.take(offset.coerceIn(0, transformed.length)).count { it == '-' }
            return (offset - dashes).coerceIn(0, raw.length)
        }
    }
    TransformedText(AnnotatedString(transformed), offsetMapping)
}

@Composable
private fun EnterCodeStage(uiState: PairingUiState, actions: PairingActions) {
    Heading(R.string.pairing_code_title, R.string.pairing_code_body)

    OutlinedTextField(
        value = uiState.enteredCode,
        onValueChange = actions::onCodeChange,
        label = { Text(stringResource(R.string.pairing_code_label)) },
        singleLine = true,
        visualTransformation = inviteCodeDashTransformation,
        modifier = Modifier.fillMaxWidth(),
    )

    Button(
        onClick = actions::onSubmitCode,
        enabled = uiState.canSubmitCode,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.pairing_code_submit)) }

    // Scans the QR code off the partner's Ready screen instead of typing the 20 characters —
    // same code, just read by camera. onSubmitCode re-checks well-formedness itself, so a
    // misread scan just leaves the field populated for the user to fix rather than failing loud.
    val scanCode = rememberInvitationCodeScanner { scanned ->
        actions.onCodeChange(InvitationToken.normalize(scanned))
        actions.onSubmitCode()
    }
    OutlinedButton(
        onClick = scanCode,
        enabled = !uiState.busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.pairing_code_scan))
    }
}

@Composable
private fun AwaitingKeyStage(uiState: PairingUiState, actions: PairingActions) {
    Heading(R.string.pairing_waiting_title, R.string.pairing_waiting_body)

    Button(
        onClick = actions::onRefresh,
        enabled = !uiState.busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.pairing_check_again)) }

    TextButton(
        onClick = actions::onShowRecoveryPhraseEntry,
        enabled = !uiState.busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.pairing_recover_with_phrase)) }
}

@Composable
private fun EnterRecoveryPhraseStage(uiState: PairingUiState, actions: PairingActions) {
    Heading(R.string.pairing_recovery_entry_title, R.string.pairing_recovery_entry_body)

    OutlinedTextField(
        value = uiState.recoveryPhraseInput,
        onValueChange = actions::onRecoveryPhraseInputChange,
        label = { Text(stringResource(R.string.pairing_recovery_entry_label)) },
        modifier = Modifier.fillMaxWidth(),
    )

    Button(
        onClick = actions::onSubmitRecoveryPhrase,
        enabled = uiState.recoveryPhraseInput.isNotBlank() && !uiState.busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.pairing_recovery_entry_submit)) }

    OutlinedButton(
        onClick = actions::onDismissRecoveryPhraseEntry,
        enabled = !uiState.busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.pairing_recovery_entry_cancel)) }
}

@Composable
private fun ReadyStage(
    stage: PairingStage.Ready,
    uiState: PairingUiState,
    actions: PairingActions,
) {
    Text(
        text = stringResource(R.string.pairing_ready_title),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
    )

    stage.pendingDevices.forEach { device ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.pairing_pending_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = device.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.pairing_pending_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { actions.onApproveDevice(device.deviceKeyId) },
                    enabled = !uiState.busy,
                ) { Text(stringResource(R.string.pairing_approve)) }
            }
        }
    }

    if (stage.inviteCode == null) {
        OutlinedButton(
            onClick = actions::onGenerateInvite,
            enabled = !uiState.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.pairing_invite_generate)) }
    } else {
        val clipboard = LocalClipboard.current
        val scope = rememberCoroutineScope()
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Lets the partner's device scan instead of this code being read aloud and
                // typed in — encodes the raw (undashed) token, same string EnterCodeStage
                // normalizes a typed or scanned code down to.
                InvitationQrCode(
                    content = stage.inviteCode,
                    contentDescription = stringResource(R.string.pairing_invite_qr_description),
                    modifier = Modifier.size(200.dp),
                )
                Text(
                    text = InvitationToken.forDisplay(stage.inviteCode).asLtrIsolate(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.pairing_invite_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            val text = InvitationToken.forDisplay(stage.inviteCode)
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("invite-code", text)))
                        }
                    },
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pairing_invite_copy))
                }
            }
        }
    }

    if (stage.revocableDevices.isNotEmpty()) {
        Text(
            text = stringResource(R.string.pairing_devices_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        stage.revocableDevices.forEach { device ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = device.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    OutlinedButton(
                        onClick = { actions.onRevokeDevice(device.deviceKeyId) },
                        enabled = !uiState.busy,
                    ) { Text(stringResource(R.string.pairing_revoke)) }
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    Button(onClick = actions::onRefresh, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.pairing_continue))
    }
}

@Composable
private fun Heading(titleRes: Int, bodyRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        text = stringResource(bodyRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Preview(showBackground = true)
@Composable
private fun PairingChoosePreview() {
    OrYareachTheme {
        PairingScreen(
            uiState = PairingUiState(stage = PairingStage.Choose),
            actions = NoopPairingActions,
        )
    }
}

private object NoopPairingActions : PairingActions {
    override fun onCreateWorkspace() = Unit
    override fun onChooseJoin() = Unit
    override fun onCodeChange(value: String) = Unit
    override fun onSubmitCode() = Unit
    override fun onPhraseConfirmedChange(confirmed: Boolean) = Unit
    override fun onFinishRecoveryPhrase() = Unit
    override fun onGenerateInvite() = Unit
    override fun onApproveDevice(deviceKeyId: String) = Unit
    override fun onRevokeDevice(deviceKeyId: String) = Unit
    override fun onShowRecoveryPhraseEntry() = Unit
    override fun onDismissRecoveryPhraseEntry() = Unit
    override fun onRecoveryPhraseInputChange(value: String) = Unit
    override fun onSubmitRecoveryPhrase() = Unit
    override fun onRefresh() = Unit
    override fun onSignOut() = Unit
}

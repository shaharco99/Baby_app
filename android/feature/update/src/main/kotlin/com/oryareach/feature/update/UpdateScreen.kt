package com.oryareach.feature.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * A blocking dialog when [uiState] has a manifest: no dismiss request, and (for a mandatory
 * update) no "later"/"skip" actions — matching [UpdateUiState.mandatory].
 */
@Composable
fun UpdateDialog(uiState: UpdateUiState, actions: UpdateActions) {
    val manifest = uiState.availableManifest ?: return

    AlertDialog(
        onDismissRequest = { if (!uiState.mandatory) actions.onLater() },
        title = { Text(stringResource(R.string.update_available_title, manifest.version)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.mandatory) {
                    Text(stringResource(R.string.update_mandatory_notice))
                }
                manifest.notes.forEach { note -> Text("• $note") }

                TextButton(onClick = actions::onViewRelease) {
                    Text(stringResource(R.string.update_view_release))
                }

                if (uiState.downloading || uiState.installing) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        if (uiState.downloading && uiState.totalBytes > 0) {
                            LinearProgressIndicator(
                                progress = { uiState.downloadFraction },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            CircularProgressIndicator()
                        }
                        Text(
                            stringResource(
                                if (uiState.installing) R.string.update_installing else R.string.update_downloading,
                            ),
                        )
                    }
                }

                uiState.errorMessage?.let { message ->
                    Text(stringResource(message), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = actions::onInstall,
                enabled = !uiState.downloading && !uiState.installing,
            ) { Text(stringResource(R.string.update_install)) }
        },
        dismissButton = if (uiState.mandatory) {
            null
        } else {
            {
                Column {
                    TextButton(onClick = actions::onLater) { Text(stringResource(R.string.update_later)) }
                    TextButton(onClick = actions::onSkip) { Text(stringResource(R.string.update_skip)) }
                }
            }
        },
    )
}

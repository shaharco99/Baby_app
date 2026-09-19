package com.oryareach.feature.conflicts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.oryareach.core.database.repository.Conflict
import com.oryareach.core.model.EntityType
import com.oryareach.core.ui.theme.OrYareachTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
fun ConflictsScreen(
    uiState: ConflictsUiState,
    actions: ConflictsActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, modifier = modifier) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    // A plural, not a format string: "1 sync conflicts" is what it used to say.
                    text = pluralStringResource(
                        R.plurals.conflicts_title_count,
                        uiState.conflicts.size,
                        uiState.conflicts.size,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.conflicts_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.conflicts, key = Conflict::recordId) { conflict ->
                        ConflictCard(
                            conflict = conflict,
                            busy = uiState.resolvingId == conflict.recordId,
                            onKeepLocal = { actions.onKeepLocal(conflict.recordId) },
                            onKeepServer = { actions.onKeepServer(conflict.recordId) },
                        )
                    }
                }

                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.conflicts_close))
                }
            }
        }
    }
}

@Composable
private fun ConflictCard(conflict: Conflict, busy: Boolean, onKeepLocal: () -> Unit, onKeepServer: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(conflict.entityType.labelRes()), style = MaterialTheme.typography.labelSmall)

            if (busy) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            } else {
                // The two titles are often identical — a feed and a pump session are named by
                // when they happened, and a conflict is usually two edits to the same one. What
                // actually tells them apart is when each was edited, so that is shown too.
                ConflictSide(
                    label = stringResource(R.string.conflicts_mine, conflict.localTitle),
                    editedAt = conflict.localUpdatedAt,
                    older = conflict.localUpdatedAt <= conflict.serverUpdatedAt,
                )
                ConflictSide(
                    label = stringResource(R.string.conflicts_theirs, conflict.serverTitle),
                    editedAt = conflict.serverUpdatedAt,
                    older = conflict.serverUpdatedAt < conflict.localUpdatedAt,
                )

                // The older edit is the filled button: in a log the first entry is usually the
                // real one and the second is someone entering it again, so the original is what
                // should survive by default. Both are one tap either way.
                val localIsOlder = conflict.localUpdatedAt <= conflict.serverUpdatedAt
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (localIsOlder) {
                        Button(onClick = onKeepLocal, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.conflicts_keep_mine))
                        }
                        OutlinedButton(onClick = onKeepServer, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.conflicts_keep_theirs))
                        }
                    } else {
                        Button(onClick = onKeepServer, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.conflicts_keep_theirs))
                        }
                        OutlinedButton(onClick = onKeepLocal, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.conflicts_keep_mine))
                        }
                    }
                }
            }
        }
    }
}

/** One side of a conflict: what it says, when it was edited, and whether it is the earlier one. */
@Composable
private fun ConflictSide(label: String, editedAt: Long, older: Boolean) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = if (older) {
                stringResource(R.string.conflicts_edited_at_older, editedLabel(editedAt))
            } else {
                stringResource(R.string.conflicts_edited_at, editedLabel(editedAt))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** When an edit was made, to the minute — the same shape the conflict titles use. */
private fun editedLabel(epochMillis: Long): String {
    val moment = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "%02d.%02d %02d:%02d".format(
        moment.day,
        moment.month.ordinal + 1,
        moment.hour,
        moment.minute,
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun ConflictsPreview() {
    OrYareachTheme {
        ConflictsScreen(
            uiState = ConflictsUiState(
                conflicts = listOf(
                    Conflict(
                        recordId = "1",
                        entityType = EntityType.TASK,
                        localTitle = "Pack hospital bag",
                        localUpdatedAt = 0,
                        serverTitle = "Pack hospital bag (updated)",
                        serverUpdatedAt = 0,
                    ),
                ),
            ),
            actions = NoopConflictsActions,
            onDismiss = {},
        )
    }
}

private object NoopConflictsActions : ConflictsActions {
    override fun onKeepLocal(recordId: String) = Unit
    override fun onKeepServer(recordId: String) = Unit
}

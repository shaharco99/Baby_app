package com.oryareach.feature.dates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oryareach.core.model.ImportantDate
import com.oryareach.core.ui.theme.OrYareachTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatesScreen(
    uiState: DatesUiState,
    actions: DatesActions,
    modifier: Modifier = Modifier,
    highlightId: String? = null,
    onHighlightConsumed: () -> Unit = {},
) {
    var deleteConfirmDate by remember { mutableStateOf<ImportantDate?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(highlightId, uiState.dates) {
        val index = uiState.dates.indexOfFirst { it.id == highlightId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            kotlinx.coroutines.delay(1500)
            onHighlightConsumed()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().safeDrawingPadding(),
        floatingActionButton = {
            FloatingActionButton(onClick = actions::onAddClick) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.dates_add))
            }
        },
    ) { padding ->
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = uiState.refreshing,
            onRefresh = actions::onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.dates_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            if (uiState.dates.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.dates_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.dates, key = ImportantDate::id) { date ->
                        DateRow(
                            date = date,
                            actions = actions,
                            onDeleteClick = { deleteConfirmDate = date },
                            highlighted = date.id == highlightId,
                        )
                    }
                }
            }
        }
        }
    }

    if (uiState.sheetVisible) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = actions::onDismissSheet, sheetState = sheetState) {
            DateForm(uiState = uiState, actions = actions)
        }
    }

    if (uiState.datePickerVisible) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = uiState.formDate?.toUtcMillis())
        DatePickerDialog(
            onDismissRequest = actions::onDismissDatePicker,
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { actions.onDateChange(it.toLocalDate()) }
                }) { Text(stringResource(R.string.dates_pick_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = actions::onDismissDatePicker) { Text(stringResource(R.string.dates_pick_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    deleteConfirmDate?.let { date ->
        AlertDialog(
            onDismissRequest = { deleteConfirmDate = null },
            title = { Text(stringResource(R.string.dates_delete_title)) },
            text = { Text(stringResource(R.string.dates_delete_body, date.title)) },
            confirmButton = {
                TextButton(onClick = {
                    actions.onDelete(date.id)
                    deleteConfirmDate = null
                }) { Text(stringResource(R.string.dates_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmDate = null }) { Text(stringResource(R.string.dates_cancel)) }
            },
        )
    }
}

@Composable
private fun DateRow(
    date: ImportantDate,
    actions: DatesActions,
    onDeleteClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Card(
        modifier = if (highlighted) {
            Modifier.fillMaxWidth().border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
        } else {
            Modifier.fillMaxWidth()
        },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).clickable { actions.onEditClick(date) }) {
                Text(text = date.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = date.date.toString() + (date.wish?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.dates_delete))
            }
        }
    }
}

@Composable
private fun DateForm(uiState: DatesUiState, actions: DatesActions) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(if (uiState.isEditing) R.string.dates_edit_title else R.string.dates_add_title),
            style = MaterialTheme.typography.titleLarge,
        )

        OutlinedButton(onClick = actions::onOpenDatePicker, modifier = Modifier.fillMaxWidth()) {
            Text(uiState.formDate?.toString() ?: stringResource(R.string.dates_field_pick_date))
        }

        OutlinedTextField(
            value = uiState.formTitle,
            onValueChange = actions::onTitleChange,
            label = { Text(stringResource(R.string.dates_field_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = uiState.formWish,
            onValueChange = actions::onWishChange,
            label = { Text(stringResource(R.string.dates_field_wish)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = actions::onSubmit,
            enabled = uiState.canSubmitForm,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.dates_save))
        }

        Spacer(Modifier.height(8.dp))
    }
}

private fun LocalDate.toUtcMillis(): Long =
    Instant.parse("${this}T00:00:00Z").toEpochMilliseconds()

private fun Long.toLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

@Preview(showBackground = true)
@Composable
private fun DatesPreview() {
    OrYareachTheme {
        DatesScreen(
            uiState = DatesUiState(
                dates = listOf(ImportantDate(id = "1", date = LocalDate(2026, 12, 25), title = "Due date")),
            ),
            actions = NoopDatesActions,
        )
    }
}

private object NoopDatesActions : DatesActions {
    override fun onAddClick() = Unit
    override fun onEditClick(date: ImportantDate) = Unit
    override fun onDismissSheet() = Unit
    override fun onOpenDatePicker() = Unit
    override fun onDismissDatePicker() = Unit
    override fun onDateChange(value: LocalDate) = Unit
    override fun onTitleChange(value: String) = Unit
    override fun onWishChange(value: String) = Unit
    override fun onSubmit() = Unit
    override fun onDelete(id: String) = Unit
    override fun onRefresh() = Unit
}

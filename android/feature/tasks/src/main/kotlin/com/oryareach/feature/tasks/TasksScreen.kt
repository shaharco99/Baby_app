package com.oryareach.feature.tasks

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oryareach.core.model.Assignee
import com.oryareach.core.model.Document
import com.oryareach.core.model.Priority
import com.oryareach.core.model.Recurrence
import com.oryareach.core.model.RecurrenceFrequency
import com.oryareach.core.model.Task
import com.oryareach.core.model.TaskCategory
import com.oryareach.core.scanner.rememberDocumentScanner
import com.oryareach.core.ui.theme.OrYareachTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    uiState: TasksUiState,
    actions: TasksActions,
    modifier: Modifier = Modifier,
    highlightId: String? = null,
    onHighlightConsumed: () -> Unit = {},
) {
    val hospitalBagTitles = androidx.compose.ui.res.stringArrayResource(R.array.hospital_bag_preset).toList()
    var deleteConfirmTask by remember { mutableStateOf<Task?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(highlightId, uiState.visibleTasks) {
        val index = uiState.visibleTasks.indexOfFirst { it.id == highlightId }
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
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.tasks_add))
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
                text = stringResource(R.string.tasks_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            OutlinedButton(
                onClick = { actions.onSeedHospitalBag(hospitalBagTitles) },
                enabled = !uiState.seedingHospitalBag,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text(stringResource(R.string.tasks_seed_hospital_bag))
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (uiState.allTags.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.allTags, key = { it }) { tag ->
                        FilterChip(
                            selected = uiState.activeTagFilter == tag,
                            onClick = { actions.onTagFilterChange(tag) },
                            label = { Text("#$tag") },
                        )
                    }
                }
            }

            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Priority.entries, key = { "priority-${it.name}" }) { priority ->
                    FilterChip(
                        selected = uiState.activePriorityFilter == priority,
                        onClick = { actions.onPriorityFilterChange(priority) },
                        label = { Text(stringResource(priority.labelRes())) },
                    )
                }
                items(Assignee.entries, key = { "assignee-${it.name}" }) { assignee ->
                    FilterChip(
                        selected = uiState.activeAssigneeFilter == assignee,
                        onClick = { actions.onAssigneeFilterChange(assignee) },
                        label = { Text(assignee.assigneeLabel(uiState.partnerOneName, uiState.partnerTwoName)) },
                    )
                }
            }

            if (uiState.tasks.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.tasks_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // Extra bottom padding keeps the last row's delete button clear of the
                    // floating action button — without it, a short list (down to just one
                    // task) leaves that row sitting directly under the FAB, unreachable.
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = 96.dp + 72.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.visibleTasks, key = Task::id) { task ->
                        TaskRow(
                            task = task,
                            actions = actions,
                            onDeleteClick = { deleteConfirmTask = task },
                            highlighted = task.id == highlightId,
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
            TaskForm(uiState = uiState, actions = actions)
        }
    }

    deleteConfirmTask?.let { task ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteConfirmTask = null },
            title = { Text(stringResource(R.string.tasks_delete_title)) },
            text = { Text(stringResource(R.string.tasks_delete_body, task.title)) },
            confirmButton = {
                TextButton(onClick = {
                    actions.onDelete(task.id)
                    deleteConfirmTask = null
                }) { Text(stringResource(R.string.tasks_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTask = null }) { Text(stringResource(R.string.tasks_cancel)) }
            },
        )
    }
}

@Composable
private fun TaskRow(task: Task, actions: TasksActions, onDeleteClick: () -> Unit, highlighted: Boolean = false) {
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
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Checkbox(checked = task.done, onCheckedChange = { actions.onToggleDone(task.id) })
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
                    .clickable { actions.onEditClick(task) },
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        text = stringResource(task.category.labelRes()) +
                            " · " + stringResource(task.priority.labelRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val recurrence = task.recurrence
                    if (recurrence != null) {
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = stringResource(recurrence.frequency.recurrenceLabelRes()),
                            modifier = Modifier.padding(start = 4.dp).size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.tasks_delete),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskForm(uiState: TasksUiState, actions: TasksActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(if (uiState.isEditing) R.string.tasks_edit_title else R.string.tasks_add_title),
            style = MaterialTheme.typography.titleLarge,
        )

        OutlinedTextField(
            value = uiState.formTitle,
            onValueChange = actions::onTitleChange,
            label = { Text(stringResource(R.string.tasks_field_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        CategoryDropdown(value = uiState.formCategory, onChange = actions::onCategoryChange)

        Text(
            text = stringResource(R.string.tasks_field_priority),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            Priority.entries.forEachIndexed { index, priority ->
                SegmentedButton(
                    selected = uiState.formPriority == priority,
                    onClick = { actions.onPriorityChange(priority) },
                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = Priority.entries.size,
                    ),
                ) {
                    Text(stringResource(priority.labelRes()))
                }
            }
        }

        Text(
            text = stringResource(R.string.tasks_field_assignee),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Assignee.entries.forEach { option ->
                FilterChip(
                    selected = uiState.formAssignee == option,
                    onClick = { actions.onAssigneeChange(option) },
                    label = { Text(option.assigneeLabel(uiState.partnerOneName, uiState.partnerTwoName)) },
                )
            }
        }

        OutlinedTextField(
            value = uiState.formNote,
            onValueChange = actions::onNoteChange,
            label = { Text(stringResource(R.string.tasks_field_note)) },
            modifier = Modifier.fillMaxWidth(),
        )

        DueDateField(value = uiState.formDueDate, onChange = actions::onDueDateChange)

        if (uiState.formDueDate != null) {
            RecurrenceField(value = uiState.formRecurrence, onChange = actions::onRecurrenceChange)
        }

        TagField(
            tags = uiState.formTags,
            input = uiState.formTagInput,
            onInputChange = actions::onTagInputChange,
            onAdd = actions::onAddTag,
            onRemove = actions::onRemoveTag,
        )

        uiState.errorMessage?.let { message ->
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (uiState.isEditing) {
            AttachmentsSection(uiState = uiState, actions = actions)
        }

        Spacer(Modifier.height(4.dp))

        androidx.compose.material3.Button(
            onClick = actions::onSubmit,
            enabled = uiState.canSubmitForm,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.tasks_save))
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AttachmentsSection(uiState: TasksUiState, actions: TasksActions) {
    val context = LocalContext.current
    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        } ?: uri.lastPathSegment.orEmpty()
        actions.onAttachDocument(name, mimeType, bytes)
    }
    val startScan = rememberDocumentScanner { scanned ->
        actions.onAttachDocument(scanned.name, scanned.mimeType, scanned.bytes)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.tasks_field_attachments),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        uiState.attachments.forEach { document ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text(
                    text = document.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                IconButton(onClick = { actions.onDeleteAttachment(document) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.tasks_remove_attachment))
                }
            }
        }
        if (uiState.attaching) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { attachLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.tasks_attach_document))
                }
                TextButton(onClick = startScan) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.tasks_scan_document))
                }
            }
        }
    }
}

@Composable
private fun DueDateField(value: LocalDate?, onChange: (LocalDate?) -> Unit) {
    var pickerVisible by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pickerVisible = true }, modifier = Modifier.weight(1f)) {
            Text(
                value?.toString() ?: stringResource(R.string.tasks_field_due_date),
            )
        }
        if (value != null) {
            IconButton(onClick = { onChange(null) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.tasks_due_date_clear))
            }
        }
    }

    if (pickerVisible) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = value?.toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { pickerVisible = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onChange(it.toLocalDate()) }
                    pickerVisible = false
                }) { Text(stringResource(R.string.tasks_due_date_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pickerVisible = false }) { Text(stringResource(R.string.tasks_due_date_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun RecurrenceField(value: Recurrence?, onChange: (Recurrence?) -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.tasks_field_recurrence),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val options: List<RecurrenceFrequency?> = listOf(null) + RecurrenceFrequency.entries
            options.forEach { option ->
                FilterChip(
                    selected = value?.frequency == option,
                    onClick = { onChange(option?.let { Recurrence(it) }) },
                    label = { Text(stringResource(option.recurrenceLabelRes())) },
                )
            }
        }
    }
}

@Composable
private fun TagField(
    tags: List<String>,
    input: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.tasks_field_tags),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    FilterChip(
                        selected = true,
                        onClick = { onRemove(tag) },
                        label = { Text("#$tag") },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.tasks_remove_tag)) },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                label = { Text(stringResource(R.string.tasks_field_tag_input)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAdd, enabled = input.isNotBlank()) {
                Text(stringResource(R.string.tasks_add_tag))
            }
        }
    }
}

private fun LocalDate.toUtcMillis(): Long = Instant.parse("${this}T00:00:00Z").toEpochMilliseconds()

private fun Long.toLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

@Composable
private fun CategoryDropdown(value: TaskCategory, onChange: (TaskCategory) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.tasks_field_category) + ": " + stringResource(value.labelRes()))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TaskCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(stringResource(category.labelRes())) },
                    onClick = {
                        onChange(category)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TasksPreview() {
    OrYareachTheme {
        TasksScreen(
            uiState = TasksUiState(
                tasks = listOf(
                    Task(id = "1", title = "Pack hospital bag", category = TaskCategory.HOSPITAL_BAG),
                    Task(id = "2", title = "Book pediatrician", category = TaskCategory.MEDICAL, done = true),
                ),
            ),
            actions = NoopTasksActions,
        )
    }
}

private object NoopTasksActions : TasksActions {
    override fun onAddClick() = Unit
    override fun onEditClick(task: Task) = Unit
    override fun onDismissSheet() = Unit
    override fun onTitleChange(value: String) = Unit
    override fun onCategoryChange(value: TaskCategory) = Unit
    override fun onPriorityChange(value: Priority) = Unit
    override fun onAssigneeChange(value: Assignee) = Unit
    override fun onNoteChange(value: String) = Unit
    override fun onDueDateChange(value: LocalDate?) = Unit
    override fun onRecurrenceChange(value: Recurrence?) = Unit
    override fun onTagInputChange(value: String) = Unit
    override fun onAddTag() = Unit
    override fun onRemoveTag(tag: String) = Unit
    override fun onTagFilterChange(tag: String?) = Unit
    override fun onPriorityFilterChange(priority: Priority?) = Unit
    override fun onAssigneeFilterChange(assignee: Assignee?) = Unit
    override fun onSubmit() = Unit
    override fun onToggleDone(id: String) = Unit
    override fun onDelete(id: String) = Unit
    override fun onSeedHospitalBag(titles: List<String>) = Unit
    override fun onAttachDocument(name: String, mimeType: String, bytes: ByteArray) = Unit
    override fun onDeleteAttachment(document: Document) = Unit
    override fun onRefresh() = Unit
}

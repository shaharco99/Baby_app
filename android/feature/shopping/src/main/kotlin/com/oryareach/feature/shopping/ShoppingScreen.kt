package com.oryareach.feature.shopping

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oryareach.core.model.Assignee
import com.oryareach.core.model.Priority
import com.oryareach.core.model.ShoppingCategory
import com.oryareach.core.model.ShoppingItem
import com.oryareach.core.model.ShoppingStatus
import com.oryareach.core.ui.theme.OrYareachTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    uiState: ShoppingUiState,
    actions: ShoppingActions,
    modifier: Modifier = Modifier,
    highlightId: String? = null,
    onHighlightConsumed: () -> Unit = {},
) {
    var deleteConfirmItem by remember { mutableStateOf<ShoppingItem?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(highlightId, uiState.sortedItems) {
        val index = uiState.sortedItems.indexOfFirst { it.id == highlightId }
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
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.shopping_add))
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
                text = stringResource(R.string.shopping_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            BudgetSummary(uiState = uiState)

            if (uiState.items.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.shopping_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.sortedItems, key = ShoppingItem::id) { item ->
                        ShoppingRow(
                            item = item,
                            actions = actions,
                            onDeleteClick = { deleteConfirmItem = item },
                            highlighted = item.id == highlightId,
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
            ShoppingForm(uiState = uiState, actions = actions)
        }
    }

    deleteConfirmItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteConfirmItem = null },
            title = { Text(stringResource(R.string.shopping_delete_title)) },
            text = { Text(stringResource(R.string.shopping_delete_body, item.name)) },
            confirmButton = {
                TextButton(onClick = {
                    actions.onDelete(item.id)
                    deleteConfirmItem = null
                }) { Text(stringResource(R.string.shopping_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmItem = null }) { Text(stringResource(R.string.shopping_cancel)) }
            },
        )
    }
}

@Composable
private fun BudgetSummary(uiState: ShoppingUiState) {
    val budget = uiState.budget
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = stringResource(R.string.shopping_budget_estimated),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(formatIls(budget.estimatedTotal), style = MaterialTheme.typography.titleMedium)
        }
        Column {
            Text(
                text = stringResource(R.string.shopping_budget_spent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(formatIls(budget.spentTotal), style = MaterialTheme.typography.titleMedium)
        }
        Column {
            Text(
                text = stringResource(R.string.shopping_budget_bought),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("${budget.boughtCount}/${budget.totalCount}", style = MaterialTheme.typography.titleMedium)
        }
    }
    }
}

@Composable
private fun ShoppingRow(
    item: ShoppingItem,
    actions: ShoppingActions,
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
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f).clickable { actions.onEditClick(item) },
                ) {
                    Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(item.category.labelRes()) +
                            " · " + stringResource(item.priority.labelRes()) +
                            item.estimatedPrice?.let { " · " + formatIls(it) }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.shopping_delete))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShoppingStatus.entries.forEach { status ->
                    FilterChip(
                        selected = item.status == status,
                        onClick = { actions.onStatusChange(item.id, status) },
                        label = { Text(stringResource(status.labelRes())) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingForm(uiState: ShoppingUiState, actions: ShoppingActions) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(if (uiState.isEditing) R.string.shopping_edit_title else R.string.shopping_add_title),
            style = MaterialTheme.typography.titleLarge,
        )

        OutlinedTextField(
            value = uiState.formName,
            onValueChange = actions::onNameChange,
            label = { Text(stringResource(R.string.shopping_field_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        CategoryDropdown(value = uiState.formCategory, onChange = actions::onCategoryChange)

        OutlinedTextField(
            value = uiState.formEstimatedPrice,
            onValueChange = actions::onEstimatedPriceChange,
            label = { Text(stringResource(R.string.shopping_field_estimated_price)) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource(R.string.shopping_field_priority),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            Priority.entries.forEachIndexed { index, priority ->
                SegmentedButton(
                    selected = uiState.formPriority == priority,
                    onClick = { actions.onPriorityChange(priority) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = Priority.entries.size),
                ) {
                    Text(stringResource(priority.labelRes()))
                }
            }
        }

        Text(
            text = stringResource(R.string.shopping_field_assignee),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val options: List<Assignee?> = listOf(null, Assignee.PARTNER_ONE, Assignee.PARTNER_TWO, Assignee.BOTH)
            options.forEach { option ->
                FilterChip(
                    selected = uiState.formAssignee == option,
                    onClick = { actions.onAssigneeChange(option) },
                    label = { Text(stringResource(option.labelRes())) },
                )
            }
        }

        OutlinedTextField(
            value = uiState.formLink,
            onValueChange = actions::onLinkChange,
            label = { Text(stringResource(R.string.shopping_field_link)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = uiState.formNote,
            onValueChange = actions::onNoteChange,
            label = { Text(stringResource(R.string.shopping_field_note)) },
            modifier = Modifier.fillMaxWidth(),
        )

        if (uiState.isEditing) {
            AlternativesSection(uiState = uiState, actions = actions)
            AttachmentsSection(uiState = uiState, actions = actions)
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = actions::onSubmit,
            enabled = uiState.canSubmitForm,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.shopping_save))
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AlternativesSection(uiState: ShoppingUiState, actions: ShoppingActions) {
    Text(
        text = stringResource(R.string.shopping_field_alternatives),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    uiState.formAlternatives.forEach { alternative ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = alternative.name + alternative.price?.let { " · " + formatIls(it) }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { actions.onRemoveAlternative(alternative.id) }) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.shopping_alternative_remove))
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = uiState.formAltName,
            onValueChange = actions::onAltNameChange,
            label = { Text(stringResource(R.string.shopping_alternative_name)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = uiState.formAltPrice,
            onValueChange = actions::onAltPriceChange,
            label = { Text(stringResource(R.string.shopping_field_estimated_price)) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = actions::onAddAlternative, enabled = uiState.formAltName.isNotBlank()) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.shopping_alternative_add))
        }
    }
}

@Composable
private fun AttachmentsSection(uiState: ShoppingUiState, actions: ShoppingActions) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val attachLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val name = context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        } ?: uri.lastPathSegment.orEmpty()
        actions.onAttachDocument(name, mimeType, bytes)
    }
    val startScan = com.oryareach.core.scanner.rememberDocumentScanner { scanned ->
        actions.onAttachDocument(scanned.name, scanned.mimeType, scanned.bytes)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.shopping_field_attachments),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        uiState.attachments.forEach { document ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = document.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                IconButton(onClick = { actions.onDeleteAttachment(document) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.shopping_remove_attachment))
                }
            }
        }
        if (uiState.attaching) {
            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { attachLauncher.launch(arrayOf("*/*")) }) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.shopping_attach_document))
                }
                TextButton(onClick = startScan) {
                    Icon(
                        Icons.Default.DocumentScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.shopping_scan_document))
                }
            }
        }
    }
}

@Composable
private fun CategoryDropdown(value: ShoppingCategory, onChange: (ShoppingCategory) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.shopping_field_category) + ": " + stringResource(value.labelRes()))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ShoppingCategory.entries.forEach { category ->
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

private fun formatIls(amount: Double): String =
    if (amount == amount.toLong().toDouble()) "₪${amount.toLong()}" else "₪%.2f".format(amount)

@Preview(showBackground = true)
@Composable
private fun ShoppingPreview() {
    OrYareachTheme {
        ShoppingScreen(
            uiState = ShoppingUiState(
                items = listOf(
                    ShoppingItem(id = "1", name = "Crib", category = ShoppingCategory.NURSERY, estimatedPrice = 800.0),
                ),
            ),
            actions = NoopShoppingActions,
        )
    }
}

private object NoopShoppingActions : ShoppingActions {
    override fun onAddClick() = Unit
    override fun onEditClick(item: ShoppingItem) = Unit
    override fun onDismissSheet() = Unit
    override fun onNameChange(value: String) = Unit
    override fun onCategoryChange(value: ShoppingCategory) = Unit
    override fun onEstimatedPriceChange(value: String) = Unit
    override fun onPriorityChange(value: Priority) = Unit
    override fun onAssigneeChange(value: Assignee?) = Unit
    override fun onNoteChange(value: String) = Unit
    override fun onLinkChange(value: String) = Unit
    override fun onAltNameChange(value: String) = Unit
    override fun onAltPriceChange(value: String) = Unit
    override fun onAddAlternative() = Unit
    override fun onRemoveAlternative(id: String) = Unit
    override fun onSubmit() = Unit
    override fun onStatusChange(id: String, status: ShoppingStatus) = Unit
    override fun onDelete(id: String) = Unit
    override fun onAttachDocument(name: String, mimeType: String, bytes: ByteArray) = Unit
    override fun onDeleteAttachment(document: com.oryareach.core.model.Document) = Unit
    override fun onRefresh() = Unit
}

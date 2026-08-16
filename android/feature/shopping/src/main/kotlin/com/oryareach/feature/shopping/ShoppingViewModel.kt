package com.oryareach.feature.shopping

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryareach.core.database.repository.ShoppingItemRepository
import com.oryareach.core.model.Assignee
import com.oryareach.core.model.Priority
import com.oryareach.core.model.ShoppingAlternative
import com.oryareach.core.model.ShoppingCategory
import com.oryareach.core.model.ShoppingItem
import com.oryareach.core.model.ShoppingStatus
import com.oryareach.core.network.auth.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID

@Stable
interface ShoppingActions {
    fun onAddClick()
    fun onEditClick(item: ShoppingItem)
    fun onDismissSheet()
    fun onNameChange(value: String)
    fun onCategoryChange(value: ShoppingCategory)
    fun onEstimatedPriceChange(value: String)
    fun onPriorityChange(value: Priority)
    fun onAssigneeChange(value: Assignee?)
    fun onNoteChange(value: String)
    fun onLinkChange(value: String)
    fun onAltNameChange(value: String)
    fun onAltPriceChange(value: String)
    fun onAddAlternative()
    fun onRemoveAlternative(id: String)
    fun onSubmit()
    fun onStatusChange(id: String, status: ShoppingStatus)
    fun onDelete(id: String)
    fun onRefresh()
}

/**
 * The workspace id is read once, same as [com.oryareach.feature.tasks.TasksViewModel]: routing
 * already guarantees a paired, unlocked device by the time this screen is reachable.
 */
class ShoppingViewModel(
    private val repository: ShoppingItemRepository,
    private val auth: AuthRepository,
    private val syncEngine: com.oryareach.core.sync.SyncEngine,
    private val workspaceId: () -> String?,
) : ViewModel(), ShoppingActions {

    private val _uiState = MutableStateFlow(ShoppingUiState())
    val uiState: StateFlow<ShoppingUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ShoppingEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        workspaceId()?.let { id ->
            viewModelScope.launch {
                repository.observeAll(id).collect { items -> set { it.copy(items = items) } }
            }
        }
    }

    override fun onAddClick() = set {
        it.copy(
            sheetVisible = true,
            editingId = null,
            formName = "",
            formCategory = ShoppingCategory.OTHER,
            formEstimatedPrice = "",
            formPriority = Priority.NORMAL,
            formAssignee = null,
            formNote = "",
            formLink = "",
            formAlternatives = emptyList(),
            formAltName = "",
            formAltPrice = "",
        )
    }

    override fun onEditClick(item: ShoppingItem) = set {
        it.copy(
            sheetVisible = true,
            editingId = item.id,
            formName = item.name,
            formCategory = item.category,
            formEstimatedPrice = item.estimatedPrice?.toString().orEmpty(),
            formPriority = item.priority,
            formAssignee = item.assignee,
            formNote = item.note.orEmpty(),
            formLink = item.link.orEmpty(),
            formAlternatives = item.alternatives,
            formAltName = "",
            formAltPrice = "",
        )
    }

    override fun onDismissSheet() {
        set { it.copy(sheetVisible = false) }
        _effects.trySend(ShoppingEffect.SheetDismissed)
    }

    override fun onNameChange(value: String) = set { it.copy(formName = value) }
    override fun onCategoryChange(value: ShoppingCategory) = set { it.copy(formCategory = value) }
    override fun onEstimatedPriceChange(value: String) = set {
        it.copy(formEstimatedPrice = value.filter(Char::isDigit))
    }
    override fun onPriorityChange(value: Priority) = set { it.copy(formPriority = value) }
    override fun onAssigneeChange(value: Assignee?) = set { it.copy(formAssignee = value) }
    override fun onNoteChange(value: String) = set { it.copy(formNote = value) }
    override fun onLinkChange(value: String) = set { it.copy(formLink = value) }

    override fun onAltNameChange(value: String) = set { it.copy(formAltName = value) }
    override fun onAltPriceChange(value: String) = set {
        it.copy(formAltPrice = value.filter(Char::isDigit))
    }

    override fun onAddAlternative() = set { state ->
        if (state.formAltName.isBlank()) return@set state
        val alternative = ShoppingAlternative(
            id = UUID.randomUUID().toString(),
            name = state.formAltName,
            price = state.formAltPrice.toIntOrNull(),
        )
        state.copy(
            formAlternatives = state.formAlternatives + alternative,
            formAltName = "",
            formAltPrice = "",
        )
    }

    override fun onRemoveAlternative(id: String) = set { state ->
        state.copy(formAlternatives = state.formAlternatives.filterNot { it.id == id })
    }

    override fun onSubmit() {
        val state = _uiState.value
        val workspace = workspaceId() ?: return
        if (!state.canSubmitForm) return
        set { it.copy(submitting = true) }

        viewModelScope.launch {
            val price = state.formEstimatedPrice.toIntOrNull()
            val editingId = state.editingId
            if (editingId == null) {
                repository.create(
                    workspaceId = workspace,
                    userId = auth.currentUserId().orEmpty(),
                    name = state.formName,
                    category = state.formCategory,
                    estimatedPrice = price,
                    priority = state.formPriority,
                    assignee = state.formAssignee,
                    note = state.formNote.ifBlank { null },
                    link = state.formLink.ifBlank { null },
                )
            } else {
                val existing = state.items.first { it.id == editingId }
                repository.update(
                    id = editingId,
                    name = state.formName,
                    category = state.formCategory,
                    estimatedPrice = price,
                    actualPrice = existing.actualPrice,
                    priority = state.formPriority,
                    assignee = state.formAssignee,
                    note = state.formNote.ifBlank { null },
                    link = state.formLink.ifBlank { null },
                    alternatives = state.formAlternatives,
                    chosenAlternativeId = existing.chosenAlternativeId
                        ?.takeIf { chosenId -> state.formAlternatives.any { it.id == chosenId } },
                )
            }
            set { it.copy(submitting = false, sheetVisible = false) }
            _effects.trySend(ShoppingEffect.SheetDismissed)
        }
    }

    override fun onStatusChange(id: String, status: ShoppingStatus) {
        viewModelScope.launch { repository.setStatus(id, status) }
    }

    override fun onDelete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    override fun onRefresh() {
        if (_uiState.value.refreshing) return
        set { it.copy(refreshing = true) }
        viewModelScope.launch {
            syncEngine.sync()
            set { it.copy(refreshing = false) }
        }
    }

    private fun set(block: (ShoppingUiState) -> ShoppingUiState) {
        _uiState.value = block(_uiState.value)
    }
}

package com.oryareach.feature.tasks

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oryareach.core.database.repository.AppSettingsRepository
import com.oryareach.core.database.repository.DocumentRepository
import com.oryareach.core.database.repository.TaskRepository
import com.oryareach.core.domain.task.nextDueDate
import com.oryareach.core.model.Assignee
import com.oryareach.core.model.Document
import com.oryareach.core.model.Priority
import com.oryareach.core.model.Recurrence
import com.oryareach.core.model.Task
import com.oryareach.core.model.TaskCategory
import com.oryareach.core.network.auth.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

@Stable
interface TasksActions {
    fun onAddClick()
    fun onEditClick(task: Task)
    fun onDismissSheet()
    fun onTitleChange(value: String)
    fun onCategoryChange(value: TaskCategory)
    fun onPriorityChange(value: Priority)
    fun onAssigneeChange(value: Assignee)
    fun onNoteChange(value: String)
    fun onDueDateChange(value: LocalDate?)
    fun onRecurrenceChange(value: Recurrence?)
    fun onTagInputChange(value: String)
    fun onAddTag()
    fun onRemoveTag(tag: String)
    fun onTagFilterChange(tag: String?)
    fun onPriorityFilterChange(priority: Priority?)
    fun onAssigneeFilterChange(assignee: Assignee?)
    fun onSubmit()
    fun onToggleDone(id: String)
    fun onDelete(id: String)
    fun onSeedHospitalBag(titles: List<String>)
    fun onAttachDocument(name: String, mimeType: String, bytes: ByteArray)
    fun onDeleteAttachment(document: Document)
    fun onRefresh()
}

/**
 * The workspace id is read once: by the time this screen can be reached, the app's routing
 * has already confirmed the device is paired and unlocked, and there is no in-app flow that
 * changes the open workspace without a process restart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModel(
    private val repository: TaskRepository,
    private val settingsRepository: AppSettingsRepository,
    private val documents: DocumentRepository,
    private val auth: AuthRepository,
    private val syncEngine: com.oryareach.core.sync.SyncEngine,
    private val workspaceId: () -> String?,
) : ViewModel(), TasksActions {

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    private val _effects = Channel<TasksEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** Kept separate from [_uiState] so editing the form never re-triggers the attachments
     * query — only actually switching which task is open should. */
    private val editingTaskId = MutableStateFlow<String?>(null)

    init {
        workspaceId()?.let { id ->
            viewModelScope.launch {
                repository.observeAll(id).collect { list ->
                    _uiState.update { it.copy(tasks = list) }
                }
            }
            viewModelScope.launch {
                settingsRepository.observe(id).collect { settings ->
                    _uiState.update {
                        it.copy(partnerOneName = settings?.partnerOneName, partnerTwoName = settings?.partnerTwoName)
                    }
                }
            }
            viewModelScope.launch {
                editingTaskId.flatMapLatest { taskId ->
                    if (taskId == null) emptyFlow() else documents.observeForTask(id, taskId)
                }.collect { list -> _uiState.update { it.copy(attachments = list) } }
            }
        }
    }

    override fun onAddClick() = set {
        editingTaskId.value = null
        TasksUiState(
            tasks = it.tasks,
            activeTagFilter = it.activeTagFilter,
            activePriorityFilter = it.activePriorityFilter,
            activeAssigneeFilter = it.activeAssigneeFilter,
            sheetVisible = true,
        )
    }

    override fun onEditClick(task: Task) = set {
        editingTaskId.value = task.id
        it.copy(
            editingId = task.id,
            formTitle = task.title,
            formCategory = task.category,
            formPriority = task.priority,
            formAssignee = task.assignee ?: Assignee.BOTH,
            formNote = task.note.orEmpty(),
            formDueDate = task.dueDate,
            formRecurrence = task.recurrence,
            formTags = task.tags,
            formTagInput = "",
            sheetVisible = true,
            errorMessage = null,
        )
    }

    override fun onDismissSheet() {
        editingTaskId.value = null
        set {
            TasksUiState(
                tasks = it.tasks,
                activeTagFilter = it.activeTagFilter,
                activePriorityFilter = it.activePriorityFilter,
                activeAssigneeFilter = it.activeAssigneeFilter,
            )
        }
        _effects.trySend(TasksEffect.SheetDismissed)
    }

    override fun onTitleChange(value: String) = set { it.copy(formTitle = value, errorMessage = null) }
    override fun onCategoryChange(value: TaskCategory) = set { it.copy(formCategory = value) }
    override fun onPriorityChange(value: Priority) = set { it.copy(formPriority = value) }
    override fun onAssigneeChange(value: Assignee) = set { it.copy(formAssignee = value) }
    override fun onNoteChange(value: String) = set { it.copy(formNote = value) }
    override fun onDueDateChange(value: LocalDate?) = set {
        it.copy(formDueDate = value, formRecurrence = if (value == null) null else it.formRecurrence)
    }
    override fun onRecurrenceChange(value: Recurrence?) = set { it.copy(formRecurrence = value) }

    override fun onTagInputChange(value: String) = set { it.copy(formTagInput = value) }

    override fun onAddTag() {
        val tag = normalizeTag(_uiState.value.formTagInput)
        if (tag.isEmpty()) return
        set {
            it.copy(
                formTags = if (tag in it.formTags) it.formTags else it.formTags + tag,
                formTagInput = "",
            )
        }
    }

    override fun onRemoveTag(tag: String) = set { it.copy(formTags = it.formTags - tag) }

    override fun onTagFilterChange(tag: String?) = set {
        it.copy(activeTagFilter = if (it.activeTagFilter == tag) null else tag)
    }

    override fun onPriorityFilterChange(priority: Priority?) = set {
        it.copy(activePriorityFilter = if (it.activePriorityFilter == priority) null else priority)
    }

    override fun onAssigneeFilterChange(assignee: Assignee?) = set {
        it.copy(activeAssigneeFilter = if (it.activeAssigneeFilter == assignee) null else assignee)
    }

    override fun onSubmit() {
        val state = _uiState.value
        val workspace = workspaceId() ?: return
        if (!state.canSubmitForm) return

        set { it.copy(submitting = true) }

        viewModelScope.launch {
            val note = state.formNote.ifBlank { null }
            if (state.editingId != null) {
                repository.update(
                    id = state.editingId,
                    title = state.formTitle.trim(),
                    category = state.formCategory,
                    priority = state.formPriority,
                    assignee = state.formAssignee,
                    note = note,
                    dueDate = state.formDueDate,
                    recurrence = state.formRecurrence,
                    tags = state.formTags,
                )
            } else {
                repository.create(
                    workspaceId = workspace,
                    userId = auth.currentUserId().orEmpty(),
                    title = state.formTitle.trim(),
                    category = state.formCategory,
                    priority = state.formPriority,
                    assignee = state.formAssignee,
                    note = note,
                    dueDate = state.formDueDate,
                    recurrence = state.formRecurrence,
                    tags = state.formTags,
                )
            }
            editingTaskId.value = null
            set {
                TasksUiState(
                    tasks = it.tasks,
                    activeTagFilter = it.activeTagFilter,
                    activePriorityFilter = it.activePriorityFilter,
                    activeAssigneeFilter = it.activeAssigneeFilter,
                )
            }
            _effects.trySend(TasksEffect.SheetDismissed)
        }
    }

    /** Completing a recurring task (one with both a due date and a recurrence rule) schedules
     * its next occurrence instead of just toggling done — see `TaskRepository.completeAndScheduleNext`.
     * Un-completing one, or completing a non-recurring one, is a plain toggle. */
    override fun onToggleDone(id: String) {
        val task = _uiState.value.tasks.firstOrNull { it.id == id }
        val dueDate = task?.dueDate
        val recurrence = task?.recurrence

        viewModelScope.launch {
            if (task != null && !task.done && dueDate != null && recurrence != null) {
                repository.completeAndScheduleNext(id, nextDueDate(dueDate, recurrence))
            } else {
                repository.toggleDone(id)
            }
        }
    }

    override fun onDelete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    /**
     * Additive and re-runnable, like the web app's preset: only titles not already present
     * (in any category — someone may have moved one) are created, so tapping this twice
     * never duplicates the checklist.
     */
    override fun onSeedHospitalBag(titles: List<String>) {
        val workspace = workspaceId() ?: return
        if (_uiState.value.seedingHospitalBag) return
        set { it.copy(seedingHospitalBag = true) }

        viewModelScope.launch {
            val existingTitles = _uiState.value.tasks.map { it.title.trim().lowercase() }.toSet()
            val userId = auth.currentUserId().orEmpty()
            titles.filter { it.trim().lowercase() !in existingTitles }.forEach { title ->
                repository.create(
                    workspaceId = workspace,
                    userId = userId,
                    title = title,
                    category = TaskCategory.HOSPITAL_BAG,
                )
            }
            set { it.copy(seedingHospitalBag = false) }
        }
    }

    override fun onRefresh() {
        if (_uiState.value.refreshing) return
        set { it.copy(refreshing = true) }

        viewModelScope.launch {
            syncEngine.sync()
            set { it.copy(refreshing = false) }
        }
    }

    override fun onAttachDocument(name: String, mimeType: String, bytes: ByteArray) {
        val workspace = workspaceId() ?: return
        val taskId = _uiState.value.editingId ?: return

        viewModelScope.launch {
            set { it.copy(attaching = true) }
            documents.upload(
                workspaceId = workspace,
                userId = auth.currentUserId().orEmpty(),
                taskId = taskId,
                name = name,
                mimeType = mimeType,
                bytes = bytes,
            )
            set { it.copy(attaching = false) }
        }
    }

    override fun onDeleteAttachment(document: Document) {
        viewModelScope.launch { documents.delete(document.id) }
    }

    private fun set(block: (TasksUiState) -> TasksUiState) {
        _uiState.value = block(_uiState.value)
    }
}

private fun MutableStateFlow<TasksUiState>.update(block: (TasksUiState) -> TasksUiState) {
    value = block(value)
}

/** Strips a leading `#` and lowercases, so `#Medical`, `medical`, and `Medical` are all the
 * same tag — the point of tags is quick filtering, which a case/`#`-sensitive match would
 * quietly break the first time the user typed it differently. */
private fun normalizeTag(raw: String): String = raw.trim().removePrefix("#").lowercase()

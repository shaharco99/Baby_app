package com.oryareach.feature.tasks

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.oryareach.core.model.Assignee
import com.oryareach.core.model.Document
import com.oryareach.core.model.Priority
import com.oryareach.core.model.Recurrence
import com.oryareach.core.model.Task
import com.oryareach.core.model.TaskCategory
import kotlinx.datetime.LocalDate

@Immutable
data class TasksUiState(
    // Persisted snapshot: the live list from Room, already decrypted.
    val tasks: List<Task> = emptyList(),

    // Editable input: the add/edit sheet's form.
    val editingId: String? = null,
    val formTitle: String = "",
    val formCategory: TaskCategory = TaskCategory.OTHER,
    val formPriority: Priority = Priority.NORMAL,
    val formAssignee: Assignee = Assignee.BOTH,
    val formNote: String = "",
    val formDueDate: LocalDate? = null,
    val formRecurrence: Recurrence? = null,
    val formTags: List<String> = emptyList(),
    val formTagInput: String = "",

    // Attachments — only meaningful while editing an existing task (a task must exist before
    // anything can be attached to it).
    val attachments: List<Document> = emptyList(),
    val attaching: Boolean = false,

    // Filters the task list; null means "all".
    val activeTagFilter: String? = null,
    val activePriorityFilter: Priority? = null,
    val activeAssigneeFilter: Assignee? = null,

    // Transient UI-only: must not survive the screen.
    val sheetVisible: Boolean = false,
    val submitting: Boolean = false,
    val seedingHospitalBag: Boolean = false,
    val refreshing: Boolean = false,
    @StringRes val errorMessage: Int? = null,
) {
    // Derived as a getter so it can never drift from the inputs it describes.
    val canSubmitForm: Boolean get() = formTitle.isNotBlank() && !submitting

    val isEditing: Boolean get() = editingId != null

    val allTags: List<String> get() = tasks.flatMap { it.tags }.distinct().sorted()

    val visibleTasks: List<Task>
        get() = tasks
            .let { list -> activeTagFilter?.let { tag -> list.filter { tag in it.tags } } ?: list }
            .let { list -> activePriorityFilter?.let { p -> list.filter { it.priority == p } } ?: list }
            .let { list -> activeAssigneeFilter?.let { a -> list.filter { it.assignee == a } } ?: list }
}

sealed interface TasksEffect {
    data object SheetDismissed : TasksEffect
}

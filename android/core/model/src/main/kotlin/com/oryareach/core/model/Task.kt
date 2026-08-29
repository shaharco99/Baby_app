package com.oryareach.core.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

enum class TaskCategory {
    HOME_PREP,
    DOCUMENTS_AND_INSURANCE,
    MEDICAL,
    HOSPITAL_BAG,
    FOR_THE_BABY,
    OTHER,
}

enum class RecurrenceFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
}

/** [interval] of `2` with [frequency] `WEEKLY` means "every 2 weeks" — a bare frequency
 * enum alone can't express that, and "every N units" covers every rule this app needs
 * without a full RFC 5545 RRULE grammar. */
@Serializable
data class Recurrence(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
)

@Serializable
data class Task(
    val id: String,
    val title: String,
    val category: TaskCategory,
    val priority: Priority = Priority.NORMAL,
    val done: Boolean = false,
    val dueDate: LocalDate? = null,
    val assignee: Assignee? = null,
    val note: String? = null,
    /** Only meaningful alongside [dueDate] — a recurrence rule needs a date to advance from. */
    val recurrence: Recurrence? = null,
    /** Free-form labels (`#home`, `#medical`, …), not a fixed enum like [category] — the point
     * of a tag system is that the user defines the vocabulary, not this app. Stored per-task
     * rather than as a normalized tags table: this app has one couple's worth of tasks, not a
     * multi-tenant catalog that needs tag reuse/autocomplete infrastructure to stay fast. */
    val tags: List<String> = emptyList(),
) {
    /**
     * Takes today as a parameter rather than reading a clock: a model that reads the current
     * time cannot be tested at a chosen date, and would make list rendering depend on when
     * it happened to run.
     */
    fun isOverdue(today: LocalDate): Boolean = !done && dueDate != null && dueDate < today
}

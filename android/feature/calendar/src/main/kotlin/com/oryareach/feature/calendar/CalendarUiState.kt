package com.oryareach.feature.calendar

import androidx.compose.runtime.Immutable
import com.oryareach.core.model.EntityType
import com.oryareach.core.model.ImportantDate
import kotlinx.datetime.LocalDate

enum class CalendarEventKind {
    TASK_DUE,
    IMPORTANT_DATE,
    PERIOD_ACTUAL,
    PERIOD_PREDICTED,
    GOOGLE_EVENT,
}

data class CalendarEvent(
    val date: LocalDate,
    /** Null for [CalendarEventKind.GOOGLE_EVENT]: a fetched Google event has no in-app record
     * or tab to open, unlike every other kind here — see [CalendarEffect.OpenEvent]. */
    val entityType: EntityType?,
    val recordId: String,
    val title: String,
    val kind: CalendarEventKind,
)

@Immutable
data class CalendarUiState(
    val visibleMonth: LocalDate = LocalDate(2000, 1, 1),
    val events: List<CalendarEvent> = emptyList(),
    val selectedDate: LocalDate? = null,
    val refreshing: Boolean = false,

    // Raw important-date records (unlike `events`, which is the merged, display-only
    // projection) — kept so add/edit can look up and prefill the full record by id.
    val importantDates: List<ImportantDate> = emptyList(),

    // Editable input: the add/edit important-date sheet's form.
    val editingId: String? = null,
    val formDate: LocalDate? = null,
    val formTitle: String = "",
    val formWish: String = "",

    // Transient UI-only: must not survive the screen.
    val sheetVisible: Boolean = false,
    val datePickerVisible: Boolean = false,
    val submitting: Boolean = false,
) {
    val eventsForSelectedDate: List<CalendarEvent>
        get() = selectedDate?.let { date -> events.filter { it.date == date } }.orEmpty()

    val daySheetVisible: Boolean get() = selectedDate != null

    val canSubmitForm: Boolean get() = formDate != null && formTitle.isNotBlank() && !submitting

    val isEditing: Boolean get() = editingId != null
}

sealed interface CalendarEffect {
    /** Handled in `:app`: switches to the tab that owns the tapped event — see
     * `:feature:search`'s identical gap note for why this doesn't deep-link to the exact row. */
    data class OpenEvent(val event: CalendarEvent) : CalendarEffect
}

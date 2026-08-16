package com.oryareach.feature.calendar

import androidx.compose.runtime.Immutable
import com.oryareach.core.model.EntityType
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
) {
    val eventsForSelectedDate: List<CalendarEvent>
        get() = selectedDate?.let { date -> events.filter { it.date == date } }.orEmpty()

    val daySheetVisible: Boolean get() = selectedDate != null
}

sealed interface CalendarEffect {
    /** Handled in `:app`: switches to the tab that owns the tapped event — see
     * `:feature:search`'s identical gap note for why this doesn't deep-link to the exact row. */
    data class OpenEvent(val event: CalendarEvent) : CalendarEffect
}

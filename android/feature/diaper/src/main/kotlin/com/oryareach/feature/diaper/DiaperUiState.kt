package com.oryareach.feature.diaper

import androidx.compose.runtime.Immutable
import com.oryareach.core.domain.diaper.DiaperDay
import com.oryareach.core.domain.diaper.DiaperEvent
import com.oryareach.core.model.Baby
import kotlinx.datetime.LocalDate

@Immutable
data class DiaperUiState(
    // Persisted snapshot: feeds with marks and changes logged here, merged into days.
    val baby: Baby? = null,
    val days: List<DiaperDay> = emptyList(),
    /** Today in the viewer's zone, so the day headers name today and yesterday. */
    val today: LocalDate? = null,

    // The log-a-change sheet, which doubles as the edit sheet.
    val sheetVisible: Boolean = false,
    /** Null while logging a new change, the row's id while editing one. */
    val editingId: String? = null,
    val formHadUrine: Boolean = false,
    val formHadStool: Boolean = false,
    val formNote: String = "",
    val formChangedAtEpochMillis: Long = 0,
    val datePickerVisible: Boolean = false,
    val timePickerVisible: Boolean = false,

    // Transient UI-only.
    val busy: Boolean = false,
    val refreshing: Boolean = false,
    /** The change whose trash icon was tapped, while the are-you-sure dialog is up. */
    val deleteConfirm: DiaperEvent? = null,
    /** A change just deleted, while the undo is still on offer. */
    val undoDeleteId: String? = null,
) {
    val isEditing: Boolean get() = editingId != null

    val todayDay: DiaperDay? get() = days.firstOrNull { it.date == today }

    /** The newest change anywhere in the log, for the "last change at" line. */
    val lastChange: DiaperEvent? get() = days.firstOrNull()?.events?.lastOrNull()
}

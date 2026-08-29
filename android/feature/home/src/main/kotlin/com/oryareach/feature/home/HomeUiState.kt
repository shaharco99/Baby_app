package com.oryareach.feature.home

import androidx.compose.runtime.Immutable
import com.oryareach.core.domain.pregnancy.PregnancyProgress
import kotlinx.datetime.LocalDate

@Immutable
data class HomeUiState(
    // Persisted snapshot: what's actually stored.
    val dueDate: LocalDate? = null,
    val babyName: String? = null,
    val partnerOneName: String? = null,
    val partnerTwoName: String? = null,
    val openTaskCount: Int = 0,
    val budgetEstimated: Double = 0.0,
    val budgetSpent: Double = 0.0,
    /** Of [budgetSpent], what the couple paid vs. what came as gifts (assignee "other"). */
    val budgetSpentByUs: Double = 0.0,
    val budgetSpentByOthers: Double = 0.0,

    // Derived from dueDate — a getter, never stored, so it can never go stale.
    val progress: PregnancyProgress? = null,

    // Editable input: the last-period/name edit sheet. The due date itself is derived from
    // this (via `dueDateFromLastPeriod`) at submit time, not entered directly — see
    // `PregnancyProgress.kt`'s doc comment for why that's the more accurate starting point.
    val editingLastPeriodDate: LocalDate? = null,
    val editingBabyName: String = "",
    val editingPartnerOneName: String = "",
    val editingPartnerTwoName: String = "",

    // Transient UI-only.
    val sheetVisible: Boolean = false,
    val datePickerVisible: Boolean = false,
    val importing: Boolean = false,
    val importResult: ImportResult? = null,
    val refreshing: Boolean = false,
    /** Easter egg: long-pressing the moon shows the "Book of Love" tip, but only when the
     * partner has been active recently — see [HomeViewModel.onMoonLongPress]. */
    val bookOfLoveVisible: Boolean = false,
) {
    val hasDueDate: Boolean get() = dueDate != null
    val canSubmitForm: Boolean get() = editingLastPeriodDate != null
}

sealed interface ImportResult {
    data class Success(val taskCount: Int, val shoppingCount: Int, val dateCount: Int) : ImportResult
    data object InvalidFile : ImportResult
}

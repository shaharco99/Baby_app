package com.oryareach.feature.shopping

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.oryareach.core.domain.shopping.calculateBudget
import com.oryareach.core.model.Assignee
import com.oryareach.core.model.Priority
import com.oryareach.core.model.ShoppingCategory
import com.oryareach.core.model.ShoppingAlternative
import com.oryareach.core.model.ShoppingItem
import com.oryareach.core.model.ShoppingStatus

@Immutable
data class ShoppingUiState(
    // Persisted snapshot: the live list from Room, already decrypted.
    val items: List<ShoppingItem> = emptyList(),

    // Editable input: the add/edit sheet's form.
    val editingId: String? = null,
    val formName: String = "",
    val formCategory: ShoppingCategory = ShoppingCategory.OTHER,
    val formEstimatedPrice: String = "",
    val formPriority: Priority = Priority.NORMAL,
    val formAssignee: Assignee? = null,
    val formNote: String = "",
    val formLink: String = "",
    val formAlternatives: List<ShoppingAlternative> = emptyList(),
    val formAltName: String = "",
    val formAltPrice: String = "",

    // Transient UI-only: must not survive the screen.
    val sheetVisible: Boolean = false,
    val submitting: Boolean = false,
    val refreshing: Boolean = false,
    @StringRes val errorMessage: Int? = null,
) {
    val canSubmitForm: Boolean get() = formName.isNotBlank() && !submitting

    val isEditing: Boolean get() = editingId != null

    // Derived from the current list, never persisted — a stale total would drift from the
    // items it is supposed to summarize the moment one of them changes.
    val budget get() = calculateBudget(items)
}

sealed interface ShoppingEffect {
    data object SheetDismissed : ShoppingEffect
}

@StringRes
internal fun ShoppingStatus.labelRes(): Int = when (this) {
    ShoppingStatus.NEED -> R.string.shopping_status_need
    ShoppingStatus.ORDERED -> R.string.shopping_status_ordered
    ShoppingStatus.BOUGHT -> R.string.shopping_status_bought
}

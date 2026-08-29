package com.oryareach.feature.shopping

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.oryareach.core.domain.shopping.calculateBudget
import com.oryareach.core.model.Assignee
import com.oryareach.core.model.Document
import com.oryareach.core.model.Priority
import com.oryareach.core.model.ShoppingCategory
import com.oryareach.core.model.ShoppingAlternative
import com.oryareach.core.model.ShoppingItem
import com.oryareach.core.model.ShoppingStatus
import kotlinx.datetime.LocalDate

@Immutable
data class ShoppingUiState(
    // Persisted snapshot: the live list from Room, already decrypted.
    val items: List<ShoppingItem> = emptyList(),
    val partnerOneName: String? = null,
    val partnerTwoName: String? = null,

    // Editable input: the add/edit sheet's form.
    val editingId: String? = null,
    val formName: String = "",
    val formCategory: ShoppingCategory = ShoppingCategory.OTHER,
    val formEstimatedPrice: String = "",
    val formPriority: Priority = Priority.NORMAL,
    val formAssignee: Assignee? = null,
    val formCustomAssigneeName: String = "",
    val formNote: String = "",
    val formLink: String = "",
    val formAlternatives: List<ShoppingAlternative> = emptyList(),
    val formAltName: String = "",
    val formAltPrice: String = "",
    val formPurchaseDate: LocalDate? = null,
    val formWarrantyMonths: String = "",

    // Attachments — only meaningful while editing an existing item (an item must exist before
    // anything, like a receipt, can be attached to it).
    val attachments: List<Document> = emptyList(),
    val attaching: Boolean = false,

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

    // Still-needed items first, bought ones last — a bought item is done, so it shouldn't
    // compete with what's still outstanding for the top of the list.
    val sortedItems: List<ShoppingItem> get() = items.sortedBy { it.status.sortOrder }
}

private val ShoppingStatus.sortOrder: Int
    get() = when (this) {
        ShoppingStatus.NEED -> 0
        ShoppingStatus.ORDERED -> 1
        ShoppingStatus.BOUGHT -> 2
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

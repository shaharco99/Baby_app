package com.oryareach.feature.shopping

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.oryareach.core.model.Assignee
import com.oryareach.core.model.Priority
import com.oryareach.core.model.ShoppingCategory

@StringRes
internal fun ShoppingCategory.labelRes(): Int = when (this) {
    ShoppingCategory.NURSERY -> R.string.shopping_category_nursery
    ShoppingCategory.CLOTHING -> R.string.shopping_category_clothing
    ShoppingCategory.FEEDING -> R.string.shopping_category_feeding
    ShoppingCategory.CARE_AND_HEALTH -> R.string.shopping_category_care_and_health
    ShoppingCategory.SAFETY -> R.string.shopping_category_safety
    ShoppingCategory.MATERNITY_SUPPLIES -> R.string.shopping_category_maternity_supplies
    ShoppingCategory.OTHER -> R.string.shopping_category_other
}

@StringRes
internal fun Priority.labelRes(): Int = when (this) {
    Priority.LOW -> R.string.priority_low
    Priority.NORMAL -> R.string.priority_normal
    Priority.HIGH -> R.string.priority_high
}

/**
 * [partnerOneName]/[partnerTwoName] fall back to the default names (settings-editable, see
 * `HomeUiState`) when the couple hasn't customized them; [customName] only applies to
 * [Assignee.BOTH] — shopping's repurposed "other" option, see [Assignee]'s doc comment.
 */
@Composable
internal fun Assignee?.assigneeLabel(
    partnerOneName: String?,
    partnerTwoName: String?,
    customName: String?,
): String = when (this) {
    Assignee.PARTNER_ONE -> partnerOneName?.ifBlank { null } ?: stringResource(R.string.default_partner_one_name)
    Assignee.PARTNER_TWO -> partnerTwoName?.ifBlank { null } ?: stringResource(R.string.default_partner_two_name)
    Assignee.BOTH -> customName?.ifBlank { null } ?: stringResource(R.string.assignee_other)
    null -> stringResource(R.string.assignee_unassigned)
}

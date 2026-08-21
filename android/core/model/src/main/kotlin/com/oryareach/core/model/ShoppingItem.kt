package com.oryareach.core.model

import kotlinx.serialization.Serializable

enum class ShoppingCategory {
    NURSERY,
    CLOTHING,
    FEEDING,
    CARE_AND_HEALTH,
    SAFETY,
    MATERNITY_SUPPLIES,
    OTHER,
}

enum class ShoppingStatus {
    NEED,
    ORDERED,
    BOUGHT,
}

@Serializable
data class ShoppingAlternative(
    val id: String,
    val name: String,
    val price: Double? = null,
    val link: String? = null,
    val note: String? = null,
)

@Serializable
data class ShoppingItem(
    val id: String,
    val name: String,
    val category: ShoppingCategory,
    val estimatedPrice: Double? = null,
    val actualPrice: Double? = null,
    val priority: Priority = Priority.NORMAL,
    val status: ShoppingStatus = ShoppingStatus.NEED,
    val assignee: Assignee? = null,
    /** Only meaningful when [assignee] is [Assignee.BOTH] — shopping's "other" option can
     * optionally be given a name instead of the generic "Other" label. */
    val customAssigneeName: String? = null,
    val note: String? = null,
    val link: String? = null,
    val alternatives: List<ShoppingAlternative> = emptyList(),
    val chosenAlternativeId: String? = null,
)

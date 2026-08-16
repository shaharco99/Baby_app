package com.oryareach.core.domain.shopping

import com.oryareach.core.model.ShoppingCategory
import com.oryareach.core.model.ShoppingItem
import com.oryareach.core.model.ShoppingStatus

data class CategoryTotals(val category: ShoppingCategory, val estimated: Double, val spent: Double)

data class BudgetTotals(
    val estimatedTotal: Double,
    val spentTotal: Double,
    val boughtCount: Int,
    val totalCount: Int,
    val byCategory: List<CategoryTotals>,
)

/**
 * `actualPrice` wins over everything else — it is what was really paid. Absent that, a
 * chosen alternative's own price stands in; only then does the original estimate apply.
 */
fun itemEffectivePrice(item: ShoppingItem): Double? {
    item.actualPrice?.let { return it }
    item.chosenAlternativeId?.let { chosenId ->
        item.alternatives.firstOrNull { it.id == chosenId }?.price?.let { return it }
    }
    return item.estimatedPrice
}

fun calculateBudget(items: List<ShoppingItem>): BudgetTotals {
    val byCategory = linkedMapOf<ShoppingCategory, MutableList<Double>>()
    var estimatedTotal = 0.0
    var spentTotal = 0.0
    var boughtCount = 0

    for (item in items) {
        val estimated = item.estimatedPrice ?: itemEffectivePrice(item) ?: 0.0
        val spent = if (item.status == ShoppingStatus.BOUGHT) itemEffectivePrice(item) ?: 0.0 else 0.0
        estimatedTotal += estimated
        spentTotal += spent
        if (item.status == ShoppingStatus.BOUGHT) boughtCount += 1

        val bucket = byCategory.getOrPut(item.category) { mutableListOf(0.0, 0.0) }
        bucket[0] += estimated
        bucket[1] += spent
    }

    return BudgetTotals(
        estimatedTotal = estimatedTotal,
        spentTotal = spentTotal,
        boughtCount = boughtCount,
        totalCount = items.size,
        byCategory = byCategory.map { (category, totals) -> CategoryTotals(category, totals[0], totals[1]) },
    )
}

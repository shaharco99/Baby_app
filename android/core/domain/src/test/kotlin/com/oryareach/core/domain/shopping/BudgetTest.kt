package com.oryareach.core.domain.shopping

import com.oryareach.core.model.Priority
import com.oryareach.core.model.ShoppingAlternative
import com.oryareach.core.model.ShoppingCategory
import com.oryareach.core.model.ShoppingItem
import com.oryareach.core.model.ShoppingStatus
import io.kotest.matchers.shouldBe
import org.junit.Test

private fun item(
    category: ShoppingCategory = ShoppingCategory.NURSERY,
    estimatedPrice: Double? = null,
    actualPrice: Double? = null,
    status: ShoppingStatus = ShoppingStatus.NEED,
    alternatives: List<ShoppingAlternative> = emptyList(),
    chosenAlternativeId: String? = null,
    assignee: com.oryareach.core.model.Assignee? = null,
) = ShoppingItem(
    id = "id",
    name = "item",
    category = category,
    estimatedPrice = estimatedPrice,
    actualPrice = actualPrice,
    priority = Priority.NORMAL,
    status = status,
    assignee = assignee,
    alternatives = alternatives,
    chosenAlternativeId = chosenAlternativeId,
)

class BudgetTest {

    @Test
    fun `itemEffectivePrice prefers actualPrice over everything else`() {
        itemEffectivePrice(item(actualPrice = 100.0, estimatedPrice = 50.0)) shouldBe 100.0
    }

    @Test
    fun `itemEffectivePrice falls back to the chosen alternative price`() {
        val i = item(
            estimatedPrice = 50.0,
            chosenAlternativeId = "alt-1",
            alternatives = listOf(ShoppingAlternative(id = "alt-1", name = "alt", price = 80.0)),
        )
        itemEffectivePrice(i) shouldBe 80.0
    }

    @Test
    fun `itemEffectivePrice falls back to estimatedPrice when nothing else is set`() {
        itemEffectivePrice(item(estimatedPrice = 50.0)) shouldBe 50.0
    }

    @Test
    fun `itemEffectivePrice is null when no price is known`() {
        itemEffectivePrice(item()) shouldBe null
    }

    @Test
    fun `itemEffectivePrice preserves fractional shekel amounts`() {
        itemEffectivePrice(item(estimatedPrice = 5804.25)) shouldBe 5804.25
    }

    @Test
    fun `calculateBudget only counts spent for bought items`() {
        val totals = calculateBudget(
            listOf(
                item(status = ShoppingStatus.BOUGHT, actualPrice = 100.0),
                item(status = ShoppingStatus.NEED, estimatedPrice = 40.0),
            ),
        )
        totals.spentTotal shouldBe 100.0
        totals.estimatedTotal shouldBe 140.0
        totals.boughtCount shouldBe 1
        totals.totalCount shouldBe 2
    }

    @Test
    fun `calculateBudget splits spent into what the couple paid and gifts from others`() {
        val totals = calculateBudget(
            listOf(
                item(status = ShoppingStatus.BOUGHT, actualPrice = 100.0),
                item(
                    status = ShoppingStatus.BOUGHT,
                    actualPrice = 60.0,
                    assignee = com.oryareach.core.model.Assignee.BOTH,
                ),
                item(
                    status = ShoppingStatus.BOUGHT,
                    actualPrice = 30.0,
                    assignee = com.oryareach.core.model.Assignee.PARTNER_ONE,
                ),
            ),
        )
        totals.spentTotal shouldBe 190.0
        totals.spentByUs shouldBe 130.0
        totals.spentByOthers shouldBe 60.0
    }

    @Test
    fun `calculateBudget groups totals by category`() {
        val totals = calculateBudget(
            listOf(
                item(category = ShoppingCategory.CLOTHING, estimatedPrice = 20.0),
                item(category = ShoppingCategory.CLOTHING, estimatedPrice = 30.0),
                item(category = ShoppingCategory.FEEDING, estimatedPrice = 10.0),
            ),
        )
        totals.byCategory.first { it.category == ShoppingCategory.CLOTHING }.estimated shouldBe 50.0
    }

    @Test
    fun `calculateBudget handles an empty list`() {
        val totals = calculateBudget(emptyList())
        totals.estimatedTotal shouldBe 0.0
        totals.spentTotal shouldBe 0.0
        totals.boughtCount shouldBe 0
        totals.totalCount shouldBe 0
        totals.byCategory shouldBe emptyList()
    }
}

package com.oryareach.core.domain.shopping

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.junit.Test

class WarrantyTest {

    @Test
    fun `adds the warranty months to the purchase date`() {
        warrantyEndDate(LocalDate(2026, 1, 15), 12) shouldBe LocalDate(2027, 1, 15)
    }

    @Test
    fun `clamps a month roll-over to the shorter month`() {
        warrantyEndDate(LocalDate(2025, 12, 31), 2) shouldBe LocalDate(2026, 2, 28)
    }

    @Test
    fun `null unless both inputs are present and positive`() {
        warrantyEndDate(null, 12) shouldBe null
        warrantyEndDate(LocalDate(2026, 1, 1), null) shouldBe null
        warrantyEndDate(LocalDate(2026, 1, 1), 0) shouldBe null
    }
}

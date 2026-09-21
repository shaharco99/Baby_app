package com.oryareach.core.domain.baby

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.junit.Test

class BabyAgeTest {

    @Test
    fun `the birth day itself is day zero`() {
        ageInDays(LocalDate(2026, 9, 21), LocalDate(2026, 9, 21)) shouldBe 0
    }

    @Test
    fun `counts whole calendar days`() {
        ageInDays(LocalDate(2026, 9, 21), LocalDate(2026, 9, 28)) shouldBe 7
    }

    @Test
    fun `crosses a month and a leap day`() {
        ageInDays(LocalDate(2028, 2, 27), LocalDate(2028, 3, 1)) shouldBe 3
    }

    @Test
    fun `is negative before the birth date`() {
        ageInDays(LocalDate(2026, 9, 21), LocalDate(2026, 9, 20)) shouldBe -1
    }
}

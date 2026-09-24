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

    @Test
    fun `the birth day is zero in every unit`() {
        val age = babyAge(LocalDate(2026, 9, 16), LocalDate(2026, 9, 16))!!

        age.totalDays shouldBe 0
        age.weeks shouldBe 0
        age.daysInWeek shouldBe 0
        age.years shouldBe 0
        age.months shouldBe 0
        age.days shouldBe 0
    }

    @Test
    fun `under a week is days only`() {
        val age = babyAge(LocalDate(2026, 9, 16), LocalDate(2026, 9, 22))!!

        age.totalDays shouldBe 6
        age.weeks shouldBe 0
        age.daysInWeek shouldBe 6
        age.days shouldBe 6
    }

    @Test
    fun `the seventh day is one week and no days`() {
        val age = babyAge(LocalDate(2026, 9, 16), LocalDate(2026, 9, 23))!!

        age.weeks shouldBe 1
        age.daysInWeek shouldBe 0
    }

    @Test
    fun `a month is the same day of the month, not thirty days`() {
        val age = babyAge(LocalDate(2026, 9, 16), LocalDate(2026, 10, 16))!!

        age.totalDays shouldBe 30
        age.months shouldBe 1
        age.days shouldBe 0
        age.weeks shouldBe 4
        age.daysInWeek shouldBe 2
    }

    @Test
    fun `past a month the remaining days split into weeks and days`() {
        val age = babyAge(LocalDate(2026, 9, 16), LocalDate(2026, 11, 3))!!

        age.months shouldBe 1
        age.days shouldBe 18
        age.weeksAfterMonths shouldBe 2
        age.daysAfterWeeks shouldBe 4
    }

    @Test
    fun `a month from the 31st lands on the short month's end`() {
        // 31 Jan + one month has no 31 Feb to land on, so the period is 28 days and no month.
        val age = babyAge(LocalDate(2026, 1, 31), LocalDate(2026, 2, 28))!!

        age.months shouldBe 0
        age.days shouldBe 28
    }

    @Test
    fun `a year and a bit reads as years, months and days`() {
        val age = babyAge(LocalDate(2026, 9, 16), LocalDate(2028, 1, 20))!!

        age.years shouldBe 1
        age.months shouldBe 4
        age.days shouldBe 4
    }

    @Test
    fun `there is no age before the birth date`() {
        babyAge(LocalDate(2026, 9, 16), LocalDate(2026, 9, 15)) shouldBe null
    }
}

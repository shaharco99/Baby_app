package com.oryareach.core.domain.log

import io.kotest.matchers.shouldBe
import org.junit.Test

class LogDaysTest {

    @Test
    fun `keeps the first days open and folds the rest away`() {
        val split = splitLogDays(listOf("today", "yesterday", "monday", "sunday"))

        split.recent shouldBe listOf("today", "yesterday")
        split.older shouldBe listOf("monday", "sunday")
    }

    @Test
    fun `a log shorter than the window has nothing to fold`() {
        val split = splitLogDays(listOf("today"))

        split.recent shouldBe listOf("today")
        split.older shouldBe emptyList()
    }

    @Test
    fun `an empty log stays empty on both sides`() {
        val split = splitLogDays(emptyList<String>())

        split.recent shouldBe emptyList()
        split.older shouldBe emptyList()
    }

    @Test
    fun `a window of zero folds everything away`() {
        val split = splitLogDays(listOf("today", "yesterday"), recentCount = 0)

        split.recent shouldBe emptyList()
        split.older shouldBe listOf("today", "yesterday")
    }

    @Test
    fun `a negative window is treated as zero rather than dropping days`() {
        val split = splitLogDays(listOf("today", "yesterday"), recentCount = -3)

        split.recent shouldBe emptyList()
        split.older shouldBe listOf("today", "yesterday")
    }

    @Test
    fun `nothing is ever lost between the two halves`() {
        val days = (1..10).map { "day-$it" }

        listOf(-1, 0, 1, 3, 10, 25).forEach { window ->
            val split = splitLogDays(days, recentCount = window)
            split.recent + split.older shouldBe days
        }
    }
}

package com.oryareach.core.watch

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.Test

class TileScheduleTest {

    @Test
    fun `both due times ahead come back in order`() {
        WatchTimers(feedDueAt = 300, pumpDueAt = 200).layoutChangesAfter(now = 100) shouldBe listOf(200L, 300L)
    }

    @Test
    fun `a due time already passed changes nothing more`() {
        WatchTimers(feedDueAt = 50, pumpDueAt = 200).layoutChangesAfter(now = 100) shouldBe listOf(200L)
    }

    @Test
    fun `a running timer hides its due time`() {
        WatchTimers(feedDueAt = 300, nursing = RunningTimer(startedAt = 0)).layoutChangesAfter(now = 100).shouldBeEmpty()
    }

    @Test
    fun `the same moment twice is one change`() {
        WatchTimers(feedDueAt = 300, pumpDueAt = 300).layoutChangesAfter(now = 100) shouldBe listOf(300L)
    }
}

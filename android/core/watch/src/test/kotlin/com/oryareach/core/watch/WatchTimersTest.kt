package com.oryareach.core.watch

import io.kotest.matchers.shouldBe
import org.junit.Test

class WatchTimersTest {

    @Test
    fun `everything set survives the round trip`() {
        val timers = WatchTimers(
            lastFedAt = 1_000,
            feedDueAt = 2_000,
            nursing = RunningTimer(startedAt = 1_000, pausedMillis = 30, pausedAt = 1_500),
            lastPumpAt = 3_000,
            pumpDueAt = 4_000,
            pumping = RunningTimer(startedAt = 3_000, pausedMillis = 0),
        )

        WatchTimers.fromLongs(timers.toLongs()) shouldBe timers
    }

    @Test
    fun `nothing set reads back as empty`() {
        WatchTimers.fromLongs(WatchTimers().toLongs()).isEmpty shouldBe true
    }

    @Test
    fun `a pump with no feed keeps the feed side empty`() {
        val timers = WatchTimers(lastPumpAt = 5, pumping = RunningTimer(startedAt = 5))

        val back = WatchTimers.fromLongs(timers.toLongs())

        back.lastFedAt shouldBe null
        back.nursing shouldBe null
        back.pumping shouldBe RunningTimer(startedAt = 5)
    }

    @Test
    fun `running timer subtracts closed pauses`() {
        RunningTimer(startedAt = 0, pausedMillis = 100).elapsedMillisAt(1_000) shouldBe 900
    }

    @Test
    fun `paused timer stops at the pause`() {
        val timer = RunningTimer(startedAt = 0, pausedMillis = 100, pausedAt = 500)

        timer.elapsedMillisAt(10_000) shouldBe 400
    }

    @Test
    fun `clock skew never shows a negative time`() {
        RunningTimer(startedAt = 1_000).elapsedMillisAt(500) shouldBe 0
    }
}

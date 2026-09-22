package com.oryareach.core.domain.pumping

import com.oryareach.core.model.PumpSession
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.time.Instant

class PumpScheduleTest {

    @Test
    fun `duration is derived from the two ends, rounded down to the minute`() {
        // 18 minutes and 40 seconds is an eighteen-minute session, not a nineteen-minute one.
        session("a", "2026-09-18T10:00:00Z", "2026-09-18T10:18:40Z").durationMinutes shouldBe 18
    }

    @Test
    fun `a running session has no duration and knows it is running`() {
        val running = session("a", "2026-09-18T10:00:00Z", endedAt = null)

        running.isRunning shouldBe true
        running.durationMinutes shouldBe null
    }

    @Test
    fun `groups sessions into local days, newest day first and each day in time order`() {
        val days = groupPumpsByDay(
            listOf(
                session("a", "2026-09-17T15:00:00Z", "2026-09-17T15:20:00Z"),
                session("b", "2026-09-18T05:00:00Z", "2026-09-18T05:20:00Z"),
                session("c", "2026-09-18T02:00:00Z", "2026-09-18T02:20:00Z"),
            ),
            TimeZone.of("Asia/Jerusalem"),
        )

        days.map { it.date } shouldBe listOf(LocalDate(2026, 9, 18), LocalDate(2026, 9, 17))
        days.first().sessions.map { it.id } shouldBe listOf("c", "b")
    }

    @Test
    fun `a session just after local midnight belongs to that new day, not the UTC one`() {
        // 22:30 UTC is 01:30 the next morning in Jerusalem — the night it happened in.
        val days = groupPumpsByDay(
            listOf(session("a", "2026-09-17T22:30:00Z", "2026-09-17T22:50:00Z")),
            TimeZone.of("Asia/Jerusalem"),
        )

        days.single().date shouldBe LocalDate(2026, 9, 18)
    }

    @Test
    fun `a session that runs past midnight is filed under the day it started`() {
        val days = groupPumpsByDay(
            listOf(session("a", "2026-09-17T20:55:00Z", "2026-09-17T21:15:00Z")),
            TimeZone.of("Asia/Jerusalem"),
        )

        // 23:55 to 00:15 local: one day, the one it began in.
        days.map { it.date } shouldBe listOf(LocalDate(2026, 9, 17))
    }

    @Test
    fun `a day sums only what was measured`() {
        val day = groupPumpsByDay(
            listOf(
                session("a", "2026-09-18T06:00:00Z", "2026-09-18T06:20:00Z", amountMl = 90),
                session("b", "2026-09-18T09:00:00Z", "2026-09-18T09:15:00Z", amountMl = null),
            ),
            TimeZone.UTC,
        ).single()

        day.totalMl shouldBe 90
        day.totalMinutes shouldBe 35
    }

    @Test
    fun `a day of only unmeasured sessions has no total`() {
        val day = groupPumpsByDay(
            listOf(session("a", "2026-09-18T06:00:00Z", "2026-09-18T06:20:00Z")),
            TimeZone.UTC,
        ).single()

        day.totalMl shouldBe null
    }

    @Test
    fun `the tally counts a running session but does not guess its length`() {
        val tally = pumpingTally(
            listOf(
                session("a", "2026-09-18T06:00:00Z", "2026-09-18T06:20:00Z", amountMl = 90),
                session("b", "2026-09-18T09:00:00Z", endedAt = null),
            ),
        )

        tally.totalSessions shouldBe 2
        tally.totalMinutes shouldBe 20
        tally.totalMl shouldBe 90
    }

    @Test
    fun `a pause comes out of the duration`() {
        // Half past ten to eleven is thirty minutes of wall time, seven of them paused.
        val paused = session(
            "a",
            "2026-09-18T10:30:00Z",
            "2026-09-18T11:00:00Z",
            pausedMillis = 7 * 60_000L,
        )

        paused.durationMinutes shouldBe 23
    }

    @Test
    fun `while paused the elapsed time stops moving`() {
        val paused = session("a", "2026-09-18T10:00:00Z", endedAt = null)
            .copy(pausedAtEpochMillis = at("2026-09-18T10:12:00Z"))

        paused.isPaused shouldBe true
        // Ten minutes after the pause began, it still reads twelve minutes.
        paused.elapsedMillisAt(at("2026-09-18T10:22:00Z")) shouldBe 12 * 60_000L
    }

    @Test
    fun `a session paused more than it ran cannot go negative`() {
        val odd = session("a", "2026-09-18T10:00:00Z", "2026-09-18T10:05:00Z", pausedMillis = 600_000L)

        odd.durationMinutes shouldBe 0
    }

    @Test
    fun `an unpaused session is unaffected`() {
        session("a", "2026-09-18T10:00:00Z", "2026-09-18T10:20:00Z").durationMinutes shouldBe 20
    }

    @Test
    fun `the stash is silent until something has been measured`() {
        val unmeasured = listOf(session("a", "2026-09-18T06:00:00Z", "2026-09-18T06:20:00Z"))

        milkStash(unmeasured, TimeZone.UTC) shouldBe null
    }

    @Test
    fun `the stash totals the measured output and translates it into feeds`() {
        val stash = milkStash(
            listOf(
                session("a", "2026-09-17T06:00:00Z", "2026-09-17T06:20:00Z", amountMl = 100),
                session("b", "2026-09-18T06:00:00Z", "2026-09-18T06:30:00Z", amountMl = 90),
                session("c", "2026-09-18T09:00:00Z", "2026-09-18T09:25:00Z", amountMl = 130),
                // Unmeasured: it counts as a session and as minutes, but not as millilitres.
                session("d", "2026-09-18T12:00:00Z", "2026-09-18T12:10:00Z"),
            ),
            TimeZone.UTC,
        )!!

        stash.totalMl shouldBe 320
        stash.sessions shouldBe 4
        stash.totalMinutes shouldBe 85
        // The 18th: 90 and 130 together beat the 17th's 100.
        stash.bestDayMl shouldBe 220
        // 320 ml is two 120 ml feeds, with the remainder left out rather than rounded up.
        stash.feedsCovered shouldBe 2
    }

    private fun session(
        id: String,
        startedAt: String,
        endedAt: String?,
        amountMl: Int? = null,
        pausedMillis: Long = 0,
    ) = PumpSession(
        id = id,
        startedAtEpochMillis = at(startedAt),
        endedAtEpochMillis = endedAt?.let(::at),
        amountMl = amountMl,
        pausedMillis = pausedMillis,
    )

    private fun at(instant: String): Long = Instant.parse(instant).toEpochMilliseconds()

    @Test
    fun `the stash milestone is the highest round volume already passed`() {
        val first = session("a", "2026-09-18T06:00:00Z", "2026-09-18T06:20:00Z", amountMl = 400)
        val second = session("b", "2026-09-18T12:00:00Z", "2026-09-18T12:20:00Z", amountMl = 150)

        val under = milkStash(listOf(first), TimeZone.UTC)
        val over = milkStash(listOf(first, second), TimeZone.UTC)

        under!!.milestoneMl shouldBe null
        over!!.milestoneMl shouldBe 500
    }
}

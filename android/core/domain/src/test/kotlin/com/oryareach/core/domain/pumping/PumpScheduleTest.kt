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

    private fun session(
        id: String,
        startedAt: String,
        endedAt: String?,
        amountMl: Int? = null,
    ) = PumpSession(
        id = id,
        startedAtEpochMillis = at(startedAt),
        endedAtEpochMillis = endedAt?.let(::at),
        amountMl = amountMl,
    )

    private fun at(instant: String): Long = Instant.parse(instant).toEpochMilliseconds()
}

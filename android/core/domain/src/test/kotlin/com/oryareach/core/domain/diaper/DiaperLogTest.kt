package com.oryareach.core.domain.diaper

import com.oryareach.core.model.DiaperChange
import com.oryareach.core.model.FeedingEntry
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Test
import io.kotest.matchers.shouldBe

class DiaperLogTest {

    private val zone = TimeZone.of("Asia/Jerusalem")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime(2026, 9, day, hour, minute).toInstant(zone).toEpochMilliseconds()

    private fun feed(id: String, time: Long, urine: Boolean = false, stool: Boolean = false) =
        FeedingEntry(id = id, babyId = "b", fedAtEpochMillis = time, hadUrine = urine, hadStool = stool)

    private fun change(id: String, time: Long, urine: Boolean = false, stool: Boolean = false) =
        DiaperChange(id = id, babyId = "b", changedAtEpochMillis = time, hadUrine = urine, hadStool = stool)

    @Test
    fun `feeds without marks are not nappy changes`() {
        val days = diaperDays(listOf(feed("f1", at(24, 8))), emptyList(), zone)
        days.isEmpty() shouldBe true
    }

    @Test
    fun `marked feeds and own changes merge into one day`() {
        val days = diaperDays(
            feeds = listOf(feed("f1", at(24, 8), urine = true), feed("f2", at(24, 11), stool = true)),
            changes = listOf(change("c1", at(24, 9), urine = true, stool = true), change("c2", at(24, 14))),
            timeZone = zone,
        )
        days.size shouldBe 1
        val day = days.single()
        day.events.map { it.id } shouldBe listOf("f1", "c1", "f2", "c2")
        day.changeCount shouldBe 4
        day.urineCount shouldBe 2
        day.stoolCount shouldBe 2
        day.events.map { it.fromFeed } shouldBe listOf(true, false, true, false)
        day.events.last().isDry shouldBe true
    }

    @Test
    fun `days split on local midnight, newest first`() {
        val days = diaperDays(
            feeds = listOf(feed("f1", at(23, 23, 30), urine = true)),
            changes = listOf(change("c1", at(24, 0, 30), stool = true)),
            timeZone = zone,
        )
        days.map { it.date } shouldBe listOf(LocalDate(2026, 9, 24), LocalDate(2026, 9, 23))
    }

    @Test
    fun `counts changes from a day onward`() {
        val days = diaperDays(
            feeds = listOf(feed("f1", at(20, 8), urine = true)),
            changes = listOf(change("c1", at(23, 8)), change("c2", at(24, 8), stool = true)),
            timeZone = zone,
        )
        changesSince(days, LocalDate(2026, 9, 23)) shouldBe 2
    }

    @Test
    fun `urine seen without a change counts the urine, not the nappy`() {
        val days = diaperDays(
            feeds = listOf(
                feed("seen", at(24, 8), urine = true).copy(diaperChanged = false),
                feed("changed", at(24, 11), urine = true),
            ),
            changes = emptyList(),
            timeZone = zone,
        )
        val day = days.single()
        day.events.size shouldBe 2
        day.changeCount shouldBe 1
        day.urineCount shouldBe 2
        day.events.first().changed shouldBe false
    }
}

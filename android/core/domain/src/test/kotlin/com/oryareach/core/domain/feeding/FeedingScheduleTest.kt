package com.oryareach.core.domain.feeding

import com.oryareach.core.model.FeedingEntry
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.time.Instant

class FeedingScheduleTest {

    @Test
    fun `counts down from the last feed plus the interval`() {
        val lastFed = at("2026-09-18T10:00:00Z")
        val countdown = nextFeedCountdown(lastFed, intervalMinutes = 180, nowEpochMillis = at("2026-09-18T11:30:00Z"))

        countdown?.dueAtEpochMillis shouldBe at("2026-09-18T13:00:00Z")
        countdown?.remainingMillis shouldBe 90 * 60_000L
        countdown?.isOverdue shouldBe false
    }

    @Test
    fun `goes negative once the feed is late rather than clamping at zero`() {
        val countdown = nextFeedCountdown(
            lastFedAtEpochMillis = at("2026-09-18T10:00:00Z"),
            intervalMinutes = 180,
            nowEpochMillis = at("2026-09-18T13:20:00Z"),
        )

        countdown?.remainingMillis shouldBe -20 * 60_000L
        countdown?.isOverdue shouldBe true
    }

    @Test
    fun `no countdown before the first feed is logged`() {
        nextFeedCountdown(null, intervalMinutes = 180, nowEpochMillis = at("2026-09-18T10:00:00Z")) shouldBe null
    }

    @Test
    fun `groups feeds into local days, newest day first and each day in time order`() {
        val zone = TimeZone.of("Asia/Jerusalem")
        val days = groupFeedsByDay(
            listOf(
                feed("a", "2026-09-17T15:00:00Z"),
                feed("b", "2026-09-18T05:00:00Z"),
                feed("c", "2026-09-18T02:00:00Z"),
            ),
            zone,
        )

        days.map { it.date } shouldBe listOf(LocalDate(2026, 9, 18), LocalDate(2026, 9, 17))
        days.first().feeds.map { it.id } shouldBe listOf("c", "b")
    }

    @Test
    fun `a feed just after local midnight belongs to that new day, not the UTC one`() {
        // 22:30 UTC is 01:30 the next morning in Jerusalem — the night it happened in.
        val days = groupFeedsByDay(listOf(feed("a", "2026-09-17T22:30:00Z")), TimeZone.of("Asia/Jerusalem"))

        days.single().date shouldBe LocalDate(2026, 9, 18)
    }

    @Test
    fun `a day's total counts only the feeds that were measured`() {
        val day = groupFeedsByDay(
            listOf(
                feed("a", "2026-09-18T06:00:00Z", amountMl = 90),
                feed("b", "2026-09-18T09:00:00Z", amountMl = null),
                feed("c", "2026-09-18T12:00:00Z", amountMl = 60),
            ),
            TimeZone.UTC,
        ).single()

        day.totalMl shouldBe 150
    }

    @Test
    fun `a day with nothing measured has no total rather than a zero`() {
        val day = groupFeedsByDay(listOf(feed("a", "2026-09-18T06:00:00Z", amountMl = null)), TimeZone.UTC).single()

        day.totalMl shouldBe null
    }

    @Test
    fun `formats a countdown unsigned, so late and pending read the same`() {
        formatCountdown(90 * 60_000L) shouldBe "1:30:00"
        formatCountdown(-90 * 60_000L) shouldBe "1:30:00"
        formatCountdown(5_000L) shouldBe "0:00:05"
    }

    @Test
    fun `tallies the night shift by local hour, not UTC`() {
        // 22:00 and 01:00 UTC are 01:00 and 04:00 in Jerusalem — both night feeds there.
        val tally = feedingTally(
            listOf(
                feed("a", "2026-09-17T22:00:00Z"),
                feed("b", "2026-09-18T01:00:00Z"),
                feed("c", "2026-09-18T09:00:00Z"),
            ),
            TimeZone.of("Asia/Jerusalem"),
        )

        tally.totalFeeds shouldBe 3
        tally.nightFeeds shouldBe 2
    }

    @Test
    fun `the longest stretch is the widest gap between consecutive feeds`() {
        val tally = feedingTally(
            listOf(
                feed("a", "2026-09-18T00:00:00Z"),
                feed("c", "2026-09-18T08:00:00Z"),
                feed("b", "2026-09-18T02:00:00Z"),
            ),
            TimeZone.UTC,
        )

        // Sorted by time first, so the gaps are 2h then 6h regardless of input order.
        tally.longestStretchMillis shouldBe 6 * 60 * 60_000L
    }

    @Test
    fun `a single feed has no stretch to measure`() {
        val tally = feedingTally(listOf(feed("a", "2026-09-18T00:00:00Z")), TimeZone.UTC)

        tally.longestStretchMillis shouldBe null
        tally.totalFeeds shouldBe 1
    }

    @Test
    fun `an empty log tallies to nothing rather than zeroes it cannot know`() {
        val tally = feedingTally(emptyList(), TimeZone.UTC)

        tally.totalFeeds shouldBe 0
        tally.nightFeeds shouldBe 0
        tally.totalMl shouldBe null
        tally.longestStretchMillis shouldBe null
    }

    @Test
    fun `a day's total sums both sources of a mixed feed`() {
        val days = groupFeedsByDay(
            listOf(
                mixedFeed("a", "2026-09-18T09:00:00Z", breastMl = 30, formulaMl = 30),
                mixedFeed("b", "2026-09-18T13:00:00Z", breastMl = 40),
            ),
            TimeZone.UTC,
        )

        days.single().totalMl shouldBe 100
        days.single().breastMl shouldBe 70
        days.single().formulaMl shouldBe 30
        days.single().hasSourceBreakdown shouldBe true
    }

    @Test
    fun `a day fed from one source has nothing to break down`() {
        val day = groupFeedsByDay(
            listOf(mixedFeed("a", "2026-09-18T09:00:00Z", breastMl = 30)),
            TimeZone.UTC,
        ).single()

        day.totalMl shouldBe 30
        day.formulaMl shouldBe null
        day.hasSourceBreakdown shouldBe false
    }

    @Test
    fun `a feed written before the split still counts through its legacy amount`() {
        val day = groupFeedsByDay(
            listOf(feed("old", "2026-09-18T09:00:00Z", amountMl = 60)),
            TimeZone.UTC,
        ).single()

        day.totalMl shouldBe 60
        day.hasSourceBreakdown shouldBe false
    }

    @Test
    fun `the tally counts a mixed feed once, at its total`() {
        val tally = feedingTally(
            listOf(mixedFeed("a", "2026-09-18T03:00:00Z", breastMl = 30, formulaMl = 30)),
            TimeZone.UTC,
        )

        tally.totalFeeds shouldBe 1
        tally.nightFeeds shouldBe 1
        tally.totalMl shouldBe 60
    }

    private fun at(iso: String): Long = Instant.parse(iso).toEpochMilliseconds()

    private fun mixedFeed(
        id: String,
        iso: String,
        breastMl: Int? = null,
        formulaMl: Int? = null,
    ) = FeedingEntry(
        id = id,
        babyId = "baby",
        fedAtEpochMillis = at(iso),
        breastMl = breastMl,
        formulaMl = formulaMl,
    )

    private fun feed(id: String, iso: String, amountMl: Int? = null) = FeedingEntry(
        id = id,
        babyId = "baby",
        fedAtEpochMillis = at(iso),
        amountMl = amountMl,
    )
}

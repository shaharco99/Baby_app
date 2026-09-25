package com.oryareach.core.domain.feeding

import com.oryareach.core.model.DiaperChange
import com.oryareach.core.model.FeedType
import com.oryareach.core.model.FeedingEntry
import com.oryareach.core.model.VitaminDose
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.time.Instant

class FeedingSummaryTest {

    private val zone = TimeZone.UTC

    @Test
    fun `a day counts its urine and stool marks`() {
        val day = FeedingDay(
            date = LocalDate(2026, 9, 20),
            feeds = listOf(
                feed("a", "2026-09-20T01:00:00Z", urine = true, stool = true),
                feed("b", "2026-09-20T04:00:00Z", urine = true),
                feed("c", "2026-09-20T07:00:00Z"),
            ),
        )

        day.urineCount shouldBe 2
        day.stoolCount shouldBe 1
    }

    @Test
    fun `a stretch sums its sources and averages the gaps between feeds`() {
        val stretch = summarizeFeeds(
            listOf(
                feed("b", "2026-09-20T03:00:00Z", breast = 40, formula = 20),
                feed("a", "2026-09-20T00:00:00Z", breast = 30),
                feed("c", "2026-09-20T07:00:00Z", formula = 60, urine = true),
            ),
        )

        stretch.feeds shouldBe 3
        stretch.totalMl shouldBe 150
        stretch.breastMl shouldBe 70
        stretch.formulaMl shouldBe 80
        stretch.urineCount shouldBe 1
        stretch.averageGapMinutes shouldBe 210
    }

    @Test
    fun `nothing measured reads as unknown, not as zero`() {
        val stretch = summarizeFeeds(listOf(feed("a", "2026-09-20T00:00:00Z")))

        stretch.totalMl shouldBe null
        stretch.averageGapMinutes shouldBe null
    }

    @Test
    fun `the week is complete days before today, with empty days kept`() {
        val summary = doctorSummary(
            feeds = listOf(
                feed("today", "2026-09-24T08:00:00Z", breast = 100),
                feed("yesterday", "2026-09-23T08:00:00Z", breast = 70, urine = true),
                feed("old", "2026-09-17T08:00:00Z", breast = 50),
                feed("too-old", "2026-09-16T08:00:00Z", breast = 999),
            ),
            doses = emptyList(),
            nowEpochMillis = at("2026-09-24T12:00:00Z"),
            timeZone = zone,
            birthDate = null,
            firstFeedEpochMillis = null,
        )

        summary.days.map { it.date } shouldBe (17..23).map { LocalDate(2026, 9, it) }
        summary.days.map { it.feeds.size } shouldBe listOf(1, 0, 0, 0, 0, 0, 1)
        summary.week.totalMl shouldBe 120
        summary.averageMlPerDay shouldBe 120.0 / 7
        summary.averageUrinePerDay shouldBe 1.0 / 7
    }

    @Test
    fun `the last 24 hours roll across midnight`() {
        val summary = doctorSummary(
            feeds = listOf(
                feed("in", "2026-09-23T13:00:00Z", formula = 60),
                feed("in2", "2026-09-24T02:00:00Z", formula = 40),
                feed("out", "2026-09-23T11:00:00Z", formula = 999),
            ),
            doses = emptyList(),
            nowEpochMillis = at("2026-09-24T12:00:00Z"),
            timeZone = zone,
            birthDate = null,
            firstFeedEpochMillis = null,
        )

        summary.last24Hours.feeds shouldBe 2
        summary.last24Hours.totalMl shouldBe 100
    }

    @Test
    fun `days before the birth are not counted as empty days`() {
        val summary = doctorSummary(
            feeds = emptyList(),
            doses = listOf(
                dose("2026-09-22T09:00:00Z"),
                dose("2026-09-22T19:00:00Z"),
                dose("2026-09-23T09:00:00Z"),
                dose("2026-09-24T09:00:00Z"),
            ),
            nowEpochMillis = at("2026-09-24T12:00:00Z"),
            timeZone = zone,
            birthDate = LocalDate(2026, 9, 21),
            firstFeedEpochMillis = null,
        )

        summary.dayCount shouldBe 3
        summary.vitaminDays shouldBe 2
        summary.averageMlPerDay shouldBe null
    }

    @Test
    fun `born today leaves no complete days to average`() {
        val summary = doctorSummary(
            feeds = emptyList(),
            doses = emptyList(),
            nowEpochMillis = at("2026-09-24T12:00:00Z"),
            timeZone = zone,
            birthDate = LocalDate(2026, 9, 24),
            firstFeedEpochMillis = null,
        )

        summary.dayCount shouldBe 0
        summary.averageFeedsPerDay shouldBe null
    }

    @Test
    fun `days before the log began are not counted as empty days`() {
        val summary = doctorSummary(
            feeds = listOf(
                feed("first", "2026-09-19T10:00:00Z", breast = 30),
                feed("second", "2026-09-23T10:00:00Z", breast = 90),
            ),
            doses = emptyList(),
            nowEpochMillis = at("2026-09-24T12:00:00Z"),
            timeZone = zone,
            birthDate = LocalDate(2026, 9, 16),
            firstFeedEpochMillis = at("2026-09-19T10:00:00Z"),
        )

        summary.days.first().date shouldBe LocalDate(2026, 9, 19)
        summary.dayCount shouldBe 5
        summary.averageMlPerDay shouldBe 120.0 / 5
    }

    @Test
    fun `nappies count the nappy page's changes as well as marked feeds`() {
        val summary = doctorSummary(
            feeds = listOf(
                feed("marked", "2026-09-23T08:00:00Z", urine = true),
                feed("unmarked", "2026-09-23T11:00:00Z"),
                feed("recent", "2026-09-24T09:00:00Z", stool = true),
                feed("seen-only", "2026-09-23T20:00:00Z", urine = true).copy(diaperChanged = false),
            ),
            doses = emptyList(),
            changes = listOf(
                change("wet-dirty", "2026-09-23T09:00:00Z", urine = true, stool = true),
                change("dry", "2026-09-23T15:00:00Z"),
                change("recent-wet", "2026-09-24T10:00:00Z", urine = true),
            ),
            nowEpochMillis = at("2026-09-24T12:00:00Z"),
            timeZone = zone,
            birthDate = LocalDate(2026, 9, 23),
            firstFeedEpochMillis = null,
        )

        // The seen-only feed adds urine but no nappy.
        summary.week.diaperCount shouldBe 3
        summary.week.urineCount shouldBe 3
        summary.week.stoolCount shouldBe 1
        summary.averageDiapersPerDay shouldBe 3.0
        summary.diapers.single().changeCount shouldBe 3
        // From 12:00 on the 23rd: the dry change, the stool feed, the wet change, and the
        // seen-only feed's urine (no nappy).
        summary.last24Hours.diaperCount shouldBe 3
        summary.last24Hours.urineCount shouldBe 2
        summary.last24Hours.stoolCount shouldBe 1
    }

    private fun change(id: String, time: String, urine: Boolean = false, stool: Boolean = false) =
        DiaperChange(
            id = id,
            babyId = "baby",
            changedAtEpochMillis = at(time),
            hadUrine = urine,
            hadStool = stool,
        )

    private fun feed(
        id: String,
        time: String,
        breast: Int? = null,
        formula: Int? = null,
        urine: Boolean = false,
        stool: Boolean = false,
    ) = FeedingEntry(
        id = id,
        babyId = "baby",
        fedAtEpochMillis = at(time),
        feedType = if (breast == null && formula != null) FeedType.FORMULA else FeedType.BREAST_MILK,
        breastMl = breast,
        formulaMl = formula,
        hadUrine = urine,
        hadStool = stool,
    )

    private fun dose(time: String) = VitaminDose(id = time, babyId = "baby", givenAtEpochMillis = at(time))

    private fun at(iso: String) = Instant.parse(iso).toEpochMilliseconds()
}

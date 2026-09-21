package com.oryareach.core.domain.feeding

import io.kotest.matchers.shouldBe
import org.junit.Test

class FeedGuidanceTest {

    @Test
    fun `says nothing before the birth day`() {
        feedGuidance(-1) shouldBe null
    }

    @Test
    fun `the birth day is a teaspoon at a time`() {
        val day0 = feedGuidance(0)!!
        day0.perFeedMinMl shouldBe 5
        day0.perFeedMaxMl shouldBe 7
    }

    @Test
    fun `the first week steps up day by day`() {
        val perFeedMax = (0..6).map { feedGuidance(it)!!.perFeedMaxMl }
        perFeedMax shouldBe listOf(7, 10, 15, 27, 35, 45, 55)
    }

    @Test
    fun `day six and day seven are different bands`() {
        feedGuidance(6)!!.perFeedMaxMl shouldBe 55
        feedGuidance(7)!!.perFeedMinMl shouldBe 45
        feedGuidance(7)!!.perFeedMaxMl shouldBe 60
    }

    @Test
    fun `the rest of the first fortnight holds one band`() {
        feedGuidance(13) shouldBe feedGuidance(7)
    }

    @Test
    fun `two weeks opens the next band`() {
        feedGuidance(14) shouldBe FeedGuidance(60, 90, 7, 10)
        feedGuidance(27) shouldBe feedGuidance(14)
    }

    @Test
    fun `one month opens the next band`() {
        feedGuidance(28) shouldBe FeedGuidance(90, 120, 6, 8)
        feedGuidance(59) shouldBe feedGuidance(28)
    }

    @Test
    fun `two four and six months each open a band`() {
        feedGuidance(60) shouldBe FeedGuidance(120, 150, 5, 7)
        feedGuidance(119) shouldBe feedGuidance(60)
        feedGuidance(120) shouldBe FeedGuidance(150, 180, 4, 6)
        feedGuidance(179) shouldBe feedGuidance(120)
        feedGuidance(180) shouldBe FeedGuidance(180, 240, 4, 5)
    }

    @Test
    fun `the band never goes backwards as the baby grows`() {
        val mins = (0..400).map { feedGuidance(it)!!.perFeedMinMl }
        mins shouldBe mins.sorted()
    }

    @Test
    fun `the daily band multiplies the per-feed band by the feed count`() {
        val day3 = feedGuidance(3)!!
        day3.dailyMinMl shouldBe 22 * 8
        day3.dailyMaxMl shouldBe 27 * 12
    }

    @Test
    fun `the daily maximum is capped at the AAP ceiling`() {
        // 240 * 5 = 1200, which is over the ceiling and must not be shown.
        feedGuidance(180)!!.dailyMaxMl shouldBe DAILY_CEILING_ML
        // 180 * 6 = 1080, likewise.
        feedGuidance(120)!!.dailyMaxMl shouldBe DAILY_CEILING_ML
        // 120 * 8 = 960 lands exactly on it rather than over.
        feedGuidance(28)!!.dailyMaxMl shouldBe DAILY_CEILING_ML
        // The early weeks are nowhere near it, so the cap must not be silently flattening them.
        feedGuidance(7)!!.dailyMaxMl shouldBe 720
    }
}

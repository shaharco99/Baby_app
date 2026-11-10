package com.oryareach.core.domain.log

import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.time.Instant

class PickedTimeTest {

    private val utc = TimeZone.UTC
    private val now = at("2026-11-10T18:00:00Z")

    @Test
    fun `a picked time keeps the date`() {
        resolvePickedTime(now, utc, time = LocalTime(9, 15)) shouldBe at("2026-11-10T09:15:00Z")
    }

    @Test
    fun `a picked date keeps the time of day`() {
        resolvePickedTime(now, utc, date = LocalDate(2026, 11, 8)) shouldBe at("2026-11-08T18:00:00Z")
    }

    @Test
    fun `a time later today is in the future`() {
        val picked = resolvePickedTime(now, utc, time = LocalTime(22, 53))

        isInFuture(picked, now) shouldBe true
    }

    @Test
    fun `now itself and the past are not in the future`() {
        isInFuture(now, now) shouldBe false
        isInFuture(now - 60_000L, now) shouldBe false
    }

    @Test
    fun `an earlier time on an earlier day is not in the future`() {
        val twoDaysAgo = at("2026-11-08T12:00:00Z")
        val picked = resolvePickedTime(twoDaysAgo, utc, time = LocalTime(22, 53))

        isInFuture(picked, now) shouldBe false
    }

    @Test
    fun `clampToNow never returns a moment after now`() {
        clampToNow(now + 5 * 3_600_000L, now) shouldBe now
        clampToNow(now - 3_600_000L, now) shouldBe now - 3_600_000L
    }

    private fun at(iso: String) = Instant.parse(iso).toEpochMilliseconds()
}

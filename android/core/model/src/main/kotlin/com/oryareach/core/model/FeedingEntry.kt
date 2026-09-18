package com.oryareach.core.model

import kotlinx.serialization.Serializable

enum class FeedType {
    BREAST_MILK,
    FORMULA,
    SOLID,
}

/**
 * One feed, logged as it happens.
 *
 * The time is kept as epoch milliseconds rather than a local date-time because the countdown
 * to the next feed is arithmetic on an instant, and a feed logged just before midnight has to
 * stay the same distance from the next one however the calendar day falls.
 *
 * [hadUrine] / [hadStool] ride along on the feed rather than being their own records: on the
 * paper day-sheet this mirrors they are marks in the same column as the feed, and logging them
 * separately would mean two taps for something noticed at the same moment.
 */
@Serializable
data class FeedingEntry(
    val id: String,
    val babyId: String,
    val fedAtEpochMillis: Long,
    val feedType: FeedType = FeedType.BREAST_MILK,
    /** Null when the feed was not measured — common for breastfeeding. */
    val amountMl: Int? = null,
    val hadUrine: Boolean = false,
    val hadStool: Boolean = false,
    val note: String? = null,
)

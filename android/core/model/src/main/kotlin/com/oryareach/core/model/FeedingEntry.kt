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
    /** Null when no breast milk was given, or when it was given but not measured. */
    val breastMl: Int? = null,
    /** Null when no formula was given. */
    val formulaMl: Int? = null,
    /**
     * Legacy single amount, kept as a mirror of [totalMl] on every write.
     *
     * It is what a partner still on a build from before the breast/formula split reads, and
     * during a staggered rollout that is one of the two phones. Records written by such a
     * build arrive with only this field set, which is why [totalMl] falls back to it.
     */
    val amountMl: Int? = null,
    val hadUrine: Boolean = false,
    val hadStool: Boolean = false,
    /**
     * False when urine or stool was seen at the feed but the nappy was left on. The marks still
     * count as urine/stool; only the nappy count skips it. True by default, which is what every
     * record from before this field existed meant.
     */
    val diaperChanged: Boolean = true,
    val note: String? = null,
) {
    /**
     * What this feed came to in all — the one number the history rows show, in the same place
     * they have always shown an amount. A feed that was breast and formula together is its
     * sum; a feed from one source is just that source.
     */
    val totalMl: Int?
        get() = listOfNotNull(breastMl, formulaMl).takeIf { it.isNotEmpty() }?.sum() ?: amountMl

    /**
     * The two amounts again, but reading a pre-split record for what it plainly is.
     *
     * A feed written by a build from before [breastMl] existed — or pulled from a partner still
     * running one — carries only [amountMl] and a [feedType] saying which source it came from.
     * [totalMl] already falls back to it, so such a feed counted toward a day's total; these did
     * not, so it went missing from the day's breakdown. A day could read "215 ml" beside
     * "35 + 120", and the 60 that made up the difference was a formula feed the other phone had
     * logged before it was updated.
     *
     * The guards keep a mixed feed from being counted twice: [amountMl] is a mirror of the sum
     * on anything written since the split, so it is only trusted when neither column is set.
     */
    val breastAmountMl: Int?
        get() = breastMl ?: amountMl.takeIf { formulaMl == null && feedType == FeedType.BREAST_MILK }

    val formulaAmountMl: Int?
        get() = formulaMl ?: amountMl.takeIf { breastMl == null && feedType == FeedType.FORMULA }
}

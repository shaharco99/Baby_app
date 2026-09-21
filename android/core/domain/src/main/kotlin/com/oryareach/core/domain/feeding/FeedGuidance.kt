package com.oryareach.core.domain.feeding

/**
 * Roughly how much one feed should be, and roughly how many feeds a day, at a given age.
 *
 * A guide, never a rule: how much a particular baby takes is between the parents and their
 * nurse. What this is for is the 4am question — "is 30 ml about right or wildly off?" — which
 * is otherwise a phone search with one hand.
 *
 * The bands are day-by-day through the first week, because that is when they change fastest:
 * a newborn's stomach does not stretch at all on days 1 and 2 and then roughly quadruples by
 * day 7. After that they widen to weeks and then months, because so does the real variation.
 *
 * Sources:
 *  - American Academy of Pediatrics, "Amount and Schedule of Baby Formula Feedings"
 *    (healthychildren.org): 30–60 ml per feed in the first week, 90–120 ml by the end of the
 *    first month, 180–240 ml across 4–5 feeds by six months, and no more than 960 ml a day.
 *  - La Leche League Canada, "Newborns Have Small Stomachs": 5–7 ml on day 1, 22–27 ml by
 *    day 3, 45–60 ml by day 7.
 */
data class FeedGuidance(
    val perFeedMinMl: Int,
    val perFeedMaxMl: Int,
    val feedsPerDayMin: Int,
    val feedsPerDayMax: Int,
) {
    val dailyMinMl: Int get() = perFeedMinMl * feedsPerDayMin

    /**
     * Clamped at [DAILY_CEILING_ML]. The ceiling only starts to bite past the first month,
     * which is why it is applied here rather than written into the table — the early rows
     * are nowhere near it and hard-coding a clamped number would hide where it came from.
     */
    val dailyMaxMl: Int get() = minOf(perFeedMaxMl * feedsPerDayMax, DAILY_CEILING_ML)
}

/**
 * Null before the birth day — there is nothing to recommend to someone who is still pregnant,
 * and the caller shows nothing rather than a band invented from a negative age.
 */
fun feedGuidance(ageInDays: Int): FeedGuidance? = when {
    ageInDays < 0 -> null

    // The first week, day by day. Eight to twelve feeds throughout: the count barely moves
    // while the volume does, which is the whole shape of the first week.
    ageInDays == 0 -> FeedGuidance(5, 7, 8, 12)
    ageInDays == 1 -> FeedGuidance(5, 10, 8, 12)
    ageInDays == 2 -> FeedGuidance(10, 15, 8, 12)
    ageInDays == 3 -> FeedGuidance(22, 27, 8, 12)
    ageInDays == 4 -> FeedGuidance(27, 35, 8, 12)
    ageInDays == 5 -> FeedGuidance(35, 45, 8, 12)
    ageInDays == 6 -> FeedGuidance(40, 55, 8, 12)

    // Rest of the first two weeks.
    ageInDays < 14 -> FeedGuidance(45, 60, 8, 12)

    // To one month.
    ageInDays < 28 -> FeedGuidance(60, 90, 7, 10)

    ageInDays < 60 -> FeedGuidance(90, 120, 6, 8)
    ageInDays < 120 -> FeedGuidance(120, 150, 5, 7)
    ageInDays < 180 -> FeedGuidance(150, 180, 4, 6)

    // Six months on, where solids start displacing milk and a band stops meaning much.
    else -> FeedGuidance(180, 240, 4, 5)
}

/** The AAP's daily ceiling: more than this in 24 hours is worth asking someone about. */
const val DAILY_CEILING_ML = 960

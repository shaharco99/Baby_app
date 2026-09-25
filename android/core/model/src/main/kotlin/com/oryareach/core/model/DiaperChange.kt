package com.oryareach.core.model

import kotlinx.serialization.Serializable

/**
 * One nappy change logged on its own, away from a feed.
 *
 * A feed marked with urine or stool already counts as a change — [FeedingEntry.hadUrine] and
 * [FeedingEntry.hadStool] stay on the feed, and the nappy log reads them from there rather than
 * copying them into rows of this type. That is what keeps the two screens from drifting: there
 * is one record per change, whichever screen it was typed into.
 *
 * Both marks false is a dry nappy, which is still a nappy changed.
 */
@Serializable
data class DiaperChange(
    val id: String,
    val babyId: String,
    val changedAtEpochMillis: Long,
    val hadUrine: Boolean = false,
    val hadStool: Boolean = false,
    val note: String? = null,
)

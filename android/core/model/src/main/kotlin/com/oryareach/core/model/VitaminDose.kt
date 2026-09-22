package com.oryareach.core.model

import kotlinx.serialization.Serializable

/**
 * One dose of a daily supplement, logged when it is given.
 *
 * Named for the dose rather than for vitamin D so that iron drops, or anything else a
 * paediatrician adds later, need no second entity: [kind] is what says which.
 *
 * The time is epoch milliseconds, like a feed, but only the local *day* it falls in is ever read
 * — "was today's dose given" is the whole question. It is kept as an instant anyway so that the
 * card can say "given at 18:04", and so a dose logged either side of midnight lands on the day it
 * actually happened on the phone that logged it.
 */
@Serializable
data class VitaminDose(
    val id: String,
    val babyId: String,
    val givenAtEpochMillis: Long,
    val kind: SupplementKind = SupplementKind.VITAMIN_D,
    val note: String? = null,
)

enum class SupplementKind {
    VITAMIN_D,
}

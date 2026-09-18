package com.oryareach.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/**
 * One child of the couple's — the pregnancy first, then the baby once born.
 *
 * Deliberately its own record rather than fields on [AppSettings]: a second child must not
 * overwrite the first, and a feeding entry has to stay attached to the child it belongs to
 * once a sibling exists. [birthDate] being null is what "still pregnant" means, and it is what
 * the home page branches on — there is no separate mode flag to keep in sync with reality.
 */
@Serializable
data class Baby(
    val id: String,
    val name: String? = null,
    val dueDate: LocalDate? = null,
    val birthDate: LocalDate? = null,
    val birthTime: LocalTime? = null,
    val birthWeightGrams: Int? = null,
    val birthPlace: String? = null,
) {
    val isBorn: Boolean get() = birthDate != null
}

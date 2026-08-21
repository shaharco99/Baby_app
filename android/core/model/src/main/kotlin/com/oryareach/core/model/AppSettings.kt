package com.oryareach.core.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** One row per workspace — the couple's shared settings, not per-device preferences. */
@Serializable
data class AppSettings(
    val id: String,
    val dueDate: LocalDate,
    val babyName: String? = null,
    /** Null means "use the default name" — resolved per feature/locale, not stored here. */
    val partnerOneName: String? = null,
    val partnerTwoName: String? = null,
)

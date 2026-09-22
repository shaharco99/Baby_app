package com.oryareach.core.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** One row per workspace — the couple's shared settings, not per-device preferences. */
@Serializable
data class AppSettings(
    val id: String,
    val dueDate: LocalDate,
    /**
     * Superseded by [Baby.name]. Kept, not dropped, so the one-time seed of the first [Baby]
     * has something to read on an existing install; nothing writes it for a new child.
     */
    val babyName: String? = null,
    /** Null means "use the default name" — resolved per feature/locale, not stored here. */
    val partnerOneName: String? = null,
    val partnerTwoName: String? = null,
    /**
     * Which [Baby] the home page, feeding log and reminders currently point at. Shared between
     * the partners on purpose: both should be looking at the same child. Null until the
     * one-time seed runs on first launch after this version.
     */
    val activeBabyId: String? = null,
    /** How long after a feed the reminder fires. */
    val feedIntervalMinutes: Int = DEFAULT_FEED_INTERVAL_MINUTES,
    /**
     * How long after a pumping session *starts* the next reminder fires. Separate from
     * [feedIntervalMinutes] because a pump schedule and a feed schedule rarely line up, and
     * counted from the start because that is how pump schedules are spaced.
     */
    val pumpIntervalMinutes: Int = DEFAULT_PUMP_INTERVAL_MINUTES,
    /**
     * When the daily vitamin D reminder fires, as minutes past local midnight. Null means no
     * reminder is set.
     *
     * Shared between the partners rather than kept per device, for the same reason the child is:
     * there is one baby and one dose a day, so both phones should be asking about the same one
     * at the same time, and either partner can move it.
     */
    val vitaminDMinuteOfDay: Int? = null,
) {
    companion object {
        const val DEFAULT_FEED_INTERVAL_MINUTES = 180
        const val DEFAULT_PUMP_INTERVAL_MINUTES = 180
    }
}

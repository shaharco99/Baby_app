package com.oryareach.feature.home

import androidx.compose.runtime.Immutable
import com.oryareach.core.domain.feeding.FeedCountdown
import com.oryareach.core.domain.feeding.FeedingTally
import com.oryareach.core.domain.pumping.MilkStash
import com.oryareach.core.domain.pregnancy.PregnancyProgress
import com.oryareach.core.model.Baby
import com.oryareach.core.model.PumpSession
import com.oryareach.core.domain.baby.BabyAge
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Immutable
data class HomeUiState(
    /**
     * False until the database has answered once. Every field below defaults to "nothing stored",
     * which renders as a genuine empty state ("when did your last period start?") — so until this
     * flips, the screen must not be shown at all, or a cold start briefly lies about the data.
     */
    val isLoaded: Boolean = false,

    // Persisted snapshot: what's actually stored.
    val dueDate: LocalDate? = null,
    val babyName: String? = null,
    val partnerOneName: String? = null,
    val partnerTwoName: String? = null,
    /** Every child of the workspace, newest first — the switcher's list. */
    val children: List<Baby> = emptyList(),
    /** The child everything currently points at. Null only before the one-time seed runs. */
    val activeBaby: Baby? = null,
    /**
     * Baby mode only: how old this child is today, in all four units at once. Computed in the
     * ViewModel rather than the card, so the date math stays out of the composable.
     */
    val babyAge: BabyAge? = null,
    /** Baby mode only: how long until this child's next feed. Null until a first feed exists. */
    val feedCountdown: FeedCountdown? = null,
    /** When the last feed was, for the card's "last feed at" line. Null until a first feed exists. */
    val lastFedAtEpochMillis: Long? = null,
    /** How long ago that was, recomputed with the countdown's tick. */
    val sinceLastFeedMillis: Long = 0,
    /** Today's feeds for this child, in the phone's own day — the running tally under the timer. */
    val todayFeedCount: Int = 0,
    /** Null when none of today's feeds had an amount entered. */
    val todayFeedMl: Int? = null,
    /**
     * How long until the next pump. Not baby-scoped and not baby-mode-only: pumping belongs to
     * the mother, so this shows on the moon page too once there is anything to count from.
     */
    val pumpCountdown: FeedCountdown? = null,
    /** A pump session in progress, if there is one — the row with no end time. */
    val pumpRunning: PumpSession? = null,
    /** Frozen while that session is paused, the same as on the pumping page. */
    val pumpElapsedMillis: Long = 0,
    val openTaskCount: Int = 0,
    val budgetEstimated: Double = 0.0,
    val budgetSpent: Double = 0.0,
    /** Of [budgetSpent], what the couple paid vs. what came as gifts (assignee "other"). */
    val budgetSpentByUs: Double = 0.0,
    val budgetSpentByOthers: Double = 0.0,

    // Derived from dueDate — a getter, never stored, so it can never go stale.
    val progress: PregnancyProgress? = null,

    // Editable input: the last-period/name edit sheet. The due date itself is derived from
    // this (via `dueDateFromLastPeriod`) at submit time, not entered directly — see
    // `PregnancyProgress.kt`'s doc comment for why that's the more accurate starting point.
    val editingLastPeriodDate: LocalDate? = null,
    val editingBabyName: String = "",
    val editingPartnerOneName: String = "",
    val editingPartnerTwoName: String = "",

    // Editable input: the birth-details sheet, which is what flips this screen into baby mode.
    val editingBirthDate: LocalDate? = null,
    val editingBirthTime: LocalTime? = null,
    val editingBirthWeightGrams: String = "",
    val editingBirthPlace: String = "",

    // Transient UI-only.
    val sheetVisible: Boolean = false,
    val datePickerVisible: Boolean = false,
    val birthSheetVisible: Boolean = false,
    val birthDatePickerVisible: Boolean = false,
    val birthTimePickerVisible: Boolean = false,
    val refreshing: Boolean = false,
    /** Easter egg: long-pressing the moon shows the "Book of Love" tip, but only when the
     * partner has been active recently — see [HomeViewModel.onMoonLongPress]. */
    val bookOfLoveVisible: Boolean = false,
    /** Easter eggs borrowed from the two logs: long-press the feed or pump card. */
    val nightWatchTally: FeedingTally? = null,
    val nightWatchMine: Int? = null,
    val nightWatchTheirs: Int? = null,
    val stash: MilkStash? = null,
) {
    val hasDueDate: Boolean get() = dueDate != null

    /**
     * Which home page shows. Derived from the active child having been born, never a stored
     * flag: there is nothing to remember to toggle, and switching to an older sibling shows
     * their page without any further state changing.
     */
    val isBabyMode: Boolean get() = activeBaby?.isBorn == true


    /**
     * The pump card earns its place or is not drawn at all: before the first session there is
     * nothing to count from, and an empty card on the moon page would just be furniture.
     */
    val showPumpCard: Boolean get() = pumpRunning != null || pumpCountdown != null

    val canSubmitForm: Boolean get() = editingLastPeriodDate != null
    val canSubmitBirthDetails: Boolean get() = editingBirthDate != null
}

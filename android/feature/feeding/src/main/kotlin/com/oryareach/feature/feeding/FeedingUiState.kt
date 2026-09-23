package com.oryareach.feature.feeding

import androidx.compose.runtime.Immutable
import com.oryareach.core.domain.baby.ageInDays
import com.oryareach.core.domain.feeding.FeedCountdown
import com.oryareach.core.domain.feeding.FeedGuidance
import com.oryareach.core.domain.feeding.FeedingDay
import com.oryareach.core.domain.feeding.FeedingTally
import com.oryareach.core.domain.feeding.feedGuidance
import com.oryareach.core.model.AppSettings
import com.oryareach.core.model.Baby
import com.oryareach.core.model.FeedType
import com.oryareach.core.model.FeedingEntry
import com.oryareach.core.model.VitaminDose
import com.oryareach.core.ui.component.DropBurst
import kotlinx.datetime.LocalDate

/** The history has two shapes; the toggle above it picks which one is drawn. */
enum class HistoryView { LIST, TABLE }

@Immutable
data class FeedingUiState(
    // Persisted snapshot: the live data from Room, already decrypted.
    val baby: Baby? = null,
    val days: List<FeedingDay> = emptyList(),

    // Derived from the last feed and the workspace's interval, recomputed on every tick.
    val countdown: FeedCountdown? = null,
    /** Today, in the viewer's zone: the day headers name today and yesterday rather than dating them. */
    val today: LocalDate? = null,
    /** Shared setting, kept here so logging a feed can schedule the reminder off it. */
    val intervalMinutes: Int = AppSettings.DEFAULT_FEED_INTERVAL_MINUTES,

    // The log-a-feed sheet, which doubles as the edit sheet — [editingFeedId] is what tells
    // them apart.
    val sheetVisible: Boolean = false,
    /** Null while logging a new feed, the row's id while editing an existing one. */
    val editingFeedId: String? = null,
    /**
     * The id of a feed just deleted, while the undo is still on offer. The row is soft-deleted
     * either way — this is only what keeps the snackbar on screen.
     */
    val undoDeleteId: String? = null,
    /** The feed whose trash icon was tapped, while the are-you-sure dialog is up. */
    val deleteConfirmFeed: FeedingEntry? = null,
    val formFeedType: FeedType = FeedType.BREAST_MILK,
    /**
     * The two amounts a milk feed can have. Both fillable at once — a breastfeed topped up with
     * a bottle is one feed, not two, and it is entered as one row with two numbers.
     */
    val formBreastMl: String = "",
    val formFormulaMl: String = "",
    val formHadUrine: Boolean = false,
    val formHadStool: Boolean = false,
    val formNote: String = "",
    /** When the feed happened. Defaults to now; retroactive entries move it back. */
    val formFedAtEpochMillis: Long = 0,
    val datePickerVisible: Boolean = false,
    val timePickerVisible: Boolean = false,

    // The daily vitamin. Its own small card above the countdown: it is the one thing in the
    // day that is not a feed but is asked about at the same moment.
    /** Minutes past local midnight for the reminder; null means no time has been set. */
    val vitaminMinuteOfDay: Int? = null,
    /** Today's dose, if it has been given — by either partner, on either phone. */
    val vitaminDoseToday: VitaminDose? = null,
    /** The last fortnight of doses, newest first, for the history the card long-presses open. */
    val vitaminHistory: List<VitaminDose> = emptyList(),
    val vitaminTimePickerVisible: Boolean = false,
    val vitaminHistoryVisible: Boolean = false,

    // Transient UI-only.
    val historyView: HistoryView = HistoryView.LIST,
    /**
     * Easter egg: long-pressing the countdown shows what the night shift added up to. Only
     * earned once a night feed has actually been logged — see [FeedingViewModel.onCountdownLongPress].
     */
    val nightWatchTally: FeedingTally? = null,
    /**
     * How the feeds split between the two of you, over the whole log. Null when only one person
     * has ever logged one — "you 151 · them 0" is not the point of the panel.
     */
    /**
     * The one celebration in this screen that is not hidden: the feed that lands on a round
     * number gets a fall of drops and a line in the snackbar. [milestoneBurst] is the animation,
     * [milestoneReached] the number to say.
     */
    val milestoneBurst: DropBurst? = null,
    val milestoneReached: Int? = null,
    val nightWatchMine: Int? = null,
    val nightWatchTheirs: Int? = null,
    val busy: Boolean = false,
    val refreshing: Boolean = false,
) {
    /**
     * Nothing to feed until a child exists. Distinct from "no feeds logged yet", which still
     * shows the screen with an empty history and a working "Feed" button.
     */
    val hasBaby: Boolean get() = baby != null

    /**
     * Editing an existing feed leaves its date and time read-only. The moment a feed happened is
     * what the whole log is arranged by — the countdown, the day grouping, the reminder that was
     * already scheduled off it — so it is set once, when the feed is entered, and everything else
     * about the row stays correctable.
     */
    val isEditing: Boolean get() = editingFeedId != null

    /**
     * What the feed being entered comes to. Shown under the two fields only when both are
     * filled — this is the number that will land in the history row, and seeing it before
     * saving beats discovering it afterwards.
     */
    val formTotalMl: Int?
        get() = listOfNotNull(formBreastMl.toIntOrNull(), formFormulaMl.toIntOrNull())
            .takeIf { it.isNotEmpty() }
            ?.sum()

    val formHasBothAmounts: Boolean
        get() = formBreastMl.toIntOrNull() != null && formFormulaMl.toIntOrNull() != null

    /** Milk has amounts to enter; a solid feed does not, so the two fields are hidden for it. */
    val formTakesAmounts: Boolean get() = formFeedType != FeedType.SOLID

    /**
     * Roughly how much the next feed should be, for how old the child is *today*. Null while
     * pregnant or when no birth date was entered — there is nothing to recommend then, and the
     * card shows the countdown alone.
     *
     * The day lines compute their own, from the date of the day they are labelling rather than
     * from today, so scrolling back shows the band that applied then.
     */
    val guidance: FeedGuidance?
        get() {
            val birth = baby?.birthDate ?: return null
            val day = today ?: return null
            return feedGuidance(ageInDays(birth, day))
        }
}

/** Null for a solid feed, which has no amounts, and for a field left empty. */
internal fun FeedingUiState.enteredBreastMl(): Int? =
    formBreastMl.toIntOrNull().takeIf { formTakesAmounts }

internal fun FeedingUiState.enteredFormulaMl(): Int? =
    formFormulaMl.toIntOrNull().takeIf { formTakesAmounts }

/**
 * What to record as the feed's type once the amounts are known.
 *
 * The selector still decides for a solid feed, and it still stands when neither amount was
 * measured. Otherwise the amounts are the better evidence: someone who types a formula amount
 * without touching the selector meant a formula feed, and a feed with both amounts is recorded
 * as a breastfeed with a top-up — the two columns, not this, are what the totals read.
 */
internal fun FeedingUiState.resolvedFeedType(): FeedType = when {
    !formTakesAmounts -> formFeedType
    enteredBreastMl() != null -> FeedType.BREAST_MILK
    enteredFormulaMl() != null -> FeedType.FORMULA
    else -> formFeedType
}

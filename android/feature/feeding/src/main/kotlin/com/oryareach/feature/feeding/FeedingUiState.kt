package com.oryareach.feature.feeding

import androidx.compose.runtime.Immutable
import com.oryareach.core.domain.feeding.FeedCountdown
import com.oryareach.core.domain.feeding.FeedingDay
import com.oryareach.core.domain.feeding.FeedingTally
import com.oryareach.core.model.AppSettings
import com.oryareach.core.model.Baby
import com.oryareach.core.model.FeedType
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
    val formFeedType: FeedType = FeedType.BREAST_MILK,
    val formAmountMl: String = "",
    val formHadUrine: Boolean = false,
    val formHadStool: Boolean = false,
    val formNote: String = "",
    /** When the feed happened. Defaults to now; retroactive entries move it back. */
    val formFedAtEpochMillis: Long = 0,
    val datePickerVisible: Boolean = false,
    val timePickerVisible: Boolean = false,

    // Transient UI-only.
    val historyView: HistoryView = HistoryView.LIST,
    /**
     * Easter egg: long-pressing the countdown shows what the night shift added up to. Only
     * earned once a night feed has actually been logged — see [FeedingViewModel.onCountdownLongPress].
     */
    val nightWatchTally: FeedingTally? = null,
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
}

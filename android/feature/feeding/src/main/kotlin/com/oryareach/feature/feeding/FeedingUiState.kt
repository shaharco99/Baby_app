package com.oryareach.feature.feeding

import androidx.compose.runtime.Immutable
import com.oryareach.core.domain.feeding.FeedCountdown
import com.oryareach.core.domain.feeding.FeedingDay
import com.oryareach.core.domain.feeding.FeedingTally
import com.oryareach.core.model.AppSettings
import com.oryareach.core.model.Baby
import com.oryareach.core.model.FeedType

/** The history has two shapes; the toggle above it picks which one is drawn. */
enum class HistoryView { LIST, TABLE }

@Immutable
data class FeedingUiState(
    // Persisted snapshot: the live data from Room, already decrypted.
    val baby: Baby? = null,
    val days: List<FeedingDay> = emptyList(),

    // Derived from the last feed and the workspace's interval, recomputed on every tick.
    val countdown: FeedCountdown? = null,
    /** Shared setting, kept here so logging a feed can schedule the reminder off it. */
    val intervalMinutes: Int = AppSettings.DEFAULT_FEED_INTERVAL_MINUTES,

    // The log-a-feed sheet.
    val sheetVisible: Boolean = false,
    val formFeedType: FeedType = FeedType.BREAST_MILK,
    val formAmountMl: String = "",
    val formHadUrine: Boolean = false,
    val formHadStool: Boolean = false,
    val formNote: String = "",

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
}

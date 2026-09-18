package com.oryareach.feature.feeding

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oryareach.core.domain.feeding.FeedCountdown
import com.oryareach.core.domain.feeding.FeedingDay
import com.oryareach.core.domain.feeding.formatCountdown
import com.oryareach.core.model.Baby
import com.oryareach.core.model.FeedType
import com.oryareach.core.model.FeedingEntry
import com.oryareach.core.ui.theme.OrYareachTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedingScreen(
    uiState: FeedingUiState,
    actions: FeedingActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize().safeDrawingPadding()) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.refreshing,
            onRefresh = actions::onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.feeding_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )

                if (!uiState.hasBaby) {
                    NoBabyCard()
                    return@Column
                }

                CountdownCard(countdown = uiState.countdown)

                Button(onClick = actions::onLogFeedClick, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.feeding_log_feed))
                }

                HistoryViewToggle(selected = uiState.historyView, actions = actions)

                when (uiState.historyView) {
                    HistoryView.LIST -> FeedingList(days = uiState.days, actions = actions)
                    HistoryView.TABLE -> FeedingTable(days = uiState.days)
                }
            }
        }
    }

    if (uiState.sheetVisible) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = actions::onDismissSheet, sheetState = sheetState) {
            LogFeedForm(uiState = uiState, actions = actions)
        }
    }
}

@Composable
private fun NoBabyCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.feeding_no_baby_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.feeding_no_baby_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The one number the screen exists for. Turns red once it passes zero and counts *up* from
 * there, because at that point "how late is this feed" is the question being asked.
 */
@Composable
private fun CountdownCard(countdown: FeedCountdown?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (countdown == null) {
                Text(
                    text = stringResource(R.string.feeding_no_feeds_yet),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                return@Column
            }

            Text(
                text = stringResource(
                    if (countdown.isOverdue) R.string.feeding_overdue_label else R.string.feeding_next_feed_label,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatCountdown(countdown.remainingMillis),
                style = MaterialTheme.typography.displaySmall,
                color = if (countdown.isOverdue) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = stringResource(R.string.feeding_due_at, formatClock(countdown.dueAtEpochMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryViewToggle(selected: HistoryView, actions: FeedingActions) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        HistoryView.entries.forEachIndexed { index, view ->
            SegmentedButton(
                selected = view == selected,
                onClick = { actions.onHistoryViewChange(view) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = HistoryView.entries.size),
            ) {
                Text(stringResource(view.labelRes()))
            }
        }
    }
}

@Composable
private fun FeedingList(days: List<FeedingDay>, actions: FeedingActions) {
    if (days.isEmpty()) {
        Text(
            text = stringResource(R.string.feeding_empty_history),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { day ->
            item(key = "header-${day.date}") {
                Text(
                    text = day.totalMl
                        ?.let { stringResource(R.string.feeding_day_header_with_total, day.date.toString(), it) }
                        ?: day.date.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            // Newest first within the day: the list reads as a feed log, while the table's
            // columns read left-to-right through the day.
            items(day.feeds.reversed(), key = { it.id }) { feed ->
                FeedRow(feed = feed, onDelete = { actions.onDeleteFeed(feed.id) })
            }
        }
    }
}

@Composable
private fun FeedRow(feed: FeedingEntry, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = formatClock(feed.fedAtEpochMillis),
                style = MaterialTheme.typography.titleMedium,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(feed.feedType.labelRes()), style = MaterialTheme.typography.bodyMedium)
                val marks = feedMarks(feed)
                if (marks.isNotEmpty()) {
                    Text(
                        text = marks,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                feed.note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            feed.amountMl?.let {
                Text(stringResource(R.string.feeding_amount_ml, it), style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.feeding_delete)) }
        }
    }
}

@Composable
private fun feedMarks(feed: FeedingEntry): String = listOfNotNull(
    stringResource(R.string.feeding_urine_short).takeIf { feed.hadUrine },
    stringResource(R.string.feeding_stool_short).takeIf { feed.hadStool },
).joinToString(" · ")

/**
 * The paper day-sheet, on a screen: a thick-bordered column group per day, a thin column per
 * feed inside it, one row per thing tracked. Hand-laid-out rather than a table widget, which
 * Compose doesn't have — a [Row] of day groups, each a fixed set of rows.
 */
@Composable
private fun FeedingTable(days: List<FeedingDay>) {
    if (days.isEmpty()) {
        Text(
            text = stringResource(R.string.feeding_empty_history),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        RowLabels()
        days.forEach { day ->
            VerticalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
            DayColumnGroup(day = day)
        }
    }
}

@Composable
private fun RowLabels() {
    Column(modifier = Modifier.width(96.dp)) {
        TableCell(text = "", header = true)
        TableRowLabels.forEach { label ->
            HorizontalDivider()
            TableCell(text = stringResource(label), header = true)
        }
    }
}

@Composable
private fun DayColumnGroup(day: FeedingDay) {
    Column {
        TableCell(
            text = day.date.toString(),
            header = true,
            modifier = Modifier.width((FEED_COLUMN_WIDTH_DP * day.feeds.size).dp),
        )
        Row {
            day.feeds.forEachIndexed { index, feed ->
                if (index > 0) VerticalDivider()
                FeedColumn(feed = feed)
            }
        }
    }
}

@Composable
private fun FeedColumn(feed: FeedingEntry) {
    Column(modifier = Modifier.width(FEED_COLUMN_WIDTH_DP.dp)) {
        HorizontalDivider()
        TableCell(text = formatClock(feed.fedAtEpochMillis))
        HorizontalDivider()
        TableCell(text = stringResource(feed.feedType.labelRes()))
        HorizontalDivider()
        TableCell(text = feed.amountMl?.toString().orEmpty())
        HorizontalDivider()
        TableCell(text = if (feed.hadUrine) MARK else "")
        HorizontalDivider()
        TableCell(text = if (feed.hadStool) MARK else "")
    }
}

@Composable
private fun TableCell(text: String, header: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(36.dp)
            .background(
                if (header) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            )
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogFeedForm(uiState: FeedingUiState, actions: FeedingActions) {
    Column(
        modifier = Modifier.fillMaxWidth().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.feeding_log_feed), style = MaterialTheme.typography.titleMedium)

        // Segmented buttons rather than a dropdown: three options, tapped constantly, and the
        // one being picked is worth seeing without opening anything.
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            FeedType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = type == uiState.formFeedType,
                    onClick = { actions.onFeedTypeChange(type) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = FeedType.entries.size),
                ) {
                    Text(stringResource(type.labelRes()))
                }
            }
        }

        OutlinedTextField(
            value = uiState.formAmountMl,
            onValueChange = actions::onAmountChange,
            label = { Text(stringResource(R.string.feeding_amount_field)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = uiState.formHadUrine, onCheckedChange = { actions.onToggleUrine() })
            Text(stringResource(R.string.feeding_urine))
            Spacer(Modifier.width(16.dp))
            Checkbox(checked = uiState.formHadStool, onCheckedChange = { actions.onToggleStool() })
            Text(stringResource(R.string.feeding_stool))
        }

        OutlinedTextField(
            value = uiState.formNote,
            onValueChange = actions::onNoteChange,
            label = { Text(stringResource(R.string.feeding_note_field)) },
            modifier = Modifier.fillMaxWidth(),
        )

        FilledTonalButton(
            onClick = actions::onLogFeed,
            enabled = !uiState.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.feeding_save))
        }

        Spacer(Modifier.height(8.dp))
    }
}

private fun formatClock(epochMillis: Long): String {
    val time = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "%02d:%02d".format(time.hour, time.minute)
}

private const val FEED_COLUMN_WIDTH_DP = 64
private const val MARK = "✓"

private val TableRowLabels = listOf(
    R.string.feeding_row_time,
    R.string.feeding_row_type,
    R.string.feeding_row_amount,
    R.string.feeding_row_urine,
    R.string.feeding_row_stool,
)

@Preview(showBackground = true)
@Composable
private fun FeedingPreview() {
    OrYareachTheme {
        FeedingScreen(
            uiState = FeedingUiState(
                baby = Baby(id = "1", name = "Yarden", birthDate = LocalDate(2026, 12, 20)),
                countdown = FeedCountdown(dueAtEpochMillis = 0, remainingMillis = 95 * 60_000L),
                days = listOf(
                    FeedingDay(
                        date = LocalDate(2026, 12, 21),
                        feeds = listOf(
                            FeedingEntry(id = "a", babyId = "1", fedAtEpochMillis = 1_766_300_000_000, amountMl = 60),
                            FeedingEntry(
                                id = "b",
                                babyId = "1",
                                fedAtEpochMillis = 1_766_310_000_000,
                                feedType = FeedType.FORMULA,
                                amountMl = 90,
                                hadUrine = true,
                            ),
                        ),
                    ),
                ),
            ),
            actions = NoopFeedingActions,
        )
    }
}

private object NoopFeedingActions : FeedingActions {
    override fun onLogFeedClick() = Unit
    override fun onDismissSheet() = Unit
    override fun onFeedTypeChange(value: FeedType) = Unit
    override fun onAmountChange(value: String) = Unit
    override fun onToggleUrine() = Unit
    override fun onToggleStool() = Unit
    override fun onNoteChange(value: String) = Unit
    override fun onLogFeed() = Unit
    override fun onDeleteFeed(id: String) = Unit
    override fun onHistoryViewChange(value: HistoryView) = Unit
    override fun onRefresh() = Unit
}

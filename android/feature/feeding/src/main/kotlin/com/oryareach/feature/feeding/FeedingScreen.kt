package com.oryareach.feature.feeding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.Icons
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oryareach.core.domain.feeding.FeedCountdown
import com.oryareach.core.domain.feeding.FeedingDay
import com.oryareach.core.domain.feeding.FeedingTally
import com.oryareach.core.domain.feeding.formatCountdown
import com.oryareach.core.model.Baby
import com.oryareach.core.model.FeedType
import com.oryareach.core.model.FeedingEntry
import com.oryareach.core.ui.text.dayLabel
import com.oryareach.core.ui.theme.OrYareachTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedingScreen(
    uiState: FeedingUiState,
    actions: FeedingActions,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.feeding_undo_action)
    val deletedLabel = stringResource(R.string.feeding_deleted)

    // The snackbar owns the undo window: when it goes, so does the offer.
    LaunchedEffect(uiState.undoDeleteId) {
        val id = uiState.undoDeleteId ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedLabel,
            actionLabel = undoLabel,
            withDismissAction = false,
        )
        if (result == SnackbarResult.ActionPerformed) actions.onUndoDelete() else actions.onUndoDismissed()
    }

    Scaffold(
        modifier = modifier.fillMaxSize().safeDrawingPadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
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

                CountdownCard(countdown = uiState.countdown, actions = actions)

                Button(onClick = actions::onLogFeedClick, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.feeding_log_feed))
                }

                HistoryViewToggle(selected = uiState.historyView, actions = actions)

                when (uiState.historyView) {
                    HistoryView.LIST -> FeedingList(days = uiState.days, today = uiState.today, actions = actions)
                    HistoryView.TABLE -> FeedingTable(days = uiState.days, today = uiState.today, actions = actions)
                }
            }
        }
    }

    uiState.nightWatchTally?.let { tally ->
        NightWatchDialog(tally = tally, onDismiss = actions::onDismissNightWatch)
    }

    if (uiState.sheetVisible) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = actions::onDismissSheet, sheetState = sheetState) {
            LogFeedForm(uiState = uiState, actions = actions)
        }
    }

    if (uiState.datePickerVisible) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.formFedAtEpochMillis.toUtcDateMillis(),
        )
        DatePickerDialog(
            onDismissRequest = actions::onDismissDatePicker,
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { actions.onFedDateChange(it.toPickedDate()) }
                }) { Text(stringResource(R.string.feeding_pick_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = actions::onDismissDatePicker) {
                    Text(stringResource(R.string.feeding_pick_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (uiState.timePickerVisible) {
        val fedAt = uiState.formFedAtEpochMillis.toLocalDateTime()
        val timeState = rememberTimePickerState(
            initialHour = fedAt.hour,
            initialMinute = fedAt.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = actions::onDismissTimePicker,
            confirmButton = {
                TextButton(onClick = {
                    actions.onFedTimeChange(LocalTime(timeState.hour, timeState.minute))
                }) { Text(stringResource(R.string.feeding_pick_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = actions::onDismissTimePicker) {
                    Text(stringResource(R.string.feeding_pick_cancel))
                }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}

/**
 * The night-watch easter egg: what the small hours actually came to. Warm rather than clinical
 * — the numbers are real, the framing is a medal for whoever was awake.
 */
@Composable
private fun NightWatchDialog(tally: FeedingTally, onDismiss: () -> Unit) {
    val lines = androidx.compose.ui.res.stringArrayResource(R.array.feeding_night_watch_praise)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feeding_night_watch_close)) } },
        title = { Text(stringResource(R.string.feeding_night_watch_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.feeding_night_watch_count, tally.nightFeeds),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.feeding_night_watch_total, tally.totalFeeds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                tally.longestStretchMillis?.let { stretch ->
                    Text(
                        text = stringResource(R.string.feeding_night_watch_stretch, formatCountdown(stretch)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                tally.totalMl?.let { ml ->
                    Text(
                        text = stringResource(R.string.feeding_night_watch_ml, ml),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Keyed to the tally, so the line changes as the log grows rather than on
                // every recomposition — a message that reshuffles mid-read is just noise.
                Text(
                    text = lines[tally.nightFeeds % lines.size],
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
    )
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CountdownCard(countdown: FeedCountdown?, actions: FeedingActions) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = actions::onCountdownLongPress,
            ),
    ) {
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
private fun FeedingList(days: List<FeedingDay>, today: LocalDate?, actions: FeedingActions) {
    if (days.isEmpty()) {
        EmptyHistory()
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { day ->
            item(key = "header-${day.date}") {
                Text(
                    text = dayHeader(day, today),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            // Newest first, and the table reads the same way: the feed you just logged is the
            // one you are looking for, so it belongs at the top of its day in both views.
            items(day.feeds.reversed(), key = { it.id }) { feed ->
                FeedRow(
                    feed = feed,
                    onEdit = { actions.onEditFeedClick(feed) },
                    onDelete = { actions.onDeleteFeed(feed.id) },
                )
            }
        }
    }
}

/**
 * One feed. The row itself opens it, so there is no Edit button competing for the same space, and
 * Delete is the one icon rather than a word sitting a thumb's width from it.
 */
@Composable
private fun FeedRow(feed: FeedingEntry, onEdit: () -> Unit, onDelete: () -> Unit) {
    val description = feedDescription(feed)
    val deleteLabel = stringResource(R.string.feeding_delete)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // Announced as one activatable thing: read cell by cell, a row of times and numbers
            // tells a screen reader nothing and offers it nothing to press.
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            }
            .clickable(onClick = onEdit),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
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
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Outlined.Delete, contentDescription = deleteLabel)
            }
        }
    }
}

/**
 * What a screen reader is told about a feed, in the order someone would say it: when, what, how
 * much if it was measured, and what came of it.
 */
@Composable
private fun feedDescription(feed: FeedingEntry): String {
    val amount = feed.amountMl?.let { stringResource(R.string.feeding_amount_ml, it) }
    val marks = feedMarks(feed).takeIf { it.isNotEmpty() }
    return listOfNotNull(
        formatClock(feed.fedAtEpochMillis),
        stringResource(feed.feedType.labelRes()),
        amount,
        marks,
    ).joinToString(", ")
}

/** Says what to do rather than only that there is nothing — an empty log is the first thing seen. */
@Composable
private fun EmptyHistory() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.feeding_empty_history),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.feeding_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
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
private fun FeedingTable(days: List<FeedingDay>, today: LocalDate?, actions: FeedingActions) {
    if (days.isEmpty()) {
        EmptyHistory()
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        TableHeaderRow()
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)

        LazyColumn {
            days.forEach { day ->
                item(key = "day-${day.date}") { DayTitleRow(day = day, today = today) }
                items(day.feeds.reversed(), key = { it.id }) { feed ->
                    HorizontalDivider()
                    FeedCellsRow(feed = feed, onEdit = { actions.onEditFeedClick(feed) })
                }
            }
        }
    }
}

/** The column titles, once at the top — every feed below reads against these. */
@Composable
private fun TableHeaderRow() {
    // IntrinsicSize.Min is load-bearing: `VerticalDivider` fills max height, which in a Row
    // resolves against the *incoming* constraint rather than the row's content. Without it the
    // header stretched to the whole remaining column and left the rows below it no height at all.
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        TableRowLabels.forEachIndexed { index, label ->
            if (index > 0) VerticalDivider()
            TableCell(
                text = stringResource(label),
                header = true,
                modifier = Modifier.weight(ColumnWeights[index]),
            )
        }
    }
}

/** Separates one calendar day's feeds from the next, the thick divider on the paper sheet. */
@Composable
private fun DayTitleRow(day: FeedingDay, today: LocalDate?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = dayHeader(day, today),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeedCellsRow(feed: FeedingEntry, onEdit: () -> Unit) {
    val cells = listOf(
        formatClock(feed.fedAtEpochMillis),
        stringResource(feed.feedType.labelRes()),
        feed.amountMl?.toString().orEmpty(),
        if (feed.hadUrine) MARK else "",
        if (feed.hadStool) MARK else "",
    )

    val description = feedDescription(feed)

    // A cell is too small a target for its own button, so the whole row opens the feed.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            }
            .clickable(onClick = onEdit),
    ) {
        cells.forEachIndexed { index, text ->
            if (index > 0) VerticalDivider()
            TableCell(text = text, modifier = Modifier.weight(ColumnWeights[index]))
        }
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
        Text(
            text = stringResource(
                if (uiState.isEditing) R.string.feeding_edit_feed else R.string.feeding_log_feed,
            ),
            style = MaterialTheme.typography.titleMedium,
        )

        WhenFedRow(uiState = uiState, actions = actions)

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

/**
 * When the feed happened. Two buttons while logging — the default is now, and a retroactive
 * entry walks either half back — and plain text while editing, because the time a feed happened
 * is not something a later correction gets to move.
 */
@Composable
private fun WhenFedRow(uiState: FeedingUiState, actions: FeedingActions) {
    val fedAt = uiState.formFedAtEpochMillis.toLocalDateTime()
    val date = fedAt.date.toString()
    val time = "%02d:%02d".format(fedAt.hour, fedAt.minute)

    if (uiState.isEditing) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.feeding_when_value, date, time),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.feeding_when_locked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = actions::onOpenDatePicker, modifier = Modifier.weight(1f)) {
            Text(date)
        }
        OutlinedButton(onClick = actions::onOpenTimePicker, modifier = Modifier.weight(1f)) {
            Text(time)
        }
    }
}

private fun Long.toLocalDateTime(): LocalDateTime =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())

/**
 * `DatePicker` reads and writes UTC midnights, so the local calendar day goes in and comes back
 * out through [TimeZone.UTC] rather than being shifted by the device's offset on the way.
 */
private fun Long.toUtcDateMillis(): Long =
    toLocalDateTime().date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toPickedDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

private fun formatClock(epochMillis: Long): String {
    val time = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "%02d:%02d".format(time.hour, time.minute)
}

private const val MARK = "✓"

/** Time and type carry the most text; the two marks are a tick or nothing. */
private val ColumnWeights = listOf(1.1f, 1.6f, 1f, 0.7f, 0.7f)

/**
 * Today and yesterday by name, anything older by weekday and a short date — the same labels the
 * pumping log uses, from `:core:ui`, so a day reads identically in both.
 */
@Composable
private fun dayHeader(day: FeedingDay, today: LocalDate?): String {
    val date = today?.let { dayLabel(day.date, it) } ?: day.date.toString()
    return day.totalMl?.let { stringResource(R.string.feeding_day_header_with_total, date, it) } ?: date
}

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
    override fun onEditFeedClick(feed: FeedingEntry) = Unit
    override fun onDismissSheet() = Unit
    override fun onFeedTypeChange(value: FeedType) = Unit
    override fun onAmountChange(value: String) = Unit
    override fun onToggleUrine() = Unit
    override fun onToggleStool() = Unit
    override fun onNoteChange(value: String) = Unit
    override fun onOpenDatePicker() = Unit
    override fun onDismissDatePicker() = Unit
    override fun onFedDateChange(value: LocalDate) = Unit
    override fun onOpenTimePicker() = Unit
    override fun onDismissTimePicker() = Unit
    override fun onFedTimeChange(value: LocalTime) = Unit
    override fun onLogFeed() = Unit
    override fun onDeleteFeed(id: String) = Unit
    override fun onUndoDelete() = Unit
    override fun onUndoDismissed() = Unit
    override fun onHistoryViewChange(value: HistoryView) = Unit
    override fun onCountdownLongPress() = Unit
    override fun onDismissNightWatch() = Unit
    override fun onRefresh() = Unit
}

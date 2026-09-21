package com.oryareach.feature.feeding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.Icons
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.oryareach.core.domain.baby.ageInDays
import com.oryareach.core.domain.feeding.FeedCountdown
import com.oryareach.core.domain.feeding.FeedGuidance
import com.oryareach.core.domain.feeding.FeedingDay
import com.oryareach.core.domain.feeding.FeedingTally
import com.oryareach.core.domain.feeding.feedGuidance
import com.oryareach.core.domain.feeding.formatCountdown
import com.oryareach.core.model.Baby
import com.oryareach.core.model.FeedType
import com.oryareach.core.model.FeedingEntry
import com.oryareach.core.ui.text.dateLabel
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

                CountdownCard(
                    countdown = uiState.countdown,
                    guidance = uiState.guidance,
                    actions = actions,
                )

                Button(onClick = actions::onLogFeedClick, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.feeding_log_feed))
                }

                HistoryViewToggle(selected = uiState.historyView, actions = actions)

                when (uiState.historyView) {
                    HistoryView.LIST -> FeedingList(
                        days = uiState.days,
                        today = uiState.today,
                        birthDate = uiState.baby?.birthDate,
                        actions = actions,
                    )

                    HistoryView.TABLE -> FeedingTable(
                        days = uiState.days,
                        today = uiState.today,
                        birthDate = uiState.baby?.birthDate,
                        actions = actions,
                    )
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
private fun CountdownCard(
    countdown: FeedCountdown?,
    guidance: FeedGuidance?,
    actions: FeedingActions,
) {
    var guidanceNoteVisible by rememberSaveable { mutableStateOf(false) }

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
            // When and how much on one line. They were three stacked lines plus two of
            // disclaimer, which pushed the history off the bottom of the screen — and the
            // disclaimer does not need re-reading every time someone checks the clock.
            // Absent while pregnant or with no birth date: no age, no band, and a guessed one
            // would be worse than nothing at this hour.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = listOfNotNull(
                        stringResource(R.string.feeding_due_at, formatClock(countdown.dueAtEpochMillis)),
                        guidance?.let {
                            stringResource(
                                R.string.feeding_guidance_per_feed,
                                it.perFeedMinMl,
                                it.perFeedMaxMl,
                            )
                        },
                    ).joinToString(SEPARATOR),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                if (guidance != null) {
                    IconButton(
                        onClick = { guidanceNoteVisible = true },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.feeding_guidance_more),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (guidanceNoteVisible) {
        AlertDialog(
            onDismissRequest = { guidanceNoteVisible = false },
            confirmButton = {
                TextButton(onClick = { guidanceNoteVisible = false }) {
                    Text(stringResource(R.string.feeding_guidance_close))
                }
            },
            title = { Text(stringResource(R.string.feeding_guidance_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.feeding_guidance_disclaimer))
                    Text(
                        text = stringResource(R.string.feeding_guidance_source),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
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
private fun FeedingList(
    days: List<FeedingDay>,
    today: LocalDate?,
    birthDate: LocalDate?,
    actions: FeedingActions,
) {
    if (days.isEmpty()) {
        EmptyHistory()
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { day ->
            item(key = "header-${day.date}") {
                DayTotalLine(
                    day = day,
                    today = today,
                    birthDate = birthDate,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                    showBar = day.date == today,
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
            // The icons, not the words: the day line above already says "breast" and "formula"
            // in pictures, and a row that spells it out reads as a different fact rather than
            // the same one. A feed that was both shows both.
            //
            // Its own slot beside the time, rather than the first line of the text column —
            // there it sat alone above the small grey marks and read as a stray mark itself.
            FeedTypeMarks(feed)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
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
            // One number, in the place a feed's amount has always been drawn: a feed that was
            // breast and formula together shows their sum rather than two figures fighting for
            // the same slot. The per-source split lives on the day line above.
            feed.totalMl?.let {
                Text(stringResource(R.string.feeding_amount_ml, it), style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Outlined.Delete, contentDescription = deleteLabel)
            }
        }
    }
}

/**
 * Which sources a feed came from, as icons.
 *
 * Shape rather than colour carries the distinction. The palette's `blush` and `moss` were the
 * obvious pair to tint these with, but both fall under 3:1 against the light theme's card, so
 * the colour would have been decoration with an accessibility cost. A person and a bottle are
 * already unmistakable, in either theme and without colour vision.
 *
 * Falls back to the type's own word for a solid feed, which has no amounts to infer from.
 */
@Composable
private fun FeedTypeMarks(feed: FeedingEntry) {
    val icons = listOfNotNull(
        R.drawable.ic_feed_breast.takeIf { feed.breastMl != null },
        R.drawable.ic_feed_bottle.takeIf { feed.formulaMl != null },
    ).ifEmpty {
        // Nothing measured: fall back to what the feed says it was.
        when (feed.feedType) {
            FeedType.BREAST_MILK -> listOf(R.drawable.ic_feed_breast)
            FeedType.FORMULA -> listOf(R.drawable.ic_feed_bottle)
            FeedType.SOLID -> emptyList()
        }
    }

    val label = stringResource(feed.feedType.labelRes())

    if (icons.isEmpty()) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icons.forEach { icon ->
            Icon(
                painter = painterResource(icon),
                // The row's own description already names the feed; repeating it per icon
                // would have a screen reader say "breast, breast".
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * What a screen reader is told about a feed, in the order someone would say it: when, what, how
 * much if it was measured, and what came of it.
 */
@Composable
private fun feedDescription(feed: FeedingEntry): String {
    val amount = feed.totalMl?.let { stringResource(R.string.feeding_amount_ml, it) }
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
private fun FeedingTable(
    days: List<FeedingDay>,
    today: LocalDate?,
    birthDate: LocalDate?,
    actions: FeedingActions,
) {
    if (days.isEmpty()) {
        EmptyHistory()
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        TableHeaderRow()
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)

        LazyColumn {
            days.forEach { day ->
                item(key = "day-${day.date}") {
                    DayTitleRow(day = day, today = today, birthDate = birthDate)
                }
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
private fun DayTitleRow(day: FeedingDay, today: LocalDate?, birthDate: LocalDate?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        DayTotalLine(
            day = day,
            today = today,
            birthDate = birthDate,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeedCellsRow(feed: FeedingEntry, onEdit: () -> Unit) {
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
        TableCell(text = formatClock(feed.fedAtEpochMillis), modifier = Modifier.weight(ColumnWeights[0]))
        VerticalDivider()
        // The one cell that is not text. Same icons as the list and the day line, so the two
        // views say the same thing the same way.
        TableCellBox(modifier = Modifier.weight(ColumnWeights[1])) { FeedTypeMarks(feed) }
        VerticalDivider()
        TableCell(text = feed.totalMl?.toString().orEmpty(), modifier = Modifier.weight(ColumnWeights[2]))
        VerticalDivider()
        TableCell(text = if (feed.hadUrine) MARK else "", modifier = Modifier.weight(ColumnWeights[3]))
        VerticalDivider()
        TableCell(text = if (feed.hadStool) MARK else "", modifier = Modifier.weight(ColumnWeights[4]))
    }
}

/** A cell that holds something other than a line of text, at the same size as [TableCell]. */
@Composable
private fun TableCellBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .height(36.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
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

        // Milk or solid, and nothing finer.
        //
        // This used to offer Breast / Formula / Solid, which contradicted the two amount
        // fields below it: typing into Formula while the control still showed Breast selected.
        // The type is already derived from which amounts were filled (see
        // `FeedingUiState.resolvedFeedType`), so the three-way choice was both decorative and
        // wrong. Milk vs solid is the only part the amounts cannot answer.
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            MilkOrSolid.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option.matches(uiState.formFeedType),
                    onClick = { actions.onFeedTypeChange(option.feedType) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = MilkOrSolid.entries.size),
                ) {
                    Text(stringResource(option.labelRes))
                }
            }
        }

        // Two fields, not one with a picker: a breastfeed topped up with a bottle is one feed
        // with two numbers, and making that a mode to switch between is a step too many at 4am.
        // Either, both, or neither may be filled; filling both is what "we did both" means.
        if (uiState.formTakesAmounts) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AmountField(
                    value = uiState.formBreastMl,
                    onValueChange = actions::onBreastMlChange,
                    label = R.string.feeding_amount_breast_field,
                    icon = R.drawable.ic_feed_breast,
                    modifier = Modifier.weight(1f),
                )
                AmountField(
                    value = uiState.formFormulaMl,
                    onValueChange = actions::onFormulaMlChange,
                    label = R.string.feeding_amount_formula_field,
                    icon = R.drawable.ic_feed_bottle,
                    modifier = Modifier.weight(1f),
                )
            }

            // Only once both are filled: with one amount the total is the number already on
            // screen, and echoing it back reads as a second, different figure.
            if (uiState.formHasBothAmounts) {
                uiState.formTotalMl?.let { total ->
                    Text(
                        text = stringResource(R.string.feeding_amount_total, total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Chips, not checkboxes. A checkbox is a 20dp target; these get tapped one-handed, in
        // the dark, holding a baby. The whole chip is the target, and it reads as pressed
        // rather than needing a tick to be spotted.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiState.formHadUrine,
                onClick = actions::onToggleUrine,
                label = { Text(stringResource(R.string.feeding_urine)) },
            )
            FilterChip(
                selected = uiState.formHadStool,
                onClick = actions::onToggleStool,
                label = { Text(stringResource(R.string.feeding_stool)) },
            )
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
            // Names the outcome rather than the mechanism, and matches the button that opened
            // the sheet: "Log a feed" on the screen, "Log feed" here.
            Text(
                stringResource(
                    if (uiState.isEditing) R.string.feeding_save_changes else R.string.feeding_log_feed_action,
                ),
            )
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
    val date = dateLabel(fedAt.date)
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

/**
 * The only type choice the sheet still asks for. Breast and formula are read off the amount
 * fields instead — see the comment where this is used.
 */
private enum class MilkOrSolid(val feedType: FeedType, @StringRes val labelRes: Int) {
    MILK(FeedType.BREAST_MILK, R.string.feeding_type_milk),
    SOLID(FeedType.SOLID, R.string.feeding_type_solid),
    ;

    /** Either milk type counts as milk; the amounts decide which. */
    fun matches(current: FeedType): Boolean =
        if (this == SOLID) current == FeedType.SOLID else current != FeedType.SOLID
}

/**
 * One of the two amount fields. The icon is the label that gets read at a glance — the words
 * are there for a screen reader and for the first time someone sees the sheet.
 */
@Composable
private fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        // The unit is a suffix rather than part of the label: at half the sheet's width,
        // "Breast (ml)" wraps onto two lines while the field is empty, which is most of the
        // time someone is looking at it.
        label = { Text(stringResource(label)) },
        suffix = { Text(stringResource(R.string.feeding_unit_ml)) },
        leadingIcon = {
            Icon(painter = painterResource(icon), contentDescription = null)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
    )
}

/**
 * An icon and a number, the shape the day line breaks its total down into. The icon carries the
 * meaning; the surrounding line's `contentDescription` is what says it in words.
 */
@Composable
private fun SourceAmount(@DrawableRes icon: Int, amountMl: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = amountMl.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatClock(epochMillis: Long): String {
    val time = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "%02d:%02d".format(time.hour, time.minute)
}

private const val MARK = "✓"

/** Between two facts on one line — when the feed is due, and roughly how much. */
private const val SEPARATOR = " · "

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

/**
 * The day's line: its name and total, then what each source contributed, then roughly what a
 * day at that age comes to.
 *
 * The breakdown only appears when there is one — a day fed from a single source would otherwise
 * repeat its own total beside itself. The guidance is computed from [FeedingDay.date], not from
 * today: scrolling back through the log should show the band that applied on the day being read,
 * which for a newborn is a different band every day.
 *
 * Laid out as a [Row] of separate pieces rather than one formatted string so the icons can sit
 * between the numbers; the whole line is merged into a single announcement, because an icon
 * tells a screen reader nothing on its own.
 */
@Composable
private fun DayTotalLine(
    day: FeedingDay,
    today: LocalDate?,
    birthDate: LocalDate?,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    showBar: Boolean = false,
) {
    val header = dayHeader(day, today)
    val breakdown = day.takeIf { it.hasSourceBreakdown }
    val guidance = birthDate?.let { feedGuidance(ageInDays(it, day.date)) }

    val description = when {
        breakdown == null -> header
        else -> stringResource(
            R.string.feeding_day_header_breakdown,
            today?.let { dayLabel(day.date, it) } ?: day.date.toString(),
            day.totalMl ?: 0,
            breakdown.breastMl ?: 0,
            breakdown.formulaMl ?: 0,
        )
    }

    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // FlowRow, not Row: "Yesterday", both breakdown chips and the guidance band together
        // overflow a phone's width, and a Row clips the last item mid-word rather than moving
        // it down. Wrapping is the honest answer — every part of the line stays readable.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = header, style = style, color = color)

            if (breakdown != null) {
                breakdown.breastMl?.let { SourceAmount(icon = R.drawable.ic_feed_breast, amountMl = it) }
                breakdown.formulaMl?.let { SourceAmount(icon = R.drawable.ic_feed_bottle, amountMl = it) }
            }

            if (guidance != null && day.totalMl != null) {
                Text(
                    text = stringResource(
                        R.string.feeding_guidance_day_total,
                        guidance.dailyMinMl,
                        guidance.dailyMaxMl,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Only today, and only once something has been measured. "240 of about 280–540" is a
        // comparison the numbers make you do in your head; the bar just shows it. Finished days
        // are a record rather than something to act on, so they stay as figures — putting a bar
        // on every header would turn a log into a wall of charts.
        if (showBar && guidance != null && day.totalMl != null) {
            DayRangeBar(totalMl = day.totalMl!!, guidance = guidance)
        }
    }
}

/**
 * Where the day sits against the recommended band.
 *
 * The track is the whole band, nothing to its top; the tick is the bottom of it. So the three
 * states you can be in are the three things the bar can look like: short of the tick, past the
 * tick, or full and in the error colour because the day has gone over.
 *
 * The first attempt shaded the whole min-to-max band, which read as a second, lighter fill
 * sitting next to the real one — two bars in one. A single tick says the same thing without
 * competing with the number beside it.
 *
 * Redundant to a screen reader on purpose: the line above already states both figures.
 *
 * Mirrored by hand for Hebrew. A `Canvas` is the one thing on this screen Compose does not flip
 * for you — every row beside it mirrored correctly while the bar kept filling from the left,
 * against the direction the line above it reads.
 */
@Composable
private fun DayRangeBar(totalMl: Int, guidance: FeedGuidance) {
    val target = guidance.dailyMaxMl.coerceAtLeast(1)
    val over = totalMl > target
    val filled = (totalMl.toFloat() / target).coerceIn(0f, 1f)
    val tick = (guidance.dailyMinMl.toFloat() / target).coerceIn(0f, 1f)

    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clearAndSetSemantics { },
    ) {
        val rtl = layoutDirection == LayoutDirection.Rtl
        /** How far along the bar a fraction sits, measured from the reading edge. */
        fun x(fraction: Float) = if (rtl) size.width * (1f - fraction) else size.width * fraction

        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color = track, cornerRadius = radius)

        if (filled > 0f) {
            drawRoundRect(
                color = fill,
                cornerRadius = radius,
                topLeft = Offset(if (rtl) x(filled) else 0f, 0f),
                size = Size(size.width * filled, size.height),
            )
        }

        // Inset so the tick cannot land half outside the rounded end.
        val tickX = x(tick).coerceIn(TICK_WIDTH, size.width - TICK_WIDTH)
        drawRect(
            color = tickColor,
            topLeft = Offset(tickX - TICK_WIDTH / 2, 0f),
            size = Size(TICK_WIDTH, size.height),
        )
    }
}

private val BAR_HEIGHT = 6.dp
private const val TICK_WIDTH = 2f

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
                today = LocalDate(2026, 12, 21),
                countdown = FeedCountdown(dueAtEpochMillis = 0, remainingMillis = 95 * 60_000L),
                days = listOf(
                    FeedingDay(
                        date = LocalDate(2026, 12, 21),
                        feeds = listOf(
                            // A feed that was breast and formula together: one row, one total.
                            FeedingEntry(
                                id = "a",
                                babyId = "1",
                                fedAtEpochMillis = 1_766_300_000_000,
                                breastMl = 30,
                                formulaMl = 30,
                            ),
                            FeedingEntry(
                                id = "b",
                                babyId = "1",
                                fedAtEpochMillis = 1_766_310_000_000,
                                feedType = FeedType.FORMULA,
                                formulaMl = 90,
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
    override fun onBreastMlChange(value: String) = Unit
    override fun onFormulaMlChange(value: String) = Unit
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

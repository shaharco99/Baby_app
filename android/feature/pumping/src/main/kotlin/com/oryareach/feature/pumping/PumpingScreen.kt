package com.oryareach.feature.pumping

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oryareach.core.domain.feeding.FeedCountdown
import com.oryareach.core.domain.feeding.formatCountdown
import com.oryareach.core.domain.pumping.MilkStash
import com.oryareach.core.domain.pumping.PumpingDay
import com.oryareach.core.domain.log.splitLogDays
import com.oryareach.core.model.PumpSession
import com.oryareach.core.model.PumpSide
import com.oryareach.core.ui.component.DrawerHeader
import com.oryareach.core.ui.component.DropFall
import com.oryareach.core.ui.text.dateLabel
import com.oryareach.core.ui.text.dayLabel
import com.oryareach.core.ui.theme.OrYareachTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PumpingScreen(
    uiState: PumpingUiState,
    actions: PumpingActions,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    // Hoisted above the list/table switch on purpose: the two views are the same log read two
    // ways, so opening the older days in one and finding them shut in the other would read as
    // the app having forgotten.
    var olderDaysExpanded by rememberSaveable { mutableStateOf(false) }
    val undoLabel = stringResource(R.string.pumping_undo_action)
    val deletedLabel = stringResource(R.string.pumping_deleted)

    // The snackbar owns the undo window: when it goes, so does the offer.
    //
    // `duration` and `withDismissAction` are both stated rather than left to default, because
    // the default is wrong here in a way that is easy to miss: Material 3's `showSnackbar`
    // picks `Indefinite` as soon as an `actionLabel` is passed, so this bar sat on the screen
    // forever, over the list, until the undo was tapped. `Long` gives roughly ten seconds —
    // long enough to notice a mistaken delete, short enough to get out of the way — and the
    // dismiss action adds an X for closing it on the spot. A sideways swipe also dismisses it.
    LaunchedEffect(uiState.undoDeleteId) {
        val id = uiState.undoDeleteId ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedLabel,
            actionLabel = undoLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Long,
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
                    text = stringResource(R.string.pumping_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )

                TimerCard(uiState = uiState, actions = actions)

                HistoryViewToggle(selected = uiState.historyView, actions = actions)

                when (uiState.historyView) {
                    PumpHistoryView.LIST -> PumpingList(
                        days = uiState.days,
                        today = uiState.today,
                        actions = actions,
                        olderExpanded = olderDaysExpanded,
                        onToggleOlder = { olderDaysExpanded = !olderDaysExpanded },
                    )

                    PumpHistoryView.TABLE -> PumpingTable(
                        days = uiState.days,
                        today = uiState.today,
                        actions = actions,
                        olderExpanded = olderDaysExpanded,
                        onToggleOlder = { olderDaysExpanded = !olderDaysExpanded },
                    )
                }
            }
        }
    }

    uiState.milkDrops?.let { drops ->
        DropFall(burst = drops, onFinished = actions::onDropsShown)
    }

    uiState.stash?.let { stash ->
        MilkStashDialog(stash = stash, onDismiss = actions::onDismissStash)
    }

    uiState.deleteConfirmSession?.let { session ->
        val clock = formatClock(session.startedAtEpochMillis)
        AlertDialog(
            onDismissRequest = actions::onDismissDeleteSession,
            text = {
                Text(
                    session.amountMl
                        ?.let { stringResource(R.string.pumping_delete_confirm_amount, clock, it) }
                        ?: stringResource(R.string.pumping_delete_confirm, clock),
                )
            },
            confirmButton = {
                TextButton(onClick = actions::onConfirmDeleteSession) { Text(stringResource(R.string.pumping_delete)) }
            },
            dismissButton = {
                TextButton(onClick = actions::onDismissDeleteSession) {
                    Text(stringResource(R.string.pumping_pick_cancel))
                }
            },
        )
    }

    if (uiState.sheetVisible) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = actions::onDismissSheet, sheetState = sheetState) {
            PumpSessionForm(uiState = uiState, actions = actions)
        }
    }

    if (uiState.datePickerVisible) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.formStartedAtEpochMillis.toUtcDateMillis(),
        )
        DatePickerDialog(
            onDismissRequest = actions::onDismissDatePicker,
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { actions.onStartedDateChange(it.toPickedDate()) }
                }) { Text(stringResource(R.string.pumping_pick_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = actions::onDismissDatePicker) {
                    Text(stringResource(R.string.pumping_pick_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (uiState.timePickerVisible) {
        val startedAt = uiState.formStartedAtEpochMillis.toLocalDateTime()
        val timeState = rememberTimePickerState(
            initialHour = startedAt.hour,
            initialMinute = startedAt.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = actions::onDismissTimePicker,
            confirmButton = {
                TextButton(onClick = {
                    actions.onStartedTimeChange(LocalTime(timeState.hour, timeState.minute))
                }) { Text(stringResource(R.string.pumping_pick_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = actions::onDismissTimePicker) {
                    Text(stringResource(R.string.pumping_pick_cancel))
                }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}

/**
 * One card doing two jobs, because at any moment only one of them applies: while a session runs it
 * is the elapsed clock and the Stop button, and the rest of the time it is the countdown to the
 * next pump and the Start button.
 */
@Composable
private fun TimerCard(uiState: PumpingUiState, actions: PumpingActions) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // Long-press opens the stash, the same gesture the feeding countdown uses for its
            // night watch. Nothing on a short press, and no ripple either: the card's own buttons
            // are the tap targets, and a ripple under a dead tap reads as a broken button.
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = actions::onTimerLongPress,
            ),
    ) {
        Column(
            // Tighter while running: the card is then the only thing anyone is looking at, and
            // the history below it deserves the rest of the screen.
            modifier = Modifier.fillMaxWidth().padding(if (uiState.isRunning) 16.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (uiState.isRunning) {
                Text(
                    text = stringResource(
                        if (uiState.isPaused) {
                            R.string.pumping_paused_label
                        } else {
                            R.string.pumping_running_label
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatCountdown(uiState.elapsedMillis),
                    style = MaterialTheme.typography.displaySmall,
                    // Dimmed while paused, so a clock that has stopped moving looks stopped
                    // rather than looking broken.
                    color = if (uiState.isPaused) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                // Which side, and since when: without this the running card cannot answer
                // "wait, which one did I pick", and leaving the page to find out is worse.
                uiState.running?.let { running ->
                    Text(
                        text = stringResource(
                            R.string.pumping_running_detail,
                            stringResource(running.side.labelRes()),
                            formatClock(running.startedAtEpochMillis),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (uiState.isPaused) {
                        Button(onClick = actions::onResumeClick, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.pumping_resume))
                        }
                    } else {
                        OutlinedButton(onClick = actions::onPauseClick, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.pumping_pause))
                        }
                    }
                    Button(onClick = actions::onStopClick, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.pumping_stop))
                    }
                }
                return@Column
            }

            CountdownText(countdown = uiState.countdown)

            // Which side is picked before Start, so the running card stays a clock and a Stop
            // button — and it can still be corrected in the sheet on the way out. Its own state,
            // never the sheet's: a dismissed sheet must not rewrite what the card was set to.
            SideRow(selected = uiState.pendingSide, onChange = actions::onPendingSideChange)

            Button(onClick = actions::onStartClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pumping_start))
            }

            // In the card rather than below it: as a full-width button of its own it cost the
            // history a row, for something used far less often than Start.
            TextButton(onClick = actions::onLogPastClick) {
                Text(stringResource(R.string.pumping_log_past))
            }
        }
    }
}


/**
 * The stash: what the pumping has added up to. Warm rather than clinical, on the same principle as
 * the feeding log's night watch — the numbers are real, the framing is for whoever did the work.
 */
@Composable
private fun MilkStashDialog(stash: MilkStash, onDismiss: () -> Unit) {
    val lines = stringArrayResource(R.array.pumping_stash_praise)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.pumping_stash_close)) }
        },
        title = { Text(stringResource(R.string.pumping_stash_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.pumping_stash_total, stash.totalMl),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (stash.feedsCovered > 0) {
                    Text(
                        text = stringResource(R.string.pumping_stash_feeds, stash.feedsCovered),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    // Hours and minutes, not a raw minute count: past a couple of days at the
                    // pump "1100 minutes" stops being a length of time anyone can feel.
                    text = stringResource(
                        R.string.pumping_stash_sessions,
                        stash.sessions,
                        stash.totalMinutes.toHoursAndMinutes(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.pumping_stash_best_day, stash.bestDayMl),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                stash.milestoneMl?.let { milestone ->
                    Text(
                        text = stringResource(R.string.pumping_stash_milestone, milestone.toLitres()),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                // Keyed to the count, so the line changes as the log grows rather than on every
                // recomposition — a message that reshuffles mid-read is just noise.
                Text(
                    text = lines[stash.sessions % lines.size],
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
    )
}

/**
 * Turns red once it passes zero and counts *up* from there, because at that point "how late is
 * this" is the question being asked.
 */
@Composable
private fun CountdownText(countdown: FeedCountdown?) {
    if (countdown == null) {
        Text(
            text = stringResource(R.string.pumping_none_yet),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        return
    }

    Text(
        text = stringResource(
            if (countdown.isOverdue) R.string.pumping_overdue_label else R.string.pumping_next_label,
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
        text = stringResource(R.string.pumping_due_at, formatClock(countdown.dueAtEpochMillis)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SideRow(selected: PumpSide, onChange: (PumpSide) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        PumpSide.entries.forEachIndexed { index, side ->
            SegmentedButton(
                selected = side == selected,
                onClick = { onChange(side) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = PumpSide.entries.size),
            ) {
                Text(stringResource(side.labelRes()))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryViewToggle(selected: PumpHistoryView, actions: PumpingActions) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        PumpHistoryView.entries.forEachIndexed { index, view ->
            SegmentedButton(
                selected = view == selected,
                onClick = { actions.onHistoryViewChange(view) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = PumpHistoryView.entries.size,
                ),
            ) {
                Text(stringResource(view.labelRes()))
            }
        }
    }
}

@Composable
private fun PumpingList(
    days: List<PumpingDay>,
    today: LocalDate?,
    actions: PumpingActions,
    olderExpanded: Boolean,
    onToggleOlder: () -> Unit,
) {
    if (days.isEmpty()) {
        EmptyHistory()
        return
    }

    // Today and yesterday stay open; everything before them goes in the drawer, the same way
    // the feeding log does it.
    val split = splitLogDays(days)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        split.recent.forEach { day -> listDay(day, today, actions) }

        if (split.older.isNotEmpty()) {
            item(key = OLDER_DAYS_DRAWER_KEY) {
                OlderDaysHeader(dayCount = split.older.size, expanded = olderExpanded, onToggle = onToggleOlder)
            }
            if (olderExpanded) {
                split.older.forEach { day -> listDay(day, today, actions) }
            }
        }
    }
}

/** One day in the list view: its header, then its sessions. */
private fun LazyListScope.listDay(day: PumpingDay, today: LocalDate?, actions: PumpingActions) {
    item(key = "header-${day.date}") {
        Text(
            text = dayHeader(day, today),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    // Newest first, and the table reads the same way: the session you just finished is
    // the one you are looking for, so it belongs at the top of its day in both views.
    items(day.sessions.reversed(), key = { it.id }) { session ->
        SessionRow(
            session = session,
            onEdit = { actions.onEditClick(session) },
            onDelete = { actions.onDeleteSessionClick(session) },
        )
    }
}

private const val OLDER_DAYS_DRAWER_KEY = "older-days-drawer"

/**
 * The drawer bar over the part of the log that is history rather than working memory. Counts
 * days rather than sessions, for the same reason the feeding log does.
 */
@Composable
private fun OlderDaysHeader(
    dayCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DrawerHeader(
        title = stringResource(R.string.pumping_older_days_drawer),
        count = dayCount,
        expanded = expanded,
        onToggle = onToggle,
        modifier = modifier,
    )
}

/**
 * One session. The row itself opens it, so there is no Edit button competing for the same space —
 * that button, a delete button and the content together left nothing room enough to read in
 * Hebrew. Delete is the one icon; it asks first, then still offers an undo.
 */
@Composable
private fun SessionRow(session: PumpSession, onEdit: () -> Unit, onDelete: () -> Unit) {
    val description = sessionDescription(session)
    val deleteLabel = stringResource(R.string.pumping_delete)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // Announced as one activatable thing with everything in it: read cell by cell, a row
            // of times and numbers tells a screen reader nothing and offers it nothing to press.
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
                text = formatClock(session.startedAtEpochMillis),
                style = MaterialTheme.typography.titleMedium,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(session.side.labelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = session.durationMinutes
                        ?.let { stringResource(R.string.pumping_minutes, it) }
                        ?: stringResource(R.string.pumping_in_progress),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                session.note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            session.amountMl?.let {
                Text(
                    text = stringResource(R.string.pumping_amount_ml, it),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = deleteLabel,
                )
            }
        }
    }
}

/**
 * What a screen reader is told about a session, in the order someone would say it: when, which
 * side, how long, and how much if it was measured.
 */
@Composable
private fun sessionDescription(session: PumpSession): String {
    val side = stringResource(session.side.labelRes())
    val length = session.durationMinutes
        ?.let { stringResource(R.string.pumping_minutes, it) }
        ?: stringResource(R.string.pumping_in_progress)
    val amount = session.amountMl?.let { stringResource(R.string.pumping_amount_ml, it) }
    return listOfNotNull(formatClock(session.startedAtEpochMillis), side, length, amount)
        .joinToString(", ")
}

/**
 * The same day-sheet shape the feeding log uses: a header row of column titles, a thick divider
 * per calendar day, one thin row per session. Hand-laid-out, because Compose has no table.
 */
@Composable
private fun PumpingTable(
    days: List<PumpingDay>,
    today: LocalDate?,
    actions: PumpingActions,
    olderExpanded: Boolean,
    onToggleOlder: () -> Unit,
) {
    if (days.isEmpty()) {
        EmptyHistory()
        return
    }

    val split = splitLogDays(days)

    Column(modifier = Modifier.fillMaxWidth()) {
        TableHeaderRow()
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)

        LazyColumn {
            split.recent.forEach { day -> tableDay(day, today, actions) }

            if (split.older.isNotEmpty()) {
                item(key = OLDER_DAYS_DRAWER_KEY) {
                    OlderDaysHeader(
                        dayCount = split.older.size,
                        expanded = olderExpanded,
                        onToggle = onToggleOlder,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                if (olderExpanded) {
                    split.older.forEach { day -> tableDay(day, today, actions) }
                }
            }
        }
    }
}

/** One day in the table view: its title row, then a thin row per session. */
private fun LazyListScope.tableDay(day: PumpingDay, today: LocalDate?, actions: PumpingActions) {
    item(key = "day-${day.date}") { DayTitleRow(day = day, today = today) }
    items(day.sessions.reversed(), key = { it.id }) { session ->
        HorizontalDivider()
        SessionCellsRow(session = session, onEdit = { actions.onEditClick(session) })
    }
}

@Composable
private fun TableHeaderRow() {
    // IntrinsicSize.Min is load-bearing: `VerticalDivider` fills max height, which in a Row
    // resolves against the *incoming* constraint rather than the row's content — without it the
    // header takes the whole column and the rows below get no height at all.
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

@Composable
private fun DayTitleRow(day: PumpingDay, today: LocalDate?) {
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
private fun SessionCellsRow(session: PumpSession, onEdit: () -> Unit) {
    val cells = listOf(
        formatClock(session.startedAtEpochMillis),
        stringResource(session.side.labelRes()),
        // Two different kinds of nothing, told apart: still going, versus never measured.
        session.durationMinutes?.toString() ?: MARK_RUNNING,
        session.amountMl?.toString() ?: MARK_UNMEASURED,
    )
    val description = sessionDescription(session)

    // A cell is too small a target for its own button, so the whole row opens the session.
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

/** Says what to do rather than only that there is nothing — an empty log is the first thing seen. */
@Composable
private fun EmptyHistory() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.pumping_empty_history),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.pumping_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PumpSessionForm(uiState: PumpingUiState, actions: PumpingActions) {
    // Scrolls inside the sheet: with the keyboard up on a short phone, Save was below the fold.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(
                if (uiState.isEditing) R.string.pumping_edit_session else R.string.pumping_log_past,
            ),
            style = MaterialTheme.typography.titleMedium,
        )

        WhenStartedRow(uiState = uiState, actions = actions)

        SideRow(selected = uiState.formSide, onChange = actions::onSideChange)

        // The one required field, and marked as such: a dead Save button with nothing said
        // about why is the worst version of this form.
        OutlinedTextField(
            value = uiState.formMinutes,
            onValueChange = actions::onMinutesChange,
            label = { Text(stringResource(R.string.pumping_minutes_field)) },
            isError = uiState.minutesError,
            supportingText = if (uiState.minutesError) {
                { Text(stringResource(R.string.pumping_minutes_required)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = uiState.formAmountMl,
            onValueChange = actions::onAmountChange,
            label = { Text(stringResource(R.string.pumping_amount_field)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = uiState.formNote,
            onValueChange = actions::onNoteChange,
            label = { Text(stringResource(R.string.pumping_note_field)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            // Bounded: an unbounded note field grows until it pushes Save off the sheet.
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        FilledTonalButton(
            onClick = actions::onSave,
            enabled = uiState.canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Names the outcome rather than the mechanism, and changes with what the sheet is
            // doing — the same pattern every other form in the app uses.
            Text(stringResource(if (uiState.isEditing) R.string.pumping_save_changes else R.string.pumping_add_action))
        }

        // Only for the session just stopped: a Start pressed by mistake shouldn't have to be
        // hunted down in the history afterwards.
        if (uiState.discardable) {
            TextButton(onClick = actions::onDiscard, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pumping_discard))
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

/**
 * When the session started. Two buttons while typing in a past one, and plain text otherwise:
 * a session that has already been recorded has its start fixed, and only its length is correctable.
 */
@Composable
private fun WhenStartedRow(uiState: PumpingUiState, actions: PumpingActions) {
    val startedAt = uiState.formStartedAtEpochMillis.toLocalDateTime()
    val date = dateLabel(startedAt.date)
    val time = "%02d:%02d".format(startedAt.hour, startedAt.minute)

    if (uiState.isEditing) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.pumping_when_value, date, time),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.pumping_when_locked),
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

/** A day's totals, with the millilitres only when something that day was measured. */
@Composable
private fun dayHeader(day: PumpingDay, today: LocalDate?): String {
    // Today and yesterday by name, anything older by weekday: at 3am that places a row faster
    // than an ISO date does. Shared with the feeding log, hence `:core:ui`.
    val date = today?.let { dayLabel(day.date, it) } ?: dateLabel(day.date)
    val minutes = day.totalMinutes ?: return date
    val ml = day.totalMl ?: return stringResource(R.string.pumping_day_header, date, minutes)
    return stringResource(R.string.pumping_day_header_with_ml, date, minutes, ml)
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

/** A session with no length yet — it is still going. */
private const val MARK_RUNNING = "…"

/** An output nobody measured, as distinct from a cell that failed to draw. */
private const val MARK_UNMEASURED = "–"

/** The side label is the widest cell; the two numbers are three digits at most. */
private val ColumnWeights = listOf(1f, 1.2f, 1f, 1f)

private val TableRowLabels = listOf(
    R.string.pumping_row_time,
    R.string.pumping_row_side,
    R.string.pumping_row_minutes,
    R.string.pumping_row_amount,
)

@Preview(showBackground = true)
@Composable
private fun PumpingPreview() {
    OrYareachTheme {
        PumpingScreen(
            uiState = PumpingUiState(
                countdown = FeedCountdown(dueAtEpochMillis = 0, remainingMillis = 65 * 60_000L),
                days = listOf(
                    PumpingDay(
                        date = LocalDate(2026, 12, 21),
                        sessions = listOf(
                            PumpSession(
                                id = "a",
                                startedAtEpochMillis = 1_766_300_000_000,
                                endedAtEpochMillis = 1_766_301_080_000,
                                side = PumpSide.LEFT,
                                amountMl = 70,
                            ),
                            PumpSession(
                                id = "b",
                                startedAtEpochMillis = 1_766_310_000_000,
                                endedAtEpochMillis = 1_766_311_200_000,
                                amountMl = 120,
                            ),
                        ),
                    ),
                ),
            ),
            actions = NoopPumpingActions,
        )
    }
}

private object NoopPumpingActions : PumpingActions {
    override fun onStartClick() = Unit
    override fun onPauseClick() = Unit
    override fun onResumeClick() = Unit
    override fun onStopClick() = Unit
    override fun onTimerLongPress() = Unit
    override fun onDismissStash() = Unit
    override fun onDropsShown() = Unit
    override fun onLogPastClick() = Unit
    override fun onEditClick(session: PumpSession) = Unit
    override fun onDismissSheet() = Unit
    override fun onSideChange(value: PumpSide) = Unit
    override fun onPendingSideChange(value: PumpSide) = Unit
    override fun onMinutesChange(value: String) = Unit
    override fun onAmountChange(value: String) = Unit
    override fun onNoteChange(value: String) = Unit
    override fun onOpenDatePicker() = Unit
    override fun onDismissDatePicker() = Unit
    override fun onStartedDateChange(value: LocalDate) = Unit
    override fun onOpenTimePicker() = Unit
    override fun onDismissTimePicker() = Unit
    override fun onStartedTimeChange(value: LocalTime) = Unit
    override fun onSave() = Unit
    override fun onDiscard() = Unit
    override fun onDeleteSessionClick(session: PumpSession) = Unit
    override fun onDismissDeleteSession() = Unit
    override fun onConfirmDeleteSession() = Unit
    override fun onUndoDelete() = Unit
    override fun onUndoDismissed() = Unit
    override fun onHistoryViewChange(value: PumpHistoryView) = Unit
    override fun onRefresh() = Unit
}

private const val MINUTES_PER_HOUR = 60
private const val MILLILITRES_PER_LITRE = 1_000

/** "18h 20m" above an hour, "45m" below it. */
private fun Int.toHoursAndMinutes(): String =
    if (this < MINUTES_PER_HOUR) "${this}m" else "${this / MINUTES_PER_HOUR}h ${this % MINUTES_PER_HOUR}m"

/** One decimal place: "1.5 litres" is a quantity, "1.487 litres" is a reading. */
private fun Int.toLitres(): String = "%.1f".format(this / MILLILITRES_PER_LITRE.toFloat())

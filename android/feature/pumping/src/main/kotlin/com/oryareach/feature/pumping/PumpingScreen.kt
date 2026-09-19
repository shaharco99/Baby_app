package com.oryareach.feature.pumping

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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
import com.oryareach.core.domain.feeding.formatCountdown
import com.oryareach.core.domain.pumping.PumpingDay
import com.oryareach.core.model.PumpSession
import com.oryareach.core.model.PumpSide
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
fun PumpingScreen(
    uiState: PumpingUiState,
    actions: PumpingActions,
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
                    text = stringResource(R.string.pumping_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )

                TimerCard(uiState = uiState, actions = actions)

                OutlinedButton(onClick = actions::onLogPastClick, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pumping_log_past))
                }

                HistoryViewToggle(selected = uiState.historyView, actions = actions)

                when (uiState.historyView) {
                    PumpHistoryView.LIST -> PumpingList(days = uiState.days, actions = actions)
                    PumpHistoryView.TABLE -> PumpingTable(days = uiState.days, actions = actions)
                }
            }
        }
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (uiState.isRunning) {
                Text(
                    text = stringResource(R.string.pumping_running_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatCountdown(uiState.elapsedMillis),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Button(onClick = actions::onStopClick, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pumping_stop))
                }
                return@Column
            }

            CountdownText(countdown = uiState.countdown)

            // Which side is picked before Start, so the running card stays a clock and a Stop
            // button — and it can still be corrected in the sheet on the way out.
            SideRow(selected = uiState.formSide, onChange = actions::onSideChange)

            Button(onClick = actions::onStartClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pumping_start))
            }
        }
    }
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
private fun PumpingList(days: List<PumpingDay>, actions: PumpingActions) {
    if (days.isEmpty()) {
        EmptyHistory()
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { day ->
            item(key = "header-${day.date}") {
                Text(
                    text = dayHeader(day),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            // Newest first within the day: the list reads as a log, while the table's rows read
            // forward through the day.
            items(day.sessions.reversed(), key = { it.id }) { session ->
                SessionRow(
                    session = session,
                    onEdit = { actions.onEditClick(session) },
                    onDelete = { actions.onDeleteSession(session.id) },
                )
            }
        }
    }
}

@Composable
private fun SessionRow(session: PumpSession, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
            TextButton(onClick = onEdit) { Text(stringResource(R.string.pumping_edit)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.pumping_delete)) }
        }
    }
}

/**
 * The same day-sheet shape the feeding log uses: a header row of column titles, a thick divider
 * per calendar day, one thin row per session. Hand-laid-out, because Compose has no table.
 */
@Composable
private fun PumpingTable(days: List<PumpingDay>, actions: PumpingActions) {
    if (days.isEmpty()) {
        EmptyHistory()
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        TableHeaderRow()
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)

        LazyColumn {
            days.forEach { day ->
                item(key = "day-${day.date}") { DayTitleRow(day = day) }
                items(day.sessions, key = { it.id }) { session ->
                    HorizontalDivider()
                    SessionCellsRow(session = session, onEdit = { actions.onEditClick(session) })
                }
            }
        }
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
private fun DayTitleRow(day: PumpingDay) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = dayHeader(day),
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
        session.durationMinutes?.toString() ?: MARK_RUNNING,
        session.amountMl?.toString().orEmpty(),
    )

    // A cell is too small a target for its own button, so the whole row opens the session.
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onEdit)) {
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

@Composable
private fun EmptyHistory() {
    Text(
        text = stringResource(R.string.pumping_empty_history),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PumpSessionForm(uiState: PumpingUiState, actions: PumpingActions) {
    Column(
        modifier = Modifier.fillMaxWidth().imePadding().padding(24.dp),
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

        OutlinedTextField(
            value = uiState.formMinutes,
            onValueChange = actions::onMinutesChange,
            label = { Text(stringResource(R.string.pumping_minutes_field)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = uiState.formAmountMl,
            onValueChange = actions::onAmountChange,
            label = { Text(stringResource(R.string.pumping_amount_field)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = uiState.formNote,
            onValueChange = actions::onNoteChange,
            label = { Text(stringResource(R.string.pumping_note_field)) },
            modifier = Modifier.fillMaxWidth(),
        )

        FilledTonalButton(
            onClick = actions::onSave,
            enabled = uiState.canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.pumping_save))
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
    val date = startedAt.date.toString()
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
private fun dayHeader(day: PumpingDay): String {
    val date = day.date.toString()
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
    override fun onStopClick() = Unit
    override fun onLogPastClick() = Unit
    override fun onEditClick(session: PumpSession) = Unit
    override fun onDismissSheet() = Unit
    override fun onSideChange(value: PumpSide) = Unit
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
    override fun onDeleteSession(id: String) = Unit
    override fun onHistoryViewChange(value: PumpHistoryView) = Unit
    override fun onRefresh() = Unit
}

package com.oryareach.feature.diaper

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oryareach.core.domain.diaper.DiaperDay
import com.oryareach.core.domain.diaper.DiaperEvent
import com.oryareach.core.domain.diaper.changesSince
import com.oryareach.core.domain.log.splitLogDays
import com.oryareach.core.ui.component.DrawerHeader
import com.oryareach.core.ui.text.asLtrIsolate
import com.oryareach.core.ui.text.dateLabel
import com.oryareach.core.ui.text.dayLabel
import com.oryareach.core.ui.theme.OrYareachTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaperScreen(
    uiState: DiaperUiState,
    actions: DiaperActions,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var olderDaysExpanded by rememberSaveable { mutableStateOf(false) }
    val undoLabel = stringResource(R.string.diaper_undo_action)
    val deletedLabel = stringResource(R.string.diaper_deleted)

    // `Long` + dismiss action stated on purpose: with an action label M3 otherwise defaults to
    // Indefinite, and the bar sits over the list until someone taps it.
    LaunchedEffect(uiState.undoDeleteId) {
        uiState.undoDeleteId ?: return@LaunchedEffect
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
                    text = stringResource(R.string.diaper_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )

                TodayCard(uiState = uiState, actions = actions)

                DiaperList(
                    days = uiState.days,
                    today = uiState.today,
                    actions = actions,
                    olderExpanded = olderDaysExpanded,
                    onToggleOlder = { olderDaysExpanded = !olderDaysExpanded },
                )
            }
        }
    }

    uiState.deleteConfirm?.let { event ->
        AlertDialog(
            onDismissRequest = actions::onDismissDelete,
            text = { Text(stringResource(R.string.diaper_delete_confirm, formatClock(event.atEpochMillis))) },
            confirmButton = {
                TextButton(onClick = actions::onConfirmDelete) { Text(stringResource(R.string.diaper_delete)) }
            },
            dismissButton = {
                TextButton(onClick = actions::onDismissDelete) { Text(stringResource(R.string.diaper_pick_cancel)) }
            },
        )
    }

    if (uiState.sheetVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = actions::onDismissSheet, sheetState = sheetState) {
            DiaperForm(uiState = uiState, actions = actions)
        }
    }

    if (uiState.datePickerVisible) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.formChangedAtEpochMillis.toUtcDateMillis(),
        )
        DatePickerDialog(
            onDismissRequest = actions::onDismissDatePicker,
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { actions.onDateChange(it.toPickedDate()) }
                }) { Text(stringResource(R.string.diaper_pick_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = actions::onDismissDatePicker) {
                    Text(stringResource(R.string.diaper_pick_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (uiState.timePickerVisible) {
        val changedAt = uiState.formChangedAtEpochMillis.toLocalDateTime()
        val timeState = rememberTimePickerState(
            initialHour = changedAt.hour,
            initialMinute = changedAt.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = actions::onDismissTimePicker,
            confirmButton = {
                TextButton(onClick = {
                    actions.onTimeChange(LocalTime(timeState.hour, timeState.minute))
                }) { Text(stringResource(R.string.diaper_pick_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = actions::onDismissTimePicker) {
                    Text(stringResource(R.string.diaper_pick_cancel))
                }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}

/**
 * Today's count up top — the number asked for — with the day's marks, the last change and the
 * week's total under it, then the one button this screen is for.
 */
@Composable
private fun TodayCard(uiState: DiaperUiState, actions: DiaperActions) {
    val day = uiState.todayDay
    val count = day?.changeCount ?: 0

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.diaper_today_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = pluralStringResource(R.plurals.diaper_count, count, count),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (day != null) {
                Text(
                    text = stringResource(R.string.diaper_marks_count, day.urineCount, day.stoolCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            uiState.lastChange?.let { last ->
                Text(
                    text = stringResource(R.string.diaper_last_change, formatClock(last.atEpochMillis).asLtrIsolate()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            uiState.today?.let { today ->
                val week = changesSince(uiState.days, today.minus(WEEK_DAYS - 1, DateTimeUnit.DAY))
                Text(
                    text = stringResource(
                        R.string.diaper_week_total,
                        pluralStringResource(R.plurals.diaper_count, week, week),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Button(onClick = actions::onLogClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.diaper_log_action))
            }
        }
    }
}

@Composable
private fun DiaperList(
    days: List<DiaperDay>,
    today: LocalDate?,
    actions: DiaperActions,
    olderExpanded: Boolean,
    onToggleOlder: () -> Unit,
) {
    if (days.isEmpty()) {
        EmptyHistory()
        return
    }

    val split = splitLogDays(days)

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        split.recent.forEach { day -> listDay(day, today, actions) }

        if (split.older.isNotEmpty()) {
            item(key = OLDER_DAYS_DRAWER_KEY) {
                DrawerHeader(
                    title = stringResource(R.string.diaper_older_days_drawer),
                    count = split.older.size,
                    expanded = olderExpanded,
                    onToggle = onToggleOlder,
                )
            }
            if (olderExpanded) {
                split.older.forEach { day -> listDay(day, today, actions) }
            }
        }
    }
}

private fun LazyListScope.listDay(day: DiaperDay, today: LocalDate?, actions: DiaperActions) {
    item(key = "header-${day.date}") {
        Text(
            text = dayHeader(day, today),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    // Keyed by source as well as id: a feed and a change are different tables and their ids
    // only have to be unique within their own.
    items(day.events.reversed(), key = { "${it.fromFeed}-${it.id}" }) { event ->
        EventRow(
            event = event,
            onEdit = { actions.onEditClick(event) },
            onDelete = { actions.onDeleteClick(event) },
        )
    }
}

private const val OLDER_DAYS_DRAWER_KEY = "older-days-drawer"

/**
 * One change. A change logged here opens on tap and has a trash icon; one that came from a feed
 * has neither — it belongs to the feed, and says where to edit it instead.
 */
@Composable
private fun EventRow(event: DiaperEvent, onEdit: () -> Unit, onDelete: () -> Unit) {
    val clock = formatClock(event.atEpochMillis)
    val marks = stringResource(event.marksRes()).let {
        if (event.changed) it else stringResource(R.string.diaper_not_changed, it)
    }
    val source = if (event.fromFeed) stringResource(R.string.diaper_from_feed) else null
    val description = listOfNotNull(clock, marks, event.note, source).joinToString(", ")

    val rowModifier = Modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = true) {
            contentDescription = description
            if (!event.fromFeed) role = Role.Button
        }
        .let { if (event.fromFeed) it else it.clickable(onClick = onEdit) }

    Card(modifier = rowModifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = if (event.fromFeed) 16.dp else 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = clock, style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = marks, style = MaterialTheme.typography.bodyMedium)
                event.note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                source?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!event.fromFeed) {
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Outlined.Delete, contentDescription = stringResource(R.string.diaper_delete))
                }
            }
        }
    }
}

private fun DiaperEvent.marksRes(): Int = when {
    hadUrine && hadStool -> R.string.diaper_both
    hadUrine -> R.string.diaper_urine
    hadStool -> R.string.diaper_stool
    else -> R.string.diaper_dry
}

/** Says what to do rather than only that there is nothing — including that feeds count too. */
@Composable
private fun EmptyHistory() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.diaper_empty_history),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.diaper_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DiaperForm(uiState: DiaperUiState, actions: DiaperActions) {
    val changedAt = uiState.formChangedAtEpochMillis.toLocalDateTime()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(if (uiState.isEditing) R.string.diaper_edit_title else R.string.diaper_new_title),
            style = MaterialTheme.typography.titleMedium,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = actions::onOpenDatePicker, modifier = Modifier.weight(1f)) {
                Text(dateLabel(changedAt.date))
            }
            OutlinedButton(onClick = actions::onOpenTimePicker, modifier = Modifier.weight(1f)) {
                Text("%02d:%02d".format(changedAt.hour, changedAt.minute))
            }
        }

        Text(text = stringResource(R.string.diaper_marks_prompt), style = MaterialTheme.typography.bodyMedium)
        // Chips, like the feed sheet's: big targets for one hand in the dark.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = uiState.formHadUrine,
                onClick = actions::onToggleUrine,
                label = { Text(stringResource(R.string.diaper_urine)) },
            )
            FilterChip(
                selected = uiState.formHadStool,
                onClick = actions::onToggleStool,
                label = { Text(stringResource(R.string.diaper_stool)) },
            )
        }
        Text(
            text = stringResource(R.string.diaper_marks_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = uiState.formNote,
            onValueChange = actions::onNoteChange,
            label = { Text(stringResource(R.string.diaper_note_field)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        FilledTonalButton(
            onClick = actions::onSave,
            enabled = !uiState.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (uiState.isEditing) R.string.diaper_save_changes else R.string.diaper_add_action))
        }

        Spacer(Modifier.height(8.dp))
    }
}

/** Today and yesterday by name, then the count, then the marks when there are any. */
@Composable
private fun dayHeader(day: DiaperDay, today: LocalDate?): String {
    val date = today?.let { dayLabel(day.date, it) } ?: dateLabel(day.date)
    val count = pluralStringResource(R.plurals.diaper_count, day.changeCount, day.changeCount)
    return if (day.urineCount == 0 && day.stoolCount == 0) {
        stringResource(R.string.diaper_day_header, date, count)
    } else {
        stringResource(R.string.diaper_day_header_marks, date, count, day.urineCount, day.stoolCount)
    }
}

private const val WEEK_DAYS = 7

private fun Long.toLocalDateTime(): LocalDateTime =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())

/** `DatePicker` reads and writes UTC midnights; the local day goes through UTC unshifted. */
private fun Long.toUtcDateMillis(): Long =
    toLocalDateTime().date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toPickedDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

private fun formatClock(epochMillis: Long): String {
    val time = epochMillis.toLocalDateTime()
    return "%02d:%02d".format(time.hour, time.minute)
}

@Preview(showBackground = true)
@Composable
private fun DiaperPreview() {
    OrYareachTheme {
        DiaperScreen(
            uiState = DiaperUiState(
                today = LocalDate(2026, 9, 25),
                days = listOf(
                    DiaperDay(
                        date = LocalDate(2026, 9, 25),
                        events = listOf(
                            DiaperEvent("f1", 1_790_300_000_000, hadUrine = true, hadStool = false, note = null, fromFeed = true),
                            DiaperEvent("c1", 1_790_310_000_000, hadUrine = true, hadStool = true, note = null, fromFeed = false),
                        ),
                    ),
                ),
            ),
            actions = NoopDiaperActions,
        )
    }
}

private object NoopDiaperActions : DiaperActions {
    override fun onLogClick() = Unit
    override fun onEditClick(event: DiaperEvent) = Unit
    override fun onDismissSheet() = Unit
    override fun onToggleUrine() = Unit
    override fun onToggleStool() = Unit
    override fun onNoteChange(value: String) = Unit
    override fun onOpenDatePicker() = Unit
    override fun onDismissDatePicker() = Unit
    override fun onDateChange(value: LocalDate) = Unit
    override fun onOpenTimePicker() = Unit
    override fun onDismissTimePicker() = Unit
    override fun onTimeChange(value: LocalTime) = Unit
    override fun onSave() = Unit
    override fun onDeleteClick(event: DiaperEvent) = Unit
    override fun onDismissDelete() = Unit
    override fun onConfirmDelete() = Unit
    override fun onUndoDelete() = Unit
    override fun onUndoDismissed() = Unit
    override fun onRefresh() = Unit
}

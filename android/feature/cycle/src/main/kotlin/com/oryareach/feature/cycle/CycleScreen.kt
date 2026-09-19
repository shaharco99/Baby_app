package com.oryareach.feature.cycle

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oryareach.core.domain.cycle.CyclePrediction
import com.oryareach.core.domain.cycle.CycleStatistics
import com.oryareach.core.model.Document
import com.oryareach.core.model.FlowLevel
import com.oryareach.core.model.MenstrualCycle
import com.oryareach.core.model.Mood
import com.oryareach.core.model.PainLevel
import com.oryareach.core.model.Symptom
import com.oryareach.core.scanner.rememberDocumentScanner
import com.oryareach.core.ui.text.asLtrIsolate
import com.oryareach.core.ui.theme.OrYareachTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleScreen(
    uiState: CycleUiState,
    actions: CycleActions,
    modifier: Modifier = Modifier,
) {
    var deleteConfirmCycle by remember { mutableStateOf<MenstrualCycle?>(null) }
    var deleteConfirmEntry by remember { mutableStateOf(false) }

    androidx.compose.material3.Scaffold(
        modifier = modifier.fillMaxSize().safeDrawingPadding(),
    ) { padding ->
      androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = uiState.refreshing,
        onRefresh = actions::onRefresh,
        modifier = Modifier.fillMaxSize().padding(padding),
      ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.cycle_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
            }

            item { OngoingCard(uiState = uiState, actions = actions) }
            item { PredictionCard(prediction = uiState.prediction) }
            item { StatisticsCard(statistics = uiState.statistics) }
            item { CalendarCard(uiState = uiState, actions = actions) }

            item {
                Text(
                    text = stringResource(R.string.cycle_history_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            if (uiState.history.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.cycle_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(uiState.history, key = MenstrualCycle::id) { cycle ->
                    HistoryRow(
                        cycle = cycle,
                        uiState = uiState,
                        actions = actions,
                        onDeleteClick = { deleteConfirmCycle = cycle },
                    )
                }
            }
        }
      }
    }

    if (uiState.daySheetVisible) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = actions::onDismissDaySheet, sheetState = sheetState) {
            DayForm(uiState = uiState, actions = actions, onDeleteClick = { deleteConfirmEntry = true })
        }
    }

    deleteConfirmCycle?.let { cycle ->
        AlertDialog(
            onDismissRequest = { deleteConfirmCycle = null },
            title = { Text(stringResource(R.string.cycle_delete_title)) },
            text = { Text(stringResource(R.string.cycle_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    actions.onDelete(cycle.id)
                    deleteConfirmCycle = null
                }) { Text(stringResource(R.string.cycle_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmCycle = null }) { Text(stringResource(R.string.cycle_cancel)) }
            },
        )
    }

    if (deleteConfirmEntry) {
        AlertDialog(
            onDismissRequest = { deleteConfirmEntry = false },
            title = { Text(stringResource(R.string.cycle_delete_entry_title)) },
            confirmButton = {
                TextButton(onClick = {
                    actions.onDeleteEntry()
                    deleteConfirmEntry = false
                }) { Text(stringResource(R.string.cycle_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmEntry = false }) { Text(stringResource(R.string.cycle_cancel)) }
            },
        )
    }
}

@Composable
private fun OngoingCard(uiState: CycleUiState, actions: CycleActions) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val ongoing = uiState.ongoing
            Text(
                text = if (ongoing != null) {
                    stringResource(R.string.cycle_ongoing_started, ongoing.startDate.toString().asLtrIsolate())
                } else {
                    stringResource(R.string.cycle_no_ongoing)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (ongoing == null) {
                Button(
                    onClick = actions::onStartPeriod,
                    enabled = !uiState.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.cycle_start))
                }
            } else {
                OutlinedButton(
                    onClick = actions::onEndPeriod,
                    enabled = !uiState.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.cycle_end))
                }
            }
        }
    }
}

@Composable
private fun PredictionCard(prediction: CyclePrediction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.cycle_prediction_title), style = MaterialTheme.typography.titleSmall)

            if (!prediction.hasSufficientHistory) {
                Text(
                    text = stringResource(R.string.cycle_prediction_insufficient),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                prediction.nextPeriodStart?.let {
                    Text(
                        stringResource(R.string.cycle_prediction_next_period, it.toString().asLtrIsolate()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (prediction.fertileWindowStart != null && prediction.fertileWindowEnd != null) {
                    Text(
                        stringResource(
                            R.string.cycle_prediction_fertile_window,
                            prediction.fertileWindowStart.toString().asLtrIsolate(),
                            prediction.fertileWindowEnd.toString().asLtrIsolate(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                prediction.ovulationDate?.let {
                    Text(
                        stringResource(R.string.cycle_prediction_ovulation, it.toString().asLtrIsolate()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatisticsCard(statistics: CycleStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.cycle_stats_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.cycle_stats_count, statistics.cycleCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            statistics.averageCycleLengthDays?.let {
                Text(stringResource(R.string.cycle_stats_avg_cycle, it), style = MaterialTheme.typography.bodyMedium)
            }
            statistics.averagePeriodLengthDays?.let {
                Text(stringResource(R.string.cycle_stats_avg_period, it), style = MaterialTheme.typography.bodyMedium)
            }
            val shortest = statistics.shortestCycleLengthDays
            val longest = statistics.longestCycleLengthDays
            if (shortest != null && longest != null) {
                Text(
                    stringResource(R.string.cycle_stats_range, shortest, longest),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun CalendarCard(uiState: CycleUiState, actions: CycleActions) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Pinned left-to-right in every language: "back" is the left button pointing left and
            // "next" the right one pointing right. Under RTL the Row used to swap the buttons but
            // not the chevrons, so in Hebrew the right-hand arrow went back a month.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = actions::onPreviousMonth) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.cycle_calendar_previous_month))
                    }
                    Text(uiState.visibleMonth.toString().asLtrIsolate(), style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = actions::onNextMonth) {
                        Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.cycle_calendar_next_month))
                    }
                }
            }

            CalendarGrid(uiState = uiState, onSelectDate = actions::onSelectDate)

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LegendRow(color = MaterialTheme.colorScheme.error, label = stringResource(R.string.cycle_calendar_legend_actual), filled = true)
                LegendRow(color = MaterialTheme.colorScheme.primary, label = stringResource(R.string.cycle_calendar_legend_predicted), filled = false)
                LegendRow(color = MaterialTheme.colorScheme.tertiary, label = stringResource(R.string.cycle_calendar_legend_fertile), filled = true, small = true)
                LegendRow(color = MaterialTheme.colorScheme.secondary, label = stringResource(R.string.cycle_calendar_legend_ovulation), filled = true, small = true, square = true)
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, filled: Boolean, small: Boolean = false, square: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val size = if (small) 8.dp else 14.dp
        Box(
            modifier = Modifier
                .size(size)
                .let { if (square) it else it.clip(CircleShape) }
                .then(
                    if (filled) Modifier.background(color) else Modifier.background(Color.Transparent),
                ),
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CalendarGrid(uiState: CycleUiState, onSelectDate: (LocalDate) -> Unit) {
    val month = uiState.visibleMonth
    val dayCount = daysInMonth(month)
    // Sunday-first week; DayOfWeek's ordinal is MONDAY=0..SUNDAY=6, so shifting by one and
    // wrapping puts Sunday at offset 0.
    val leadingBlanks = (month.dayOfWeek.ordinal + 1) % 7

    val actualDays = remember(uiState.history) { actualPeriodDays(uiState.history) }
    val prediction = uiState.prediction
    val predictedDays = remember(prediction, uiState.statistics) {
        predictedPeriodDays(prediction, uiState.statistics.averagePeriodLengthDays ?: 5)
    }
    val fertileDays = remember(prediction) { fertileWindowDays(prediction) }
    val loggedDates = remember(uiState.entriesInMonth) { uiState.entriesInMonth.map { it.date }.toSet() }

    val cells = leadingBlanks + dayCount
    val rows = (cells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - leadingBlanks + 1
                    // An equal seventh each. Seven fixed-size cells overflowed a narrow card, and
                    // the squeezed edge cell broke "12" into two stacked digits.
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    if (dayNumber >= 1 && dayNumber <= dayCount) {
                        val date = LocalDate(month.year, month.month, dayNumber)
                        DayCell(
                            day = dayNumber,
                            isActual = date in actualDays,
                            isPredicted = date in predictedDays,
                            isFertile = date in fertileDays,
                            isOvulation = date == prediction.ovulationDate,
                            hasEntry = date in loggedDates,
                            onClick = { onSelectDate(date) },
                        )
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    isActual: Boolean,
    isPredicted: Boolean,
    isFertile: Boolean,
    isOvulation: Boolean,
    hasEntry: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(36.dp)
                .clip(CircleShape)
                .then(
                    when {
                        isActual -> Modifier.background(MaterialTheme.colorScheme.error)
                        isPredicted -> Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        else -> Modifier
                    },
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                softWrap = false,
                fontWeight = if (hasEntry) FontWeight.Bold else FontWeight.Normal,
                color = if (isActual) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (isFertile) {
                Box(
                    modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary),
                )
            }
            if (isOvulation) {
                Box(modifier = Modifier.size(6.dp).background(MaterialTheme.colorScheme.secondary))
            }
        }
    }
}

@Composable
private fun HistoryRow(cycle: MenstrualCycle, uiState: CycleUiState, actions: CycleActions, onDeleteClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val text = cycle.endDate?.let { end ->
                    stringResource(
                        R.string.cycle_history_row_range,
                        cycle.startDate.toString().asLtrIsolate(),
                        end.toString().asLtrIsolate(),
                        cycle.periodLengthDays ?: 0,
                    )
                } ?: stringResource(R.string.cycle_history_row_ongoing, cycle.startDate.toString().asLtrIsolate())

                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).clickable { actions.onToggleAttachments(cycle.id) },
                )
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cycle_delete))
                }
            }

            if (uiState.expandedCycleId == cycle.id) {
                AttachmentsSection(uiState = uiState, actions = actions)
            }
        }
    }
}

@Composable
private fun AttachmentsSection(uiState: CycleUiState, actions: CycleActions) {
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        } ?: uri.lastPathSegment.orEmpty()
        actions.onAttachDocument(name, mimeType, bytes)
    }
    val startScan = rememberDocumentScanner { scanned ->
        actions.onAttachDocument(scanned.name, scanned.mimeType, scanned.bytes)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.cycle_attachments),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        uiState.cycleAttachments.forEach { document ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text(
                    text = document.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                IconButton(onClick = { actions.onDeleteAttachment(document) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cycle_remove_attachment))
                }
            }
        }
        if (uiState.attaching) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.cycle_attach_document))
                }
                TextButton(onClick = startScan) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.cycle_scan_document))
                }
            }
        }
    }
}

@Composable
private fun DayForm(uiState: CycleUiState, actions: CycleActions, onDeleteClick: () -> Unit) {
    val date = uiState.selectedDate ?: return

    Column(
        modifier = Modifier.fillMaxWidth().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.cycle_day_sheet_title, date.toString().asLtrIsolate()),
            style = MaterialTheme.typography.titleLarge,
        )

        Text(stringResource(R.string.cycle_day_flow), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowLevel.entries.forEach { level ->
                FilterChip(
                    selected = uiState.formFlow == level,
                    onClick = { actions.onFlowChange(level) },
                    label = { Text(stringResource(level.labelRes())) },
                )
            }
        }

        Text(stringResource(R.string.cycle_day_pain), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PainLevel.entries.forEach { level ->
                FilterChip(
                    selected = uiState.formPain == level,
                    onClick = { actions.onPainChange(level) },
                    label = { Text(stringResource(level.labelRes())) },
                )
            }
        }

        Text(stringResource(R.string.cycle_day_mood), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mood.entries.forEach { mood ->
                FilterChip(
                    selected = mood in uiState.formMood,
                    onClick = { actions.onToggleMood(mood) },
                    label = { Text(stringResource(mood.labelRes())) },
                )
            }
        }

        Text(stringResource(R.string.cycle_day_symptoms), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Symptom.entries.forEach { symptom ->
                FilterChip(
                    selected = symptom in uiState.formSymptoms,
                    onClick = { actions.onToggleSymptom(symptom) },
                    label = { Text(stringResource(symptom.labelRes())) },
                )
            }
        }

        OutlinedTextField(
            value = uiState.formNote,
            onValueChange = actions::onNoteChange,
            label = { Text(stringResource(R.string.cycle_day_note)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(onClick = actions::onSaveEntry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cycle_day_save))
        }
        OutlinedButton(onClick = onDeleteClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cycle_day_delete_entry))
        }
    }
}

private fun daysInMonth(month: LocalDate): Int {
    val start = LocalDate(month.year, month.month, 1)
    val nextMonth = start.plus(1, DateTimeUnit.MONTH)
    return (nextMonth.minus(1, DateTimeUnit.DAY).day)
}

private fun actualPeriodDays(history: List<MenstrualCycle>): Set<LocalDate> {
    val days = mutableSetOf<LocalDate>()
    history.forEach { cycle ->
        val end = cycle.endDate ?: cycle.startDate
        var d = cycle.startDate
        while (d <= end) {
            days += d
            d = d.plus(1, DateTimeUnit.DAY)
        }
    }
    return days
}

private fun predictedPeriodDays(prediction: CyclePrediction, periodLengthDays: Int): Set<LocalDate> {
    val start = prediction.nextPeriodStart ?: return emptySet()
    val days = mutableSetOf<LocalDate>()
    var d = start
    repeat(periodLengthDays.coerceAtLeast(1)) {
        days += d
        d = d.plus(1, DateTimeUnit.DAY)
    }
    return days
}

private fun fertileWindowDays(prediction: CyclePrediction): Set<LocalDate> {
    val start = prediction.fertileWindowStart ?: return emptySet()
    val end = prediction.fertileWindowEnd ?: return emptySet()
    val days = mutableSetOf<LocalDate>()
    var d = start
    while (d <= end) {
        days += d
        d = d.plus(1, DateTimeUnit.DAY)
    }
    return days
}

@Preview(showBackground = true)
@Composable
private fun CyclePreview() {
    OrYareachTheme {
        CycleScreen(
            uiState = CycleUiState(
                ongoing = MenstrualCycle(id = "1", startDate = LocalDate(2026, 8, 10)),
                history = listOf(
                    MenstrualCycle(id = "1", startDate = LocalDate(2026, 8, 10)),
                    MenstrualCycle(
                        id = "2",
                        startDate = LocalDate(2026, 7, 12),
                        endDate = LocalDate(2026, 7, 17),
                    ),
                ),
                visibleMonth = LocalDate(2026, 8, 1),
            ),
            actions = NoopCycleActions,
        )
    }
}

private object NoopCycleActions : CycleActions {
    override fun onStartPeriod() = Unit
    override fun onEndPeriod() = Unit
    override fun onDelete(id: String) = Unit
    override fun onPreviousMonth() = Unit
    override fun onNextMonth() = Unit
    override fun onSelectDate(date: LocalDate) = Unit
    override fun onDismissDaySheet() = Unit
    override fun onFlowChange(value: FlowLevel?) = Unit
    override fun onToggleSymptom(value: Symptom) = Unit
    override fun onToggleMood(value: Mood) = Unit
    override fun onPainChange(value: PainLevel?) = Unit
    override fun onNoteChange(value: String) = Unit
    override fun onSaveEntry() = Unit
    override fun onDeleteEntry() = Unit
    override fun onToggleAttachments(cycleId: String) = Unit
    override fun onAttachDocument(name: String, mimeType: String, bytes: ByteArray) = Unit
    override fun onDeleteAttachment(document: Document) = Unit
    override fun onRefresh() = Unit
}

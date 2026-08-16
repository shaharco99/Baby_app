package com.oryareach.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oryareach.core.model.EntityType
import com.oryareach.core.ui.text.asLtrIsolate
import com.oryareach.core.ui.theme.OrYareachTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    uiState: CalendarUiState,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Scaffold(
        modifier = modifier.fillMaxSize().safeDrawingPadding(),
    ) { padding ->
      androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = uiState.refreshing,
        onRefresh = actions::onRefresh,
        modifier = Modifier.fillMaxSize().padding(padding),
      ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.calendar_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = actions::onPreviousMonth) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.calendar_previous_month))
                }
                Text(uiState.visibleMonth.toString().asLtrIsolate(), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = actions::onNextMonth) {
                    Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.calendar_next_month))
                }
            }

            CalendarGrid(uiState = uiState, onSelectDate = actions::onSelectDate)

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LegendRow(color = MaterialTheme.colorScheme.primary, label = stringResource(R.string.calendar_legend_task))
                LegendRow(color = MaterialTheme.colorScheme.tertiary, label = stringResource(R.string.calendar_legend_important_date))
                LegendRow(color = MaterialTheme.colorScheme.error, label = stringResource(R.string.calendar_legend_period))
                LegendRow(color = MaterialTheme.colorScheme.secondary, label = stringResource(R.string.calendar_legend_google))
            }
        }
      }
    }

    if (uiState.daySheetVisible) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = actions::onDismissDaySheet, sheetState = sheetState) {
            DaySheetContent(uiState = uiState, actions = actions)
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CalendarGrid(uiState: CalendarUiState, onSelectDate: (LocalDate) -> Unit) {
    val month = uiState.visibleMonth
    val dayCount = daysInMonth(month)
    val leadingBlanks = (month.dayOfWeek.ordinal + 1) % 7
    val eventsByDate = remember(uiState.events) { uiState.events.groupBy { it.date } }

    val cells = leadingBlanks + dayCount
    val rows = (cells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - leadingBlanks + 1
                    if (dayNumber < 1 || dayNumber > dayCount) {
                        Box(modifier = Modifier.minimumInteractiveComponentSize().size(36.dp))
                    } else {
                        val date = LocalDate(month.year, month.month, dayNumber)
                        DayCell(day = dayNumber, kinds = eventsByDate[date]?.map { it.kind }.orEmpty().toSet(), onClick = { onSelectDate(date) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, kinds: Set<CalendarEventKind>, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.minimumInteractiveComponentSize().size(36.dp).clip(CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (kinds.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (CalendarEventKind.TASK_DUE in kinds) Dot(MaterialTheme.colorScheme.primary)
            if (CalendarEventKind.IMPORTANT_DATE in kinds) Dot(MaterialTheme.colorScheme.tertiary)
            if (CalendarEventKind.PERIOD_ACTUAL in kinds || CalendarEventKind.PERIOD_PREDICTED in kinds) {
                Dot(MaterialTheme.colorScheme.error)
            }
            if (CalendarEventKind.GOOGLE_EVENT in kinds) Dot(MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
}

@Composable
private fun DaySheetContent(uiState: CalendarUiState, actions: CalendarActions) {
    val date = uiState.selectedDate ?: return
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(date.toString().asLtrIsolate(), style = MaterialTheme.typography.titleLarge)

        if (uiState.eventsForSelectedDate.isEmpty()) {
            Text(
                stringResource(R.string.calendar_no_events),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(uiState.eventsForSelectedDate, key = { it.recordId + it.kind }) { event ->
                    EventRow(event = event, onClick = { actions.onEventClick(event) })
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = stringResource(event.kind.labelRes()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (event.title.isNotBlank()) {
                Text(event.title, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun daysInMonth(month: LocalDate): Int {
    val start = LocalDate(month.year, month.month, 1)
    val nextMonth = start.plus(1, DateTimeUnit.MONTH)
    return nextMonth.minus(1, DateTimeUnit.DAY).day
}

@Preview(showBackground = true)
@Composable
private fun CalendarPreview() {
    OrYareachTheme {
        CalendarScreen(
            uiState = CalendarUiState(
                visibleMonth = LocalDate(2026, 8, 1),
                events = listOf(
                    CalendarEvent(LocalDate(2026, 8, 10), EntityType.TASK, "1", "Pack hospital bag", CalendarEventKind.TASK_DUE),
                    CalendarEvent(LocalDate(2026, 8, 15), EntityType.IMPORTANT_DATE, "2", "Baby shower", CalendarEventKind.IMPORTANT_DATE),
                ),
            ),
            actions = NoopCalendarActions,
        )
    }
}

private object NoopCalendarActions : CalendarActions {
    override fun onPreviousMonth() = Unit
    override fun onNextMonth() = Unit
    override fun onSelectDate(date: LocalDate) = Unit
    override fun onDismissDaySheet() = Unit
    override fun onEventClick(event: CalendarEvent) = Unit
    override fun onRefresh() = Unit
}

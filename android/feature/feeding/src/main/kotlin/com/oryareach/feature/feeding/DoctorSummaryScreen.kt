package com.oryareach.feature.feeding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oryareach.core.domain.feeding.DoctorSummary
import com.oryareach.core.domain.feeding.FeedingStretch
import com.oryareach.core.model.Baby
import com.oryareach.core.ui.text.dateLabel
import com.oryareach.core.ui.text.dayLabel
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/**
 * The quick look for a checkup: what the last day and the last week of the log add up to, on one
 * screen that can be handed across a desk.
 *
 * A sub-screen of the feeding tab rather than a route — the app has no back stack, so it is shown
 * in place of the log while [FeedingUiState.doctorSummary] is set, and back closes it.
 *
 * The figures are the ones asked at a newborn checkup: how many feeds, how much, how far apart,
 * how many wet and dirty nappies, and whether the vitamin is being given. Everything is computed
 * in `:core:domain`'s [com.oryareach.core.domain.feeding.doctorSummary]; this only lays it out.
 */
@Composable
internal fun DoctorSummaryScreen(
    summary: DoctorSummary,
    baby: Baby?,
    today: LocalDate?,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.feeding_summary_close),
                )
            }
            Text(
                text = stringResource(R.string.feeding_summary_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
        }

        baby?.let { BabyLine(it) }

        StretchCard(
            title = stringResource(R.string.feeding_summary_last_24h),
            stretch = summary.last24Hours,
        )

        WeekCard(summary = summary)

        if (summary.dayCount > 0) {
            DayByDayCard(summary = summary, today = today)
        }

        Text(
            text = stringResource(R.string.feeding_summary_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BabyLine(baby: Baby) {
    val parts = listOfNotNull(
        baby.name?.takeIf { it.isNotBlank() },
        baby.birthDate?.let { stringResource(R.string.feeding_summary_born, dateLabel(it)) },
        baby.birthWeightGrams?.let { stringResource(R.string.feeding_summary_birth_weight, it) },
    )
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(SEPARATOR),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StretchCard(title: String, stretch: FeedingStretch) {
    SummaryCard(title = title) {
        FigureRow(stringResource(R.string.feeding_summary_feeds), stretch.feeds.toString())
        FigureRow(
            label = stringResource(R.string.feeding_summary_milk),
            value = stretch.totalMl?.let { stringResource(R.string.feeding_amount_ml, it) }
                ?: stringResource(R.string.feeding_summary_not_measured),
            detail = sourceSplit(stretch.breastMl, stretch.formulaMl),
        )
        stretch.averageGapMinutes?.let { minutes ->
            FigureRow(
                label = stringResource(R.string.feeding_summary_interval),
                value = stringResource(R.string.feeding_summary_interval_value, minutes / 60, minutes % 60),
            )
        }
        FigureRow(stringResource(R.string.feeding_urine), stretch.urineCount.toString())
        FigureRow(stringResource(R.string.feeding_stool), stretch.stoolCount.toString())
    }
}

/** Only when both sources were used — one source alone is already the total above it. */
@Composable
private fun sourceSplit(breastMl: Int?, formulaMl: Int?): String? {
    if (breastMl == null || formulaMl == null) return null
    return stringResource(R.string.feeding_summary_source_split, breastMl, formulaMl)
}

@Composable
private fun WeekCard(summary: DoctorSummary) {
    val title = if (summary.dayCount > 0) {
        pluralStringResource(R.plurals.feeding_summary_week_title, summary.dayCount, summary.dayCount)
    } else {
        stringResource(R.string.feeding_summary_week_title_empty)
    }
    SummaryCard(title = title) {
        if (summary.dayCount == 0) {
            Text(
                text = stringResource(R.string.feeding_summary_no_days),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SummaryCard
        }
        FigureRow(stringResource(R.string.feeding_summary_feeds), summary.averageFeedsPerDay.oneDecimal())
        FigureRow(
            label = stringResource(R.string.feeding_summary_milk),
            value = summary.averageMlPerDay
                ?.let { stringResource(R.string.feeding_amount_ml, it.roundToInt()) }
                ?: stringResource(R.string.feeding_summary_not_measured),
        )
        summary.week.averageGapMinutes?.let { minutes ->
            FigureRow(
                label = stringResource(R.string.feeding_summary_interval),
                value = stringResource(R.string.feeding_summary_interval_value, minutes / 60, minutes % 60),
            )
        }
        FigureRow(stringResource(R.string.feeding_urine), summary.averageUrinePerDay.oneDecimal())
        FigureRow(stringResource(R.string.feeding_stool), summary.averageStoolPerDay.oneDecimal())
        FigureRow(
            label = stringResource(R.string.feeding_summary_vitamin),
            value = stringResource(R.string.feeding_summary_vitamin_value, summary.vitaminDays, summary.dayCount),
        )
    }
}

/**
 * The week one day at a time: a bar per day for the milk, then the same days as a table.
 *
 * The bars are plain boxes in a [Row], not a canvas, so they mirror with the layout — in Hebrew
 * the newest day sits on the left, the end the row reads towards, same as the table's order
 * reads down. Silent to a screen reader: the table under it says every figure the bars draw.
 */
@Composable
private fun DayByDayCard(summary: DoctorSummary, today: LocalDate?) {
    SummaryCard(title = stringResource(R.string.feeding_summary_by_day)) {
        val most = summary.days.maxOf { it.totalMl ?: 0 }.coerceAtLeast(1)
        if (summary.days.any { it.totalMl != null }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                summary.days.forEach { day ->
                    val ml = day.totalMl ?: 0
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = day.totalMl?.toString().orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(CHART_HEIGHT * (ml.toFloat() / most).coerceAtLeast(MIN_BAR_FRACTION))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (ml > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        )
                        Text(
                            text = day.date.day.toString(),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }

        TableRow(
            cells = listOf(
                stringResource(R.string.feeding_summary_col_day),
                stringResource(R.string.feeding_summary_col_feeds),
                stringResource(R.string.feeding_row_amount),
                stringResource(R.string.feeding_row_urine),
                stringResource(R.string.feeding_row_stool),
            ),
            header = true,
        )
        summary.days.asReversed().forEach { day ->
            TableRow(
                cells = listOf(
                    today?.let { dayLabel(day.date, it) } ?: dateLabel(day.date),
                    day.feeds.size.toString(),
                    day.totalMl?.toString() ?: "–",
                    day.urineCount.toString(),
                    day.stoolCount.toString(),
                ),
            )
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { index, text ->
            Text(
                text = text,
                style = if (header) {
                    MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                } else {
                    MaterialTheme.typography.bodySmall
                },
                color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textAlign = if (index == 0) TextAlign.Start else TextAlign.Center,
                modifier = Modifier.weight(if (index == 0) DAY_COLUMN_WEIGHT else 1f),
            )
        }
    }
}

@Composable
private fun SummaryCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}

/** A label at the reading start, its figure at the end; the optional detail sits under the figure. */
@Composable
private fun FigureRow(label: String, value: String, detail: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun Double?.oneDecimal(): String = this?.let { "%.1f".format(it) } ?: "–"

private const val SEPARATOR = " · "
private const val DAY_COLUMN_WEIGHT = 2.2f
private const val MIN_BAR_FRACTION = 0.04f
private val CHART_HEIGHT = 96.dp

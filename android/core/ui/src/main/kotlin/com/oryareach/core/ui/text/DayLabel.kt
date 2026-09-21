package com.oryareach.core.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.oryareach.core.ui.R
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/**
 * How a calendar day is named in a log.
 *
 * Shared because the feeding and pumping logs both group by day, and a date is one of the few
 * things worth reading identically in both. It lives in `:core:ui` rather than `:core:domain`
 * because the weekday names are translated resources, not arithmetic.
 *
 * Today and yesterday are named rather than dated: those two are most of what anyone reads, and
 * "yesterday" places a row faster than a number does at four in the morning. Anything older gets
 * its weekday and a short date — still the weekday first, for the same reason.
 */
@Composable
fun dayLabel(date: LocalDate, today: LocalDate): String {
    val daysAgo = today.toEpochDays() - date.toEpochDays()
    return when (daysAgo) {
        0L -> stringResource(R.string.day_label_today)
        1L -> stringResource(R.string.day_label_yesterday)
        else -> stringResource(
            R.string.day_label_value,
            stringArrayResource(R.array.day_label_weekdays)[date.dayOfWeek.isoDayNumber - 1],
            date.day,
            // `Month` here is an enum, and `monthNumber` is deprecated: January is ordinal 0.
            date.month.ordinal + 1,
        )
    }
}

/**
 * How a month is named above a calendar grid.
 *
 * Both the cycle and calendar grids were printing `visibleMonth.toString()`, which on a
 * `LocalDate` is its ISO form — the header over September read "2026-09-01", a full date where
 * a month belongs, with a day number that meant nothing.
 *
 * Here beside [dayLabel] for the same reason: the month names are translated resources rather
 * than arithmetic, and the two grids should say it the same way.
 */
@Composable
fun monthLabel(date: LocalDate): String = stringResource(
    R.string.month_label_value,
    // `Month` is an enum here, and `monthNumber` is deprecated: January is ordinal 0.
    stringArrayResource(R.array.month_label_names)[date.month.ordinal],
    date.year,
)

/**
 * A single date, written out: "16 September 2026".
 *
 * Several screens were calling `toString()` on a `LocalDate`, which gives its ISO form, so a
 * birth announcement read "Born on 2026-09-16" and a period history read the same way. A date
 * someone reads should not look like a database column.
 *
 * Spelled with the month's name rather than as numbers on purpose: "16.9" and "9.16" are the
 * same six characters and different days depending on where you grew up, and this app is read
 * in two languages.
 *
 * Distinct from [dayLabel], which is for a log's day headers and answers "which day am I
 * looking at" in relative terms. This one is for a date stated once, on its own.
 */
@Composable
fun dateLabel(date: LocalDate): String = stringResource(
    R.string.date_label_value,
    date.day,
    stringArrayResource(R.array.month_label_names)[date.month.ordinal],
    date.year,
)

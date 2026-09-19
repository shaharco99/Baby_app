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

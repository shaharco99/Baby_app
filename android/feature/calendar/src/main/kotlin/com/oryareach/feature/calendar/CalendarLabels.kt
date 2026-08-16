package com.oryareach.feature.calendar

import androidx.annotation.StringRes

@StringRes
internal fun CalendarEventKind.labelRes(): Int = when (this) {
    CalendarEventKind.TASK_DUE -> R.string.calendar_legend_task
    CalendarEventKind.IMPORTANT_DATE -> R.string.calendar_legend_important_date
    CalendarEventKind.PERIOD_ACTUAL -> R.string.calendar_legend_period
    CalendarEventKind.PERIOD_PREDICTED -> R.string.calendar_period_predicted
    CalendarEventKind.GOOGLE_EVENT -> R.string.calendar_legend_google
}

package com.oryareach.core.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.oryareach.core.ui.R
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Shown when a feed, a diaper change or a pump is given a time that has not happened yet. Those
 * logs record the past: a future time would pin "last feed" and its countdown ahead of the real
 * ones. Shared so the three screens say the same thing in the same words.
 */
@Composable
fun FutureTimeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.future_time_title)) },
        text = { Text(stringResource(R.string.future_time_message)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.future_time_ok)) } },
    )
}

/**
 * Date-picker rule for logs of things that already happened: no day after today. `DatePicker`
 * reads and writes UTC midnights, so today's local date is taken through UTC unshifted.
 */
@OptIn(ExperimentalMaterial3Api::class)
fun notAfterToday(zone: TimeZone = TimeZone.currentSystemDefault()): SelectableDates {
    val todayUtcMillis = Clock.System.todayIn(zone).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    return object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtcMillis
    }
}

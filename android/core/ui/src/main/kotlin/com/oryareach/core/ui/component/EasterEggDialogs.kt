package com.oryareach.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oryareach.core.domain.feeding.FeedingTally
import com.oryareach.core.domain.pumping.MilkStash
import com.oryareach.core.ui.R
import com.oryareach.core.ui.text.dateLabel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

// The two log easter eggs, shared: each opens from its own screen's countdown and from the same
// countdown on Home, and features can't share composables any other way.

/**
 * The night-watch easter egg: what the small hours actually came to. Warm rather than clinical
 * — the numbers are real, the framing is a medal for whoever was awake.
 */
@Composable
fun NightWatchDialog(tally: FeedingTally, mine: Int?, theirs: Int?, onDismiss: () -> Unit) {
    val lines = stringArrayResource(R.array.feeding_night_watch_praise)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feeding_night_watch_close)) } },
        title = { Text(stringResource(R.string.feeding_night_watch_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.feeding_night_watch_count, tally.nightFeeds),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.feeding_night_watch_total, tally.totalFeeds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                tally.totalMl?.let { ml ->
                    Text(
                        // Past a litre the number stops meaning anything as millilitres. It is
                        // the same figure, said in a unit a person can picture.
                        text = if (ml >= MILLILITRES_PER_LITRE) {
                            stringResource(R.string.feeding_night_watch_litres, ml.toLitres())
                        } else {
                            stringResource(R.string.feeding_night_watch_ml, ml)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                tally.firstFeedEpochMillis?.let { first ->
                    Text(
                        text = stringResource(
                            R.string.feeding_night_watch_since,
                            dateLabel(Instant.fromEpochMilliseconds(first).toLocalDateTime(TimeZone.currentSystemDefault()).date),
                            tally.daysLogged,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (mine != null && theirs != null) {
                    Text(
                        text = stringResource(R.string.feeding_night_watch_split, mine, theirs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                tally.milestone?.let { milestone ->
                    Text(
                        text = stringResource(R.string.feeding_night_watch_milestone, milestone),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                // Keyed to the tally, so the line changes as the log grows rather than on
                // every recomposition — a message that reshuffles mid-read is just noise.
                Text(
                    text = lines[tally.nightFeeds % lines.size],
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
    )
}

/**
 * The stash: what the pumping has added up to. Warm rather than clinical, on the same principle as
 * the feeding log's night watch — the numbers are real, the framing is for whoever did the work.
 */
@Composable
fun MilkStashDialog(stash: MilkStash, onDismiss: () -> Unit) {
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

/** One decimal place: "1.5 litres" is a quantity, "1.487 litres" is a reading. */
private fun Int.toLitres(): String = "%.1f".format(this / MILLILITRES_PER_LITRE.toFloat())

/** Units from resources: "16h 56m" was English letters sitting inside the Hebrew line. */
@Composable
private fun Int.toHoursAndMinutes(): String =
    if (this < MINUTES_PER_HOUR) {
        stringResource(R.string.duration_minutes, this)
    } else {
        stringResource(R.string.duration_hours_minutes, this / MINUTES_PER_HOUR, this % MINUTES_PER_HOUR)
    }

private const val MILLILITRES_PER_LITRE = 1000
private const val MINUTES_PER_HOUR = 60

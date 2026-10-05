package com.oryareach.wear

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.oryareach.core.watch.RunningTimer
import com.oryareach.core.watch.WatchTimers
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

private const val MILLIS_PER_SECOND = 1_000L
private const val LEFT_TO_RIGHT_ISOLATE = '\u2066'
private const val POP_DIRECTIONAL_ISOLATE = '\u2069'

@Composable
internal fun TimersApp() {
    val context = LocalContext.current
    val flow = remember(context) { watchTimers(context.applicationContext) }
    val timers by flow.collectAsStateWithLifecycle(initialValue = null)
    val now = rememberNow()

    MaterialTheme {
        AppScaffold {
            val listState = rememberTransformingLazyColumnState()
            ScreenScaffold(scrollState = listState) { contentPadding ->
                TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
                    val current = timers
                    if (current == null || current.isEmpty) {
                        item { Waiting() }
                    } else {
                        item {
                            TimerCard(
                                title = R.string.feeding,
                                running = current.nursing,
                                runningLabel = R.string.nursing_now,
                                lastAt = current.lastFedAt,
                                dueAt = current.feedDueAt,
                                now = now,
                            )
                        }
                        item {
                            TimerCard(
                                title = R.string.pumping,
                                running = current.pumping,
                                runningLabel = R.string.pumping_now,
                                lastAt = current.lastPumpAt,
                                dueAt = current.pumpDueAt,
                                now = now,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Wall-clock millis, re-read on every whole second while the screen is shown. */
@Composable
private fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MILLIS_PER_SECOND - now % MILLIS_PER_SECOND)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/**
 * One clock. A timer running on the phone wins — it is what someone is doing right now; else the
 * countdown to the next one, turning into "overdue by" once it passes, the way the phone's widget
 * does.
 */
@Composable
private fun TimerCard(
    @StringRes title: Int,
    running: RunningTimer?,
    @StringRes runningLabel: Int,
    lastAt: Long?,
    dueAt: Long?,
    now: Long,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, MaterialTheme.shapes.large)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = colors.primary,
            modifier = Modifier.semantics { heading() },
        )

        when {
            running != null -> {
                Caption(stringResource(if (running.isPaused) R.string.paused else runningLabel))
                Clock(running.elapsedMillisAt(now), colors.onSurface)
            }
            dueAt != null -> {
                val overdue = dueAt <= now
                Caption(stringResource(if (overdue) R.string.overdue_by else R.string.next_in))
                Clock(if (overdue) now - dueAt else dueAt - now, if (overdue) colors.error else colors.onSurface)
            }
            else -> Caption(stringResource(R.string.nothing_yet))
        }

        if (running == null && lastAt != null) {
            Caption(stringResource(R.string.last_at, ltr(clockTime(lastAt))))
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Clock(millis: Long, color: Color) {
    Text(text = ltr(duration(millis)), style = MaterialTheme.typography.numeralSmall, color = color)
}

@Composable
private fun Waiting() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.waiting_title),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
        Caption(stringResource(R.string.waiting_body))
    }
}

/** h:mm:ss, or m:ss under an hour. */
private fun duration(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0) / MILLIS_PER_SECOND
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private fun clockTime(epochMillis: Long): String {
    val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    return "%02d:%02d".format(time.hour, time.minute)
}

/** Keeps a clock reading left-to-right inside a Hebrew line. */
private fun ltr(text: String): String = buildString {
    append(LEFT_TO_RIGHT_ISOLATE)
    append(text)
    append(POP_DIRECTIONAL_ISOLATE)
}

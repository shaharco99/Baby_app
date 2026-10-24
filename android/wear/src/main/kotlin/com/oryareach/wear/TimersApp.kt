package com.oryareach.wear

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.oryareach.core.watch.CommandResult
import com.oryareach.core.watch.NursingSide
import com.oryareach.core.watch.RunningTimer
import com.oryareach.core.watch.WatchCommand
import com.oryareach.core.watch.WatchTimers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
internal fun TimersApp() {
    val context = LocalContext.current
    val flow = remember(context) { watchTimers(context.applicationContext) }
    val timers by flow.collectAsStateWithLifecycle(initialValue = null)
    val now = rememberNow()
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<Status?>(null) }
    // One command in flight at a time: a second tap while the first is still out is the same intent.
    val send: (WatchCommand) -> Unit = { command ->
        if (status != Status.Sending) {
            status = Status.Sending
            scope.launch {
                status = sendToPhone(context.applicationContext, command).toStatus()
                delay(STATUS_SHOWN_MILLIS)
                status = null
            }
        }
    }

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
                        item { FeedActions(nursing = current.nursing, status = status, send = send) }
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

private const val STATUS_SHOWN_MILLIS = 3_000L

/** What the last command did, shown under the buttons for a moment. */
private enum class Status(@StringRes val label: Int) {
    Sending(R.string.status_sending),
    Done(R.string.status_done),
    NoChild(R.string.status_no_child),
    NotRunning(R.string.status_not_running),
    Failed(R.string.status_failed),
    Unreachable(R.string.status_unreachable),
}

private fun CommandResult?.toStatus(): Status = when (this) {
    CommandResult.DONE -> Status.Done
    CommandResult.NOTHING_TO_LOG -> Status.NoChild
    CommandResult.NOT_RUNNING -> Status.NotRunning
    CommandResult.FAILED -> Status.Failed
    null -> Status.Unreachable
}

/**
 * Quick-log under the feeding clock. While a breastfeed runs the buttons are its controls
 * (pause or resume, stop); otherwise they log a feed or start one on a side.
 */
@Composable
private fun FeedActions(nursing: RunningTimer?, status: Status?, send: (WatchCommand) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (nursing != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val toggle = if (nursing.isPaused) WatchCommand.ResumeNursing else WatchCommand.PauseNursing
                FilledTonalButton(
                    onClick = { send(toggle) },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(if (nursing.isPaused) R.string.resume else R.string.pause)) },
                )
                Button(
                    onClick = { send(WatchCommand.StopNursing) },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.stop)) },
                )
            }
        } else {
            Button(
                onClick = { send(WatchCommand.LogFeed) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.log_feed)) },
            )
            Caption(stringResource(R.string.breastfeed))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = { send(WatchCommand.StartNursing(NursingSide.LEFT)) },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.side_left)) },
                )
                FilledTonalButton(
                    onClick = { send(WatchCommand.StartNursing(NursingSide.RIGHT)) },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.side_right)) },
                )
            }
            FilledTonalButton(
                onClick = { send(WatchCommand.StartNursing(NursingSide.BOTH)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.side_both)) },
            )
        }
        status?.let { Caption(stringResource(it.label)) }
    }
}

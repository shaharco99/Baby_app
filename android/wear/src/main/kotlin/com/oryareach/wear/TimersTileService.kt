package com.oryareach.wear

import androidx.annotation.StringRes
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.TypeBuilders.StringLayoutConstraint
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicDuration
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInt32.IntFormatter
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.layout.spacer
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.protolayout.types.LayoutString
import androidx.wear.protolayout.types.layoutString
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.tile
import androidx.wear.tiles.timeInterval
import androidx.wear.tiles.timeline
import androidx.wear.tiles.timelineEntry
import com.oryareach.core.watch.RunningTimer
import com.oryareach.core.watch.WatchTimers
import com.oryareach.core.watch.layoutChangesAfter
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

/**
 * The swipe-over tile: both clocks at a glance, without opening the app.
 *
 * The numbers tick on the watch itself — they are ProtoLayout time expressions against the
 * platform clock, so the tile is not re-requested every second. It is re-requested when the phone
 * sends new times ([TimersListenerService]); in between, a timeline swaps in a new layout at each
 * moment a due time passes, where "next in" becomes a red "overdue by".
 */
class TimersTileService : Material3TileService() {

    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val timers = storedTimers(applicationContext)
        if (timers == null || timers.isEmpty) return tile(timeline(timelineEntry(waiting())))

        val now = System.currentTimeMillis()
        val starts = listOf(now) + timers.layoutChangesAfter(now)
        // The last entry has no end: it is the default, and the shorter windows before it win
        // while they are current.
        val entries = starts.mapIndexed { index, start ->
            val end = starts.getOrNull(index + 1)
            timelineEntry(
                layout = clocks(timers, at = start),
                validity = end?.let { timeInterval(start.milliseconds, it.milliseconds) },
            )
        }
        return tile(timeline(*entries.toTypedArray()))
    }

    private fun MaterialScope.clocks(timers: WatchTimers, at: Long): LayoutElement = primaryLayout(
        onClick = clickable(action = openApp(), id = CLICK_OPEN_APP),
        mainSlot = {
            column(
                side(R.string.feeding, timers.nursing, R.string.nursing_now, timers.feedDueAt, at),
                spacer(height = dp(SPACE_BETWEEN_DP)),
                side(R.string.pumping, timers.pumping, R.string.pumping_now, timers.pumpDueAt, at),
            )
        },
    )

    /** Same precedence as the app's card: a running timer, else the countdown, else nothing. */
    private fun MaterialScope.side(
        @StringRes title: Int,
        running: RunningTimer?,
        @StringRes runningLabel: Int,
        dueAt: Long?,
        at: Long,
    ): LayoutElement {
        val state: Int
        val clock: LayoutString?
        val color: LayoutColor
        when {
            running != null -> {
                state = if (running.isPaused) R.string.paused else runningLabel
                clock = if (running.isPaused) {
                    duration(running.elapsedMillisAt(at)).layoutString
                } else {
                    ticking(duration(running.elapsedMillisAt(at)), since(running.startedAt + running.pausedMillis))
                }
                color = colorScheme.onSurface
            }
            dueAt != null && dueAt <= at -> {
                state = R.string.overdue_by
                clock = ticking(duration(at - dueAt), since(dueAt))
                color = colorScheme.error
            }
            dueAt != null -> {
                state = R.string.next_in
                clock = ticking(duration(dueAt - at), until(dueAt))
                color = colorScheme.onSurface
            }
            else -> {
                state = R.string.nothing_yet
                clock = null
                color = colorScheme.onSurface
            }
        }

        val label = text(
            getString(R.string.tile_line, getString(title), getString(state)).layoutString,
            typography = Typography.LABEL_SMALL,
            color = colorScheme.onSurfaceVariant,
        )
        return if (clock == null) {
            label
        } else {
            column(label, text(clock, typography = Typography.NUMERAL_SMALL, color = color))
        }
    }

    private fun MaterialScope.waiting(): LayoutElement = primaryLayout(
        onClick = clickable(action = openApp(), id = CLICK_OPEN_APP),
        mainSlot = {
            text(getString(R.string.waiting_title).layoutString, typography = Typography.TITLE_SMALL, maxLines = 2)
        },
    )

    private fun openApp(): ActionBuilders.LaunchAction = ActionBuilders.LaunchAction.Builder()
        .setAndroidActivity(
            ActionBuilders.AndroidActivity.Builder()
                .setPackageName(packageName)
                .setClassName(TimersActivity::class.java.name)
                .build(),
        )
        .build()

    private companion object {
        const val CLICK_OPEN_APP = "open-app"
        const val SPACE_BETWEEN_DP = 6f

        /** Widest the clock gets, so the layout pass can size the text before it ticks. */
        val CLOCK_WIDTH: StringLayoutConstraint = StringLayoutConstraint.Builder("00:00:00").build()

        val TWO_DIGITS: IntFormatter = IntFormatter.Builder().setMinIntegerDigits(2).build()

        fun instant(epochMillis: Long): DynamicInstant =
            DynamicInstant.withSecondsPrecision(Instant.ofEpochMilli(epochMillis))

        fun since(epochMillis: Long): DynamicDuration =
            instant(epochMillis).durationUntil(DynamicInstant.platformTimeWithSecondsPrecision())

        fun until(epochMillis: Long): DynamicDuration =
            DynamicInstant.platformTimeWithSecondsPrecision().durationUntil(instant(epochMillis))

        /**
         * h:mm:ss, or m:ss under an hour — the app's format, built on the watch's own clock.
         * [fallback] is what a renderer that cannot evaluate it shows instead.
         */
        fun ticking(fallback: String, duration: DynamicDuration): LayoutString {
            val seconds = duration.secondsPart.format(TWO_DIGITS)
            val colon = DynamicString.constant(":")
            val long = duration.toIntHours().format().concat(colon)
                .concat(duration.minutesPart.format(TWO_DIGITS)).concat(colon).concat(seconds)
            val short = duration.toIntMinutes().format().concat(colon).concat(seconds)
            // A countdown can run a little past zero before the timeline flips to "overdue".
            val clock = DynamicString.onCondition(duration.toIntSeconds().lt(0)).use("0:00")
                .elseUse(DynamicString.onCondition(duration.toIntHours().gt(0)).use(long).elseUse(short))
            return LayoutString(fallback, clock, CLOCK_WIDTH)
        }
    }
}

package com.oryareach.wear

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.CountUpTimeReference
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.data.TimeRange
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import java.time.Instant
import java.util.concurrent.TimeUnit

private const val PREVIEW_MINUTES = 95L

/**
 * The watch-face complication: how long until the next feed.
 *
 * Like the tile, it ticks on the watch — a [TimeDifferenceComplicationText] counts against the
 * platform clock, so nothing re-requests it every minute. It is re-requested when the phone
 * sends new times ([TimersListenerService]) and when its valid range ends, which is the moment
 * the feed falls due: the countdown is then replaced by a count-up marked "Late".
 */
class FeedComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, dueAt = System.currentTimeMillis() + PREVIEW_MINUTES * MILLIS_PER_MINUTE, now = System.currentTimeMillis())

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val dueAt = storedTimers(applicationContext)?.feedDueAt
            ?: return NoDataComplicationData()
        return build(request.complicationType, dueAt, System.currentTimeMillis())
    }

    private fun build(type: ComplicationType, dueAt: Long, now: Long): ComplicationData? {
        val overdue = dueAt <= now
        val dueInstant = Instant.ofEpochMilli(dueAt)
        val builder = if (overdue) {
            TimeDifferenceComplicationText.Builder(TimeDifferenceStyle.SHORT_DUAL_UNIT, CountUpTimeReference(dueInstant))
        } else {
            TimeDifferenceComplicationText.Builder(TimeDifferenceStyle.SHORT_DUAL_UNIT, CountDownTimeReference(dueInstant))
        }
        val diff = builder.setMinimumTimeUnit(TimeUnit.MINUTES).build()
        val label = PlainComplicationText.Builder(getString(R.string.complication_label)).build()
        val title: ComplicationText = PlainComplicationText.Builder(
            getString(if (overdue) R.string.complication_late else R.string.complication_title),
        ).build()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, TimersActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // While counting down, the data is only right until the feed falls due.
        val range = if (overdue) TimeRange.ALWAYS else TimeRange.between(Instant.ofEpochMilli(now), Instant.ofEpochMilli(dueAt))

        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(diff, label)
                .setTitle(title)
                .setTapAction(open)
                .setValidTimeRange(range)
                .build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(diff, label)
                .setTitle(title)
                .setTapAction(open)
                .setValidTimeRange(range)
                .build()
            else -> null
        }
    }
}

private const val MILLIS_PER_MINUTE = 60_000L

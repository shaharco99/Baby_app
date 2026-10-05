package com.oryareach.core.watch

import com.google.android.gms.wearable.DataMap

/**
 * What the watch shows: the feed and pump clocks, as bare timestamps.
 *
 * The same rule as the home-screen widget — times only. No child, no amount, no note, nothing
 * read out of a record's content. The Data Layer carries this off the phone (over Bluetooth, or
 * Google's relay when the watch is on Wi-Fi), so it must never carry anything the workspace key
 * protects.
 *
 * Everything is optional: a field that is null was simply not there to send — no feed yet, no
 * child born yet, no pump running.
 */
data class WatchTimers(
    /** When the last feed began. */
    val lastFedAt: Long? = null,
    /** When the next feed is due; null before the birth, as the feed reminder is. */
    val feedDueAt: Long? = null,
    /** A breastfeed whose timer is still going. */
    val nursing: RunningTimer? = null,
    /** When the last pump session began. */
    val lastPumpAt: Long? = null,
    val pumpDueAt: Long? = null,
    /** A pump session whose timer is still going. */
    val pumping: RunningTimer? = null,
) {
    val isEmpty: Boolean get() = this == WatchTimers()

    fun toDataMap(): DataMap = DataMap().apply { toLongs().forEach { (key, value) -> putLong(key, value) } }

    /** The flat form [toDataMap] writes; split out so the round trip is testable on the JVM. */
    internal fun toLongs(): Map<String, Long> = buildMap {
        lastFedAt?.let { put(KEY_LAST_FED_AT, it) }
        feedDueAt?.let { put(KEY_FEED_DUE_AT, it) }
        nursing?.let { it.putInto(this, KEY_NURSING) }
        lastPumpAt?.let { put(KEY_LAST_PUMP_AT, it) }
        pumpDueAt?.let { put(KEY_PUMP_DUE_AT, it) }
        pumping?.let { it.putInto(this, KEY_PUMPING) }
    }

    companion object {
        /** The one Data Layer path the phone writes and the watch listens on. */
        const val PATH = "/timers"

        private const val KEY_LAST_FED_AT = "last-fed-at"
        private const val KEY_FEED_DUE_AT = "feed-due-at"
        private const val KEY_NURSING = "nursing"
        private const val KEY_LAST_PUMP_AT = "last-pump-at"
        private const val KEY_PUMP_DUE_AT = "pump-due-at"
        private const val KEY_PUMPING = "pumping"

        fun fromDataMap(map: DataMap): WatchTimers =
            fromLongs(map.keySet().associateWith { map.getLong(it) })

        internal fun fromLongs(values: Map<String, Long>): WatchTimers = WatchTimers(
            lastFedAt = values[KEY_LAST_FED_AT],
            feedDueAt = values[KEY_FEED_DUE_AT],
            nursing = RunningTimer.readFrom(values, KEY_NURSING),
            lastPumpAt = values[KEY_LAST_PUMP_AT],
            pumpDueAt = values[KEY_PUMP_DUE_AT],
            pumping = RunningTimer.readFrom(values, KEY_PUMPING),
        )
    }
}

/**
 * A timer still running on the phone, in the same three numbers the phone keeps it in
 * (`PumpSession` / a breastfeed's `FeedingEntry`): the watch re-derives the elapsed time itself
 * and ticks it every second, so nothing has to be sent while it simply counts.
 */
data class RunningTimer(
    val startedAt: Long,
    /** Paused time already closed. */
    val pausedMillis: Long = 0,
    /** When the current pause began; null unless paused right now. */
    val pausedAt: Long? = null,
) {
    val isPaused: Boolean get() = pausedAt != null

    /** Mirrors `PumpSession.elapsedMillisAt`: frozen while paused, never negative. */
    fun elapsedMillisAt(now: Long): Long = ((pausedAt ?: now) - startedAt - pausedMillis).coerceAtLeast(0)

    internal fun putInto(values: MutableMap<String, Long>, prefix: String) {
        values["$prefix-started-at"] = startedAt
        values["$prefix-paused-millis"] = pausedMillis
        pausedAt?.let { values["$prefix-paused-at"] = it }
    }

    internal companion object {
        fun readFrom(values: Map<String, Long>, prefix: String): RunningTimer? {
            val startedAt = values["$prefix-started-at"] ?: return null
            return RunningTimer(
                startedAt = startedAt,
                pausedMillis = values["$prefix-paused-millis"] ?: 0,
                pausedAt = values["$prefix-paused-at"],
            )
        }
    }
}

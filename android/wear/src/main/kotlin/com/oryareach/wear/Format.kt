package com.oryareach.wear

import java.time.Instant
import java.time.ZoneId

internal const val MILLIS_PER_SECOND = 1_000L
private const val LEFT_TO_RIGHT_ISOLATE = '\u2066'
private const val POP_DIRECTIONAL_ISOLATE = '\u2069'

/** h:mm:ss, or m:ss under an hour. */
internal fun duration(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0) / MILLIS_PER_SECOND
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

internal fun clockTime(epochMillis: Long): String {
    val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    return "%02d:%02d".format(time.hour, time.minute)
}

/** Keeps a clock reading left-to-right inside a Hebrew line. */
internal fun ltr(text: String): String = buildString {
    append(LEFT_TO_RIGHT_ISOLATE)
    append(text)
    append(POP_DIRECTIONAL_ISOLATE)
}

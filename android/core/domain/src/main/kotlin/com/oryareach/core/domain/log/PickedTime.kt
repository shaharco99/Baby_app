package com.oryareach.core.domain.log

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Rebuilds [currentEpochMillis] with one half replaced — the date and time pickers each hand back
 * only their own half, and the other half has to survive.
 */
fun resolvePickedTime(
    currentEpochMillis: Long,
    zone: TimeZone,
    date: LocalDate? = null,
    time: LocalTime? = null,
): Long {
    val current = Instant.fromEpochMilliseconds(currentEpochMillis).toLocalDateTime(zone)
    return LocalDateTime(date ?: current.date, time ?: current.time).toInstant(zone).toEpochMilliseconds()
}

/**
 * A log entry records something that already happened. A moment later than now cannot be logged:
 * a feed stamped three hours ahead pinned the "last feed" and the countdown to it until the clock
 * caught up, so every real feed logged meanwhile looked like it had not been saved.
 *
 * The screens refuse such a time with a message; the repositories clamp as a last line of defence
 * ([clampToNow]) for any write path that never went through a picker.
 */
fun isInFuture(epochMillis: Long, nowEpochMillis: Long): Boolean = epochMillis > nowEpochMillis

/** A moment a person logs can be in the past or now, never later than now. */
fun clampToNow(epochMillis: Long, nowEpochMillis: Long): Long = minOf(epochMillis, nowEpochMillis)

package com.oryareach.core.watch

/**
 * When a picture of [WatchTimers] stops being right on its own.
 *
 * A clock that counts — a running timer, a countdown, an overdue count — ticks by itself on the
 * watch. What does not change by itself is *which* clock to show: "next in" has to become
 * "overdue by" (and turn red) the moment a due time passes. Those moments, after [now], in order,
 * are the only places a tile needs a new layout; a running timer hides its side's due time, so it
 * adds none.
 */
fun WatchTimers.layoutChangesAfter(now: Long): List<Long> =
    listOfNotNull(
        feedDueAt.takeIf { nursing == null },
        pumpDueAt.takeIf { pumping == null },
    ).filter { it > now }.distinct().sorted()

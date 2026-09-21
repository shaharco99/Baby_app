package com.oryareach.core.domain.baby

import kotlinx.datetime.LocalDate

/**
 * How many whole days old the child is on [on].
 *
 * Counted from the birth date in whole calendar days, not from the birth *time*: the things
 * that read this — how much a feed should be, which week of life the log is showing — step on
 * the calendar day, and nobody wants the recommended amount to change at 03:41 because that is
 * when the birth happened to be recorded.
 *
 * The birth day itself is day 0, matching how the first-week feeding table is written.
 * Negative before birth, which the callers use as "there is nothing to say yet" rather than
 * clamping it away here.
 */
fun ageInDays(birthDate: LocalDate, on: LocalDate): Int =
    (on.toEpochDays() - birthDate.toEpochDays()).toInt()

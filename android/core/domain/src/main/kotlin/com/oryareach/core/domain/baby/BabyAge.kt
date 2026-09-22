package com.oryareach.core.domain.baby

import kotlinx.datetime.LocalDate
import kotlinx.datetime.periodUntil

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

/**
 * How old the child is, said four ways at once.
 *
 * The same span read differently depending on who is asking: [totalDays] is what a midwife wants
 * in the first fortnight, [weeks]/[daysInWeek] is how the first few months are actually spoken
 * about, and [years]/[months]/[days] is the calendar breakdown that takes over after that.
 *
 * The calendar part counts *calendar* months, not thirty-day blocks, so a baby born on the 16th
 * turns one month old on the 16th whatever month it is.
 */
data class BabyAge(
    val totalDays: Int,
    val weeks: Int,
    val daysInWeek: Int,
    val years: Int,
    val months: Int,
    val days: Int,
)

/**
 * [BabyAge] on [on], or null before the child was born — the callers have nothing to say yet in
 * that case, and a negative age rendered as "-3 days old" is worse than an absent line.
 */
fun babyAge(birthDate: LocalDate, on: LocalDate): BabyAge? {
    val totalDays = ageInDays(birthDate, on)
    if (totalDays < 0) return null

    val period = birthDate.periodUntil(on)
    return BabyAge(
        totalDays = totalDays,
        weeks = totalDays / 7,
        daysInWeek = totalDays % 7,
        years = period.years,
        months = period.months,
        days = period.days,
    )
}

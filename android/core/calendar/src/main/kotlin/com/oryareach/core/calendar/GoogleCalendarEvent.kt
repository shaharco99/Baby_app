package com.oryareach.core.calendar

import kotlinx.datetime.LocalDateTime

/**
 * Free/busy-level view of a Google Calendar event: date + title only, per phase 1's decision
 * (docs/specs/03-google-calendar-integration.md) not to fetch or store description, location,
 * or attendees.
 */
data class GoogleCalendarEvent(
    val id: String,
    val calendarId: String,
    val title: String,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val allDay: Boolean,
)

/** One entry from the user's `calendarList.list` — lets them choose which calendar(s) to show,
 * rather than only ever showing the primary calendar. */
data class GoogleCalendarListEntry(
    val id: String,
    val summary: String,
    val primary: Boolean,
)

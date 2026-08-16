package com.oryareach.core.calendar

import kotlinx.datetime.LocalDate

/** Supplies the bearer token `:core:calendar` needs to call the Google Calendar API. Defined
 * here (the consumer) rather than in `:core:security` (the implementer) so `:app` wires the two
 * together with a lambda — the same cross-module seam `WorkspaceKeyProvider` uses, see
 * `docs/architecture/001-android-architecture.md`. */
fun interface GoogleAccessTokenProvider {
    suspend fun currentAccessToken(): String?
}

/** Read-only access to a signed-in Google account's calendars. One-way (Google → app) only —
 * phase 1 has no writeback, see the spec. */
interface CalendarEventSource {

    /** The user's calendar list (`calendarList.list`), so they can pick which calendar(s) to
     * show rather than being limited to the primary calendar. */
    suspend fun fetchCalendarList(): Result<List<GoogleCalendarListEntry>>

    /** Events across [calendarIds] within [from]..[to] (inclusive), date + title only — the
     * `events.list` call's `fields` param is restricted accordingly so nothing beyond that
     * ever leaves Google's servers into this app. */
    suspend fun fetchEvents(
        calendarIds: List<String>,
        from: LocalDate,
        to: LocalDate,
    ): Result<List<GoogleCalendarEvent>>
}

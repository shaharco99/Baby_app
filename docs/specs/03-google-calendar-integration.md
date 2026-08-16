# Google Calendar Integration — Spec

Status: **phase 1 implemented, not yet functional.** All the code below is
written and merged (`:core:calendar`, `:core:security`'s
`GoogleCalendarAuthManager`/`GoogleCalendarTokenStore`, the Room cache, the
Calendar-screen picker UI), and along the way "Sign in with Google" was also
added to the app's login screen (`:feature:auth`), reusing the same
Credential Manager approach. **Neither works yet** — both are blocked on the
same missing prerequisite:

- No Google Cloud OAuth client exists yet. Someone with access to the Google
  Cloud project needs to create an OAuth 2.0 client (Android type, package
  `com.oryareach.app`, debug + release SHA-1 fingerprints attached) and a
  Web-application-type client (its client ID goes in
  `android/local.properties` as `googleCalendarOauthClientId` for Calendar
  and `googleWebClientId` for login — one client can cover both).
- For "Sign in with Google" specifically, the Web client ID also needs to be
  configured as the Google provider in the Supabase dashboard
  (Authentication → Providers → Google) — Supabase verifies the ID token
  server-side against whatever's configured there.
- Until this exists, both features fail fast with a clear in-app error
  instead of hanging or crashing — see the `TODO(app owner)` comments in
  `core/security/build.gradle.kts`.

The open questions section below is now resolved (see decisions inline);
left in place as a record of what was decided and why.

## Goal

Replace `:feature:dates` (Important Dates page) with Google Calendar shown
inside `:feature:calendar`'s Calendar page. One calendar surface instead of
two.

## Scope for phase 1

- Remove Dates page/tab entirely. `:feature:dates` module deleted, its nav
  entry removed from `SaharApp.kt` bottom-tab switch, its DI module removed
  from `AppModule.kt`.
- Calendar page (`:feature:calendar`) shows events pulled from user's Google
  Calendar (read-only view).
- Adding new event in-app (Calendar page's existing add-event flow) does
  **not** push to Google Calendar. Local-only, same as today. One-way sync
  (Google → app) only, phase 1.
- No writeback, no two-way sync, no conflict resolution — out of scope until
  phase 2 (explicitly not started, not planned in detail here).

## Why one-way only for now

Two-way sync = auth token refresh, conflict resolution, offline queue,
partial-failure handling — same shape of complexity `:core:sync` already
solves for Supabase, but Google's API has different rate limits/quota rules
and its own auth flow. Read-only first de-risks: get auth + display working,
push writes later once the pattern is proven.

## Open questions — resolved

- **Auth**: Credential Manager / Sign-In-with-Google (not plain OAuth2
  device flow, not the deprecated `GoogleSignInClient`) — implemented in
  `GoogleCalendarAuthManager`.
- **Scope**: `calendar.readonly` only.
- **Which calendar(s)**: user picks from their calendar list via
  `calendarList.list`, not just primary — persisted in
  `SettingsPreferences.selectedGoogleCalendarIds`.
- **Token storage**: `:core:security`'s `GoogleCalendarTokenStore`, same
  Keystore-sealed pattern as `DeviceIdentity` — device-local, never synced
  to Supabase.
- **Offline behavior**: fetched events cache into a new local-only Room
  table (`cached_calendar_events`, migration 13→14, deliberately outside
  `RoomSyncStore`/`EntityType`). Calendar page keeps reading from Room on
  render; a `refresh()` call (wired to pull-to-refresh) hits the network on
  demand.
- **Free/busy vs. full event data**: date + title + all-day only — no
  description/location/attendees. `events.list` calls use
  `fields=items(id,summary,start,end)`.

## Suggested module shape (draft, not final)

- New `:core:calendar` — Google auth client, event-fetch API, local cache
  entity + DAO (own Room table, not part of synced `RoomSyncStore` set).
  Pure-logic pieces (date range windowing, event-to-UI mapping) unit
  tested per repo convention (`:core:domain` pattern).
- `:feature:calendar` — consumes `:core:calendar`, renders fetched events
  alongside locally-added ones in existing calendar view.
- `:feature:dates` — **not yet deleted.** An earlier pass built this
  deletion (module removed, nav/DI wiring stripped) but it was never
  committed, and may not still exist anywhere. Dates and Calendar currently
  coexist; removing Dates is a separate, deliberate follow-up (data-model
  implications) not bundled into this pass.

## Explicitly out of scope for phase 1 (do not build)

- Writing app-created events to Google Calendar.
- Two-way sync / conflict resolution.
- Multiple Google account support.
- Push/webhook-based live updates (poll-on-open is enough for phase 1).

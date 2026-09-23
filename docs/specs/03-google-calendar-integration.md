# Google Calendar Integration — Spec

Status: **phase 1 built, merged, confirmed working on both phones.** Kept as own file because several code comments cite its resolved decisions by name; condensed account of what shipped in `docs/TASK-HISTORY.md`.

"Sign in with Google" and Google Calendar connect share one OAuth client (client ID in `android/local.properties` as `googleCalendarOauthClientId`, wired in CI via `GOOGLE_WEB_CLIENT_ID` secret). Both fail fast with clear in-app error if blank — see doc comment in `core/security/build.gradle.kts`.

Open-questions section below resolved; stays as record of what decided and why.

## Goal

Replace `:feature:dates` (Important Dates page) with Google Calendar shown inside `:feature:calendar`'s Calendar page. One calendar surface, not two.

## Scope for phase 1

- Remove Dates page/tab entirely. `:feature:dates` module deleted, nav entry removed from `TakesTwoApp.kt` bottom-tab switch, DI module removed from `AppModule.kt`.
- Calendar page (`:feature:calendar`) shows events from user's Google Calendar (read-only).
- Adding event in-app (Calendar page's existing add-event flow) does **not** push to Google Calendar. Local-only, same as before. One-way sync (Google to app) only, phase 1.
- No writeback, no two-way sync, no conflict resolution — out of scope until phase 2 (not started, not planned in detail here).

## Why one-way only for now

Two-way sync = auth token refresh, conflict resolution, offline queue, partial-failure handling. Same complexity shape `:core:sync` already solves for Supabase, but Google API has different rate limits/quota rules and own auth flow. Read-only first de-risks: get auth + display working, push writes later once pattern proven.

## Open questions — resolved

- **Auth**: Credential Manager / Sign-In-with-Google (not plain OAuth2 device flow, not deprecated `GoogleSignInClient`) — implemented in `GoogleCalendarAuthManager`.
- **Scope**: `calendar.readonly` only.
- **Which calendar(s)**: user picks from calendar list via `calendarList.list`, not just primary — persisted in `SettingsPreferences.selectedGoogleCalendarIds`.
- **Token storage**: `:core:security`'s `GoogleCalendarTokenStore`, same Keystore-sealed pattern as `DeviceIdentity` — device-local, never synced to Supabase.
- **Offline behavior**: fetched events cache into new local-only Room table (`cached_calendar_events`, migration 13→14, deliberately outside `RoomSyncStore`/`EntityType`). Calendar page reads from Room on render; `refresh()` call (wired to pull-to-refresh) hits network on demand.
- **Free/busy vs. full event data**: date + title + all-day only — no description/location/attendees. `events.list` calls use `fields=items(id,summary,start,end)`.

## Suggested module shape (draft, not final)

- New `:core:calendar` — Google auth client, event-fetch API, local cache entity + DAO (own Room table, not in synced `RoomSyncStore` set). Pure-logic pieces (date range windowing, event-to-UI mapping) unit tested per repo convention (`:core:domain` pattern).
- `:feature:calendar` — consumes `:core:calendar`, renders fetched events alongside locally-added ones in existing calendar view.
- `:feature:dates` — **deleted.** Important Dates CRUD moved into `:feature:calendar`; standalone module, nav entry, DI module gone.

## Explicitly out of scope for phase 1 (do not build)

- Writing app-created events to Google Calendar.
- Two-way sync / conflict resolution.
- Multiple Google account support.
- Push/webhook-based live updates (poll-on-open enough for phase 1).
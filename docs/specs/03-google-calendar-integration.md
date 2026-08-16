# Google Calendar Integration — Spec (planned, not started)

Status: **not started**. This is a follow-up plan only — no code written yet.

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

## Open questions to resolve before implementation starts

- **Auth**: Google Sign-In (`credentials` / One Tap) vs. plain OAuth2 device
  flow. Given app has no existing Google dependency, need to pick one and
  check Play Console app-verification requirements (OAuth consent screen
  review can take days for sensitive scopes like Calendar).
- **Scope**: `calendar.readonly` (least privilege, phase-1 fits) vs. full
  `calendar` scope (needed eventually for phase 2 writeback — asking for it
  upfront avoids re-consenting users later, but wider scope = harsher Google
  verification review).
- **Which calendar(s)**: primary calendar only, or let user pick from their
  calendar list (shared/secondary calendars)?
- **Token storage**: refresh token is sensitive — needs same
  encrypted-at-rest treatment as other secrets (see
  `docs/architecture/007-encryption.md`); likely lives in `:core:security`
  or a new `:core:calendar` module, not synced to Supabase (this is a
  device-local Google credential, not couple-shared app data).
- **Offline behavior**: Calendar page currently reads from Room on render
  (per `docs/architecture/001-android-architecture.md`'s "network never on
  render path" rule). Google Calendar events break that — either cache
  fetched events into Room (a new local-only entity, not synced via
  `RoomSyncStore`/Supabase) or accept Calendar page becomes the one screen
  that hits network on render. Caching is more consistent with existing
  architecture.
- **Free/busy vs. full event data**: does the UI need full event details
  (description, location, attendees) or just date+title for the calendar
  view? Affects API surface needed (`events.list` fields param).

## Suggested module shape (draft, not final)

- New `:core:calendar` — Google auth client, event-fetch API, local cache
  entity + DAO (own Room table, not part of synced `RoomSyncStore` set).
  Pure-logic pieces (date range windowing, event-to-UI mapping) unit
  tested per repo convention (`:core:domain` pattern).
- `:feature:calendar` — consumes `:core:calendar`, renders fetched events
  alongside locally-added ones in existing calendar view.
- `:feature:dates` — deleted.

## Explicitly out of scope for phase 1 (do not build)

- Writing app-created events to Google Calendar.
- Two-way sync / conflict resolution.
- Multiple Google account support.
- Push/webhook-based live updates (poll-on-open is enough for phase 1).

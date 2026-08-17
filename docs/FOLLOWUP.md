# Follow-up — resume here

Point Claude at this file to pick up exactly where this session left off.
Branch `feature/android-app`, latest tag `v1.3.0` (released 2026-08-17,
adds forgot password, Google account linking, pairing sign-out escape
hatch — see commits `c9f3ba1`, `3683ce5`).

## Open item 1 — Google OAuth client setup — actually fixed and confirmed 2026-08-17 ~20:55

**Real root cause found and fixed:** the release Android OAuth client's registered SHA-1 had a
one-character typo in the last hex digit — `...2E:82:A1:9E` in Cloud Console vs the actual
release keystore's real fingerprint `...2E:82:A1:9D` (verified with
`apksigner verify --print-certs` against the installed APK). Not a propagation delay, not an
account issue — a plain transcription typo, present since the client was created, which is why
waiting never helped and why it "never actually worked" regardless of how long the previous
session's retest loop ran. Corrected the SHA-1 field in Cloud Console (Clients →
`Baby app - release`) to the real value and saved; confirmed the corrected value persisted
after a page reload.

Retested immediately on the MIUI device with `shaharco1804@gmail.com`: got past the point that
was failing (no `cmia`/`UNREGISTERED_ON_API_CONSOLE` at all this time), reached the real Google
consent screen ("Allow Google to sign you in to Sahar"), agreed, and the app landed on the
actual pairing-pending screen ("Almost there — you have joined the space, your partner needs to
approve this phone") — a concrete app-state success, not just "no error logged."

Before landing on this, an exhaustive re-check of every other plausible config point (Branding,
Audience/test users, Data Access scopes, enabled APIs, Supabase provider config, the actual
web client ID embedded in the installed APK's `classes.dex`) had already confirmed everything
else was correct — see the "Exhaustive config re-check" note preserved below for the record.

Confirmed on the Pixel too (`tp54477@gmail.com`) later the same session — see "Open item 3"
below. Calendar connect (shares the same `GetSignInWithGoogleOption` + client) should now work
too, not yet explicitly retested.

### Preserved history — earlier "STILL BROKEN, false positive" write-up (superseded above)

**Correction 2026-08-17 (later same day):** the paragraph below (previously headed "done,
login confirmed working") was wrong. The user confirmed it never actually worked — the
hourly retest's success signal was apparently just "no error logged in that window," not an
actual completed sign-in, so it never caught a real failure. Re-tested on-device ~20:31-20:33
the same day: identical `cmia: [8] Unknown error [status=UNREGISTERED_ON_API_CONSOLE]` during
`AccountReauth_flowRunner`, reproduced with **two different accounts**
(`shaharco1804@gmail.com`, freshly added that day, and `shaharco99@gmail.com`, an
already-established test user) — so this was never account-specific either.

Full config was re-verified directly in Cloud Console right after reproducing the failure, and
everything matches what this section already claims was fixed:
- **Android release client** (`Baby app - release`,
  `904126204563-hrne32fe8gbtm1e4b1ejtpnm0m4mnve3...`): package `com.oryareach.app`, SHA-1
  `BF:56:84:AB:05:E4:35:EA:9D:1F:ED:2E:8F:A7:76:C4:2E:82:A1:9E` — both correct, exact match.
  Created August 17, 2026, 6:47:31 PM GMT+3.
- **Audience → Test users**: 4 users listed, including both accounts tested above. Publishing
  status "Testing", user type "External" — as expected.
- **Data Access scopes**: `userinfo.email`, `userinfo.profile`, `openid`,
  `calendar.readonly` all present — as expected.
- **Enabled APIs** (Library dashboard): Identity Toolkit API, People API, Google Calendar API
  all show as enabled — but notably **zero traffic/requests logged against any of them** in
  the last 24h, including Identity Toolkit API, even immediately after a fresh failed sign-in
  attempt. This suggests the `UNREGISTERED_ON_API_CONSOLE` failure happens in Play Services'
  own client-registration check, *before* it would ever call into this project's APIs — i.e.
  the API-enablement theory from the original debugging history below was likely a red
  herring; the real gate is Play Services (device-side or Google-backend) recognizing the
  Android OAuth client as registered, independent of whether the project's APIs are enabled.

**Exhaustive config re-check 2026-08-17 ~20:50, everything else that could plausibly matter,
all confirmed correct — nothing left to fix on the config side:**
- **Branding tab** (Cloud Console → Google Auth Platform): App name "Baby app" set, User
  support email set, verification correctly shows "not required" for Testing status.
- **API Library search** for anything Sign-In-specific beyond what's already enabled — nothing
  found (`sign-in` and `identity` searches turn up nothing relevant not already enabled; the
  old standalone "Google Sign-In API" toggle doesn't exist anymore, Identity Toolkit API covers
  it and is already on).
- **Supabase dashboard → Authentication → Providers → Google**: enabled, Client IDs field
  contains exactly the web client id (`904126204563-a3h105j2d1ousge0b92ung0okpgposnc...`,
  matches the APK-embedded value), Client Secret is populated (non-empty, masked). Not actually
  reachable from this specific failure anyway — Play Services fails inside its own
  `AccountReauth` step before ever handing an ID token back to the app, so Supabase never even
  gets called.

**Next suspect, being tested now:** Play Services caches OAuth-client-registration results
locally on-device and may not be picking up the server-side fix even though Cloud Console
itself looks correct — tried `adb shell pm clear --cache-only com.google.android.gms` (cache
only, not data — doesn't sign out any Google account on the device, safe/reversible) to force
a fresh check. If that doesn't clear it, falls back to the original propagation-delay theory:
the release client is only ~2 hours old as of this writing, still within Google's stated
"5 minutes to a few hours" window — plan is to keep retrying periodically rather than
concluding it's broken prematurely.

`shaharco1804@gmail.com` added as a 4th Google Cloud test user (Audience →
Test users) 2026-08-17 — needed before that account can sign in at all while
publishing status is "Testing". Supabase itself has no separate allow-list
for Google sign-in, so this was the only place it needed adding.

Calendar connect (`GoogleCalendarAuthManagerImpl`, same
`GetSignInWithGoogleOption` mechanism) shares the same underlying mechanism and is presumably
still blocked too — not retested today, no reason to expect it fares differently.

### Original debugging history (2026-08-17), kept for context

Both **Google Calendar integration** (`:core:calendar`,
`docs/specs/03-google-calendar-integration.md`) and **Sign in with Google**
(`:feature:auth`) are fully coded and merged. External config is now done:

Both **Google Calendar integration** (`:core:calendar`,
`docs/specs/03-google-calendar-integration.md`) and **Sign in with Google**
(`:feature:auth`) are fully coded and merged. External config is now done:

- Google Cloud project `baby-app-505815`. Three OAuth clients exist (the
  Android type only supports one SHA-1 per client in the current console UI,
  hence two Android entries):
  - **Android** ("Baby app"), package `com.oryareach.app`, debug SHA-1
    (`A0:A2:97:DF:12:8C:11:AC:39:A3:03:AC:F4:C8:15:10:6E:00:F4:D9`).
  - **Android** ("Baby app - release"), same package, release SHA-1
    (`BF:56:84:AB:05:E4:35:EA:9D:1F:ED:2E:8F:A7:76:C4:2E:82:A1:9E` —
    extracted from the `v1.2.3` release APK via `apksigner verify
    --print-certs`).
  - **Web application** type (`904126204563-a3h105j2d1ousge0b92ung0okpgposnc
    .apps.googleusercontent.com`), used as the Credential Manager server
    client id for both login and Calendar (one client covers both, per the
    doc comment in `android/core/security/build.gradle.kts`). Its redirect
    URI is registered for Supabase's callback.
- `android/local.properties` (local dev, not committed) has both
  `googleWebClientId` and `googleCalendarOauthClientId` set to the Web
  client id above.
- GitHub repo secret `GOOGLE_WEB_CLIENT_ID` set; wired into
  `.github/workflows/android-release.yml` for both
  `ORG_GRADLE_PROJECT_googleWebClientId` and
  `ORG_GRADLE_PROJECT_googleCalendarOauthClientId`.
- Supabase dashboard → Authentication → Providers → Google: enabled, same
  client id + secret set.

All external config is done, including the release SHA-1. `v1.2.3` release
build succeeded and is published (`gh release view v1.2.3`).

**Still to do:**
1. `v1.2.3` installed and tested on the MIUI device — currently **fails**
   with `cmia: [8] Unknown error [status=UNREGISTERED_ON_API_CONSOLE]`
   (see logcat filtered on `Auth.Api.Credentials`). Not a config mistake in
   the APK — installed build is confirmed `v1.2.3` with the matching
   release cert, package name and SHA-1 in Cloud Console both check out.
   The account picker itself works (shows all Google accounts on-device,
   identity step succeeds) — failure is later, in Play Services' silent
   `AccountReauth` step.
2. On-device debugging (2026-08-17) found two real project-config gaps,
   both now fixed, but neither alone resolved the error:
   - **APIs enabled** on `baby-app-505815` (Cloud Console → APIs & Services
     → Library — previously only default GCP infra APIs were enabled, e.g.
     BigQuery/Storage/Logging, nothing auth- or calendar-related):
     - `people.googleapis.com` (Google People API) — reads the signed-in
       user's profile (name/photo) after `GetSignInWithGoogleOption`;
       needed by `:feature:auth`'s login step.
     - `calendar-json.googleapis.com` (Google Calendar API) — actual event
       read calls `GoogleCalendarApi` (`:core:calendar`) makes; needed for
       the Calendar integration to do anything past auth.
     - `identitytoolkit.googleapis.com` (Identity Toolkit API) — backs
       Credential Manager's `GetSignInWithGoogleOption` flow itself
       (ID-token verification); Play Services' `AccountReauth` step
       appears to call this internally, independent of Calendar. Enabling
       this one is the most likely fix for the *login* path specifically —
       worth confirming on the next retest whether login alone (no
       Calendar scope) now succeeds, to isolate it from the two below.
   - **OAuth consent-screen scopes** (Cloud Console → Google Auth Platform
     → Data Access) — was completely empty ("No rows to display" in all
     three categories), meaning the app had never declared any scope it
     asks for. Added:
     - `.../auth/userinfo.email`, `.../auth/userinfo.profile`, `openid`
       (non-sensitive) — the identity/profile scopes Credential Manager
       sign-in needs.
     - `.../auth/calendar.readonly` (sensitive) — matches
       `GoogleCalendarAuthManagerImpl`'s `CALENDAR_READONLY_SCOPE`
       constant; needed for the Calendar consent step specifically.
   - Both changes were necessary regardless of whether they fully explain
     the error — an app can't request scopes it hasn't declared, and can't
     call APIs that aren't enabled. But logcat still showed the identical
     `UNREGISTERED_ON_API_CONSOLE` ~15 min after both were saved, so at
     least one more factor is in play.
3. Remaining suspect: **OAuth client propagation delay**. All three OAuth
   clients (Android debug, Android release, Web) show creation date
   2026-08-17 — the same day as this debugging session — in Cloud
   Console's Credentials list. Google's own docs say freshly created OAuth
   clients can take "5 minutes to a few hours" to propagate; this is the
   same caveat that applied to the release SHA-1 specifically, but appears
   to apply to the whole client set. An hourly auto-retest loop was
   scheduled (2026-08-17 ~19:42 onward) to catch when it clears without
   needing a manual check each time.
4. If it still fails after several hours of retries, re-verify: SHA-1 was
   typed correctly (no truncation/typo), the release client's package name
   is exactly `com.oryareach.app`, and pull a fresh logcat filtered on
   `Auth.Api.Credentials|cmia` to see if the error code changed (a
   different code than `[8] UNREGISTERED_ON_API_CONSOLE` means the
   propagation theory was wrong and it's worth re-reading this whole
   section from scratch).
5. Test Calendar connect too — same underlying mechanism
   (`GetSignInWithGoogleOption`), currently blocked by the same error.
6. Nothing here costs money — Google Cloud project has no billing account
   linked, GitHub Actions is free (repo is public), Supabase is on the free
   plan. No surprise-bill risk from any of this setup, including enabling
   the three APIs above (all free-tier, no billing account attached to
   charge against).

## Open item 1b — Forgot-password + Google account linking (coded, needs on-device test)

Two small additions to `:feature:auth`/`AuthRepository`, both landed 2026-08-17, unrelated to
the Google OAuth issue above (email/password auth was never affected by it):

- **Link an existing account to Google later**: sign up/in with email+password now, then
  Settings → Account → "Connect Google account" attaches a Google identity to that same
  Supabase user via `linkIdentityWithIdToken` — same user, same workspace, same data, nothing
  re-encrypted (the E2EE workspace key lives device-local, never derived from the login
  method — `docs/architecture/007-encryption.md`). Uses the same OAuth client as login — now
  that Open item 1 is confirmed fixed, this should work too, but hasn't been tried on-device yet.
- **Forgot password**: "Forgot password?" on the sign-in screen calls
  `AuthRepository.sendPasswordResetEmail`. Tapping the emailed link opens the app via a new deep
  link (`com.oryareach.app://reset-password`, `AndroidManifest.xml`'s second intent-filter on
  `MainActivity`, `launchMode="singleTask"` added so a tap while the app's already running
  reuses the instance via `onNewIntent` instead of stacking a new one). `MainActivity` detects
  it (`AuthRepository.isPasswordRecoveryLink`), routes to a new `ResetPasswordScreen`
  (`SaharApp`'s routing `when`, checked before the pairing/lock branches so a recovery session
  doesn't fall through into the paired workspace), and `updatePassword` sets the new one.

Redirect URL added 2026-08-17 (Authentication → URL Configuration → Redirect URLs now lists
`com.oryareach.app://reset-password` alongside the default `https://127.0.0.1:3000`). Build,
lint, and full `test` all pass. Shipped in `v1.3.0` (released 2026-08-17). **Still to do:**
neither the Google-linking nor the forgot-password code path (dialog → email → tap the link
on-device → `ResetPasswordScreen` → `updatePassword`) has been run end to end on a real
device/inbox yet — no longer blocked on a release, just hasn't been tried.

## Open item 1c — Pairing screen: no sign-out, no partner identity (sign-out shipped, identity not built)

User-reported bug 2026-08-17: the "Almost there — you've joined the space, your partner needs
to approve this phone" pairing-pending screen (`PairingScreen`'s `AwaitingKeyStage`) gave no way
to tell *which* workspace/partner the device had joined, and — worse — no way to sign out and
try a different account if it was the wrong one. A device stuck there was stuck, full stop.

Two-part fix, only the first part built so far:

- **Sign-out escape hatch — done.** `PairingViewModel` gained `auth: AuthRepository`,
  `session: SessionController`, `localDataWiper: LocalDataWiper` (previously only had
  `WorkspaceRepository`/`DeviceIdentity`) and an `onSignOut()` action running the exact same
  sequence as `SettingsViewModel.onSignOutClick()` (`auth.signOut()` →
  `identity.forget()` → `session.signOut()` → `localDataWiper.wipeAndRestart()`) — duplicated
  rather than shared because `:feature:*` modules never depend on each other, and Settings
  itself is unreachable until a workspace is unlocked, exactly the state this screen is stuck
  in. A "Sign out" button + confirm dialog now shows on `Choose`, `EnterCode`, `AwaitingKey`,
  and `EnterRecoveryPhrase` (new `PairingStage.allowsSignOut` in `PairingUiState.kt` gates
  it) — deliberately not on `ShowRecoveryPhrase` (would abandon a just-created workspace before
  its one-time phrase display) or `Ready` (Settings' own sign-out already covers that state).
  Build, lint, and full `test` all pass.
- **Partner/workspace identity display — not built.** Investigated, not started: no backend
  query for this exists today. `workspace_members` (`supabase/migrations/0001_init.sql`) has
  only `workspace_id`/`user_id`/`joined_at`, no email or name; `WorkspaceRepository.devices()`
  returns each device's manufacturer/model label, not a person's identity. Would need a new
  `SECURITY DEFINER` Postgres function (same pattern as the existing `is_workspace_member`
  function) returning a fellow member's email, scoped so only workspace members can call it,
  plus a `WorkspaceRepository` method and a new field on `PairingStage.AwaitingKey` to carry it
  into the UI. Not attempted this session — ask before starting, it's a real schema/migration
  change to a production Supabase project, not just an app-side fix.

Shipped in `v1.3.0` (released 2026-08-17) and confirmed live on-device the same session — the
"Sign out" button showed up on the Pixel's `AwaitingKey` screen exactly as designed (see Open
item 1d below for the pairing saga it appeared in the middle of). Partner/workspace identity
display is still not built — the investigation notes above are still accurate.

## Open item 1d — old workspace unrecoverable, recreated; found a real device_keys bug along the way

2026-08-17, same session as Open item 1. Both physical devices ended up stuck simultaneously on
the pairing-pending screen ("joined the space, partner needs to approve"), each waiting on the
other to already hold the workspace key — a genuine deadlock, since neither could approve the
other. Root cause: the workspace's 24-word recovery phrase (`docs/architecture/007-encryption.md`
— it *is* the key, encoded; never stored anywhere, by design) had never been saved by the user.
Per that same doc, losing all devices' keys and the phrase means the workspace data is
unrecoverable — confirmed with the user, who chose to abandon the old workspace
(`2f734a4d-6f24-4f5f-8606-1bff87111859`, created 2026-08-15) and start fresh rather than chase
an unrecoverable key.

**What was done** (all DB writes run by the user directly in the Supabase SQL Editor — the
`execute_sql` MCP tool refused every write attempt here, including a single 0-row delete;
routing around that block via browser automation was considered and deliberately rejected as
defeating its purpose, so every write below was handed to the user to run themselves):
1. Deleted the two stale `workspace_members` rows for the old workspace (left the 34
   now-permanently-unreadable `records` rows and other cascade data alone — user's call, "for
   now," full cleanup not done).
2. On the MIUI device (`shaharco1804@gmail.com`), tapping "Check again" on the stuck pairing
   screen correctly re-evaluated to `PairingStage.Choose` (per `PairingViewModel.onRefresh()` —
   confirmed by reading the code, not guessed) once the stale membership was gone, and a
   workspace got created (`d576a8b0-6ad6-4ffe-b870-e00f6e0e587d`). Note for next time: the
   `adb shell monkey -p <pkg> -c android.intent.category.LAUNCHER 1` idiom used throughout this
   session to bring the app to the foreground **injects a random synthetic input event by
   default**, not just a launch intent — switched to `adb shell am start -n <pkg>/.MainActivity`
   (no synthetic input) for the rest of the session and going forward.
3. Invited the Pixel in via Settings → Manage devices → invite code (`WorkspaceRepository`'s
   normal invite/accept flow, `couple_invitations` table) — no recovery phrase needed for a
   fresh creation, since the creating device already holds the key live and can approve in
   real time.
4. Pixel accepted the invite and reached the same "awaiting approval" pairing-pending screen —
   but MIUI's "Manage devices" showed no pending device to approve, even after refreshing.

**Real bug found:** `PairingViewModel.registerDevice()` (`android/feature/pairing/.../
PairingViewModel.kt:338`) short-circuits on `identity.registeredKeyId` — if a device has *ever*
registered a `device_keys` row (identity keypairs are device-scoped, reused across workspace
changes by design), it returns that same row's id forever and never calls
`WorkspaceRepository.publishDeviceKey()` again. `device_keys.workspace_id`, however, is only set
at insert time and never updated on reuse. Both test devices had stale `device_keys` rows from
earlier registration attempts against the *old* (now-membership-less) workspace; since nobody is
a member of that workspace anymore, RLS hid those rows from *everyone*, including their own
owners — so `WorkspaceRepository.devices()` (queried by `showReady()` to populate the pending
list) came back empty even though the real wrapped-key linkage (`wrapped_workspace_keys`,
correctly pointing at the new workspace) was fine. Confirmed via direct SQL, not guessed: the
`device_keys` row backing MIUI's fresh registration was actually one created ~8 min earlier
(18:45:27) against `2f734a4d`, and Pixel's the same. **Not fixed in code, on purpose (user's
explicit instruction — document only).** Proper fix: either update `device_keys.workspace_id`
on the `registerDevice()` reuse path, or stop keying pending/paired lookups off that column and
join through `wrapped_workspace_keys.workspace_id` instead, which is always correct.

**Workaround used to unblock pairing this session** (also run by the user, per the same
permission pattern as above):
```sql
update device_keys
set workspace_id = 'd576a8b0-6ad6-4ffe-b870-e00f6e0e587d'
where id in ('073784ca-f5ae-4e59-8d37-bbadbd0b586b', 'f76033c1-2eff-452a-b68f-598617189636');
```
After this, MIUI's "Manage devices" correctly showed the Pixel as pending, approval succeeded,
and both devices landed on the real Home screen (cycle-tracking onboarding, confirming a fresh
empty workspace) within the same session.

**Loose end:** the new workspace's recovery phrase was never deliberately viewed/recorded this
time either (the creation flow was driven by a "Check again" tap plus one `am`/`monkey`
foreground call, not a manual walkthrough of the `ShowRecoveryPhrase` screen). Not urgent — the
raw key is already sealed on the MIUI device (`identity.workspaceKey()`), so Settings → "Show
recovery phrase" re-derives and displays the identical phrase on demand, nothing was lost — but
the user should actually do that and write it down, since it's still true that losing every
device *and* the phrase is unrecoverable by design.

## Open item 2 — `:feature:dates` removal — done

`:feature:dates` was the only add/edit/delete UI for `ImportantDate`;
`:feature:calendar` only displayed it read-only, so a naive deletion would
have silently removed the couple's only way to add/edit important dates.
Fixed properly: ported the add/edit/delete form (FAB, bottom sheet, date
picker, delete-confirm dialog) directly into `:feature:calendar`
(`CalendarViewModel`/`CalendarScreen`), same `ImportantDateRepository` calls
`:feature:dates` used, entirely local records — the read-only Google
Calendar integration is untouched throughout. `:feature:dates` module then
deleted (Gradle module, DI wiring, nav tab, all references — see commit
`feat(calendar): move ImportantDate CRUD into Calendar, delete
:feature:dates`). Build, lint, and unit tests all pass; not yet manually
tested on-device (add/edit/delete a date, confirm it still shows in the
month grid and day sheet).

## What's already shipped and working (don't redo)

- Shopping: decimal prices (was truncating "5804.25" → "580425"), receipt
  attachments, need→ordered→bought sort order.
- Tasks: priority/assigned-to filters, fixed "can't delete the last task"
  (FAB was covering the delete button).
- Folders/Documents: rename, drag-and-drop between folders with an
  animation.
- CI: release builds now actually have Supabase configured (every release
  before this session shipped broken — crashed on launch for anyone
  installing the real release APK).
- Release versioning: fixed a tie-break bug where tagging a release on the
  same commit as a previous tag could make it build mislabeled with the
  older version number.

## Where to look for more context

- `git log --oneline feature/android-app` for the full commit history.
- This session also wrote several memory entries (release-only test
  devices, the git-tag versioning bug, an agent-worktree merge gotcha) —
  ask to "check memory" if picking this up in a context that has access to
  it.

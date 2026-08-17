# Follow-up — resume here

Point Claude at this file to pick up exactly where this session left off.
Branch `feature/android-app`, latest tag `v1.2.3` (in flight as of this
write-up — see "Open item 1" below for what's still unverified).

## Open item 1 — Google OAuth client setup (mostly done, verify on device)

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
   (see logcat filtered on `Auth.Api.Credentials`). Not a config mistake —
   installed APK is confirmed `v1.2.3` with the matching release cert,
   package name and SHA-1 in Cloud Console both check out. Most likely
   cause: the "Baby app - release" Android OAuth client (SHA-1 above) was
   only just created and Google's own console warns propagation can take
   "5 minutes to a few hours." **Retry login after waiting longer** (try
   ~1 hour after creation) before assuming something else is wrong.
2. If it still fails after a real wait, re-verify: SHA-1 was typed
   correctly (no truncation/typo), the release client's package name is
   exactly `com.oryareach.app`, and pull a fresh logcat filtered on
   `Auth.Api.Credentials|cmia` to see if the error code changed.
3. Test Calendar connect too — same underlying mechanism
   (`GetSignInWithGoogleOption`), blocked on the same propagation issue.
4. Nothing here costs money — Google Cloud project has no billing account
   linked, GitHub Actions is free (repo is public), Supabase is on the free
   plan. No surprise-bill risk from any of this setup.

## Open item 2 — `:feature:dates` removal (deferred, not blocking)

The calendar spec's original plan called for deleting `:feature:dates`
(superseded by the Calendar view showing `ImportantDate` records already).
An earlier pass in this session built that deletion, but it was never
committed and the worktree it lived in may no longer exist. Dates and
Calendar currently coexist fine — this is a deliberate, separate follow-up
(data-model implications worth its own pass), not something broken.

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

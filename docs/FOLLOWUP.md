# Follow-up — resume here

Point Claude at this file to pick up exactly where this session left off.
Branch `feature/android-app`, latest tag `v1.2.2` (auto-update verified
working end-to-end on both the MIUI phone and the Pixel — see git log for
`fix(build): break git-describe ties...` and `feat(auth): sign in with
Google` for the two most recent pieces of work).

## Open item 1 — Google OAuth client setup (blocks two features)

Both **Google Calendar integration** (`:core:calendar`,
`docs/specs/03-google-calendar-integration.md`) and **Sign in with Google**
(`:feature:auth`) are fully coded and merged, but neither works yet — both
fail fast with a clear in-app error instead of hanging. They're blocked on
the same missing setup, which needs someone with access to a Google Cloud
project:

1. Create an OAuth 2.0 client, **Android** application type, package
   `com.oryareach.app`, with the debug and release keystore's SHA-1
   fingerprints attached (get them with `keytool -list -v -keystore
   <path>`).
2. Create an OAuth 2.0 client, **Web application** type. Its client ID goes
   into `android/local.properties` (never committed) as:
   - `googleCalendarOauthClientId=<id>` — for Calendar
   - `googleWebClientId=<id>` — for login
   - One client can cover both if that's simpler; see the `TODO(app
     owner)` comments in `android/core/security/build.gradle.kts` for the
     exact mechanics of how these get read.
3. For CI release builds to also have these (currently they don't — local
   dev builds work once `local.properties` is set, but the GitHub Actions
   release workflow needs the same two values as repo secrets, wired into
   `.github/workflows/android-release.yml` the same way
   `SUPABASE_URL`/`SUPABASE_ANON_KEY` already are).
4. For "Sign in with Google" specifically, also configure the Web client ID
   as the Google provider in the **Supabase dashboard** → Authentication →
   Providers → Google.

Once set, both features should work without further code changes — the
implementation is done, this is purely external configuration.

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

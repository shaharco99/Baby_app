# Follow-up — resume here

Point Claude at this file to pick up exactly where this session left off.
Branch `feature/android-app`. Latest tag `v1.7.0` (released 2026-09-19, CI green). Supabase migrations 0001-0010 are all
applied — see `git log` for full history; this file only
tracks what's still open plus enough context to act on it.

## 2026-09-19 — Xiaomi pass, cold start, background reminders

Xiaomi (`b6d8682a049d`, MIUI 14 / Android 13) runs a locally signed release of the two
unreleased commits (versionName still 1.6.3). Pixel was not connected.

**Fixed + verified on the Xiaomi:**
- Cold start: SQLCipher re-ran key derivation (~0.6s) on each of 4 WAL pool connections; Home
  showed its empty "last period" state for 3-8s. Now TRUNCATE journaling (one connection) + the
  name screen stays up until Home's first data. First frame ~0.52s (was ~1.03s), populated Home
  ~1.4s (was 4-8s), no empty frame in a screen recording.
- Feed/pump reminders moved from WorkManager to exact `AlarmManager` alarms. Rang on time with
  the app swiped away, process killed, screen off (15:00:00.8). After a reboot MIUI delivered
  `BOOT_COMPLETED` ~3 min late, the alarm re-armed and rang on time. Doze result: see below.

**Xiaomi live pass: all pass.** Every drawer destination opens, no crash, no `*:E`. Back walks
tab history, then Home, then the exit confirm. Pumping timer: pause freezes it (Home too), survives
force-stop while paused, resume continues, and stop drops the paused time from the duration.
Feeding table view + log sheet with a backdated date/time. Settings shows children, both intervals
and the version. Tasks category includes "For the baby". Test pump sessions (note `0000-…`)
were deleted afterwards.

**Found:** Calendar legend chip "Google Calendar" wrapped mid-word. Fixed later the same day.

## 2026-09-19 (later) — Pixel + both-phone pass

Both phones on a locally signed release of `362642e` (versionName 1.6.3). The Pixel now runs
**Hebrew, light theme**, and the Xiaomi English, dark theme. The same records rendered correctly
in both.

**Pixel solo, all pass:** cold start (first frame ~150ms; it showed the same false empty state for
~1.8s before the fix and shows none after), drawer sweep of 9 screens, back history, exit
confirm, Search pull-to-refresh (no crash), Book of Love dialog with its book+heart icons, and
the Calendar legend wrap fix. The reminder fired on time (17:10:00.07) with the app process killed.

**Both phones, all pass:** a feed logged on the Pixel arrived on the Xiaomi and moved its alarm
to the same instant; a conflict (same feed edited offline on both) showed the conflict screen,
resolved, and both then agreed; a pump timer started on the Pixel showed running on the Xiaomi,
and a pause from the Xiaomi showed frozen on the Pixel; a feed-interval change synced and
re-armed both alarms, and reverting it cleared both; a child rename synced both ways. All test
data was reverted or deleted.

**Findings, all fixed the same day and checked on device:** the conflict card now lists the
fields that differ ("amount ml: 22" / "11"); the Cycle grid's "12" no longer wraps; Calendar
dots are centered under their day; partner changes now arrive within ~30s while the app is open
(a foreground poll). A Hebrew RTL pass followed: the month arrows now go right = next in every
language, and the folder breadcrumb separator mirrors. By the user's choice, the Material date
picker and the top-bar back arrow keep Android's standard RTL direction.

**Not tested:** real deep Doze. It needs the screen off, which locks both phones. MIUI also
refused `force-idle` while on USB.

## Known limits (by design, not bugs)
- A partner's feed can't move this phone's alarm while this app is closed: the workspace key is
  only in memory after unlock, and a push would need a server-side trigger. It catches up on the
  next open or sync.
- Force-stop (Settings → Force stop, or MIUI "clean" on a non-whitelisted app) drops all alarms
  until the app is next opened. Nothing an app can do about that.

## Resolved 2026-08-17 (this session, shipped in v1.3.1)

- **Documents screen crashed on open.** `GmsDocumentScanning.getClient()` ran eagerly,
  unconditionally, in a `remember{}` — threw internally (ML Kit module unavailable on-device),
  crashing the whole screen before Scan was ever tapped. Same shape in the (new, unreleased)
  invite-code QR scanner. Both now wrap client creation in `runCatching`, fall back to a no-op
  instead of crashing. `core/scanner/{DocumentScanner,InvitationCodeScanner}.kt`.
- **Recovery-phrase screen crashed** (`ShowRecoveryPhrase` pairing stage and Settings' "Show
  recovery phrase" — same `RecoveryPhrase.encode`). `RecoveryPhrase.kt` looked up its BIP-39
  wordlist with a *package-relative* `getResourceAsStream` — breaks once R8 flattens/repackages
  the obfuscated class in release builds (the packaged resource itself doesn't move). Fixed with
  an absolute classpath path. Invisible in unit tests (JVM test classpath isn't R8'd) — if a
  similar `getResourceAsStream` shows up elsewhere, use an absolute path from the start.
- **QR pairing finished + shipped**: scan a partner's invite code instead of typing it; show/copy
  this device's own invite code as a QR + text; copy button added to Settings' recovery-phrase
  dialog too. All three verified live against a signed release build (clipboard round-tripped
  into a real text field, not just "no crash").
- Full sweep of all 8 bottom-nav tabs on-device — no other crashes found. `grep` for `!!` and
  eager `getClient(` calls elsewhere in `feature/`/`core/` — clean.

## Resolved — Google OAuth (`Open item 1`, resolved-then-broken saga, condensed)

Fixed 2026-08-17: the release Android OAuth client's SHA-1 had a one-character typo in Cloud
Console vs the real release keystore fingerprint. Corrected, retested end-to-end on both the
MIUI device and the Pixel — real success (reached consent screen, landed on pairing-pending).
Full debugging trail (API enablement, consent scopes, propagation-delay red herrings) is in
`git log`/prior commits if the failure mode ever resembles `UNREGISTERED_ON_API_CONSOLE` again.

Google Calendar-connect (same OAuth client) explicitly retested 2026-08-21 on both real devices:
both show "Connected as [account]" in Settings, and the Calendar screen renders its month grid
with the "Google Calendar" legend entry active on both — no error state. Google account linking
is also confirmed live-connected on both devices already.

## Done 2026-09-19 — partner identity on the pairing screen

The waiting-for-approval screen shows "Waiting on: <partner email>" from
`workspace_partner_emails(ws)` (migration 0010, **applied**, pgTAP in
`supabase/tests/004_partner_emails.sql`, green in CI). Not yet seen on a device. It only shows on
a phone that has joined but not yet been approved.

## Fixed 2026-09-19 — `device_keys.workspace_id` stale on reuse

`registerDevice()` now keeps its cached device-key id only while that id is a live device of
the workspace being joined, and registers a fresh row otherwise. `devices()` is scoped to the
given workspace. The live DB was checked the same day: both phones' current rows are in the
active workspace with wrapped keys. The old workspace still has 6 orphaned rows, which are
harmless and can be left. Not exercised on a device, since that needs a leave-and-rejoin.

## Open — small items
- Doze check (deep sleep): parked by the user. Steps are in the 2026-09-19 chat. Log a feed, then
  leave the phone unplugged, locked and still, and the reminder should ring on time.
- Startup has no Macrobenchmark `StartupTimingMetric` module, on purpose for now. It needs a
  spare device or an emulator, since a benchmark build would break the release-only rule on the
  real phones.
- Supabase advisor, older items not addressed: leaked-password protection is off and only a few
  MFA options are enabled. Both are dashboard settings.

## Done, don't redo

- Shopping: decimal prices, receipt attachments, need→ordered→bought sort.
- Tasks: priority/assigned-to filters, "can't delete the last task" (FAB covering the button).
- Folders/Documents: rename, drag-and-drop between folders with animation.
- `:feature:dates` removed — add/edit/delete important dates now lives in `:feature:calendar`.
- CI: release builds now actually have Supabase configured (used to crash on launch).
- Release versioning: fixed a same-commit-retag mislabeling bug.
- Pairing "Sign out" escape hatch on `Choose`/`EnterCode`/`AwaitingKey`/`EnterRecoveryPhrase`.
- Calendar redesign: Google Calendar-style month view (full-width grid, tonal selection, card
  wrap, chip legend).
- App renamed to "Takes Two of Us" (`SaharApp`/`SaharApplication` → `TakesTwoApp`/
  `TakesTwoApplication`); "Shahar" the partner-name string is unrelated and untouched.
- Co-op-game easter eggs (long-press moon → split-world glitch + "Book of Love" tip when the
  partner's been active in the last 5 min; 7-tap Settings title → toast) — both verified live
  on both real devices 2026-08-21 (create/sync/delete round-tripped between MIUI ↔ Pixel to
  trigger the recency check).
- Settings now shows the installed version string under "Check for updates" — added after this
  session needed `adb`-only ways to confirm what was actually installed.

## Where to look for more context

`git log --oneline feature/android-app` for full history. Memory (if available in this context):
release-only test devices, git-tag versioning bug, agent-worktree merge gotcha.

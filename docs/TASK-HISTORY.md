# Task history — resume here

One file for all that used to live in `docs/FOLLOWUP.md` and `docs/specs/`: what still open, what already done (so nobody redo), specs fully absorbed into code. Point Claude here to resume from last session.

Branch `feature/android-app`, pushed. Latest release **v1.17.4** (2026-10-02); both phones run it (checked). `c487f76` committed, **not released** (→ v1.17.5). Supabase migrations **0001–0014 applied**, only by `supabase-deploy.yml` pipeline — see rule in `CLAUDE.md`. Every migration file must stay idempotent.

`git log --oneline feature/android-app` = real history. This file = condensed version.

---

## Still open

1. **Doze (deep sleep) reminder check.** Parked by user; only they can do it: log feed on Pixel, leave unplugged, locked, still for 3h until reminder. Forced `adb` route fails — `dumpsys deviceidle force-idle deep` and `step deep` both stop at INACTIVE on Xiaomi (`mMotionActive=true`, 143 pending alarms). Cause never pinned; suspects: Android's "no deep idle within 30 min of wake-from-idle alarm" rule and MIUI's own power manager. Don't keep retrying on MIUI.
2. **Partner email on pairing screen, on real phone.** Function + pgTAP test green. Seeing it needs phone joined to workspace but not yet approved, so unpair + re-join. Parked by user 2026-09-21 — too disruptive for payoff.
3. **`device_keys` stale-on-reuse fix, on real phone.** Needs full leave-and-rejoin. Fix in; live rows checked in DB. Parked, same reason.

**Small open items:**
- **Release v1.17.5** (`c487f76`): feed + cycle sheets scroll above keyboard, breastfeed delete dialog names itself, Hebrew "שאוב" label. Then on Pixel: Breastfeed sheet one-line fields, delete dialog wording (Cancel only).
- Ideas, not asked yet: Home feed card while breastfeed runs ("Breastfeeding now · Right · 12 min", now reads "0 h 0 min ago"); sheet title after Stop ("Breastfeed finished" vs "Edit feed").
- **Updater stuck on "Installing…"** (pre-existing): if the system "Update?" prompt is dismissed without an answer (e.g. MIUI control centre over it), `UpdateViewModel.installing` never resets; only force-stop clears it. Fix: treat the confirm activity returning without a result as abort / time out the flag.
- Diaper form's date button wraps "25 September / 2026" in English (cosmetic; feed sheet has the same pair of buttons but wider).
- Feeding page's day line ("3 urine · 0 stool") still counts feed marks only, not diaper-page changes; feed rows don't show "diaper not changed". User only asked for the summary + Diapers page — ask before changing.
- Diaper page's `today` only refreshes on a DB emission (no ticker) — stale header across midnight until something changes.
- No Macrobenchmark startup module. Needs spare device or emulator; benchmark build breaks release-only rule on real phones.
- Supabase security advisor: leaked-password protection off, few MFA options enabled. Both dashboard toggles — user's to flip. "SECURITY DEFINER callable by authenticated" warnings intended — every such RPC checks membership itself.

**Kept as Android does them, by user's choice — do not "fix":** Material date picker's month arrows in Hebrew (next on left), top-bar back arrow (points right in Hebrew).

**Closed 2026-09-21:** migration 0011 applied (table was hand-created during push work, so file rewritten guarded, now matches live schema column for column); `supabase/tests/005_device_push_tokens.sql` run first time, green; whole pgTAP suite clean from `db reset`, 66/66; v1.8.0 and v1.8.1 released + installed on both phones, drawer work checked in English/dark and Hebrew/light.

**Still unverified by eye:** cycle history row's end date and three prediction lines. Not visible on either phone — no cycle history logged, which is exactly how ISO end date survived so long. Confirming = inventing period data, so they stand on build + code alone. All else in v1.8.1 checked on both phones, both languages.

---

## 2026-10-02 — breastfeeding, ID numbers, delete child (v1.17.0–1.17.4)

- **Breastfeed = a feed, not an entity.** `FeedingEntry.nursingSide` (`PumpSide` reused) + `nursingEndedAtEpochMillis`/`nursingPausedMillis`/`nursingPausedAtEpochMillis`; running while end null, like a pump session (survives force-stop, live on partner's phone). `fedAt` = start → countdown, reminder (moves at Start), widget, diapers, summary unchanged. Room 23→24, no Supabase change. Partner on ≤1.16.2 editing such a row drops the fields.
- Feeding page: L/R/Both + "🤱 Breastfeed" (TalkBack: "Start breastfeeding") beside "Log a feed"; running card: clock, side, start, Pause/Resume, Stop → feed sheet (discardable). Sheet: Breastfeed | Bottle | Solid; breastfeed = side, minutes (required, inline error), top-ups Pumped/"שאוב" + Formula. Row "Left · 20 min · 30 ml"; table ml column shows ml, else "20 min"; day line "N min breastfeeding".
- **ID numbers:** `AppSettings.partnerOne/TwoIdNumber`, `Baby.idNumber`. Settings → collapsible "ID numbers", copy per number (sensitive clip, dots in preview), one edit dialog; Israeli check digit (`:core:domain` `identity/IdNumber.kt`) warns only. Child's ID on Home birth card + doctor summary (own line; parts use NBSP so wraps only at "·").
- **Delete child** (Settings child dialog): hidden for active child; `BabyRepository.delete` refuses if active or any feed/diaper/vitamin row (`BabyDao.countLoggedRecords`). Used once: stray unnamed "Baby" record (mis-tapped "Add another child") removed, synced.
- **Checked on both phones** (Xiaomi EN/dark, Pixel HE/light): timer start/pause/resume/stop/discard, live cross-phone, sheet both languages, ID section + copy, Home ID, summary ID, real breastfeed row/day/table. Labels that wrapped/cut at half width fixed along the way (Hebrew runs longer — check every half-width label in both).
- User data touched, at user's request: 09:30 breastfeed + separate 30 ml merged into one row (Pumped 30), 30 ml row deleted.

---

## 2026-09-24 (night) — wasted space, sheets, units (v1.14.1)

- **Empty band above every page title** (user: "never want to see things like this"). Host `Scaffold` in `TakesTwoApp` padded for the top bar (which already covers the status bar), and each tab screen's `safeDrawingPadding()` added the status bar again. Host now passes `padding(padding).consumeWindowInsets(padding)` to every tab — one fix for all eleven. Screens outside the host (auth, pairing) own their insets and are unchanged.
- **Form sheets open fully expanded** (`skipPartiallyExpanded = true`, all nine). The birth-details sheet opened half-way with Save at the screen edge under the nav buttons.
- **Stash "16h 56m" in the Hebrew dialog** — units now `duration_*` strings in `:core:ui`. Dead `toLitres`/constants left in feeding/pumping after the dialog move removed.
- **v1.14.1 checked on the Xiaomi (English/dark):** band gone on all ten tabs (Home, Tasks, Shopping, Documents, Feeding, Pumping, Cycle, Calendar, Search, Settings) — title sits right under the bar; Home now fits the tasks card on one screen. **Pixel (v1.14.1, Hebrew/light):** band gone on Home; birth-details sheet opens fully with Save above the gesture bar (dismissed, nothing saved). **Updater gotcha:** relaunching the app (adb `monkey`) while the system install prompt or Play Protect's scan dialog is up cancels it — the update then fails with `INSTALL_FAILED_VERIFICATION_FAILURE`. Tap Install and wait; don't relaunch.
- **v1.14.0 checked:** Xiaomi (English/dark) "1 week and 1 day", night watch from Home's feed card; Pixel (Hebrew/light) "שבוע ויום", stash from the pump card, summary week starts 18.9 (6 days, 7.0 feeds/day, 300 ml) — v1.12.1's fix confirmed.

---

## 2026-09-25 (later) — diaper shortcuts, "seen, not changed" (v1.16.0)

- **Urine/stool seen, diaper not changed.** `FeedingEntry.diaperChanged` (default true; Room 22→23, `diaper_changed INTEGER NOT NULL DEFAULT 1`; no Supabase change — inside ciphertext). Feed sheet shows a third chip "Diaper changed" (on) once urine or stool is marked; off = marks still count, diaper does not. `DiaperEvent.changed`; `DiaperDay.changeCount` and the summary's `diaperCount` count only changed ones; urine/stool count all. Diapers page row: "Urine · diaper not changed"; "last change" skips seen-only feeds. A partner on ≤1.15.1 editing such a feed resets it to changed.
- **Shortcuts.** Home (baby mode): small Diapers card under the feed card — "Diapers today: N" + last change time, tap → Diapers tab. Feeding title row: "Diapers" button beside "Summary" (callback from `:app`, features stay independent).
- **Home order:** open-tasks card now above the budget card, both modes.
- **Checked on both phones (v1.16.0; Xiaomi English/dark, Pixel Hebrew/light), 2026-09-25.** Update 1.15.1→1.16.0 kept all data (DB 22→23). Home diaper card + tasks-above-budget; card → Diapers tab. Feeding title row fits Summary + Diapers in both languages; button → Diapers. Feed sheet: "Diaper changed" chip appears on urine/stool, on by default; Hebrew three chips fit one row. Real 10:36 feed toggled to not-changed → Diapers 4→3, urine stays 4, row "Urine · diaper not changed"; reverted. Diaper log add (sheet above keyboard), edit, dry change counts, feed rows inert, synced to Pixel; delete dialog names time, Cancel keeps, Delete + Undo restores. Summary 24h/avg/table match Diapers page. Test rows tombstoned on server; nothing else touched.
- **Found on phones, fixed in v1.16.1–1.16.2:** summary table headers broke mid-word ("Diaper/s", "חיתולי/ם", pre-existing "האכלו/ת"); v1.16.1 fixed headers (confirmed both phones) but squeezed the day to "Wednesda/y" in English; v1.16.2 weights sized from measured widths. **v1.16.2 confirmed on both phones** (Xiaomi English: "Wednesday," whole, headers one line; Pixel Hebrew: all headers and days one line).
- **Found, not fixed (pre-existing updater):** if the system "Update?" prompt is dismissed without a choice (e.g. control centre pulled over it), the update dialog stays on "Installing…" forever; only a force-stop resets it. Diaper-change date button wraps "25 September / 2026" onto two lines in English — cosmetic.

---

## 2026-09-25 — diaper log (v1.15.0)

User reversed the 2026-09-24 "declined: diaper log": wants its own page, fed by the feeding page's marks, with a count of diapers changed.

- New synced entity `DiaperChange` (`diaper_changes`, Room 21→22, Supabase `0014` — enum value only). Child-scoped like a feed. Both marks off = dry diaper, still counted.
- **Sync with feeding = a read, not a copy.** `:core:domain`'s `diaper/DiaperLog.kt` (`diaperDays`, `changesSince`, tested) merges feeds with urine/stool marked + changes logged on the diaper page into days. A feed with no mark is not a change. Editing/deleting the feed on Feeding moves the diaper row with it; nothing duplicated, so the two screens cannot drift.
- New `:feature:diaper` tab (drawer, after Feeding; `BabyChangingStation` icon). Card: diapers changed today, "N urine · M stool", last change time, last-7-days total, "Log a diaper change". List by day ("Today · 6 diapers · 4 urine · 2 stool"), older days in the drawer. Feed-sourced rows read-only ("Logged with a feed · edit it on Feeding"), no trash; own rows tap to edit (date/time editable), trash → confirm dialog → undo snackbar.
- Doctor summary (v1.15.1) counts what the diaper page counts: new "Diapers" figure in the 24h and weekly-average cards and a Diapers column in the day table; urine/stool there now include changes logged on the diaper page (`doctorSummary(changes = …)`, `DoctorSummary.diapers` one-to-one with `days`, tested). Footnote says so. Feeding's own day line ("3 urine · 1 stool") still counts feed marks only.
- Build + test + lint green. **Nothing seen on a phone yet.** To check (release, both languages): empty state, a feed with marks appearing on Diaper on both phones, add/edit/delete/undo own change, plurals (Hebrew "חיתול אחד" / "2 חיתולים"), top of screen (no band), sheet opens fully, dark/light.

---

## Older sessions

2026-08 → 2026-09-24 (evening): [`docs/history/2026-08-to-09-24.md`](history/2026-08-to-09-24.md). Read only when a question reaches back that far.

---

## Known limits, by design

- **Force-stop** (Settings → Force stop, or MIUI "clean" on non-whitelisted app) drops all app's alarms and blocks FCM until next opened by hand. Nothing app can do — and why `am force-stop` is wrong way to simulate closed app in test. Use `am kill` after backgrounding, which is what swipe-away actually does.
- Background decryption relies on `DeviceIdentity`'s Keystore-sealed copy of workspace key, so locked app does decrypt in background for sync. Trade-off written up in `docs/architecture/012-push-wake-up.md`.

---

## Specs, absorbed

**Android conversion** (was `docs/specs/01-android-conversion.md`) — original 86-section requirements spec. Every requirement implemented. One correction worth keeping: it proposed private-by-default, partner-vs-partner sharing model for cycle data. Wrong. Implemented threat model = **couple-vs-outside-world** — both partners see all workspace data, end-to-end encrypted so Supabase, attacker, lost phone kept out. See `docs/architecture/005-data-privacy.md`.

**Auto-update** (was `docs/specs/02-auto-update.md`) — fully implemented as `:core:update` + `:feature:update`: semver comparison, async non-blocking startup check, download with SHA-256 verification, `PackageInstaller` install flow, mandatory-update support, manual "Check for updates" in Settings. Standing design in `docs/architecture/011-release-signing-and-updates.md`.

**Google Calendar, phase 1** — still own file at `docs/specs/03-google-calendar-integration.md`, because several code comments cite its resolved decisions by name. Built + merged: read-only, one-way (Google → app), Credential Manager auth, `calendar.readonly`, user-picked calendar list, tokens in `:core:security`'s Keystore-sealed `GoogleCalendarTokenStore`, local-only `cached_calendar_events` Room table deliberately outside `RoomSyncStore`. Out of scope, not built: writeback, two-way sync, multiple Google accounts, push-based live updates.

Full original text of all three in git history: `git log --follow -- docs/specs/<file>`.

---

## Where to look next

- `docs/architecture/` — standing decisions, incl. `012-push-wake-up.md`.
- `docs/guides/push-setup.md` — how FCM wake-up switched on, step by step.
- `CLAUDE.md` — how app actually works today.
- `git log --oneline feature/android-app` — all this file condenses.
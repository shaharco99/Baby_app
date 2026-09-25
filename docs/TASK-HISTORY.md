# Task history — resume here

One file for all that used to live in `docs/FOLLOWUP.md` and `docs/specs/`: what still open, what already done (so nobody redo), specs fully absorbed into code. Point Claude here to resume from last session.

Branch `feature/android-app`, pushed. Latest release **v1.11.2** (2026-09-23); Pixel runs it, Xiaomi on v1.11.1 (differences: spinner colour, calendar row height). Supabase migrations **0001–0013 applied; 0014 (diaper_change) applies with v1.15.0 tag**, only by `supabase-deploy.yml` pipeline — see rule in `CLAUDE.md`. Every migration file must stay idempotent.

`git log --oneline feature/android-app` = real history. This file = condensed version.

---

## Still open

1. **Doze (deep sleep) reminder check.** Parked by user; only they can do it: log feed on Pixel, leave unplugged, locked, still for 3h until reminder. Forced `adb` route fails — `dumpsys deviceidle force-idle deep` and `step deep` both stop at INACTIVE on Xiaomi (`mMotionActive=true`, 143 pending alarms). Cause never pinned; suspects: Android's "no deep idle within 30 min of wake-from-idle alarm" rule and MIUI's own power manager. Don't keep retrying on MIUI.
2. **Partner email on pairing screen, on real phone.** Function + pgTAP test green. Seeing it needs phone joined to workspace but not yet approved, so unpair + re-join. Parked by user 2026-09-21 — too disruptive for payoff.
3. **`device_keys` stale-on-reuse fix, on real phone.** Needs full leave-and-rejoin. Fix in; live rows checked in DB. Parked, same reason.
4. **Confirm before deleting session — done, confirmed on both phones (v1.11.x); remove from this list next pass.** Trash icon on pumping session and on feed now opens dialog naming what goes ("Delete the 14:04 session, 98 ml?" / "למחוק את השאיבה של 14:04, 98 מ״ל?"; no amount, no "ml"), Undo snackbar still follows. Delete inside edit sheet unchanged. Left: release, then check on both phones, both languages — tap Cancel, never Delete, on real row. Shipped v1.11.0; confirmed on Xiaomi (English) and Pixel (Hebrew), Cancel on real rows.

**Small open items:**
- No Macrobenchmark startup module. Needs spare device or emulator; benchmark build breaks release-only rule on real phones.
- Supabase security advisor: leaked-password protection off, few MFA options enabled. Both dashboard toggles — user's to flip. "SECURITY DEFINER callable by authenticated" warnings intended — every such RPC checks membership itself.

**Kept as Android does them, by user's choice — do not "fix":** Material date picker's month arrows in Hebrew (next on left), top-bar back arrow (points right in Hebrew).

**Closed 2026-09-21:** migration 0011 applied (table was hand-created during push work, so file rewritten guarded, now matches live schema column for column); `supabase/tests/005_device_push_tokens.sql` run first time, green; whole pgTAP suite clean from `db reset`, 66/66; v1.8.0 and v1.8.1 released + installed on both phones, drawer work checked in English/dark and Hebrew/light.

**Still unverified by eye:** cycle history row's end date and three prediction lines. Not visible on either phone — no cycle history logged, which is exactly how ISO end date survived so long. Confirming = inventing period data, so they stand on build + code alone. All else in v1.8.1 checked on both phones, both languages.

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
- **Found on phones, fixed in v1.16.1:** summary table headers broke mid-word ("Diaper/s", "חיתולי/ם", pre-existing "האכלו/ת") — column weights widened for feeds/diapers.
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

## 2026-09-24 (evening) — age wording, eggs on Home (v1.14.0)

- Age reads "1 week and 1 day", then "1 month, 2 weeks and 3 days" (years in front later); zero parts dropped. `BabyAge.weeksAfterMonths`/`daysAfterWeeks` (tested). The "and" is a resource pair because Hebrew fuses ו onto a word (ויום) but hyphenates before a digit (ו-3 ימים).
- Night-watch and stash eggs now also open by long-pressing Home's feed / pump card. Both dialogs and their strings moved to `:core:ui`'s `component/EasterEggDialogs.kt` (`:core:ui` now depends on `:core:domain`). Home state also now keeps `bookOfLoveVisible` across database emissions — before, any sync tick while the Book of Love was open closed it.
- **v1.13.0 checked on the Xiaomi (English/dark):** short birth card on top, no child chips, tap opens birth-details sheet (dismissed, nothing saved), import section in Settings. **Widget placed by the user on the Xiaomi:** flipped to "Feed overdue by" in the error colour at the due time, counting up. **Pre-existing, not fixed:** birth-details sheet's Save button sits under the navigation bar.

---

## 2026-09-24 (later) — Home reshaped (v1.13.0)

User's call, same day: birth card back on top but short — title with name, born date/time, one age line — then feed, then pump. Age is one line only: days in week one, "1 week, 1 day" after, months (+ years) once there is a month; the "8 days old" line is gone. Weight and birth place are off Home (still in the edit sheet and on the doctor summary). Whole card taps to the birth-details sheet (pencil icon as the hint, `onClickLabel`), long-press still the easter egg; the "Edit birth details" button is gone.

Moved to Settings: child switching (Home's chips removed — Settings' Children section already sets the active child) and "Import from web app" (new collapsible section; logic extracted from `HomeViewModel` into `:core:database`'s `importer/WebImporter`, so a feature doesn't own four repositories' writes). Unused home strings removed.

v1.12.1 (summary week from first feed, age separator) published before this; not separately checked on a phone — rolled into v1.13.0's check.

---

## 2026-09-24 — doctor summary, nappy counts, feed widget, Home feed-first

User picked from a suggestion list; declined: diaper log (marks on feeds are enough), sleep log, growth/percentiles, vaccine schedule, notification quick-log, backup export, retiring pregnancy screens, night mode, PDF export.

**Build + test + lint green. Nothing below seen on a phone yet.**

- **Urine/stool per day.** `FeedingDay.urineCount`/`stoolCount` (`:core:domain`); day line in both log views gains "3 urine · 1 stool" / "שתן 3 · צואה 1" after the guidance band, hidden on days with no marks, included in the merged TalkBack description.
- **Summary for the doctor** — in-app only, no PDF. "Summary" button beside the Feeding title opens a sub-screen (VM `doctorSummary` non-null, `BackHandler` closes). `:core:domain`'s `feeding/FeedingSummary.kt` (`doctorSummary`, `summarizeFeeds`, unit-tested): last 24h rolling (feeds, ml + breast/formula split, average interval, urine, stool); daily averages over the 7 **complete** days before today, clipped at birth date; vitamin D "N of M days"; bar chart of daily ml (plain boxes in a Row, so RTL mirrors itself) + day-by-day table. Read once on open, not live. Longest gap deliberately left out (user dropped it from night watch).
- **Home, baby mode:** feed card now first (above birth card), adds "Last feed at 14:05 · 2 h 10 min ago" and "Today: 6 feeds · 420 ml" (Hebrew plurals incl. two).
- **Feed widget** (`:app/widget/FeedWidget.kt`, RemoteViews, no new dependency). Chronometer counts down to due time / up once overdue, "Last feed at …". Reads two timestamps from plain prefs (`feed-widget`) written by `AlarmFeedingReminderScheduler` — the path every feed move goes through, incl. partner sync — so it works with the app locked; no child/amount stored. Re-drawn on feed move, when the feed alarm fires (label flips to overdue), on `rearmAll` (boot/update). Times visible on the home screen by design of a widget.
- **Audit leftovers:** Settings 7-tap title now `detectTapGestures` (no ripple, not a TalkBack button); cycle history row has paper-clip + chevron, ≥48dp, `onClickLabel` show/hide attachments; copy buttons toast "Copied" below Android 13 (`:core:ui` `text/confirmCopied`). **Card colour closed, not a bug:** `surface` is the app-wide card override (~17 cards: shopping, tasks, cycle, calendar, home, search, settings, pairing), not Folders drift as the 2026-09-23 audit assumed. User chose to keep it — do not "fix".

**Checked on the Xiaomi (v1.12.0, English/dark then Hebrew; installed via the startup update dialog, one-time Play Protect scan).** Home: feed card first, "Last feed at 09:27 · 2 h 39 min ago", "Today: 3 feeds · 180 ml"; Hebrew mirrors correctly. Feeding: "1 urine · 1 stool" under today's line; Summary button top end; summary renders both languages, bar chart runs newest-first from the reading end, table mirrors, back closes it. No data created or deleted.

**Found on the Xiaomi, fixed in v1.12.1:** the summary's week counted 17.9 — before the first feed was ever logged — as an empty day, pulling every average down (6.0 feeds/day). The week now starts at the first logged feed as well as the birth date. Also, the pre-existing "1 week,1 day" / "שבוע,יום": `home_age_separator` was `", "`, and aapt drops trailing whitespace, so it is now `,\u0020`. Grepped all strings.xml for other trailing-space values: none.

**Widget not placed by me:** long-pressing the MIUI home screen hit the clock widget and opened its Remove menu (dismissed with Back, nothing changed). Widget editing by tap is too close to deleting the user's layout. The provider, `xml/widget_feed_info` and layout are in the release APK; the user adds it by hand.

**To see on phones (release, both languages):** summary screen (chart direction in Hebrew, table widths at 200% font, "no full day yet" state), day-line marks wrapping, Home card order + two new lines, widget add/resize/dark, overdue flip at due time, widget after reboot, cycle row chevron.

---

## 2026-09-23 — UI/UX audit (docs/UIUX.md run end to end)

Grep-driven sweep of every `:feature:*` screen for classes of mistake (a11y descriptions, delete paths, raw error text, touch targets, hardcoded colours/sizes/strings, EN/IW string parity, FAB padding, empty/loading states). App mostly clean: every delete already confirmed, no hardcoded `.sp`, string parity exact (home's only gap = two `translatable="false"` emoji arrays), FAB lists padded.

**Fixed (build + test + lint green, not yet on a phone):**
- **P0 — recovery phrase copied as plain clipboard text.** Android 13+ shows copied text in system overlay; keyboards keep clipboard history. Now `:core:ui`'s `text/sensitiveClipEntry()` sets `EXTRA_IS_SENSITIVE` (literal key pre-33). Used by pairing + Settings copy buttons. Invite code still plain on purpose — meant to be shared.
- **P1 — raw exception text on screen.** Update dialog printed `"Update failed: " + error.toString()`; Settings' Google Calendar printed `exception.message` — English in Hebrew UI, no next step. Both now `@StringRes` in state, bilingual, "what happened → what to do".
- **P1 — Google Calendar picker lied on failure.** Failed fetch read "No calendars found on this account"; loading showed blank dialog (spinner behind it on card). Now four states: loading spinner, error, truly empty, list.
- **P1 — Folders speed-dial FAB.** Stayed "+" labelled "New folder" while its menu was open. Now turns into ×, labelled "Add a folder or document" / "Close".
- **P1 — Folders rows.** Only name text opened folder/previewed document. Whole card now tappable (`Card(onClick)`); long-press drag on documents unchanged. Breadcrumb crumbs got 12dp vertical padding — tap target was one text line.
- **P2 — Sign-in "Remember me".** Checkbox + separately clickable row = two TalkBack stops. Now one `toggleable(role = Checkbox)` row, ≥48dp.

**Slow taps, audited (same day).** Every ViewModel action that waits on network/crypto/files checked for visible progress. Already fine: sign-in/reset-password spinners, attachments, folders import, document preview, Google Calendar connect. Fixed with new `:core:ui` `component/BusyLabel` (spinner replaces label, button keeps width):
- **"Check for updates" did nothing visible.** `UpdateUiState.checking` existed, never rendered; a check finding nothing new ended in silence, and `settings_up_to_date` string was never used. Now spinner, then "You have the latest version" or "Could not check…" (manual checks only; startup check stays silent).
- **Pairing** (network + key derivation): create workspace, submit code, submit recovery phrase, check again, continue — spinner in button. "Check again" had no busy flag at all; now has one. Ready stage (approve / revoke / new invite share one flag) gets a progress bar under the title.
- **Settings sign-out** (waits on server), **Google account link** (moved to `BusyLabel`), **Home "import from web"** — spinner in button.
- Not changed: local Room writes (save/delete forms, cycle start/end, feeds) — instant, no spinner wanted.

**New repo skill** `.claude/skills/android-uiux/SKILL.md` — app-specific UI rules (RTL/LTR + bidi, strings, states, errors, deletes, slow taps, platform, device checks); loads on any Android UI change so new features follow it. Generic M3 rules deliberately left to `android-skills:android-ux`, which it points to; `docs/UIUX.md` trimmed to audit process only, same reason.

**Checked on the Xiaomi (v1.11.0, English/dark, then Hebrew via per-app locale).** Installed through the app's own updater (startup dialog → Install → system Update), not adb. "Check for updates": spinner in place of label, button width held, then "You have the latest version" / "יש לך את הגרסה העדכנית ביותר". Calendar picker: spinner inside dialog ~1s, then list (was blank). Documents: FAB turns × labelled "Close" / "סגירה"; card tap outside the name opens folder and previews document; tap at top edge of "Documents" crumb (outside text line) navigates; Hebrew layout mirrors (chevron, FAB on start side, trash at far end). Feed and pumping trash → "Delete the 08:46 feed, 70 ml?" / "Delete the 09:55 session, 80 ml?", Cancel kept both rows, day totals unchanged — **item 4 above confirmed on Xiaomi**. No data created or deleted.

**Found on device, fixed in v1.11.1:** long-press on a document without moving opened the preview on release (card click fired after the drag's long-press). Pre-existing on the name text; whole-card tap widened it. Click now suppressed from long-press until 300ms after drag end. **Confirmed on the Xiaomi (v1.11.1, installed via Settings → Check for updates → Install):** two 1s long-presses without moving → no preview; plain tap right after → preview opens; drag released on empty space → card springs back, no preview. Drag *into* a folder not exercised (would move a real document).

**Checked on the Pixel (v1.11.1, Hebrew/light, installed from the startup update dialog, 1.10.2 → 1.11.1 directly).** Update check → "יש לך את הגרסה העדכנית ביותר". Calendar picker: spinner in dialog, then list. Documents: card tap opens folder / previews document, long-press without moving → no preview, crumb tap at its top edge navigates, FAB × "סגירה" on the start (left) side, trash at the far end. Feed / pumping trash → "למחוק את ההאכלה של 08:46, 70 מ״ל?" / "למחוק את השאיבה של 09:55, 80 מ״ל?" — times and amounts in the right order, Cancel kept both rows. **Item 4 now confirmed on both phones, both languages.** No data created or deleted.

**Found on the Pixel, fixed in v1.11.2:** `BusyLabel`'s spinner took the disabled button's 38% content colour — all but invisible on the light theme; now `primary`. Google Calendar picker rows were one text line tall (~13dp) with the checkbox touching the name; now ≥48dp with a gap. **Both confirmed on the Pixel (v1.11.2, Hebrew/light):** picker rows 138px (~52dp, were ~35px), checkbox spaced from name, selection untouched; update-check spinner now green `primary` — on this network the check finishes in ~0.1s, so it only flashes. Pixel install needed a one-time Google Play Protect scan ("לסריקת האפליקציה") before the system update prompt.

**Still unseen:** error branches (update check failing, calendar fetch failing — need airplane mode), pairing spinners (fresh join only), sign-out and web-import spinners (destructive / create data), remember-me row (signed-out only), Pixel pass. Update-check spinner is faint on the disabled outlined button — readable, noted.

**Recommendations (P2/P3) — all three below done 2026-09-24, see that entry:**
- Settings title's 7-tap easter egg uses `clickable` — ripple on a heading, TalkBack announces it as a button. Swap to `pointerInput { detectTapGestures }`.
- Cycle history row toggles attachments by tapping its text, no affordance (chevron or "N attachments" hint).
- Copy buttons give no feedback below Android 13 (13+ shows system toast). Snackbar "Copied" on older.

---

## 2026-09-22 — vitamin D, the baby's age, and an easter-egg pass

**Vitamin D, reminded and ticked off.** New synced entity `VitaminDose` (`vitamin_doses`, Room 20→21, Supabase `0012` — enum value only, applied), plus `AppSettings.vitaminDMinuteOfDay`, shared by couple so both phones ask same hour and either can move it. Card at top of feeding screen sets time, ticks dose (undo deletes it); long-press opens last fortnight — plain list, deliberately no streak. `ReminderKind.VITAMIN_D` = first `repeatsDaily` alarm: arms next occurrence the moment it rings, from minute-of-day kept in plain preferences so receiver works with DB still locked; missed one **not** rung late on reboot like feed reminder. `VitaminReminderRefresher` re-derives it on workspace open, after sync pull, after any tick — that's what makes dose given on partner's phone silence this one.

**The baby's age on Home.** `:core:domain`'s `babyAge()` gives days, weeks + days, and calendar years/months/days (from `periodUntil`, so month = same day of month). Shown on `BirthStatsCard` under birth date, zero components dropped, Hebrew via real `<plurals>` (יום / יומיים / ימים).

**Easter eggs, audited.** All five read end to end. Three broken:
- **Book of Love and moon glitch unreachable** — hang off `MoonCountdown`, which only draws on pregnancy branch, so died once birth date entered. Gesture now also on `BirthStatsCard`, sharing one `glitchFlicker` helper with moon.
- **Book of Love tip re-rolled** every recomposition (`tips.random()` read in composition). Now picked once per opening.
- **"feeds logged in all" / "ml pumped, all told" meant "in the last 14 days"** — both panels tallied screen's window. Both now read whole log on long press.

**Removed:** night watch's "longest stretch between feeds" line (user's call — irrelevant), and `MilkStash.longestSessionMinutes`, computed + unit-tested but never rendered.

**Added to the two panels:** feed milestones (50/100/250/500/1000/2000), litres once past a litre, "logging since <date> · N days in", split between the two of you (`group by created_by` count, hidden unless both logged). Stash gained hours-and-minutes instead of raw minutes, plus litre milestone. Feed landing on milestone now gets drop animation, moved from `:feature:pumping` to `:core:ui`'s `component/DropFall.kt` as `DropBurst`/`DropFall` so both logs use it.

**Checked on the Xiaomi (v1.9.0, English then Hebrew).** Reminder = part worth recording: armed exact for 18:00 (`window=0`, `exactAllowReason=policy_permission`), notification posted 18:00:00.9 on `vitamin-reminders` channel, and **next day's alarm armed itself** for 23.9 18:00 — re-arm chain most likely thing to quietly break, and it holds. Tick reached Supabase as `vitamin_dose` row; undo removed it. Age read "6 days old" / "בגיל 6 ימים", no second line in first week, as designed. Night watch showed 31 feeds in all, 1.3 litres, "logging since 18 September", no longest-stretch line; stash showed 11h 46m, not 706 raw minutes.

**Two things device pass caught, fixed in v1.9.1:** vitamin history listed days *before birth* as "not logged"; stash milestone read "0.5 litres pumped, all in" right under "796 ml pumped, all told" — reads as contradiction, not threshold ("Past 0.5 litres" now).

**Still unseen:** feed milestone burst — log at 32 feeds, first milestone 50, faking 18 feeds into real log not worth it. Will show itself. (Book of Love seen on both phones in v1.10.0, see below.)

---

## 2026-09-22 (later) — presence, and what the Book of Love now asks

Book of Love asked wrong question. Surfaced when partner had *edited* task or shopping item in last five minutes — says yes long after they put phone down, no while they sit reading app untouched. Real question: are both holding phones same moment.

**`device_presence` (migration 0013, applied by the pipeline on v1.10.0).** One row per device: device id, owner, when it last said hello. Each phone upserts own row every 30s while app on screen — heartbeat rides `ForegroundSyncController`'s existing poll, no own timer — deletes it on way out. Heartbeat counts 90s (`PRESENCE_WINDOW_MILLIS`), well over beat gap, so one dropped request doesn't blink dot off. Nothing about couple, child, or workspace contents in table; nothing to encrypt.

Deliberately **not** Supabase Realtime: app has no websocket anywhere; adding one for presence dot = transport to keep alive, reconnect, pay for in battery. `PartnerPresence` in `:core:sync` = seam, `SupabasePartnerPresence` in `:core:network` = implementation; failure swallowed exactly like wake-up's — presence is ornament on sync that works without it.

Shown two places: Book of Love opens only when partner actually present; drawer header carries lit dot with "your partner is here right now".

`supabase/tests/006_device_presence.sql` covers what matters — table is claim about who's present, so member cannot announce device in someone else's name, cannot refresh or clear partner's heartbeat, cannot see another workspace's at all. Suite 73/73 from `db reset`.

**Migrations now have a pipeline, and by-hand is over.** `supabase-deploy.yml` = reusable workflow: proves every migration on empty DB (`db reset` + `test db`), only then links and pushes to live project; `android-release.yml` calls it as job release waits on, so on tag schema lands before APK. Also runs on `workflow_dispatch` for schema change without release. `CLAUDE.md` carries rule: nothing else applies migrations — not local CLI, not agent with DB access. `Supabase Access Control` workflow unchanged, still deploys nothing; clean-room check on branch pushes.

**The schema history was reconciled** same day: `0007`–`0011` recorded under timestamps (applied through MCP, which names migration by run time) and `0012` not at all, so six timestamped rows replaced with `0007`–`0012`. Bookkeeping only; row counts checked either side. `0013_device_presence` left **pending on purpose** — table exists from hand-apply, history row removed, pipeline's first job records it as no-op it now is.

**All thirteen migrations are genuinely idempotent now, which they were not.** `0001_init.sql` was plain `create table` / `create type` / `create index` / `create trigger` / `create policy` throughout, failed on second run; `0005_document_storage.sql`'s two storage policies had no guard. Both fixed + proved: every file re-applied against fully-migrated DB, 13/13 clean, seeded rows intact byte for byte.

---

## 2026-09-22 (night) — v1.10.0 to v1.10.2 on both phones

**The pipeline's first real run.** v1.10.0 stopped at migrations job until user added `SUPABASE_ACCESS_TOKEN` and `SUPABASE_DB_PASSWORD` to `production` environment; once approved, recorded `0013` as designed; live history now reads `0001`–`0013` by filename. v1.10.1 and v1.10.2 each re-ran it as no-op. (Parallel session reworked CI same evening — build parallel with migrations, then publish — see `fb10853`/`aa714e3`.)

**Checked on the Xiaomi (English, dark) and the Pixel (Hebrew, dark), release builds:**
- Presence: dot and "your partner is here right now" / "בן/בת הזוג כאן עכשיו" on both within one 30s beat; Book of Love opens on both only while other phone open, and now speaks own words — **"Will do" / "אעשה את זה"**, not date picker's "Set", which import dialog was also borrowing (now "OK" / "אישור").
- Vitamin row renders compact under "Log a feed", RTL in Hebrew; tick on Pixel showed on Xiaomi within ~5s, undo from either clears both; history dialog starts at birth date.
- Night watch: 32 feeds in all, 1.3 litres, "logging since 18 September · 5 days in". Stash: hours and minutes, "Past 0.5 litres". Every tab renders.
- **The milk drops were near invisible** (v1.10.2 fix): filled with `surfaceBright`, grey-purple one step off dark background. Now fixed milk white with warm `primary` rim, slightly larger — confirmed on screen recordings from both phones.

**A testing accident, recovered.** Cleaning up test pumping session, tap aimed at Undo snackbar's X landed on next row's trash icon after snackbar gone — twice — deleting two real sessions (14:04, 98 ml; 18:31, 85 ml). Trash has no confirmation. Both restored with user's OK by un-tombstoning exactly those two `records` rows (`deleted_at = null`, `updated_at = now()`, `version + 1`): ciphertext survives delete and `applyRemote` takes un-delete, so both phones pulled them back intact. Every other deletion in window checked against server — test data or user's own.

**Open from this pass:**
- Presence goodbye can fail with 401 as app backgrounds (seen once on Pixel), so partner's dot can linger up to 90s window. Suspect: supabase-kt's own lifecycle hook dropping session before our `onStop` delete lands. Harmless; not chased.
- Deleting pump session (and feed) = one tap with only Undo snackbar — user decided it gets confirm dialog; tracked as item 4 under "Still open".

---

## Known limits, by design

- **Force-stop** (Settings → Force stop, or MIUI "clean" on non-whitelisted app) drops all app's alarms and blocks FCM until next opened by hand. Nothing app can do — and why `am force-stop` is wrong way to simulate closed app in test. Use `am kill` after backgrounding, which is what swipe-away actually does.
- Background decryption relies on `DeviceIdentity`'s Keystore-sealed copy of workspace key, so locked app does decrypt in background for sync. Trade-off written up in `docs/architecture/012-push-wake-up.md`.

---

## 2026-09-21 — feeding guidance, breast + formula, push, drawers

**Feeding guidance by age.** `:core:domain`'s `baby/BabyAge.kt` (`ageInDays`, birth day = day 0) and `feeding/FeedGuidance.kt` — day-by-day table through week one, then widening to weeks and months, clamped at AAP's 960 ml/day. Shown on countdown card and each day's total line, computed **per day**, so scrolling back shows band that applied then. Sources cited in file's doc comment.

**A feed can be breast and formula at once.** `FeedingEntry` gained `breastMl`/`formulaMl` (migration 19→20, backfilled from `amount_ml` by `feed_type`); `amountMl` kept as legacy mirror of total so partner on older build still reads right number. History rows keep single amount slot, show sum; per-source breakdown only on day line, with bottle icon and curly-haired-woman icon. Log sheet has two labelled fields.

**Push wake-up, verified both directions.** Migration 0011 `device_push_tokens`, `notify-workspace` edge function, FCM data-only high-priority messages. Alarm moved on receiving phone within ~5s (Xiaomi) / ~10s (Pixel) with app killed. Three separate bugs each made whole thing silent no-op — see `docs/architecture/012-push-wake-up.md` and `docs/guides/push-setup.md`. MIUI Autostart / battery exemption turned out **not** needed, though only tested plugged in and recently used.

**Theme fix with the widest blast radius.** Colour scheme never defined Material 3 *container* roles, so every component reading `primaryContainer`/`secondaryContainer` fell back to default lavender. Defined in both schemes, plus surface ladder — `surfaceContainerHighest` must be **card** colour, because filled `Card` reads it.

**Drawers for finished things.** `:core:ui`'s `component/CollapsibleDrawer.kt` gives one bar — chevron, title, count, one TalkBack announcement — used by:

| Screen | What folds away | Open by default |
|---|---|---|
| Shopping | bought items | still needed / ordered |
| Tasks | done tasks (honouring the active filters) | open tasks |
| Cycle | the history list | now / prediction / statistics / grid |
| Feeding, Pumping | days before yesterday, in both list and table view | today + yesterday |
| Settings | Account, Recovery, Devices, Google Calendar | Children, Security, Notifications |

Every drawer shut on arrival, not remembered across visits: lists read for what's still outstanding; drawer left open would quietly undo that. In two logs state hoisted above list/table switch, so both views of same log agree. Search can point at row inside drawer, so shopping and tasks open theirs first, then scroll by row's index in list actually emitted — open rows, then bar, then drawer contents. `:core:domain`'s `log/splitLogDays` does day split, unit-tested.

**The undo snackbar never went away.** Material 3's `showSnackbar` silently picks `SnackbarDuration.Indefinite` once `actionLabel` passed, so "feed deleted · undo" sat over list until tapped. Both logs now state `SnackbarDuration.Long` and `withDismissAction = true`.

---

## 2026-09-21 (later) — v1.8.0 and v1.8.1 on both phones

Released from tag, per `011-release-signing-and-updates.md`: CI runs tests + lint, builds signed APK, publishes. Both phones took it as update, never uninstall.

**Verified on the Xiaomi (English, dark) and the Pixel (Hebrew, light):** every drawer shut on arrival, opened on tap; task ticked done moved into drawer, count 11 to 12; shopping drawer count agreed with budget card's "5/6"; log kept today + yesterday open, folded two earlier days; drawer stayed open across list/table switch; Friday 18.9 showed own guidance band (80–180 ml, day 2), not today's. Undo snackbar appeared with X, gone by t+13s — used to sit forever.

All test data created and removed: one task, one feed. Feed's delete restored countdown to 15:27 and day total to 215 ml; Pixel had both changes untouched — push wake-up doing its job again.

**v1.8.1 confirmed on both phones.** On Pixel in Hebrew: "פעיל/ה · נולד/ה ב-16 בספטמבר 2026" on one line, "21 בספטמבר 2026" on one line in wider of two pills (date rightmost, as RTL puts it first), all four legend swatches visible in light theme, including one that used to be transparent.

**Found and fixed in v1.8.1:** ISO dates only half cleaned up before — cycle history row spelled start date but printed end as "2026-09-20", prediction card printed all three dates that way, date-picker buttons across task, shopping, calendar, home and child forms all showed ISO form, as did shopping warranty line. Cycle legend's "Predicted period" swatch was `Color.Transparent`, so legend listed four things, showed three colours. Settings said "Active · born" and stopped mid-sentence. Log sheet's date pill wrapped to two lines beside two-thirds-empty time pill.

---

## 2026-09-19 — v1.7.0: cold start, alarms, live sync, conflicts, RTL

**Cold start.** SQLCipher re-ran key derivation (~0.6s) on each of 4 WAL pool connections; Home showed empty "last period" state 3–8s. Now TRUNCATE journaling (one connection), name screen stays up until Home's first data. Xiaomi first frame ~0.52s (was ~1.03s), populated Home ~1.4s (was 4–8s); Pixel first frame ~150ms, no empty frame in screen recording.

**Reminders** moved from WorkManager to exact `AlarmManager` alarms. Rang on time with app swiped away, process killed, screen off. After reboot MIUI delivered `BOOT_COMPLETED` ~3 min late; alarm re-armed, rang on time.

**Both-phone pass, all green:** feed logged on one phone moved other's alarm; conflict (same feed edited offline on both) showed conflict screen, both then agreed; pump timer started on one showed running on other, pausing from either froze both; interval changes and child rename synced both ways. All test data reverted.

**Fixed the same day:** conflict card now lists differing fields; Cycle grid's "12" no longer wraps; Calendar dots centred under day; partner changes arrive within ~30s while app open (foreground poll); Calendar legend chip no longer wraps mid-word; month arrows go right = next in every language, folder breadcrumb separator mirrors.

**Partner identity on the pairing screen** — "Waiting on: &lt;partner email&gt;" from `workspace_partner_emails(ws)` (migration 0010, applied; pgTAP in `supabase/tests/004_partner_emails.sql`).

**`device_keys.workspace_id` stale on reuse** — `registerDevice()` now keeps cached device-key id only while that id is live device of workspace being joined; `devices()` scoped to given workspace. Old workspace still has 6 orphaned rows; harmless, leave them.

---

## 2026-08 — earlier sessions, condensed

**v1.3.1 crash fixes.** Documents screen crashed on open: `GmsDocumentScanning.getClient()` ran eagerly in `remember{}` and threw when ML Kit module unavailable, killing screen before Scan tapped. Same shape in invite-code QR scanner. Both now wrap client creation in `runCatching`. Recovery-phrase screen crashed because `RecoveryPhrase.kt` looked up BIP-39 wordlist with *package-relative* `getResourceAsStream`, which breaks once R8 repackages obfuscated class — invisible in unit tests, whose classpath not R8'd. **If similar `getResourceAsStream` turns up anywhere, use absolute classpath path from start.**

**Google OAuth** (`resolved-then-broken` saga, condensed): release Android OAuth client's SHA-1 had one-character typo in Cloud Console vs real release keystore fingerprint. Corrected 2026-08-17, retested end-to-end on both phones. Google Calendar connect, sharing client, explicitly retested 2026-08-21 — both phones show "Connected as …". Memory records earlier "confirmed working" claim here was false positive, so verify end-to-end, don't trust prior self-report.

**Done, don't redo:** QR pairing (scan partner's code, show/copy own as QR + text); shopping decimal prices, receipt attachments, need→ordered→bought sort, purchase-date/warranty months, home budget split paid-vs-gifts; task priority/assignee filters and "can't delete the last task" FAB overlap; folder/document rename and drag-and-drop; `:feature:dates` removed into `:feature:calendar`; CI release builds actually configured with Supabase; same-commit-retag version mislabeling bug; pairing "Sign out" escape hatch; Google-Calendar-style month view; rename to "Takes Two of Us" (`SaharApp`/`SaharApplication` → `TakesTwoApp`/`TakesTwoApplication` — "Shahar" partner-name string unrelated, untouched); co-op-game easter eggs (long-press moon → split-world glitch and "Book of Love" tip when partner active in last 5 minutes; 7-tap Settings title → toast); installed version string under Settings' "Check for updates".

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
# Task history — resume here

One file for everything that used to live in `docs/FOLLOWUP.md` and `docs/specs/`: what is still
open, what was already done (so nobody redoes it), and the specs that have been fully absorbed
into the code. Point Claude at this file to pick up where the last session left off.

Branch `feature/android-app`, pushed. Latest release **v1.8.1** (2026-09-21, all workflows
green); both phones run it. Supabase migrations **0001–0012 all applied** (0012 on 2026-09-22,
confirmed against the live enum). Migrations are
applied by hand and **must always be written idempotent** — see the rule in `CLAUDE.md`; tagging a
release before applying one breaks sync silently.

`git log --oneline feature/android-app` is the real history. This file is the condensed version.

---

## Still open

1. **Doze (deep sleep) reminder check.** Parked by the user, and only they can do it: log a feed on
   the Pixel, then leave it unplugged, locked and still for the 3h until the reminder. The forced
   `adb` route does not work — `dumpsys deviceidle force-idle deep` and `step deep` both stop at
   INACTIVE on the Xiaomi (`mMotionActive=true`, 143 pending alarms). Cause never pinned down;
   suspects are Android's "no deep idle within 30 min of a wake-from-idle alarm" rule and MIUI's own
   power manager. Don't keep retrying it on MIUI.
2. **Partner email on the pairing screen, on a real phone.** The function and its pgTAP test are
   green. Seeing it needs a phone that has joined a workspace but has not been approved yet, so it
   means unpairing and re-joining. Parked by the user 2026-09-21 as too disruptive for the payoff.
3. **`device_keys` stale-on-reuse fix, on a real phone.** Needs a full leave-and-rejoin. The fix is
   in and the live rows were checked in the database. Parked for the same reason.
4. **Confirm before deleting a session.** The user's decision, 2026-09-22: deleting a pumping
   session — and a feed, same one-tap trash — gets a confirm dialog naming what goes ("Delete the
   14:04 session, 98 ml?"). Today it is one tap plus an Undo snackbar, and one mistimed tap deleted
   two real sessions during testing (see the v1.10 pass below). Keep the Undo snackbar as well.

**Small open items:**
- No Macrobenchmark startup module. It needs a spare device or an emulator; a benchmark build
  breaks the release-only rule on the real phones.
- Supabase security advisor: leaked-password protection is off and few MFA options are enabled.
  Both are dashboard toggles, so they are the user's to flip. The "SECURITY DEFINER callable by
  authenticated" warnings are intended — every such RPC checks membership itself.

**Kept as Android does them, by the user's choice — do not "fix":** the Material date picker's
month arrows in Hebrew (next is on the left), and the top-bar back arrow (points right in Hebrew).

**Closed 2026-09-21:** migration 0011 applied (the table had been created by hand during the push
work, so the file was rewritten guarded and now matches the live schema column for column);
`supabase/tests/005_device_push_tokens.sql` executed for the first time and green; the whole pgTAP
suite runs clean from a `db reset`, 66/66; v1.8.0 and v1.8.1 released and installed on both phones,
with the drawer work looked at in English/dark and Hebrew/light.

**Still unverified by eye:** the cycle history row's end date and the three prediction lines. They
cannot be seen on either phone — there is no cycle history logged, which is exactly how an ISO end
date survived this long. Confirming them would mean inventing period data, so they stand on the
build and the code alone. Everything else in v1.8.1 was checked on both phones in both languages.

---

## 2026-09-22 — vitamin D, the baby's age, and an easter-egg pass

**Vitamin D, reminded and ticked off.** New synced entity `VitaminDose` (`vitamin_doses`, Room 20→21,
Supabase `0012` — enum value only, applied), plus `AppSettings.vitaminDMinuteOfDay`,
shared by the couple so both phones ask at the same hour and either can move it. A card at the top
of the feeding screen sets the time, ticks the dose (undo deletes it), and long-presses open the
last fortnight — a plain list, deliberately no streak. `ReminderKind.VITAMIN_D` is the first
`repeatsDaily` alarm: it arms the next occurrence the moment it rings, from a minute-of-day kept in
plain preferences so the receiver works with the database still locked, and a missed one is **not**
rung late on reboot the way a feed reminder is. `VitaminReminderRefresher` re-derives it on
workspace open, after a sync pull and after any tick — which is what makes a dose given on the
partner's phone silence this one.

**The baby's age on Home.** `:core:domain`'s `babyAge()` gives days, weeks + days, and the calendar
years/months/days (from `periodUntil`, so a month is the same day of the month). Shown on
`BirthStatsCard` under the birth date, zero components dropped, Hebrew through real `<plurals>`
(יום / יומיים / ימים).

**Easter eggs, audited.** All five were read end to end. Three were broken:
- **Book of Love and the moon glitch were unreachable** — they hang off `MoonCountdown`, which only
  draws on the pregnancy branch, so they died the day the birth date was entered. The gesture now
  also lives on `BirthStatsCard`, sharing one `glitchFlicker` helper with the moon.
- **The Book of Love tip re-rolled** on every recomposition (`tips.random()` read in composition).
  Picked once per opening now.
- **"feeds logged in all" / "ml pumped, all told" meant "in the last 14 days"** — both panels
  tallied the screen's window. Both now read the whole log on the long press.

**Removed:** the night watch's "longest stretch between feeds" line (the user's call — irrelevant),
and `MilkStash.longestSessionMinutes`, which was computed and unit-tested but never rendered.

**Added to the two panels:** feed milestones (50/100/250/500/1000/2000), litres once past a litre,
"logging since <date> · N days in", and the split between the two of you (a `group by created_by`
count, hidden unless both have logged). The stash gained hours-and-minutes instead of raw minutes
and a litre milestone. The feed that actually lands on a milestone now gets the drop animation,
which moved from `:feature:pumping` to `:core:ui`'s `component/DropFall.kt` as `DropBurst`/`DropFall`
so both logs can use it.

**Checked on the Xiaomi (v1.9.0, English then Hebrew).** The reminder is the part worth recording:
armed exact for 18:00 (`window=0`, `exactAllowReason=policy_permission`), the notification posted at
18:00:00.9 on the `vitamin-reminders` channel, and **the next day's alarm armed itself** for
23.9 18:00 — the re-arm chain is the thing most likely to be quietly broken, and it holds. The tick
reached Supabase as a `vitamin_dose` row; undo removed it. Age read "6 days old" / "בגיל 6 ימים",
with no second line in the first week, as designed. The night watch showed 31 feeds in all, 1.3
litres and "logging since 18 September", with no longest-stretch line; the stash showed 11h 46m
rather than 706 raw minutes.

**Two things the device pass caught, fixed in v1.9.1:** the vitamin history listed days *before the
birth* as "not logged", and the stash's milestone read "0.5 litres pumped, all in" directly under
"796 ml pumped, all told", which reads as a contradiction rather than a threshold ("Past 0.5
litres" now).

**Still unseen:** the feed milestone burst — the log is at 32 feeds and the first milestone is 50,
and faking 18 feeds into the real log is not worth it. It will show itself. (The Book of Love was
seen on both phones in v1.10.0, see below.)

---

## 2026-09-22 (later) — presence, and what the Book of Love now asks

The Book of Love asked the wrong question. It surfaced when the partner had *edited* a task or a
shopping item in the last five minutes, which says yes long after they have put the phone down and
no while they sit reading the app without touching anything. What it wanted to know was whether
both of you are holding your phones at the same moment.

**`device_presence` (migration 0013, applied by the pipeline on v1.10.0).** One row per device: device id, whose it
is, and when it last said hello. Each phone upserts its own row every 30 seconds while the app is
on screen — the heartbeat rides `ForegroundSyncController`'s existing poll rather than owning a
timer — and deletes it on the way out. A heartbeat counts for 90 seconds
(`PRESENCE_WINDOW_MILLIS`), comfortably more than the gap between beats, so one dropped request
does not blink the dot off. Nothing about the couple, the child or the workspace's contents is in
the table; there is nothing there to encrypt.

Deliberately **not** Supabase Realtime: the app has no websocket anywhere, and adding one for a
presence dot would mean a transport to keep alive, reconnect and pay for in battery. `PartnerPresence`
in `:core:sync` is the seam, `SupabasePartnerPresence` in `:core:network` the implementation, and
failure is swallowed exactly like the wake-up's — presence is an ornament on a sync that works
without it.

Shown in two places: the Book of Love now opens only when the partner is actually present, and the
drawer header carries a lit dot with "your partner is here right now".

`supabase/tests/006_device_presence.sql` covers the part that matters — the table is a claim about
who is present, so a member cannot announce a device in someone else's name, cannot refresh or
clear the partner's heartbeat, and cannot see another workspace's at all. The suite is 73/73 from a
`db reset`.

**Migrations now have a pipeline, and by-hand is over.** `supabase-deploy.yml` is a reusable
workflow that proves every migration on an empty database (`db reset` + `test db`) and only then
links and pushes to the live project; `android-release.yml` calls it as a job the release waits on,
so on a tag the schema lands before the APK. It also runs on `workflow_dispatch` for a schema
change without a release. `CLAUDE.md` carries the rule: nothing else applies migrations — not a
local CLI, not an agent with database access. The `Supabase Access Control` workflow is unchanged
and still deploys nothing; it is the clean-room check on branch pushes.

**The schema history was reconciled** the same day: `0007`–`0011` had been recorded under
timestamps (applied through the MCP, which names a migration for when it ran) and `0012` not at
all, so the six timestamped rows were replaced with `0007`–`0012`. Bookkeeping only; row counts
checked either side. `0013_device_presence` is left **pending on purpose** — its table exists from
a hand-apply, its history row was removed, and the pipeline's first job will record it as the
no-op it now is.

**All thirteen migrations are genuinely idempotent now, which they were not.** `0001_init.sql` was
plain `create table` / `create type` / `create index` / `create trigger` / `create policy`
throughout and failed on a second run; `0005_document_storage.sql`'s two storage policies had no
guard. Both fixed, and proved: every file re-applied against a fully-migrated database, 13/13 clean,
with seeded rows intact byte for byte afterwards.

---

## 2026-09-22 (night) — v1.10.0 to v1.10.2 on both phones

**The pipeline's first real run.** v1.10.0 stopped at the migrations job until the user added
`SUPABASE_ACCESS_TOKEN` and `SUPABASE_DB_PASSWORD` to the `production` environment; once approved it
recorded `0013` as designed, and the live history now reads `0001`–`0013` by filename. v1.10.1 and
v1.10.2 each re-ran it as a no-op. (A parallel session reworked the CI the same evening — build in
parallel with migrations, then publish — see `fb10853`/`aa714e3`.)

**Checked on the Xiaomi (English, dark) and the Pixel (Hebrew, dark), release builds:**
- Presence: the dot and "your partner is here right now" / "בן/בת הזוג כאן עכשיו" on both within
  one 30s beat; the Book of Love opens on both only while the other phone is open, and says so in
  its own words now — **"Will do" / "אעשה את זה"**, not the date picker's "Set", which the import
  dialog was also borrowing (now "OK" / "אישור").
- The vitamin row renders compact under "Log a feed", right-to-left in Hebrew; a tick on the Pixel
  showed on the Xiaomi within ~5s, undo from either phone clears it for both; the history dialog
  starts at the birth date.
- Night watch: 32 feeds in all, 1.3 litres, "logging since 18 September · 5 days in". Stash: hours
  and minutes, "Past 0.5 litres". Every tab renders.
- **The milk drops were near invisible** (v1.10.2 fix): they were filled with `surfaceBright`, a
  grey-purple one step off the dark background. Now a fixed milk white with a warm `primary` rim,
  a little larger — confirmed on screen recordings from both phones.

**A testing accident, recovered.** Cleaning up a test pumping session, a tap aimed at the Undo
snackbar's X landed on the next row's trash icon after the snackbar had gone — twice — deleting
two real sessions (14:04, 98 ml; 18:31, 85 ml). Trash has no confirmation. Both were restored with
the user's OK by un-tombstoning exactly those two `records` rows (`deleted_at = null`,
`updated_at = now()`, `version + 1`): the ciphertext survives a delete and `applyRemote` takes the
un-delete, so both phones pulled them back intact. Every other deletion in the window was checked
against the server and was test data or the user's own.

**Open from this pass:**
- The presence goodbye can fail with a 401 as the app backgrounds (seen once on the Pixel), so the
  partner's dot can linger up to the 90s window. Suspect: supabase-kt's own lifecycle hook dropping
  the session before our `onStop` delete lands. Harmless; not chased.
- Deleting a pump session (and a feed) is one tap with only an Undo snackbar — the user decided it
  gets a confirm dialog; tracked as item 4 under "Still open".

---

## Known limits, by design

- **Force-stop** (Settings → Force stop, or MIUI "clean" on a non-whitelisted app) drops all of an
  app's alarms and blocks FCM until it is next opened by hand. Nothing an app can do about that —
  and it is why `am force-stop` is the wrong way to simulate a closed app in a test. Use `am kill`
  after backgrounding, which is what a swipe-away actually does.
- Background decryption relies on `DeviceIdentity`'s Keystore-sealed copy of the workspace key, so
  a locked app does decrypt in the background for sync. The trade-off is written up in
  `docs/architecture/012-push-wake-up.md`.

---

## 2026-09-21 — feeding guidance, breast + formula, push, drawers

**Feeding guidance by age.** `:core:domain`'s `baby/BabyAge.kt` (`ageInDays`, birth day is day 0)
and `feeding/FeedGuidance.kt` — a day-by-day table through week one, then widening to weeks and
months, clamped at the AAP's 960 ml/day. Shown on the countdown card and on each day's total line,
computed **per day**, so scrolling back shows the band that applied then. Sources are cited in the
file's doc comment.

**A feed can be breast and formula at once.** `FeedingEntry` gained `breastMl`/`formulaMl`
(migration 19→20, backfilled from `amount_ml` by `feed_type`); `amountMl` is kept as a legacy
mirror of the total so a partner on an older build still reads the right number. History rows keep
their single amount slot and show the sum; the per-source breakdown appears only on the day line,
with a bottle icon and a curly-haired-woman icon. The log sheet has two labelled fields.

**Push wake-up, verified both directions.** Migration 0011 `device_push_tokens`, the
`notify-workspace` edge function, FCM data-only high-priority messages. Alarm moved on the
receiving phone within ~5s (Xiaomi) / ~10s (Pixel) with its app killed. Three separate bugs each
made the whole thing a silent no-op — see `docs/architecture/012-push-wake-up.md` and
`docs/guides/push-setup.md`. MIUI Autostart / battery exemption turned out **not** to be needed,
though that was only tested plugged in and recently used.

**Theme fix with the widest blast radius.** The colour scheme never defined the Material 3
*container* roles, so every component reading `primaryContainer`/`secondaryContainer` fell back to
the default lavender. Defined in both schemes, along with the surface ladder —
`surfaceContainerHighest` must be the **card** colour, because that is what a filled `Card` reads.

**Drawers for finished things.** `:core:ui`'s `component/CollapsibleDrawer.kt` gives one bar —
chevron, title, count, one TalkBack announcement — used by:

| Screen | What folds away | Open by default |
|---|---|---|
| Shopping | bought items | still needed / ordered |
| Tasks | done tasks (honouring the active filters) | open tasks |
| Cycle | the history list | now / prediction / statistics / grid |
| Feeding, Pumping | days before yesterday, in both list and table view | today + yesterday |
| Settings | Account, Recovery, Devices, Google Calendar | Children, Security, Notifications |

Every drawer is shut on arrival and not remembered across visits: these lists are read for what is
still outstanding, and a drawer left open would quietly undo that. In the two logs the state is
hoisted above the list/table switch, so the two views of the same log agree. Search can point at a
row inside a drawer, so shopping and tasks open theirs first and then scroll by the row's index in
the list actually emitted — open rows, then the bar, then the drawer's contents.
`:core:domain`'s `log/splitLogDays` does the day split, unit-tested.

**The undo snackbar never went away.** Material 3's `showSnackbar` silently picks
`SnackbarDuration.Indefinite` as soon as an `actionLabel` is passed, so "feed deleted · undo" sat
over the list until it was tapped. Both logs now state `SnackbarDuration.Long` and
`withDismissAction = true`.

---

## 2026-09-21 (later) — v1.8.0 and v1.8.1 on both phones

Released from the tag, as `011-release-signing-and-updates.md` sets out: CI runs tests and lint,
builds the signed APK and publishes it. Both phones took it as an update, never an uninstall.

**Verified on the Xiaomi (English, dark) and the Pixel (Hebrew, light):** every drawer shut on
arrival and opened on tap; a task ticked done moved into its drawer and the count went 11 to 12;
the shopping drawer's count agreed with the budget card's "5/6"; the log kept today and yesterday
open and folded two earlier days; the drawer stayed open across the list/table switch; and Friday
18.9 showed its own guidance band (80–180 ml, day 2) rather than today's. The undo snackbar
appeared with its X, and was gone by t+13s — it used to sit there forever.

All test data was created and removed: one task, one feed. The feed's delete restored the
countdown to 15:27 and the day total to 215 ml, and the Pixel had both changes without being
touched, which is the push wake-up doing its job again.

**v1.8.1 confirmed on both phones.** On the Pixel in Hebrew: "פעיל/ה · נולד/ה ב-16 בספטמבר 2026"
on one line, "21 בספטמבר 2026" on one line in the wider of the two pills (the date sits rightmost, as
RTL puts it first), and all four legend swatches visible in light theme, including the one that
used to be transparent.

**Found and fixed in v1.8.1:** ISO dates were only half cleaned up before — a cycle history row
spelled its start date and printed the end one as "2026-09-20", the prediction card printed all
three of its dates that way, and the date-picker buttons across the task, shopping, calendar, home
and child forms all showed the ISO form, as did the shopping warranty line. The cycle legend's
"Predicted period" swatch was `Color.Transparent`, so the legend listed four things and showed
three colours. Settings said "Active · born" and stopped mid-sentence. The log sheet's date pill
wrapped onto two lines beside a two-thirds-empty time pill.

---

## 2026-09-19 — v1.7.0: cold start, alarms, live sync, conflicts, RTL

**Cold start.** SQLCipher re-ran key derivation (~0.6s) on each of 4 WAL pool connections, and
Home showed its empty "last period" state for 3–8s. Now TRUNCATE journaling (one connection), and
the name screen stays up until Home's first data. Xiaomi first frame ~0.52s (was ~1.03s),
populated Home ~1.4s (was 4–8s); Pixel first frame ~150ms, no empty frame in a screen recording.

**Reminders** moved from WorkManager to exact `AlarmManager` alarms. Rang on time with the app
swiped away, process killed and screen off. After a reboot MIUI delivered `BOOT_COMPLETED` ~3 min
late; the alarm re-armed and rang on time.

**Both-phone pass, all green:** a feed logged on one phone moved the other's alarm; a conflict
(same feed edited offline on both) showed the conflict screen and both then agreed; a pump timer
started on one showed running on the other, and pausing from either froze both; interval changes
and a child rename synced both ways. All test data was reverted.

**Fixed the same day:** the conflict card now lists the fields that differ; the Cycle grid's "12"
no longer wraps; Calendar dots are centred under their day; partner changes arrive within ~30s
while the app is open (a foreground poll); the Calendar legend chip no longer wraps mid-word; the
month arrows go right = next in every language, and the folder breadcrumb separator mirrors.

**Partner identity on the pairing screen** — "Waiting on: &lt;partner email&gt;" from
`workspace_partner_emails(ws)` (migration 0010, applied; pgTAP in
`supabase/tests/004_partner_emails.sql`).

**`device_keys.workspace_id` stale on reuse** — `registerDevice()` now keeps its cached device-key
id only while that id is a live device of the workspace being joined, and `devices()` is scoped to
the given workspace. The old workspace still has 6 orphaned rows; harmless, leave them.

---

## 2026-08 — earlier sessions, condensed

**v1.3.1 crash fixes.** The Documents screen crashed on open: `GmsDocumentScanning.getClient()`
ran eagerly in a `remember{}` and threw when the ML Kit module was unavailable, taking the screen
down before Scan was ever tapped. Same shape in the invite-code QR scanner. Both now wrap client
creation in `runCatching`. The recovery-phrase screen crashed because `RecoveryPhrase.kt` looked
up its BIP-39 wordlist with a *package-relative* `getResourceAsStream`, which breaks once R8
repackages the obfuscated class — invisible in unit tests, whose classpath is not R8'd. **If a
similar `getResourceAsStream` turns up anywhere, use an absolute classpath path from the start.**

**Google OAuth** (`resolved-then-broken` saga, condensed): the release Android OAuth client's
SHA-1 had a one-character typo in Cloud Console against the real release keystore fingerprint.
Corrected 2026-08-17 and retested end-to-end on both phones. Google Calendar connect, which shares
the client, was explicitly retested 2026-08-21 — both phones show "Connected as …". Memory records
that an earlier "confirmed working" claim here was a false positive, so verify end-to-end rather
than trusting a prior self-report.

**Done, don't redo:** QR pairing (scan a partner's code, show/copy your own as QR + text);
shopping decimal prices, receipt attachments, need→ordered→bought sort, purchase-date/warranty
months, home budget split paid-vs-gifts; task priority/assignee filters and the "can't delete the
last task" FAB overlap; folder/document rename and drag-and-drop; `:feature:dates` removed into
`:feature:calendar`; CI release builds actually configured with Supabase; the same-commit-retag
version mislabeling bug; a pairing "Sign out" escape hatch; the Google-Calendar-style month view;
the rename to "Takes Two of Us" (`SaharApp`/`SaharApplication` → `TakesTwoApp`/
`TakesTwoApplication` — the "Shahar" partner-name string is unrelated and untouched); the
co-op-game easter eggs (long-press moon → split-world glitch and a "Book of Love" tip when the
partner has been active in the last 5 minutes; 7-tap Settings title → toast); and the installed
version string under Settings' "Check for updates".

---

## Specs, absorbed

**Android conversion** (was `docs/specs/01-android-conversion.md`) — the original 86-section
requirements spec. Every requirement is implemented. One correction worth keeping: it proposed a
private-by-default, partner-vs-partner sharing model for cycle data. That was wrong. The
implemented threat model is **couple-vs-outside-world** — both partners see all workspace data,
end-to-end encrypted so that Supabase, an attacker and a lost phone are what is kept out. See
`docs/architecture/005-data-privacy.md`.

**Auto-update** (was `docs/specs/02-auto-update.md`) — fully implemented as `:core:update` +
`:feature:update`: semver comparison, an async non-blocking startup check, a download with SHA-256
verification, a `PackageInstaller` install flow, mandatory-update support, and a manual "Check for
updates" in Settings. Standing design in
`docs/architecture/011-release-signing-and-updates.md`.

**Google Calendar, phase 1** — still its own file at
`docs/specs/03-google-calendar-integration.md`, because several code comments cite its resolved
decisions by name. Built and merged: read-only, one-way (Google → app), Credential Manager auth,
`calendar.readonly`, a user-picked calendar list, tokens in `:core:security`'s Keystore-sealed
`GoogleCalendarTokenStore`, and a local-only `cached_calendar_events` Room table deliberately
outside `RoomSyncStore`. Out of scope and not built: writeback, two-way sync, multiple Google
accounts, push-based live updates.

Full original text of all three is in git history: `git log --follow -- docs/specs/<file>`.

---

## Where to look next

- `docs/architecture/` — the standing decisions, including `012-push-wake-up.md`.
- `docs/guides/push-setup.md` — how the FCM wake-up was switched on, step by step.
- `CLAUDE.md` — how the app actually works today.
- `git log --oneline feature/android-app` — everything this file condenses.

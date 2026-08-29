# Follow-up — resume here

Point Claude at this file to pick up exactly where this session left off.
Branch `feature/android-app`. Latest tag `v1.3.7` — see `git log` for full history; this file
only tracks what's still open plus enough context to act on it.

## Resolved 2026-08-22 — v1.3.7 on-device verification (MIUI done; Pixel two-device check done, rest still open)

MIUI phone (`b6d8682a049d`) was already on v1.3.7 (no update needed). Verified live:
- Search tab pull-to-refresh: swiped down over search results, app survived, no crash.
- Book of Love dialog icon pair: confirmed in source (`HomeScreen.kt` — overlapping
  `Icons.AutoMirrored.Filled.MenuBook` + `Icons.Filled.Favorite`) and by user's own manual check.
- Moon long-press glitch + Book-of-Love-on-partner-activity fire: confirmed manually by user,
  no crash.

Pixel updated to v1.3.7 same session. Two-device Book-of-Love recency check (one phone acts,
other long-presses moon within 5 min) confirmed by user — works across the pair, including
across MIUI's Hebrew locale and Pixel's English-only locale.

**Still open: rest of the Pixel-solo pass** — Search pull-to-refresh and Book-of-Love icon pair
haven't been separately confirmed on the Pixel itself (only the two-device interaction was).

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

## Open — Forgot password (coded, untested end-to-end)

Shipped in `v1.3.0`. Still not run fully on-device: needs a real inbox + tapping the emailed
deep link (`com.oryareach.app://reset-password`) through to `ResetPasswordScreen`. Build/lint/
test all pass; just hasn't been clicked through. (Google account linking, the other half of this
item, is confirmed working — see OAuth section above.)

## Open — Pairing: partner/workspace identity display (not built)

The `AwaitingKey` pairing-pending screen shows no partner/workspace identity, only "you joined
the space." Needs a new `SECURITY DEFINER` Postgres function (pattern: existing
`is_workspace_member`) returning a fellow member's email, scoped to workspace members, plus a
`WorkspaceRepository` method and a field on `PairingStage.AwaitingKey`. Real schema/migration
change to a production Supabase project — **ask before starting**.

## Open — real bug, not fixed on purpose: `device_keys.workspace_id` goes stale on reuse

`PairingViewModel.registerDevice()` short-circuits on `identity.registeredKeyId` — if a device
has *ever* registered a `device_keys` row, it returns that row's id forever without calling
`WorkspaceRepository.publishDeviceKey()` again, so `device_keys.workspace_id` can point at a
workspace the device left. RLS then hides that device from its own pairing partner (`devices()`
comes back empty) even though the real key linkage (`wrapped_workspace_keys`) is correct. Hit
once already (2026-08-17), worked around with a manual `UPDATE device_keys SET workspace_id =
...` in Supabase SQL editor. Proper fix: update `device_keys.workspace_id` on the reuse path, or
stop keying pending/paired lookups off that column and join through `wrapped_workspace_keys`
instead (always correct). User explicitly asked to leave this unfixed and only documented — ask
before touching.

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

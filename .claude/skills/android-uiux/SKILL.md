---
name: android-uiux
description: UI/UX checklist for this repo's Android app (Compose, Material 3, Hebrew RTL + English LTR). Use whenever adding or changing a screen, dialog, sheet, list, button, form, empty/error state, string, icon, animation, or any other user-facing piece under android/ — new features included — and before calling UI work done. Covers RTL/LTR and bidi text, accessibility, states, errors, destructive actions, insets/keyboard/back, dark mode, and on-device verification.
---

# Android UI/UX — the house rules

Distilled from `docs/UIUX.md` (the full audit prompt) plus what device passes on this app have
actually caught. Apply to every UI change, not only redesigns. No blind redesign: find the
screen's primary goal first, change only what serves it.

**Related skills — load alongside, this one wins on conflicts:**
- `frontend-design:frontend-design` — aesthetic direction when shaping a *new* screen's look
  (hierarchy, typography choices, avoiding templated defaults). Its web/CSS specifics don't
  apply; translate its intent into this app's `:core:ui` theme, never into ad-hoc colours.
- `android-skills:android-ux` — **generic M3 rules live there, not here**: colour roles,
  typography scale, shapes, tonal elevation, M3 components, contentDescription/heading/
  mergeDescendants/48dp/WCAG contrast, motion duration tokens + reduced motion, foldables,
  and the 10-category M3 audit. Load it for any review. This skill only adds what is specific
  to this app. `android-skills:compose` — Compose APIs, state, recomposition.

## Before writing UI

- Reuse `:core:ui` first: `theme/` (colours, shapes, type), `text/` (`dayLabel`/`monthLabel`/
  `dateLabel`, `asLtrIsolate`, `sensitiveClipEntry`), `component/` (`CollapsibleDrawer`,
  `DrawerHeader`, `DropBurst`/`DropFall`, `BusyLabel`), `nav/MoonNavigationDrawer`. A widget two features
  need goes into `:core:ui`, never copied (features can't depend on each other).
- Theme exceptions to android-ux's "colour roles only": moon glitch tints and milk white
  (`DropFall.kt`) are the deliberate `Color(0x…)` literals. `surfaceContainerHighest` is the
  card colour (filled `Card` reads it). House spacing: screen gutter 16dp (24dp on Settings),
  list gap 8dp, card padding 12–16dp.
- Business/date math stays in `:core:domain`, not in the composable.
- No new dependency for a UI nicety.

## RTL / LTR (Hebrew is primary, English is fallback)

Manifest has `supportsRtl="true"` and `locales_config.xml` (he, en), so Compose mirrors layout
automatically. Your job is not to fight it, and to catch the places it can't know about.

**Layout**
- Always *start/end*, never *left/right*: `padding(start = …)`, `Alignment.CenterStart`,
  `TextAlign.Start`, `Arrangement.Start`. `absolutePadding`, `Alignment.*Left/*Right`,
  `TextAlign.Left/Right`, `Arrangement.Absolute*` only with a comment saying why (codebase has
  zero today — keep it that way).
- `Row` order is reading order: first child sits at the right in Hebrew. Put the primary/leading
  thing first, trailing actions (trash, edit) last.
- Pin LTR only for things that are physically directional in every language, wrapped in
  `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)` with a comment.
  Precedent: calendar + cycle month headers — left button/chevron = previous, right = next, in
  both languages (under RTL the Row swapped buttons but not chevrons, so "right" went back).
  Don't pin whole screens; pin the smallest block.
- Custom `Canvas`/`drawBehind`: layout direction is **not** applied to draw coordinates. Read
  `layoutDirection` inside the draw scope and measure from the reading edge (see the feed
  guidance bar in `FeedingScreen.kt`, `fun x(fraction)`). Same for gesture math (`dragAmount.x`
  sign flips in RTL) and `graphicsLayer { translationX }`.

**Icons**
- Anything that implies direction uses `Icons.AutoMirrored.*` (back/forward arrows, chevrons in
  lists/breadcrumbs, send, undo/redo, list-with-bullets, open-in/exit). Symmetric or physical
  icons (clock, bottle, moon, trash) never mirror.
- Plain `Icons.Default.ChevronLeft/Right` only inside an LTR-pinned block like the month header.
- Kept as Android does them, by the user's choice — don't "fix": Material date picker's month
  arrows in Hebrew, top-bar back arrow (points right in Hebrew).

**Bidi text**
- Any LTR value interpolated into a sentence goes through `asLtrIsolate()` **at the point of
  interpolation, value only**: versions (`1.10.2` → else `dev-0.0.0`), times, ranges (`12-15`
  → else `15-12`), amounts + units, prices, emails, file names, invite codes, numbered words.
  Never isolate the whole sentence.
- Numbers stay Western digits in Hebrew; units come from the string (`מ״ל`, `ml`), not appended
  in code.
- Mixed-direction strings are built with placeholders in the resource (`%1$s`), never by
  concatenating Hebrew and English pieces in Kotlin — word order differs between languages.
- Text fields for LTR data (email, password, numbers, URLs, invite code) keep LTR input; Compose
  picks direction from content, so don't force `TextAlign.Right` for Hebrew UI.
- Punctuation at the end of an LTR run inside Hebrew (`?`, `.`, `)`) jumps sides without an
  isolate — when a string ends with a value, isolate the value.

**Checking RTL**
- Every screen you touch is seen in Hebrew *and* English before done. Look for: swapped
  arrows, trailing icons on the wrong side, clipped/wrapped pills (Hebrew is often longer),
  numbers reordered, progress bars filling from the wrong edge.

## Strings and language

- Every string in both `values/strings.xml` and `values-iw/strings.xml`, same change. Counts use
  `<plurals>` (Hebrew has "two" forms: יומיים). `translatable="false"` only for data like emoji
  arrays.
- Hebrew tone: plural imperative for instructions (בדקו, נסו, הקישו); gendered partner text uses
  the slash form already in use (בן/בת הזוג, פעיל/ה).
- Never show ISO dates (`2026-09-20`); use `dayLabel`/`dateLabel`/`monthLabel`.
- Button labels are verbs ("Delete", "Will do"), borrowed labels from other dialogs are not
  ("Set" belongs to the date picker only).

## Every screen / list

- **Primary action obvious.** One FAB or one filled button; the rest tonal/outlined/text.
- **States:** loading, empty, error, and first-use all render something. An empty state says
  what's missing and what to do. A failed fetch must never read as "nothing here" (the Google
  Calendar picker said "No calendars found" when the request failed). Room-backed screens
  render instantly — don't add spinners where the data is local.
- **Slow taps show progress.** Any tap that waits on network, key derivation, files or install:
  disable the button while busy (guard re-entry in the ViewModel too) and put `BusyLabel(text,
  busy)` in it — spinner in place of the label, width unchanged. Several buttons sharing one
  busy flag → one `LinearProgressIndicator` above them instead (pairing's Ready stage). A tap
  whose result might be "nothing changed" still says so (update check: "You have the latest
  version", in a polite live region). Local Room writes don't need it.
- **Finished things fold away** into a `CollapsibleDrawer`, shut on arrival, never remembered.
- **FAB never covers the last row:** bottom `contentPadding` ≈ 96dp on lists under a FAB.
- A speed-dial FAB changes icon (+ → ×) and label when open.
- `LazyColumn` items get stable `key`s (entity id) so edits/deletes don't animate the wrong row
  or lose scroll position.
- Progressive disclosure: advanced fields collapsed, not a longer form.

## Errors and messages

- Never show `exception.message`, `toString()` of an error, or any English-only text. Map to a
  `@StringRes` in state (`@StringRes val error: Int?`) and render with `stringResource`.
- Shape: what happened → what to do next ("…Check your connection and try again." /
  "…בדקו את החיבור ונסו שוב.").
- Error text in `colorScheme.error`, but never colour alone — the words carry it.
- Inline field errors via `OutlinedTextField(isError, supportingText)`, not a toast.

## Destructive actions

- Every delete gets a confirm `AlertDialog` naming the thing ("Delete the 14:04 session, 98 ml?"),
  confirm = `TextButton` with the verb ("Delete"), dismiss = Cancel — same shape as every
  existing delete dialog (folders, shopping, tasks, cycle, calendar, feeding, pumping).
  Undo snackbar may follow, it does not replace the dialog.
- Snackbars with an action: `SnackbarDuration.Long` + `withDismissAction = true` (M3 otherwise
  goes Indefinite).
- Keep trash icons away from where a transient snackbar sits; a tap aimed at a vanished
  snackbar already deleted real data once.

## Touch and accessibility

- 48dp here usually fails on clickable `Text`: give it vertical padding inside the `clickable`, or use a
  `TextButton`. Make the whole card tappable (`Card(onClick = …)`), not just its title text.
- Checkbox/switch rows: `Modifier.toggleable(value, role = Role.Checkbox/Switch, …)` on the row,
  child control with `onCheckedChange = null`, plus `minimumInteractiveComponentSize()` if the
  row is short. Radio groups: `selectable(role = Role.RadioButton)` + `selectableGroup()`.
- Decorative canvases (bars, moon, drops) get `clearAndSetSemantics {}` and the value is said
  in text elsewhere.
- Status never by colour alone (presence dot, legend swatches, chips also need text).
- Contrast trouble spot here: `onSurfaceVariant` on tinted containers.
- Font scale: works at 200% — no fixed heights on text containers, use `heightIn(min = …)`;
  pills and chips may wrap, labels shouldn't clip. `maxLines` + `TextOverflow.Ellipsis` only on
  truly secondary text.
- Gesture-only features (long-press, drag, 7-tap) are fine for easter eggs; real functionality
  needs a visible or TalkBack-reachable path too (`onLongClickLabel`, `customActions`).

## Platform behaviour

- **Edge-to-edge** is on (`enableEdgeToEdge()` in `MainActivity`): every screen handles insets —
  `safeDrawingPadding()` on the screen root or Scaffold's `innerPadding`, never both, never
  neither. Nothing under the status bar or gesture bar.
- **Keyboard**: manifest is `adjustResize`; forms in sheets/dialogs add `imePadding()` and set
  `KeyboardOptions` (type Number/Decimal/Email, `ImeAction.Next/Done`) with `KeyboardActions`
  moving focus or submitting.
- **Back**: no NavHost — a sub-screen that is a `rememberSaveable` boolean needs a
  `BackHandler` to close it; back closes sheets/dialogs before leaving the screen.
- **Config changes / process death**: UI state that must survive rotation or theme switch is in
  the ViewModel or `rememberSaveable`, not `remember`.
- **Bottom sheets**: `ModalBottomSheet` for add/edit forms; the sheet scrolls, the confirm button
  stays reachable with the keyboard open.
- **Permissions**: ask in context, at the moment the feature needs it (notifications when
  enabling a reminder, camera on Scan), with a rationale line; handle "denied" with a way to
  Settings, never a dead button.
- **Notifications**: one channel per reminder kind, user-facing channel names in both languages.
- **Secrets**: recovery phrase copies via `sensitiveClipEntry(...)`, never plain `ClipData`;
  screens showing secrets rely on `MainActivity`'s `FLAG_SECURE`.

## Dark mode

- Both schemes in `theme/Theme.kt` define container roles and the surface ladder (they once
  didn't, and half the app fell back to default lavender). No pure black backgrounds.
- Check every change in dark *and* light: dividers, disabled states, legend swatches (one was
  `Color.Transparent` and vanished in light), small drawn marks (milk drops were invisible on
  dark until given a fixed fill + `primary` rim).

## Motion and feedback

- Durations/easing/reduced motion: see android-ux. House rule on top: no decoration-only
  animation (easter eggs excepted).
- Every tap gives feedback: ripple (default on clickable), state change, or snackbar. Copy
  actions confirm on Android < 13 (13+ shows a system toast).

## Done means seen

`assembleDebug`, `test`, `lint` passing does **not** prove a Compose layout renders — this repo
has no JVM Compose harness. Before calling UI work done:

1. `cd android && ./gradlew :app:assembleDebug test lint` green.
2. Grep for the *class* of mistake you just fixed across all features, not the one instance.
3. Say plainly which screens still need eyes on a real phone: English/dark on the Xiaomi,
   Hebrew/light or dark on the Pixel, via a release build (push a `v*` tag — never hand-built
   release APKs, never uninstall). Check empty lists, open drawers, forms, dialogs, font scale,
   both directions — bugs hide where nothing renders.
4. Screenshot before any tap near a trash icon or transient snackbar during device tests.
   Launch with `am start -n com.oryareach.app/.MainActivity`, never `monkey` (it unlocks the
   phone's rotation lock). Tap only when the app has focus; restore any system setting you moved.
5. Record what shipped and what is still unseen in `docs/TASK-HISTORY.md`.

For a full-app audit (not a single feature), follow `docs/UIUX.md` end to end and prioritise
P0 (blocks use) → P1 → P2 → P3 polish.

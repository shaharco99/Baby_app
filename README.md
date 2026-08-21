# Takes Two of Us (שנינו ביחד)

A private app for a couple getting ready for their daughter's arrival — named after
*It Takes Two*, the co-op game that inspired it: this is a two-player game too, just with
higher stakes. A few small nods to it (and to *Split Fiction*) are hidden in the app —
find them and you'll know.

Think of it as one shared notebook on your phones that only the two of you hold the key
to. Inside: tasks (including a hospital-bag checklist), a shopping list with budget
tracking, important dates and wishes, menstrual cycle tracking, folders and documents
(including scanning), and a countdown to the due date drawn as a moon filling up.

## Why it's safe

Everything you write gets encrypted on your phone **before** it ever leaves it. The
server (Supabase) only ever stores encrypted gibberish — even if someone broke into the
server, they couldn't read it. The key that unlocks the encryption exists only on your
two devices, never on the server. Lose both devices? A 24-word recovery phrase (like a
crypto wallet) brings everything back.

It's not "Shahar vs. Topaz" (there's nothing private from each other) — it's "the two of
you vs. the outside world." Full details: `docs/architecture/005-data-privacy.md`.

## What's inside

- **Home** — countdown to the full moon, weekly info, a daily message
- **Tasks** — including a ready-made hospital-bag checklist preset
- **Shopping** — with budget and priorities
- **Cycle** — tracking, predictions, statistics
- **Calendar** — important dates and wishes, plus a read-only view of Google Calendar
- **Folders & documents** — including document scanning straight from the camera
- **Search**, **biometric lock**

## Running locally (Android)

The active product is the Android app under `android/`:

```bash
cd android
./gradlew :app:assembleDebug   # builds a debug APK
./gradlew test                 # runs all unit tests
./gradlew lint                 # checks code style
```

Easiest to run from Android Studio, or on a connected device/emulator with
`./gradlew installDebug`. A signed release build (`assembleRelease`) needs signing
secrets that aren't in the public repo — without them the build still passes, it just
comes out unsigned.

Full module layout and architecture decisions: `docs/architecture/`.

## Auto-update: how it works and how to trigger it

The app updates itself — no Play Store, since it's not published there. Here's the flow:

1. **Publishing a release** — a developer runs `git tag v1.3.0 && git push origin v1.3.0`.
   That's the only manual step. The `android-release.yml` workflow (see CI/CD below) then
   builds a signed APK, computes its checksum, and publishes it as a GitHub Release along
   with a `manifest.json` describing the version and release notes.
2. **Checking for updates** — the app checks GitHub for a newer release once per app start
   (result cached for a few hours so it doesn't re-check every time you switch apps). It
   compares its own version against the latest release tag.
3. **Prompting you** — if a newer version exists, a dialog appears with the release notes
   and an "Install" button. A normal update can be dismissed ("Later"/"Skip"); an update
   marked mandatory (or one so old it can no longer talk to the server) can't be dismissed
   until installed.
4. **Installing** — tapping "Install" downloads the APK, verifies its checksum, then hands
   it to Android's package installer. Android shows its own "Install update?" confirmation
   (this is a normal Android security prompt, not something the app can skip), then
   restarts the app on the new version.

**To trigger a check manually** (instead of waiting for the next app start): open the side
drawer → **Settings** → **Check for updates**.

Full design rationale (why self-signed, why no Play Store, key-loss consequences):
`docs/architecture/011-release-signing-and-updates.md`.

## CI/CD

Four GitHub Actions workflows, each scoped to only the part of the repo it cares about
(so an Android change doesn't trigger a web-app deploy, etc):

- **`android-ci.yml`** — on every push/PR touching `android/`: runs unit tests and lint.
  This is the gate that must pass before a PR merges.
- **`android-release.yml`** — on pushing a tag like `v1.2.3`: re-runs tests and lint, then
  builds and publishes a signed release APK as a GitHub Release. This is how a version
  actually reaches the two phones (the app has its own in-app updater that checks GitHub
  Releases — see `docs/architecture/011-release-signing-and-updates.md`).
- **`supabase-tests.yml`** — on every push/PR touching `supabase/`: spins up a local
  Supabase instance, applies every migration from scratch, and checks the access-control
  (RLS) rules actually hold — this is what stops a database change from silently letting
  the server read private data.
- **`deploy.yml`** — on every push to `main`: builds the legacy web app (`src/`) and
  publishes it to GitHub Pages. Kept only because the retired web app is still deployed
  there; not part of active development.

## Legacy website (retired)

The `src/` folder holds an older website version (React, saved only on the phone, no
server at all). It's no longer maintained — all development moved to the Android app.
It's kept only to import old data from it once, if ever needed:

```bash
npm install
npm run dev
```

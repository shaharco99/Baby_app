# 011 — Release signing and in-app updates

**Status:** Accepted (2026-08-15)

## Context

The original auto-update spec (condensed into `docs/TASK-HISTORY.md`, full text in git history) wanted git-tag-driven releases plus in-app update prompt. Spec written platform-generic — manifest example list Windows, Linux, macOS assets — needs translate to Android, where self-updating hit constraints spec don't mention.

App distribute privately to two phones. Not through Google Play.

## Decision

**Version = newest `v*` git tag.** `versionName` and `versionCode` derive at build time by `oryareach.android.application` convention plugin; no build file hand-write version. `versionCode = major*10000 + minor*100 + patch` (1.4.0 → 10400), stays ordered while minor and patch below 100 — enforced by `require` that fail build instead of silent lower code.

Git read through Gradle `ValueSource`, not shell-out at configuration time, so configuration cache stay valid and git output tracked as build input.

Untagged builds report `0.0.0-dev` / versionCode 1, sorts below every real release.

**Release pipeline.** `.github/workflows/android-release.yml` trigger on `v*` tags: run tests and lint, build signed release APK, compute SHA-256, generate `manifest.json` with release notes from commit subjects since previous tag, publish GitHub Release with APK, manifest, checksum file attached. Tests and lint run *before* build, so failing tag produce no release.

**Signing key permanent and secret.** Android refuse install update signed by different key than installed app. Recover from lost signing key means uninstall and reinstall, which **wipes all local data on that device** — for end-to-end encrypted app also mean losing whatever not yet synced.

Therefore:

- Keystore generated once, stored only as GitHub secrets
  (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
  `ANDROID_KEY_PASSWORD`), plus offline backup user keeps.
- Never committed. `android/.gitignore` exclude `*.jks` and `*.keystore`.
- Release build reads it from environment. Secrets absent → release build stays **unsigned** rather than fall back to debug key — debug-signed APK would install fine on clean device then permanently un-updatable.
- Release workflow fails loudly if `ANDROID_KEYSTORE_BASE64` missing.

**Install flow.** Android can't silently self-install. Updater uses modern `PackageInstaller` session API, requires `REQUEST_INSTALL_PACKAGES` plus one-time user grant of "Install unknown apps". Every install shows system confirmation. "Restart the application" in spec means: session commits → system replaces APK → app relaunches.

**Update checking.** Once per app start, cached, multi-hour floor before recheck, plus manual "Check for updates" in Settings. GitHub API allows 60 unauthenticated requests per hour per IP, sits far below that, so **no token embedded in app**. Failed check logged and otherwise ignored — never blocks startup or shows alarming error.

**Update state** (`lastUpdateCheck`, `lastNotifiedVersion`, `skippedVersion`) lives in plain DataStore, deliberately outside encrypted workspace: must be readable before user unlocks anything, contains nothing sensitive.

## Consequences

- Releasing is `git tag v1.3.0 && git push origin v1.3.0`. Nothing else manual.
- Self-updating APKs violate Google Play policy. Irrelevant for private distribution, but recorded so future decision to publish not made in ignorance.
- Web PWA already has equivalent mechanism (`vite-plugin-pwa` with
  `registerType: 'prompt'`, surfaced by `src/app/pwa-update-prompt.tsx`). This ADR covers Android only; no work needed on web side.
# 011 — Release signing and in-app updates

**Status:** Accepted (2026-08-15)

## Context

Original auto-update spec (condensed into `docs/TASK-HISTORY.md`, full text in git history) wanted git-tag-driven releases + in-app update prompt. Spec platform-generic: manifest example lists Windows, Linux, macOS assets. Needs translation to Android, where self-updating hits constraints spec doesn't mention.

App distributed privately to two phones. Not via Google Play.

## Decision

**Version = newest `v*` git tag.** `oryareach.android.application` convention plugin derives `versionName` and `versionCode` at build time. No build file hand-writes version. `versionCode = major*10000 + minor*100 + patch` (1.4.0 = 10400). Stays ordered while minor and patch below 100. A `require` enforces this and fails build rather than silently emitting lower code.

Git read via Gradle `ValueSource`, not shell-out at configuration time. Configuration cache stays valid; git output tracked as build input.

Untagged builds report `0.0.0-dev` / versionCode 1. Sorts below every real release.

**Release pipeline.** `.github/workflows/android-release.yml` triggers on `v*` tags. Steps: run tests + lint, build signed release APK, compute SHA-256, generate `manifest.json` with release notes from commit subjects since previous tag, publish GitHub Release with APK, manifest, checksum file attached. Tests + lint run *before* build, so failing tag produces no release.

**Signing key permanent and secret.** Android refuses to install an update signed by a different key than the installed app. Recovering from a lost signing key means uninstall and reinstall, which **wipes all local data on that device**. For an end-to-end encrypted app, that also means losing anything not yet synced.

Therefore:

- Keystore generated once, stored only as GitHub secrets
  (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
  `ANDROID_KEY_PASSWORD`), plus offline backup user keeps.
- Never committed. `android/.gitignore` excludes `*.jks` and `*.keystore`.
- Release build reads it from environment. If secrets absent, release build stays **unsigned**. No fallback to debug key: debug-signed APK installs fine on clean device, then permanently can't be updated.
- Release workflow fails loudly if `ANDROID_KEYSTORE_BASE64` missing.

**Install flow.** Android can't silently self-install. Updater uses modern `PackageInstaller` session API. Requires `REQUEST_INSTALL_PACKAGES` + one-time user grant of "Install unknown apps". Every install shows system confirmation. Spec's "Restart the application" means: session commits, system replaces APK, app relaunches.

**Update checking.** Once per app start, cached, multi-hour floor before recheck. Plus manual "Check for updates" in Settings. GitHub API allows 60 unauthenticated requests/hour/IP; usage far below, so **no token embedded in app**. Failed check logged, else ignored. Never blocks startup or shows alarming error.

**Update state** (`lastUpdateCheck`, `lastNotifiedVersion`, `skippedVersion`) lives in plain DataStore, deliberately outside encrypted workspace. Must be readable before user unlocks anything. Contains nothing sensitive.

## Consequences

- Releasing = `git tag v1.3.0 && git push origin v1.3.0`. Nothing else manual.
- Self-updating APKs violate Google Play policy. Irrelevant for private distribution. Recorded so any future decision to publish isn't made in ignorance.
- Web PWA already has equivalent mechanism (`vite-plugin-pwa` with
  `registerType: 'prompt'`, surfaced by `src/app/pwa-update-prompt.tsx`). ADR covers Android only; no web work needed.
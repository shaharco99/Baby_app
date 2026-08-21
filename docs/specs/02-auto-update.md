# Auto-Update — Spec (implemented)

Original spec for the in-app updater. Fully implemented as `:core:update` +
`:feature:update` (semver comparison, async non-blocking startup check, download with SHA-256
verification, `PackageInstaller` install flow, mandatory-update support, manual "Check for
updates" in Settings) — see `docs/architecture/011-release-signing-and-updates.md` for the
standing design, and `git log` for what shipped when.

Full original spec text preserved in git history:
`git log --follow -- docs/specs/02-auto-update.md`.

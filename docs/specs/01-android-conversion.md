# Android App Conversion — Original Spec (superseded)

This was the original requirements/design spec that guided the Android conversion. Every
requirement in it has been implemented — see `git log` and the code itself for what was
built (no separate progress doc is maintained), `docs/architecture/` for the standing
architecture decisions, and `CLAUDE.md` for the current, correct summary of how the app
actually works.

One correction from this spec, kept here since it was a real early mistake, not just drift:
this spec originally proposed a private-by-default / partner-vs-partner sharing model for
cycle data. That was wrong — the actual, implemented threat model is couple-vs-outside-world:
both partners see all workspace data, end-to-end encrypted so only Supabase/an attacker/a
lost phone is kept out. See `docs/architecture/005-data-privacy.md` for the real model.

Full original spec text (86 numbered sections, requirements + testing checklist + phase
tracker) is preserved in git history: `git log --follow -- docs/specs/01-android-conversion.md`.

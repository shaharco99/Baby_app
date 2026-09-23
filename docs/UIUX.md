# Android App UI/UX Improvement Prompt

Full-audit prompt. Audit results + what's left: `docs/TASK-HISTORY.md`, section "2026-09-23 — UI/UX audit".

Rules live in two skills. Load both first; this file adds only the audit process on top:
- `android-skills:android-ux` — generic Material 3: colour roles, typography, shapes, elevation, components, spacing grid, adaptive layouts/foldables, navigation components, motion tokens + reduced motion, accessibility basics (descriptions, headings, merged semantics, 48dp, WCAG contrast), dark/light theming, plus 10-category M3 audit with score template.
- `.claude/skills/android-uiux/SKILL.md` (repo) — this app: RTL/LTR + bidi, Hebrew/English strings, states, errors, destructive actions, touch patterns, insets/keyboard/back/permissions, dark-mode history, on-device verification.

Act as **senior Android UI/UX designer, product designer, accessibility expert**. Review app, improve UI/UX. No core functionality change unless needed for usability.

## Main Goal

App feel **modern, polished, intuitive, fast, professional**.

No blind redesign. First understand app, screens, navigation, user flows, purpose. Then find highest-impact improvements.

## 1. Audit Existing UI

Review every screen. Run android-ux's M3 audit per screen, plus repo skill's checklist. Beyond those, find:

* Weak visual hierarchy
* Poor button placement
* Overcrowded screens / excess empty space
* Confusing navigation
* Unclear icons
* Bad form/input UX
* Needless dialogs
* Redundant UI elements
* Hard-to-reach controls
* Too many taps
* Non-native-feeling components

Grep for *class* of mistake across all features, not one screen at a time.

## 2. Navigation & User Flow

Analyze user movement through app. Cut needless navigation/taps.

Ensure:

* User always know location
* Back navigation predictable
* Important actions easy find
* Related functionality grouped
* Navigation consistent across screens

Workflow simplifiable: propose, implement simpler flow.

## 3. Responsiveness to taps

Every action that waits (network, crypto, file I/O, install) shows progress at once and blocks double-submit. No tap that looks ignored. Pattern + `BusyLabel`: repo skill, "Slow taps show progress".

## 4. UX Simplification

Every screen ask:

> "What is user's primary goal on this screen?"

Make that action obvious.

Remove/reduce:

* Needless text
* Redundant buttons
* Duplicate info
* Excess borders / cards
* Valueless decoration

Prefer **progressive disclosure** for advanced options.

## 5. Implementation Rules

Before code change:

1. Explore whole project.
2. Understand architecture (`docs/architecture/001-android-architecture.md`).
3. Identify reusable components (`:core:ui`).
4. No needless architecture changes.
5. Reuse existing functionality.
6. Keep business logic separate from UI (`:core:domain`).
7. Don't break existing functionality.
8. No needless dependencies.
9. Follow project coding conventions.

## 6. Prioritization

Classify each improvement:

**P0 — Critical**: blocks usability, major confusion, or leaks something private. Fix now.

**P1 — High**: big UX gain.

**P2 — Medium**: visual or usability gain.

**P3 — Polish**: minor refinement.

P0/P1 first.

## 7. Before/After Thinking

Each significant UI change, explain internally:

* What wrong?
* Why problem?
* New solution?
* How UX better?

No change just for new look.

## 8. Final Quality Check

Before done:

* [ ] android-ux M3 audit: no Fail left on touched screens
* [ ] Repo skill's "Done means seen" steps followed
* [ ] Navigation intuitive, clear button hierarchy
* [ ] No functionality accidentally removed
* [ ] No needless dependencies added
* [ ] `./gradlew :app:assembleDebug test lint` green
* [ ] Unseen-on-device list written to `docs/TASK-HISTORY.md`

## Important

**No blind redesign.** First analyze existing UX, understand app purpose.

Result feel like **professional, modern Android application**. Not just more colors, animations, cards, rounded corners.

Focus:

**Clarity → Simplicity → Consistency → Accessibility → Speed → Polish**

End with short summary:

1. Major UX problems found
2. Changes made
3. Screens/components improved
4. Remaining recommendations
5. Risks/trade-offs

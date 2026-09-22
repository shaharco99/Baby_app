# CLAUDE.md

Guidance for Claude Code (claude.ai/code) working this repo.

## What this is

"אור ירח" (One More Moon) — private couple organizer, Shahar & Topaz, prep for baby arrival. Hebrew, RTL. Shopping list w/ budget & priorities, tasks (incl. hospital bag), important dates & wishes, menstrual cycle tracking, folders & documents (incl. scanning), countdown to due date framed as moon filling up.

**Active product: Android app, `android/`.** End-to-end encrypted: both partners share one workspace + one encryption key; Supabase (backend) only ever sees ciphertext. See `docs/architecture/` for full decision record, and `docs/TASK-HISTORY.md` for what's still open plus a condensed account of what's already been done (don't redo those). What's built vs. planned: `git log` and the code itself are the source of truth — no separate progress doc is maintained.

`src/` = original web PWA (React 19 + Vite, `localStorage`-only, no backend) — **retired**, kept for reference only. Export/import JSON format still migration path into Android app (`:core:domain`'s `WebSnapshot`/`toImportedSnapshot`). "Web app (legacy)" section below still accurate for that subtree — skip straight to "Android app" unless touching `src/`.

## Android app

Read `docs/architecture/001-android-architecture.md` first — short, covers module shape, DI choice (Koin, not Hilt), UI pattern (Compose, MVVM w/ state/effect split) everything below assumes.

**Commands** (from `android/`):
```bash
./gradlew :app:assembleDebug   # debug APK
./gradlew test                 # all modules, incl. :core:crypto and :core:domain's pure-logic tests
./gradlew lint                 # Kotlin/Android lint, warnings are errors
```
`assembleRelease` needs signing secrets (`ANDROID_KEYSTORE_BASE64` etc., see `docs/architecture/011-release-signing-and-updates.md`); without them still builds (R8/shrink/proguard all run) but **unsigned**, by design — not build failure.

**Module rule matters most:** `:feature:*` modules never depend on each other (enforced by convention, not compiler — `AndroidFeatureConventionPlugin`'s doc comment states it, nothing fails build if violated). Anything reached from two different features — device management from Settings, "Check for updates" from Settings — wired in `:app`, only module allowed see every feature. `:core:*` modules = shared seam instead: e.g. `SessionController` and `WorkspaceKeyProvider` in `:core:security`/`:core:sync` let feature module trigger lock/read key without depending on `:app`'s concrete `SessionState`.

**Routing has no `NavHost`.** `SaharApp.kt` derives which screen show from state (`AuthState`, whether workspace key unlocked, `SessionState.isLocked`) via plain `when`, not back stack — see doc comment. `HomeRoute` = bottom-tab switch on local enum, not nav graph. Screen needing "sub-screen" (e.g. Settings' device management) flips local `rememberSaveable` boolean, not pushing route.

**Persistence.** Room (SQLCipher-encrypted, `:core:database`) only thing UI reads from — network never on render path. Background `SyncEngine` (`:core:sync`) keeps synced w/ Supabase; every repository writes locally first, then enqueues sync op via `SyncTrigger`. Record content encrypted client-side (`:core:crypto`, ChaCha20-Poly1305) before reaching `:core:network` — Supabase RLS second layer, not only one.

**Domain math stays in `:core:domain`**, pure Kotlin, unit-tested on JVM (no emulator): pregnancy progress, budget calculations, cycle predictions/statistics, web-import mapper. Don't inline date/domain math into ViewModel or composable if belongs here — see `core/domain/src/main/kotlin/com/oryareach/core/domain/` for existing shape (one subpackage per domain area) before adding new one.

**Adding entity that syncs** touches, in order: `:core:model` (data class), `:core:database` (`Entity`/`Dao`, `Migrations.kt` bump, `DatabaseConverters` for new enum/list field), `Mappers.kt` (entity ↔ domain), `RoomSyncStore` (all four spots — grep `EntityType.CYCLE_ENTRY` for most recently added one as template; `when` blocks exhaustive, missed branch = compile error, not silent gap), and `supabase/migrations/` only if `entity_type` enum doesn't already have slot for it (check first — several declared in `0001_init.sql` a phase ahead of being used).

**Migrations are applied by the pipeline, and by nothing else.** Not by hand, not from a local CLI, and not by an agent with database access — `.github/workflows/supabase-deploy.yml` is the only thing that touches the live schema. It runs on a `v*` tag (the release's publish step waits on it, so the schema is always ahead of the APK) or on `workflow_dispatch` for a schema change without a release, and it proves every migration on an empty database (a fresh `supabase start` + `test db`, via `supabase-tests.yml`) before it goes anywhere near production. If a migration needs applying, push it and tag, or dispatch the workflow; if that feels slow, the answer is still not to run it by hand, because the hand-applied path is what left the file and the database out of step twice already. See `docs/guides/migration-deploy.md`.

**Every `supabase/migrations/` file must still be idempotent**, and is: `create table if not exists`, `create index if not exists`, `drop policy if exists` before each `create policy`, the same for triggers, and a `do $$ … exception when duplicate_object` block around `create type`. All thirteen re-apply cleanly against an already-migrated database, with every row intact — which is what makes a re-run by the pipeline a no-op rather than a failure. `0011_device_push_tokens.sql` is the worked example. A migration that adds a public table must also update the exact table list in `supabase/tests/001_access_control.sql` in the same change.

**Testing the database** needs Docker and the CLI over `npx` (no local install): `npx -y supabase@latest start`, then `db reset` (applies every migration from scratch — the real check that a migration file works on an empty database) and `test db` (the pgTAP suite in `supabase/tests/`). Run it there, never against the live project: the tests insert into `auth.users` and create a `tests` schema, and a mid-script failure would leave both behind. `001_access_control.sql` asserts the **exact** set of public tables, so a migration that adds one must update that list in the same change.

**Shared UI pieces live in `:core:ui`**, not copied between features — features can't depend on each other, so a widget two of them need goes there. `theme/` (colours, shapes, type), `text/` (`dayLabel`/`monthLabel`/`dateLabel`, bidi helpers), `component/` (`DrawerHeader`/`CollapsibleDrawer` — the shut-by-default drawer that shopping, tasks, cycle, both logs and Settings all fold their finished rows into). Material 3 colour roles are all defined in `theme/Theme.kt`, including the *container* roles and the surface ladder: `surfaceContainerHighest` must stay the card colour, since that's what a filled `Card` reads.

**Strings bilingual**, `values/` (English fallback) + `values-iw/` (Hebrew) in every module w/ UI. Add both together, never just one.

## Web app (legacy)

### Commands

```bash
npm run dev       # vite dev server
npm run build      # tsc -b && vite build (type-check is part of the build, no separate typecheck script)
npm run lint       # oxlint
npm run preview    # preview production build
npm run test       # vitest run
```

Tests live next to code they cover (`*.test.ts`), config = `vitest.config.ts` (separate from `vite.config.ts` since latter isn't built w/ `vitest/config`'s `defineConfig`). Coverage limited to pure-logic files in `src/lib` and `src/features/shopping/budget.ts` — no component/integration tests.

### Architecture

**Persistence — single storage seam.** `src/stores/appStore.ts` = one zustand store (w/ `persist` middleware) holding all app state: `settings`, `shoppingItems`, `tasks`, `importantDates`. Never touches `localStorage` directly — goes through `src/lib/storage.ts`'s `createAppStorage()` adapter. If ever moves to real backend, only `storage.ts` needs change. Keep all persisted state in this one store; don't create parallel stores or read/write `localStorage` elsewhere.

**Domain types** live in `src/types/models.ts` — source of truth for shopping/task/date shapes, categories (`SHOPPING_CATEGORIES`, `TASK_CATEGORIES`), label maps (`PRIORITY_LABEL`, `SHOPPING_STATUS_LABEL`). Categories/enums = Hebrew string literals used directly as data, not just labels — adding category means editing `as const` array here.

**Feature-sliced structure**: `src/features/{home,shopping,tasks,dates,settings}` each hold page plus any form/card components specific to that feature. Cross-feature UI (nav, layout shell) in `src/components/layout`; moon countdown in `src/components/countdown`; generic shadcn/radix primitives in `src/components/ui` (standard shadcn setup, see `components.json`).

**Pure logic in `src/lib`**: `pregnancy.ts` (due-date math, weekly info, weekly fruit-size comparison, moon fraction), `messages.ts` (daily message picker), `budget.ts` under `features/shopping` (spend calculations), `hospital-bag-preset.ts` (seed data for hospital-bag task preset). Keep date/domain math here, not inline in components.

**Routing**: `src/app/router.tsx` + `src/app/layout.tsx` (`RootLayout`). Nav items declared once in `src/components/layout/nav-items.ts`, rendered as both desktop top pill-nav and mobile bottom tab bar in `RootLayout`.

**Design tokens**: all color/radius/font tokens = CSS custom properties in `src/index.css` under `:root` / `.dark`, mapped into Tailwind v4 via `@theme inline`. Named tokens beyond shadcn defaults: `moss`, `blush` (plus standard shadcn set). Headings use `--font-heading` (Assistant Variable), body uses `--font-sans` (Heebo Variable) — both support Hebrew. Add new colors/fonts as CSS vars here, not one-off Tailwind arbitrary values. Moon-countdown card (`src/components/countdown/moon-countdown.tsx`) hardcodes own always-dark "night sky" palette independent of light/dark theme — keep those hex values synced w/ `.dark`'s tone if dark palette changes.

**Bottom sheets and keyboard**: add/edit forms (`shopping-item-form.tsx`, `task-form.tsx`, `date-form.tsx`) use `Sheet` (`side="bottom"`) w/ max-height clamped to `--visual-vh` CSS var, kept live by `useVisualViewportHeight()` (`src/lib/use-visual-viewport.ts`, mounted once in `RootLayout`). iOS Safari fallback for mobile-keyboard-covers-sheet problem; `index.html`'s `interactive-widget=resizes-content` viewport meta handles natively on Chromium. Adding another bottom sheet w/ form inputs: reuse same `max-h-[min(92dvh,calc(var(--visual-vh,100dvh)*0.92))]` pattern rather than bare `dvh` value.

**PWA / deploy**: `vite.config.ts` sets `base: '/Baby_app/'` for GitHub Pages — must match repo name if repo ever renamed. `VitePWA` config (manifest, workbox caching) also lives there. Deploys automatically via `.github/workflows/deploy.yml` on push to `main`; GitHub Pages source must be set to "GitHub Actions" once per repo.

**Compiler**: React Compiler enabled via `@rolldown/plugin-babel` + `reactCompilerPreset()` in `vite.config.ts` — avoid manual `useMemo`/`useCallback` unless specific reason, compiler handles most of it.

**Path alias**: `@/*` → `./src/*` (configured in both `tsconfig.app.json` and `vite.config.ts`).
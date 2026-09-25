# CLAUDE.md

Guidance for Claude Code (claude.ai/code) in this repo.

## What this is

"אור ירח" (One More Moon): private organizer for couple Shahar & Topaz, prep for baby arrival. Hebrew, RTL. Features: shopping list w/ budget & priorities, tasks (incl. hospital bag), important dates & wishes, menstrual cycle tracking, folders & documents (incl. scanning), countdown to due date shown as moon filling up.

**Active product: Android app, `android/`.** End-to-end encrypted. Both partners share one workspace + one encryption key; Supabase (backend) only sees ciphertext. Full decision record: `docs/architecture/`. Open work + condensed history of done work (don't redo): `docs/TASK-HISTORY.md`. Built vs. planned: `git log` + code = source of truth. No separate progress doc.

`src/` = original web PWA (React 19 + Vite, `localStorage`-only, no backend). **Retired**, kept for reference only. Its export/import JSON format still = migration path into Android app (`:core:domain`'s `WebSnapshot`/`toImportedSnapshot`). "Web app (legacy)" section below still accurate for that subtree. Skip to "Android app" unless touching `src/`.

## Android app

Read `docs/architecture/001-android-architecture.md` first. Short. Covers module shape, DI choice (Koin, not Hilt), UI pattern (Compose, MVVM w/ state/effect split). Everything below assumes it.

**Commands** (from `android/`):
```bash
./gradlew :app:assembleDebug   # debug APK
./gradlew test                 # all modules, incl. :core:crypto and :core:domain's pure-logic tests
./gradlew lint                 # Kotlin/Android lint, warnings are errors
```
`assembleRelease` needs signing secrets (`ANDROID_KEYSTORE_BASE64` etc., see `docs/architecture/011-release-signing-and-updates.md`). Without them still builds (R8/shrink/proguard run) but **unsigned**, by design. Not build failure.

**Module rule matters most:** `:feature:*` modules never depend on each other. Enforced by convention only, not compiler: `AndroidFeatureConventionPlugin`'s doc comment states it, nothing fails build if violated. Anything reached from two features (device management from Settings, "Check for updates" from Settings) wired in `:app`, only module allowed to see every feature. `:core:*` modules = shared seam instead. E.g. `SessionController` and `WorkspaceKeyProvider` in `:core:security`/`:core:sync` let feature module trigger lock/read key without depending on `:app`'s concrete `SessionState`.

**Routing has no `NavHost`.** `SaharApp.kt` picks screen from state (`AuthState`, workspace key unlocked or not, `SessionState.isLocked`) via plain `when`, no back stack. See doc comment. `HomeRoute` = bottom-tab switch on local enum, not nav graph. Screen needing "sub-screen" (e.g. Settings' device management) flips local `rememberSaveable` boolean, no route push.

**Persistence.** Room (SQLCipher-encrypted, `:core:database`) = only thing UI reads. Network never on render path. Background `SyncEngine` (`:core:sync`) syncs w/ Supabase. Every repository writes locally first, then enqueues sync op via `SyncTrigger`. Record content encrypted client-side (`:core:crypto`, ChaCha20-Poly1305) before reaching `:core:network`. Supabase RLS = second layer, not only one.

**Domain math stays in `:core:domain`**, pure Kotlin, unit-tested on JVM (no emulator): pregnancy progress, budget calculations, cycle predictions/statistics, web-import mapper. Don't inline date/domain math into ViewModel or composable if belongs here. Check `core/domain/src/main/kotlin/com/oryareach/core/domain/` for existing shape (one subpackage per domain area) before adding new one.

**Adding entity that syncs** touches, in order: `:core:model` (data class), `:core:database` (`Entity`/`Dao`, `Migrations.kt` bump, `DatabaseConverters` for new enum/list field), `Mappers.kt` (entity ↔ domain), `RoomSyncStore` (all four spots; grep `EntityType.CYCLE_ENTRY` for newest one as template; `when` blocks exhaustive, missed branch = compile error, not silent gap), and `supabase/migrations/` only if `entity_type` enum lacks slot for it (check first; several declared in `0001_init.sql` a phase ahead of use).

**Migrations are applied by the pipeline, and by nothing else.** Not by hand, not from local CLI, not by agent with database access. `.github/workflows/supabase-deploy.yml` = only thing touching live schema. Runs on `v*` tag (release publish step waits on it, so schema always ahead of APK) or on `workflow_dispatch` for schema change without release. Proves every migration on empty database first (fresh `supabase start` + `test db`, via `supabase-tests.yml`) before production. Need migration applied: push it and tag, or dispatch workflow. Feels slow? Still never run by hand. Hand-applied path already put file and database out of step twice. See `docs/guides/migration-deploy.md`.

**Every `supabase/migrations/` file must still be idempotent**, and is: `create table if not exists`, `create index if not exists`, `drop policy if exists` before each `create policy`, same for triggers, and `do $$ … exception when duplicate_object` block around `create type`. All fourteen re-apply cleanly on already-migrated database, every row intact. That makes pipeline re-run a no-op, not failure. Worked example: `0011_device_push_tokens.sql`. Migration adding public table must also update exact table list in `supabase/tests/001_access_control.sql` in same change.

**Testing the database** needs Docker + CLI via `npx` (no local install): `npx -y supabase@latest start`, then `db reset` (applies every migration from scratch; real check that migration file works on empty database) and `test db` (pgTAP suite in `supabase/tests/`). Run there, never against live project: tests insert into `auth.users` and create `tests` schema, mid-script failure leaves both behind. `001_access_control.sql` asserts **exact** set of public tables, so migration adding one must update that list in same change.

**Shared UI pieces live in `:core:ui`**, not copied between features. Features can't depend on each other, so widget two of them need goes here. `theme/` (colours, shapes, type), `text/` (`dayLabel`/`monthLabel`/`dateLabel`, bidi helpers, `sensitiveClipEntry`, `confirmCopied`), `component/` (`DrawerHeader`/`CollapsibleDrawer`: shut-by-default drawer that shopping, tasks, cycle, both logs and Settings fold finished rows into; `BusyLabel`; `DropFall`; `EasterEggDialogs.kt`: night watch + stash, opened from their logs *and* Home). `:core:ui` depends on `:core:domain` (for those dialogs' types). All Material 3 colour roles defined in `theme/Theme.kt`, incl. *container* roles and surface ladder. `surfaceContainerHighest` must stay card colour, since filled `Card` reads it. Cards overriding container to `surface` are the app-wide norm (~17), not drift — user chose to keep them.

**Strings bilingual**: `values/` (English fallback) + `values-iw/` (Hebrew) in every module w/ UI. Add both together, never one. aapt strips leading/trailing whitespace from a string value: write `,\u0020`, never `", "` (the age separator lost its space this way). Units (h/min/ml) always come from strings, never built in Kotlin.

**Insets: the host owns the top.** `TakesTwoApp`'s `Scaffold` (top bar) passes every tab `Modifier.padding(padding).consumeWindowInsets(padding)`; screens keep their own `safeDrawingPadding()`, which is then a no-op at the top. A new tab must get that same `content` modifier, or an empty status-bar-high band appears above its title. User rule: no wasted vertical space, anywhere — check the top of every screen you touch.

**Form bottom sheets** use `rememberModalBottomSheetState(skipPartiallyExpanded = true)`; half-expanded left Save under the nav buttons.

**Home-screen widget** (`:app`'s `widget/FeedWidget.kt`, RemoteViews + `Chronometer`, no Glance) never reads the database — it works while the app is locked from two timestamps in plain prefs (`feed-widget`), written only by `AlarmFeedingReminderScheduler`. Anything that moves the feed reminder moves the widget; keep it that way. Re-drawn on reminder fire and `rearmAll`.

**Web-app import** lives in `:core:database`'s `importer/WebImporter` and is offered from Settings (not Home).

## Web app (legacy)

### Commands

```bash
npm run dev       # vite dev server
npm run build      # tsc -b && vite build (type-check is part of the build, no separate typecheck script)
npm run lint       # oxlint
npm run preview    # preview production build
npm run test       # vitest run
```

Tests live next to code they cover (`*.test.ts`). Config = `vitest.config.ts` (separate from `vite.config.ts`, since latter not built w/ `vitest/config`'s `defineConfig`). Coverage limited to pure-logic files in `src/lib` and `src/features/shopping/budget.ts`. No component/integration tests.

### Architecture

**Persistence: single storage seam.** `src/stores/appStore.ts` = one zustand store (w/ `persist` middleware) holding all app state: `settings`, `shoppingItems`, `tasks`, `importantDates`. Never touches `localStorage` directly; goes through `src/lib/storage.ts`'s `createAppStorage()` adapter. Move to real backend = only `storage.ts` changes. Keep all persisted state in this store. No parallel stores, no `localStorage` read/write elsewhere.

**Domain types** in `src/types/models.ts`: source of truth for shopping/task/date shapes, categories (`SHOPPING_CATEGORIES`, `TASK_CATEGORIES`), label maps (`PRIORITY_LABEL`, `SHOPPING_STATUS_LABEL`). Categories/enums = Hebrew string literals used directly as data, not just labels. New category = edit `as const` array here.

**Feature-sliced structure**: `src/features/{home,shopping,tasks,dates,settings}` each hold page + form/card components specific to that feature. Cross-feature UI (nav, layout shell) in `src/components/layout`. Moon countdown in `src/components/countdown`. Generic shadcn/radix primitives in `src/components/ui` (standard shadcn setup, see `components.json`).

**Pure logic in `src/lib`**: `pregnancy.ts` (due-date math, weekly info, weekly fruit-size comparison, moon fraction), `messages.ts` (daily message picker), `budget.ts` under `features/shopping` (spend calculations), `hospital-bag-preset.ts` (seed data for hospital-bag task preset). Keep date/domain math here, not inline in components.

**Routing**: `src/app/router.tsx` + `src/app/layout.tsx` (`RootLayout`). Nav items declared once in `src/components/layout/nav-items.ts`, rendered as desktop top pill-nav and mobile bottom tab bar in `RootLayout`.

**Design tokens**: all color/radius/font tokens = CSS custom properties in `src/index.css` under `:root` / `.dark`, mapped into Tailwind v4 via `@theme inline`. Named tokens beyond shadcn defaults: `moss`, `blush`. Headings use `--font-heading` (Assistant Variable), body `--font-sans` (Heebo Variable); both support Hebrew. New colors/fonts go here as CSS vars, not one-off Tailwind arbitrary values. Moon-countdown card (`src/components/countdown/moon-countdown.tsx`) hardcodes own always-dark "night sky" palette, independent of light/dark theme. Keep those hex values synced w/ `.dark`'s tone if dark palette changes.

**Bottom sheets and keyboard**: add/edit forms (`shopping-item-form.tsx`, `task-form.tsx`, `date-form.tsx`) use `Sheet` (`side="bottom"`) w/ max-height clamped to `--visual-vh` CSS var, kept live by `useVisualViewportHeight()` (`src/lib/use-visual-viewport.ts`, mounted once in `RootLayout`). iOS Safari fallback for keyboard covering sheet. On Chromium, `index.html`'s `interactive-widget=resizes-content` viewport meta handles it natively. New bottom sheet w/ form inputs: reuse `max-h-[min(92dvh,calc(var(--visual-vh,100dvh)*0.92))]` pattern, not bare `dvh` value.

**PWA / deploy**: `vite.config.ts` sets `base: '/Baby_app/'` for GitHub Pages. Must match repo name if repo renamed. `VitePWA` config (manifest, workbox caching) also there. Auto-deploys via `.github/workflows/deploy.yml` on push to `main`. GitHub Pages source must be set to "GitHub Actions" once per repo.

**Compiler**: React Compiler enabled via `@rolldown/plugin-babel` + `reactCompilerPreset()` in `vite.config.ts`. Avoid manual `useMemo`/`useCallback` without specific reason; compiler handles most.

**Path alias**: `@/*` → `./src/*` (set in both `tsconfig.app.json` and `vite.config.ts`).
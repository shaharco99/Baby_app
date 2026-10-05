# Web app (legacy, retired)

Guidance for the retired React PWA in `src/`; the active product is the Android app (see root `CLAUDE.md`). Commands: `package.json` scripts.

Tests live next to code they cover (`*.test.ts`). Config = `vitest.config.ts` (separate from `vite.config.ts`, since latter not built w/ `vitest/config`'s `defineConfig`). Coverage limited to pure-logic files in `src/lib` and `src/features/shopping/budget.ts`. No component/integration tests.

## Architecture

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
# 001 — Android architecture

**Status:** Accepted (2026-08-15)

## Context

Existing product: Vite + React 19 PWA, all data in `localStorage`. No backend, API, auth, or network code. Conversion spec wants real native Android app, explicitly not WebView wrapper. Adds folders, documents, scanning, menstrual cycle tracking, offline-first sync for two users.

## Decision

**Multi-module, feature-vertical.** `:core:*` = shared infrastructure, `:feature:*` = screens. Feature modules never depend on each other; `:app` wires navigation between them. `:core:model`, `:core:common`, `:core:domain`, `:core:crypto` are pure Kotlin JVM modules with zero Android deps, so domain/crypto logic is testable without emulator.

**UI layer.** Compose + Material 3. MVVM with MVI-style state/effect split. Each screen exposes one immutable `UiState` as `StateFlow`, plus `Channel`-backed effects stream for fire-once imperatives (navigation, snackbars). Effect emitted while app is backgrounded gets buffered, not dropped. No business logic in composables.

**Data layer.** Room (SQLCipher-encrypted) = single source of truth for UI. Network never on render path: UI reads local data, background sync engine updates it. Repository boundary maps errors to `AppError`. Platform exceptions never leak past it.

**DI: Koin**, not Hilt. Conversion spec names Koin explicitly (§5). Hilt normally default for pure-Android project, but Koin integrates simpler with pure-JVM modules, so fits module layout above.

**Build.** Gradle convention plugins in `build-logic` (application, library, compose, feature, room, jvm), so ~15 modules don't each repeat same config. Versions in single catalog, all pinned to stable releases. JDK 21 (LTS) = toolchain anchor.

## Consequences

- AGP 9 has built-in Kotlin support. `kotlin-android` plugin gone, library modules no longer accept `targetSdk`. Conventions written against real AGP 9 API.
- `allWarningsAsErrors` on for Kotlin; lint aborts on error. Dependency-freshness checks disabled: upgrades = reviewed decision, not build failure.
- Pure-JVM core modules make crypto/domain tests run in seconds on JVM, so security acceptance tests practical to run on every change.
# 012 — Push wake-up, and background decryption

**Status:** Accepted (2026-09-21)

## Context

Feed and pump reminders = exact `AlarmManager` alarms, derived from newest record in local DB. Correct on phone that logged feed. Wrong on other one.

Nothing reached device from server. No FCM, no edge function, no Realtime subscription. Three pull paths, all driven by this device:

- own write, via `SyncTrigger`;
- 30-second poll, foreground only (`ForegroundSyncController`);
- six-hourly `SyncWorker`.

So: partner logs feed on Pixel at 02:10. Xiaomi app closed. Its pending alarm still points at old feed, rings at wrong time, keeps doing so until someone opens app. On MIUI six-hourly worker not dependable either. Battery manager may never run it.

Second problem, under first. `SessionState` holds workspace key in memory only. `RoomSyncStore.applyRemote` returns immediately when workspace id null, and `RecordCodec` cannot decrypt without key. Woken app with no open session pulls nothing it can read. Waking it achieves nothing.

## Decision

**A content-free wake-up over FCM.**

`device_push_tokens` (migration 0011) holds one row per device: device id, workspace, user, FCM token. RLS scoped like everything else here: member sees own workspace's rows, may only register self, may not touch partner's row.

After sync cycle pushes anything, client calls `notify-workspace` edge function (`PartnerWakeUp`, bound in `networkModule`). Function checks membership **as the caller**, with client built from caller's JWT. RLS answers question; function does not trust workspace id it was handed. Only then reads tokens with service role, sends high-priority data-only FCM message to every workspace device except caller's own.

Message carries `{type: "workspace-changed", workspaceId}`. Nothing else. Cannot carry anything else: server has only ciphertext. `PushMessagingService` starts sync; woken device decrypts arrivals itself, re-derives own alarms from own DB. Why this is wake-up, not notification: server that could write notification text = server that could read feed.

High priority + data-only on purpose. Normal-priority data messages exactly what Doze holds back, and holding back is the bug.

**Firebase without `google-services.json`.** `FirebaseOptions` built from four `BuildConfig` fields, read from `local.properties` or `-P`, same as Supabase connection details. google-services Gradle plugin fails build outright when file absent. Would break every fork and fresh clone. Here, unset values = push off, everything else works unchanged. None of four is secret; service account that can actually send lives only in edge function environment.

**Background decryption.** `WorkspaceKeyProvider` and workspace-id supplier now fall back to `DeviceIdentity` when `SessionState` empty:

```kotlin
session.keyProvider().current() ?: identity.workspaceKey()
session.workspaceId ?: identity.workspaceId
```

`DeviceIdentity` reads Keystore-sealed copy whose wrapping key created deliberately **without** `setUserAuthenticationRequired` (`KeystoreSealedBox`). Pairing screen already re-derives from it every launch.

### The trade-off, stated plainly

`SessionState` doc comment claimed locked app "genuinely cannot read its own data". After this, background sync reads it while app locked. Comment corrected, not left overclaiming.

Honest accounting: SQLCipher passphrase *also* Keystore-sealed with no user authentication required (`KeystoreDatabasePassphrase`), so anything running as this app could already open DB. Lock = lock on UI, always was. What changes: background code path now exercises that capability.

Threat model unchanged where it matters (`005-data-privacy.md`, `007-encryption.md`): server still never sees plaintext; stolen phone no more readable than before. Attacker with code execution as this app already had passphrase. Given up: narrower property that a *locked* app performs no decryption.

Alternative: leave reminder wrong until someone opens app. That is the bug.

## Consequences

- **Migration 0011 must be applied before tagging a release.** Nothing applies migrations automatically here. Without it, token registration fails; push silently does nothing, app falls back to polling.
- Edge function needs `FCM_PROJECT_ID` and `FCM_SERVICE_ACCOUNT` in environment. Must deploy separately from app.
- Periodic `SyncWorker` drops from six hours to one, registered with `UPDATE` not `KEEP` so existing installs actually move. Now backstop for dropped push, not only background path.
- Device that never registers (no Play Services, no Firebase configured, network down at registration) never woken. Polls, exactly as before. Registration failures swallowed for that reason.
- Tokens rotate without warning. `onNewToken` re-registers; token FCM reports unregistered gets deleted by edge function, so dead rows don't pile up.
- Sign-out unregisters row before `DeviceIdentity.forget()` takes device id with it.
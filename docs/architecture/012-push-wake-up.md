# 012 — Push wake-up, and background decryption

**Status:** Accepted (2026-09-21)

## Context

Feed and pump reminders are exact `AlarmManager` alarms, derived from the newest record in the local database. Correct on the phone that logged the feed. Wrong on the other one.

Nothing reached a device from the server. No FCM, no edge function, no Realtime subscription. Three pull paths existed, all of them driven by this device:

- a write of its own, via `SyncTrigger`;
- a 30-second poll, only while the app is in the foreground (`ForegroundSyncController`);
- a six-hourly `SyncWorker`.

So: partner logs a feed on the Pixel at 02:10. Xiaomi's app closed. Its pending alarm still points at the old feed and rings at the wrong time, and keeps doing so until someone opens the app. On MIUI the six-hourly worker is not dependable either — the battery manager may never run it.

Second problem, underneath the first. `SessionState` holds the workspace key in memory only. `RoomSyncStore.applyRemote` returns immediately when the workspace id is null, and `RecordCodec` cannot decrypt without a key. A woken app with no open session pulls nothing it can read. Waking it would achieve nothing.

## Decision

**A content-free wake-up over FCM.**

`device_push_tokens` (migration 0011) holds one row per device: device id, workspace, user, FCM token. RLS scoped like everything else here — a member sees their own workspace's rows, may only register themselves, may not touch the partner's row.

After a sync cycle pushes anything, the client calls the `notify-workspace` edge function (`PartnerWakeUp`, bound in `networkModule`). The function checks membership **as the caller**, with a client built from the caller's JWT, so RLS answers the question rather than the function trusting the workspace id it was handed. Only then does it read tokens with the service role, and send a high-priority data-only FCM message to every device in the workspace except the caller's own.

The message carries `{type: "workspace-changed", workspaceId}`. Nothing else. It cannot carry anything else: the server has only ciphertext. `PushMessagingService` starts a sync; the woken device decrypts what arrives itself and re-derives its own alarms from its own database. That is why this is a wake-up, not a notification — a server that could write the notification text would be a server that could read the feed.

High priority and data-only on purpose. Normal-priority data messages are exactly what Doze holds back, and holding them back is the bug.

**Firebase without `google-services.json`.** `FirebaseOptions` is built from four `BuildConfig` fields, read from `local.properties` or `-P`, the same way the Supabase connection details are. The google-services Gradle plugin fails the build outright when the file is absent, which would break every fork and every fresh clone. Here, unset values mean push is off and everything else works unchanged. None of the four is a secret; the service account that can actually send is only in the edge function's environment.

**Background decryption.** `WorkspaceKeyProvider` and the workspace-id supplier now fall back to `DeviceIdentity` when `SessionState` is empty:

```kotlin
session.keyProvider().current() ?: identity.workspaceKey()
session.workspaceId ?: identity.workspaceId
```

`DeviceIdentity` reads a Keystore-sealed copy whose wrapping key is created deliberately **without** `setUserAuthenticationRequired` (`KeystoreSealedBox`) — the pairing screen already re-derives from it on every launch.

### The trade-off, stated plainly

`SessionState`'s doc comment claimed a locked app "genuinely cannot read its own data". After this, background sync reads it while the app is locked. That comment has been corrected rather than left overclaiming.

The honest accounting: the SQLCipher passphrase is *also* Keystore-sealed with no user authentication required (`KeystoreDatabasePassphrase`), so anything running as this app could already open the database. The lock is a lock on the UI, and always was. What changes is that a background code path now exercises that capability.

Threat model is unchanged where it matters (`005-data-privacy.md`, `007-encryption.md`): the server still never sees plaintext, and a stolen phone is no more readable than it was — the attacker with code execution as this app already had the passphrase. What is given up is the narrower property that a *locked* app performs no decryption.

The alternative was to leave the reminder wrong until someone opens the app, which is the bug.

## Consequences

- **Migration 0011 must be applied before tagging a release.** Nothing applies migrations automatically here. Without it, token registration fails; push silently does nothing and the app falls back to polling.
- The edge function needs `FCM_PROJECT_ID` and `FCM_SERVICE_ACCOUNT` in its environment, and must be deployed separately from the app.
- The periodic `SyncWorker` drops from six hours to one, registered with `UPDATE` rather than `KEEP` so existing installs actually move. It is now a backstop for a dropped push, not the only background path.
- A device that never registers — no Play Services, no Firebase configured, network down at registration — is never woken. It polls, exactly as before. Registration failures are swallowed for that reason.
- Tokens rotate without warning. `onNewToken` re-registers; a token FCM reports as unregistered is deleted by the edge function, so dead rows do not accumulate.
- Sign-out unregisters the row before `DeviceIdentity.forget()` takes the device id with it.

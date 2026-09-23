# 007 — Encryption

**Status:** Accepted (2026-08-15)

## Context

Per `005-data-privacy.md`, server never read workspace content. Data sensitive (menstrual cycle records, medical documents, personal notes). Must not leak outside couple, hosting provider included.

Spec (§45) require established crypto libraries, forbid invented algorithms, forbid end-to-end encryption claims unless real.

## Decision

**Bouncy Castle lightweight API**, not libsodium.

libsodium Android binding (`lazysodium-android`) JNI-bound. Force every crypto test onto emulator, make crypto module Android-only. Bouncy Castle pure Java, same behavior JVM and Android, ships RFC 9180 HPKE. Key wrapping follow published standard, not hand-built sealed box. So `:core:crypto` plain Kotlin JVM module, fast ordinary unit tests.

**Record encryption.** ChaCha20-Poly1305. Each write derive fresh key via HKDF-SHA256 over random 16-byte salt. This make all-zero nonce safe: (key, nonce) pair repeat only if same salt drawn twice. Envelope layout:

```
[version:1][salt:16][ciphertext || poly1305 tag:16]
```

Leading version byte = forward-compat seam. Future build can change construction, still read old payloads. Associated data bind ciphertext to record id and version, so ciphertext cannot move between records.

**Keys.**

| Key | Purpose | Where it lives |
|---|---|---|
| Workspace key (32 B) | Encrypts every record and file | Device only, never sent unwrapped |
| Device X25519 keypair | Receives workspace key at pairing | Private half sealed by Android Keystore AES-GCM key, blob in DataStore |
| Recovery phrase | Workspace key itself, encoded | Written down by user, never stored anywhere |

Keystore cannot hold raw X25519 material usable by HPKE. Hence wrap-private-key indirection, not Keystore-native key.

**Pairing.** Inviting device seal workspace key to joining device X25519 public key, HPKE base mode. Server relay opaque blob, cannot open.

**Recovery phrase.** Workspace key rendered as 24-word BIP-39 mnemonic. Phrase *is* key encoded, not passphrase unlocking stored copy. 32-byte key = exactly 256 bits BIP-39 entropy = exactly 24 words. Result: nothing extra on server, no KDF parameters to drift between app versions, no wrapped blob to lose. BIP-39 checksum make mistyped phrase fail loud, not silently yield key that decrypt nothing. Verified against official BIP-39 English test vectors.

Trade-off: rotating workspace key change recovery phrase. User must be told to record new one. Accepted: rotation rare, deliberate.

## Consequences

- No server-side search, filtering, sorting, validation. Search = Room FTS4 over locally decrypted data (corrected from earlier "FTS5". Room 2.8.4 has no `@Fts5` annotation, only `@Fts3`/`@Fts4`. Found during Phase 8 search build).
- No server-generated notification content. Reminders scheduled locally.
- Metadata still leak: row counts and `updated_at` reveal *that* something logged and when, not what. Fix need padding and decoy traffic. Out of scope, stated not glossed.
- Lose both devices and recovery phrase = data unrecoverable. Inherent to design. Setup flow must say so plain.

**What may be claimed.** Content end-to-end encrypted between two devices. Account metadata, timestamps, record counts not. Docs must say exactly that, no more.
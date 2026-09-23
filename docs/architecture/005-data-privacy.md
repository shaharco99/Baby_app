# 005 — Data privacy: the threat model

**Status:** Accepted (2026-08-15)

## Context

Original Android-conversion spec (condensed into `docs/TASK-HISTORY.md`, full text in git history) framed privacy as *partner-vs-partner* problem:
every item private by default, explicit opt-in sharing, granular cycle-sharing permissions,
test matrix (§69) asserting husband cannot read wife's private cycle data.

Literal implementation needs server that reads data. Field-level sharing decisions
("share period dates but not symptoms") must run somewhere partner's query reaches.
Rules out end-to-end encryption.

Before build, user corrected requirement:

> the data between both of us will be shared, but it will have security and privacy to
> others — it's sensitive information and I don't want it to leak

## Decision

Privacy boundary: **couple vs outside world**, not partner vs partner.

- Both users see everything in workspace. No `visibility` field, no
  `sharedWith`, no `cycle_sharing_permissions`, no per-item private toggle anywhere.
- `owner_id` kept only for attribution ("created by"). Never input to authorization decision.
- Protect against: Supabase, attacker with DB access, stolen phone,
  anyone not one of two users.

## Consequences

**Good.** No server-side sharing logic. Server never needs to read data,
so real end-to-end encryption practical (see `007-encryption.md`). Data model
loses whole dimension: no visibility checks spread across queries, sync,
search, calendar, notifications. No bug class where one check forgotten.

**Superseded spec sections.** §7, §12, §33–35, §40, §42, §50, §58–59, §62 of
conversion spec collapse to "both members, always." §69 test matrix replaced by
outsider-focused acceptance tests in `docs/specs` planning notes: ciphertext-only storage,
outsider access denied, unauthenticated access denied, tamper detection, invitation
expiry/reuse/revocation, workspace member cap, no plaintext in local database.

**Cost.** If users later want something hidden from each other (surprise gift,
private note), not config change. Needs second key per user, rethink of what
server stores. Recorded here so trade-off made consciously.
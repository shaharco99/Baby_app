# 005 — Data privacy: the threat model

**Status:** Accepted (2026-08-15)

## Context

The original Android-conversion spec (condensed into `docs/TASK-HISTORY.md`, full text in git history) specced privacy as *partner-vs-partner* problem:
every item private by default, explicit opt-in sharing, granular cycle-sharing permissions,
test matrix (§69) asserting husband cannot read wife's private cycle data.

Implementing literal requires server that read data — field-level sharing decisions
("share period dates but not symptoms") gotta evaluate somewhere partner's query reach.
Rules out end-to-end encryption.

Before build, user corrected requirement:

> the data between both of us will be shared, but it will have security and privacy to
> others — it's sensitive information and I don't want it to leak

## Decision

Privacy boundary: **couple vs outside world**, not partner vs partner.

- Both users see everything in workspace. No `visibility` field, no
  `sharedWith`, no `cycle_sharing_permissions`, no per-item private toggle anywhere.
- `owner_id` retained purely as attribution ("created by"), never input to authorization decision.
- Protection target: Supabase, attacker with DB access, stolen phone,
  anyone not one of two users.

## Consequences

**Good.** No server-side sharing logic to evaluate — server never needs read data,
makes genuine end-to-end encryption practical (see `007-encryption.md`). Data model
loses entire dimension: no visibility checks scattered across queries, sync,
search, calendar, notifications — no bug class where one forgets.

**Superseded spec sections.** §7, §12, §33–35, §40, §42, §50, §58–59, §62 of
conversion spec collapse to "both members, always." §69 test matrix replaced by
outsider-focused acceptance tests in `docs/specs` planning notes: ciphertext-only storage,
outsider access denied, unauthenticated access denied, tamper detection, invitation
expiry/reuse/revocation, workspace member cap, no plaintext in local database.

**Cost.** If users later want something hidden from each other — surprise gift,
private note — not config change. Needs second key per user, re-think what
server stores. Recorded here so trade-off conscious one.
-- Nappy changes logged on their own screen (changes marked on a feed stay on the feed).
--
-- Nothing but the enum value: `public.records` is generic, one ciphertext blob per record
-- whatever its type. Kept in step with `EntityType.DIAPER_CHANGE`'s wire name in
-- `core/model/.../EntityType.kt`.
--
-- Idempotent, like every migration here: `add value if not exists` re-applies as a no-op.
alter type public.entity_type add value if not exists 'diaper_change';

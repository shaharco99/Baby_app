-- Pumping sessions.
--
-- Nothing but the enum value: `public.records` is generic, holding one ciphertext blob per
-- record whatever its type, so a new synced entity needs only a discriminator the client and
-- the server agree on. Kept in step with `EntityType.PUMP_SESSION`'s wire name in
-- `core/model/.../EntityType.kt`.
alter type public.entity_type add value if not exists 'pump_session';

-- Children and feeding entries.
--
-- Both ride the generic `public.records` table like every other synced entity: the server only
-- ever sees ciphertext, so there is no new table, no new column and no new RLS policy here —
-- only the two discriminator values the client sends. Must stay in sync with
-- `EntityType` in `core/model/EntityType.kt`.

alter type public.entity_type add value if not exists 'baby';
alter type public.entity_type add value if not exists 'feeding_entry';

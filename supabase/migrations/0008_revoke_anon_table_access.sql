-- Anonymous callers hold no table privilege in the public schema.
--
-- The same miss as 0004, one layer over: 0004 revoked EXECUTE on functions from `anon`, but
-- nothing ever revoked table privileges, and Supabase's default privileges grant SELECT /
-- INSERT / UPDATE / DELETE on tables in `public` to `anon` as a named role. The posture held
-- only because the local stack's image happened not to apply them; a newer image does, and
-- `001_access_control.sql`'s "denied access to records outright" started failing.
--
-- No data was reachable either way: RLS is enabled on every table in this schema and all 20
-- policies are scoped `to authenticated`, so an anonymous select returned zero rows rather
-- than anyone's ciphertext. This restores the intended second layer — refused before RLS is
-- ever consulted — and makes it explicit rather than inherited.
--
-- `authenticated` is deliberately untouched: it is the role every signed-in client uses, and
-- its access is what the RLS policies exist to scope.

revoke all privileges on all tables in schema public from anon;
revoke all privileges on all sequences in schema public from anon;

-- Covers tables added by later migrations, so this does not have to be repeated each time.
alter default privileges in schema public revoke all on tables from anon;
alter default privileges in schema public revoke all on sequences from anon;

-- USAGE on the schema itself is deliberately left alone. Revoking it would refuse anon at the
-- schema rather than the table, which reads as the same denial here but changes the error
-- PostgREST returns for every anonymous request — a wider blast radius than this fix needs.

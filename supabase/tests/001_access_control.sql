-- Access-control acceptance tests.
--
-- These assert the boundary that matters for this product: the two members of a workspace
-- see everything in it, and nobody else sees anything at all. Run with `supabase test db`.

begin;

create extension if not exists pgtap with schema extensions;

select plan(25);

-- ---------------------------------------------------------------------------
-- Fixtures
-- ---------------------------------------------------------------------------

create schema if not exists tests;

-- Switches the session to a given authenticated user, the way PostgREST does.
create or replace function tests.act_as(user_id uuid)
returns void
language plpgsql
as $$
begin
    perform set_config('role', 'authenticated', true);
    perform set_config(
        'request.jwt.claims',
        json_build_object('sub', user_id::text, 'role', 'authenticated')::text,
        true
    );
end;
$$;

create or replace function tests.act_as_anon()
returns void
language plpgsql
as $$
begin
    perform set_config('role', 'anon', true);
    perform set_config('request.jwt.claims', json_build_object('role', 'anon')::text, true);
end;
$$;

create or replace function tests.act_as_admin()
returns void
language plpgsql
as $$
begin
    perform set_config('role', 'postgres', true);
    perform set_config('request.jwt.claims', '', true);
end;
$$;

create or replace function tests.make_user(email text)
returns uuid
language plpgsql
as $$
declare
    uid uuid := gen_random_uuid();
begin
    insert into auth.users (instance_id, id, aud, role, email)
    values ('00000000-0000-0000-0000-000000000000', uid, 'authenticated', 'authenticated', email);
    return uid;
end;
$$;

-- Shahar and Topaz are the couple; Mallory is an unrelated account on the same instance.
-- Fixtures live in the `tests` schema rather than a temp table: the session spends most of
-- this file acting as `authenticated`, which cannot reach another role's temp schema.
create table tests.ctx (name text primary key, id uuid not null);

insert into tests.ctx values
    ('shahar',  tests.make_user('shahar@example.test')),
    ('topaz',   tests.make_user('topaz@example.test')),
    ('mallory', tests.make_user('mallory@example.test'));

create table tests.ws (id uuid not null);

create or replace function tests.uid(who text)
returns uuid language sql stable as $$ select id from tests.ctx where name = who $$;

create or replace function tests.workspace()
returns uuid language sql stable as $$ select id from tests.ws limit 1 $$;

-- The helpers run while the session is acting as authenticated/anon, so those roles need to
-- reach them. This is test scaffolding only; nothing here exists in a deployed database.
grant usage on schema tests to authenticated, anon;
grant select on tests.ctx to authenticated, anon;
grant select, insert on tests.ws to authenticated, anon;
grant execute on all functions in schema tests to authenticated, anon;

-- ---------------------------------------------------------------------------
-- Workspace creation
-- ---------------------------------------------------------------------------

select tests.act_as(tests.uid('shahar'));

insert into tests.ws (id) select public.create_workspace();

select isnt(tests.workspace(), null, 'create_workspace returns a workspace id');

select is(
    (select count(*)::int from public.workspace_members where workspace_id = tests.workspace()),
    1,
    'the creator is the sole member to begin with'
);

select is(
    (select count(*)::int from public.workspaces),
    1,
    'the creator can see their own workspace'
);

-- ---------------------------------------------------------------------------
-- An outsider sees nothing, even before any pairing
-- ---------------------------------------------------------------------------

select tests.act_as(tests.uid('mallory'));

select is(
    (select count(*)::int from public.workspaces),
    0,
    'an outsider cannot see the workspace'
);

select is(
    (select count(*)::int from public.workspace_members),
    0,
    'an outsider cannot see membership rows'
);

-- ---------------------------------------------------------------------------
-- Records: written by one member, readable by the other, invisible to outsiders
-- ---------------------------------------------------------------------------

select tests.act_as(tests.uid('shahar'));

insert into public.records (id, workspace_id, entity_type, ciphertext, created_by)
values (
    '11111111-1111-1111-1111-111111111111',
    tests.workspace(),
    'task',
    decode('0102030405060708090a0b0c0d0e0f10', 'hex'),
    tests.uid('shahar')
);

select is(
    (select count(*)::int from public.records),
    1,
    'the author can read their own record'
);

select throws_ok(
    $$ insert into public.records (id, workspace_id, entity_type, ciphertext, created_by)
       values (gen_random_uuid(), tests.workspace(), 'task', '\x00'::bytea,
               tests.uid('topaz')) $$,
    '42501',
    null,
    'a member cannot forge created_by as someone else'
);

select tests.act_as(tests.uid('mallory'));

select is(
    (select count(*)::int from public.records),
    0,
    'an outsider reading records gets zero rows, not a permission error'
);

select is(
    (select count(*)::int from public.records
      where id = '11111111-1111-1111-1111-111111111111'),
    0,
    'an outsider cannot fetch a record even by its exact id'
);

-- An UPDATE whose USING clause matches nothing is a silent no-op under RLS, not an error.
-- The assertion is therefore that no row was touched, not that an exception was raised.
with attempted as (
    update public.records
       set ciphertext = '\xdead'::bytea
     where id = '11111111-1111-1111-1111-111111111111'
    returning 1
)
select is(
    (select count(*)::int from attempted),
    0,
    'an outsider updating a record changes nothing'
);

select tests.act_as_anon();

-- Stronger than an empty result: anon holds no table privilege at all, so the request is
-- refused before RLS is ever consulted.
select throws_ok(
    $$ select count(*) from public.records $$,
    '42501',
    null,
    'an unauthenticated caller is denied access to records outright'
);

-- ---------------------------------------------------------------------------
-- Invitations
-- ---------------------------------------------------------------------------

select tests.act_as(tests.uid('shahar'));

insert into public.couple_invitations (workspace_id, created_by, token_hash, expires_at)
values (
    tests.workspace(),
    tests.uid('shahar'),
    extensions.digest(convert_to('good-token', 'UTF8'), 'sha256'),
    now() + interval '1 day'
);

insert into public.couple_invitations (workspace_id, created_by, token_hash, expires_at)
values (
    tests.workspace(),
    tests.uid('shahar'),
    extensions.digest(convert_to('expired-token', 'UTF8'), 'sha256'),
    now() - interval '1 second'
);

insert into public.couple_invitations (workspace_id, created_by, token_hash, expires_at, revoked_at)
values (
    tests.workspace(),
    tests.uid('shahar'),
    extensions.digest(convert_to('revoked-token', 'UTF8'), 'sha256'),
    now() + interval '1 day',
    now()
);

select throws_ok(
    $$ select public.accept_invitation('good-token') $$,
    '22023',
    null,
    'the inviter cannot accept their own invitation'
);

select tests.act_as(tests.uid('mallory'));

select is(
    (select count(*)::int from public.couple_invitations),
    0,
    'token hashes are not readable by anyone but the inviter'
);

select throws_ok(
    $$ select public.accept_invitation('no-such-token') $$,
    '22023',
    null,
    'an unknown token is rejected'
);

select throws_ok(
    $$ select public.accept_invitation('expired-token') $$,
    '22023',
    null,
    'an expired token is rejected'
);

select throws_ok(
    $$ select public.accept_invitation('revoked-token') $$,
    '22023',
    null,
    'a revoked token is rejected'
);

-- Topaz accepts and becomes the second member.
select tests.act_as(tests.uid('topaz'));

select is(
    public.accept_invitation('good-token'),
    tests.workspace(),
    'a valid token joins the invitee to the workspace'
);

select is(
    (select count(*)::int from public.records),
    1,
    'the partner can now read records written by the other member'
);

select is(
    (select encode(ciphertext, 'hex') from public.records
      where id = '11111111-1111-1111-1111-111111111111'),
    '0102030405060708090a0b0c0d0e0f10',
    'the partner reads the same ciphertext that was written'
);

select throws_ok(
    $$ select public.accept_invitation('good-token') $$,
    '22023',
    null,
    'a token cannot be reused once accepted'
);

-- ---------------------------------------------------------------------------
-- Two-member cap
-- ---------------------------------------------------------------------------

select tests.act_as(tests.uid('shahar'));

insert into public.couple_invitations (workspace_id, created_by, token_hash, expires_at)
values (
    tests.workspace(),
    tests.uid('shahar'),
    extensions.digest(convert_to('third-wheel', 'UTF8'), 'sha256'),
    now() + interval '1 day'
);

select tests.act_as(tests.uid('mallory'));

select throws_ok(
    $$ select public.accept_invitation('third-wheel') $$,
    '23514',
    null,
    'a third member cannot join the workspace'
);

select tests.act_as(tests.uid('mallory'));

select is(
    (select count(*)::int from public.records),
    0,
    'the rejected third party still sees no records'
);

-- ---------------------------------------------------------------------------
-- Schema shape: content has nowhere to hide in plaintext
-- ---------------------------------------------------------------------------

select tests.act_as_admin();

select is(
    (select count(*)::int
       from information_schema.columns
      where table_schema = 'public'
        and table_name = 'records'
        and data_type in ('text', 'character varying', 'json', 'jsonb')),
    0,
    'the records table has no free-text column that could hold plaintext content'
);

select is(
    (select count(*)::int
       from pg_tables
      where schemaname = 'public'
        and not rowsecurity),
    0,
    'every table in the public schema has row level security enabled'
);

select bag_eq(
    $$ select tablename::text from pg_tables where schemaname = 'public' $$,
    $$ values ('profiles'), ('workspaces'), ('workspace_members'), ('device_keys'),
              ('wrapped_workspace_keys'), ('couple_invitations'), ('records'),
              ('document_blobs'), ('device_push_tokens'), ('device_presence') $$,
    'the public schema holds exactly the expected tables'
);

select * from finish();

rollback;

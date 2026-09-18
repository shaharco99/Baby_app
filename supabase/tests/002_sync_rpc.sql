-- Sync RPC behaviour: optimistic concurrency, idempotent retries, tombstones and paging.

begin;

create extension if not exists pgtap with schema extensions;

select plan(21);

create schema if not exists tests;

create or replace function tests.act_as(user_id uuid)
returns void language plpgsql as $$
begin
    perform set_config('role', 'authenticated', true);
    perform set_config(
        'request.jwt.claims',
        json_build_object('sub', user_id::text, 'role', 'authenticated')::text,
        true
    );
end;
$$;

create or replace function tests.make_user(email text)
returns uuid language plpgsql as $$
declare uid uuid := gen_random_uuid();
begin
    insert into auth.users (instance_id, id, aud, role, email)
    values ('00000000-0000-0000-0000-000000000000', uid, 'authenticated', 'authenticated', email);
    return uid;
end;
$$;

create table tests.ctx (name text primary key, id uuid not null);
create table tests.ws (id uuid not null);

insert into tests.ctx values
    ('shahar',  tests.make_user('sync-shahar@example.test')),
    ('topaz',   tests.make_user('sync-topaz@example.test')),
    ('mallory', tests.make_user('sync-mallory@example.test'));

create or replace function tests.uid(who text)
returns uuid language sql stable as $$ select id from tests.ctx where name = who $$;

create or replace function tests.workspace()
returns uuid language sql stable as $$ select id from tests.ws limit 1 $$;

grant usage on schema tests to authenticated;
grant select on tests.ctx to authenticated;
grant select, insert on tests.ws to authenticated;
grant execute on all functions in schema tests to authenticated;

-- Shahar creates the workspace and pairs with Topaz.
select tests.act_as(tests.uid('shahar'));
insert into tests.ws (id) select public.create_workspace();

insert into public.couple_invitations (workspace_id, created_by, token_hash, expires_at)
values (
    tests.workspace(), tests.uid('shahar'),
    extensions.digest(convert_to('pair-token', 'UTF8'), 'sha256'),
    now() + interval '1 day'
);

select tests.act_as(tests.uid('topaz'));
select public.accept_invitation('pair-token');

-- ---------------------------------------------------------------------------
-- Insert
-- ---------------------------------------------------------------------------

select tests.act_as(tests.uid('shahar'));

select is(
    public.push_records(tests.workspace(), jsonb_build_array(jsonb_build_object(
        'id', '22222222-2222-2222-2222-222222222222',
        'entity_type', 'task',
        'ciphertext', encode('\x0102030405060708090a0b0c0d0e0f10'::bytea, 'base64'),
        'base_version', 0,
        'client_mutation_id', gen_random_uuid()::text,
        'deleted', false
    ))) -> 0 ->> 'status',
    'applied',
    'a new record is applied'
);

select is(
    (select version from public.records where id = '22222222-2222-2222-2222-222222222222'),
    1,
    'a new record starts at version 1'
);

select is(
    (select encode(ciphertext, 'base64') from public.records
      where id = '22222222-2222-2222-2222-222222222222'),
    encode('\x0102030405060708090a0b0c0d0e0f10'::bytea, 'base64'),
    'the ciphertext is stored byte for byte'
);

-- ---------------------------------------------------------------------------
-- Update with a matching base version
-- ---------------------------------------------------------------------------

select is(
    public.push_records(tests.workspace(), jsonb_build_array(jsonb_build_object(
        'id', '22222222-2222-2222-2222-222222222222',
        'entity_type', 'task',
        'ciphertext', encode('\xaaaa'::bytea, 'base64'),
        'base_version', 1,
        'client_mutation_id', gen_random_uuid()::text,
        'deleted', false
    ))) -> 0 ->> 'version',
    '2',
    'a write against the current version bumps to 2'
);

-- ---------------------------------------------------------------------------
-- Stale write conflicts instead of overwriting
-- ---------------------------------------------------------------------------

select tests.act_as(tests.uid('topaz'));

select is(
    public.push_records(tests.workspace(), jsonb_build_array(jsonb_build_object(
        'id', '22222222-2222-2222-2222-222222222222',
        'entity_type', 'task',
        'ciphertext', encode('\xbbbb'::bytea, 'base64'),
        'base_version', 1,
        'client_mutation_id', gen_random_uuid()::text,
        'deleted', false
    ))) -> 0 ->> 'status',
    'conflict',
    'a write against a stale version conflicts'
);

select is(
    (select encode(ciphertext, 'base64') from public.records
      where id = '22222222-2222-2222-2222-222222222222'),
    encode('\xaaaa'::bytea, 'base64'),
    'the conflicting write did not overwrite the stored record'
);

select is(
    public.push_records(tests.workspace(), jsonb_build_array(jsonb_build_object(
        'id', '22222222-2222-2222-2222-222222222222',
        'entity_type', 'task',
        'ciphertext', encode('\xbbbb'::bytea, 'base64'),
        'base_version', 1,
        'client_mutation_id', gen_random_uuid()::text,
        'deleted', false
    ))) -> 0 ->> 'ciphertext',
    encode('\xaaaa'::bytea, 'base64'),
    'a conflict returns the server copy so the client can show both sides'
);

-- ---------------------------------------------------------------------------
-- Idempotent retry
-- ---------------------------------------------------------------------------

select tests.act_as(tests.uid('shahar'));

create temporary table mut as select gen_random_uuid() as id;

select is(
    public.push_records(tests.workspace(), jsonb_build_array(jsonb_build_object(
        'id', '33333333-3333-3333-3333-333333333333',
        'entity_type', 'cycle',
        'ciphertext', encode('\xcccc'::bytea, 'base64'),
        'base_version', 0,
        'client_mutation_id', (select id from mut)::text,
        'deleted', false
    ))) -> 0 ->> 'status',
    'applied',
    'first attempt applies'
);

-- Same mutation id replayed after a lost response: must not conflict with itself.
select is(
    public.push_records(tests.workspace(), jsonb_build_array(jsonb_build_object(
        'id', '33333333-3333-3333-3333-333333333333',
        'entity_type', 'cycle',
        'ciphertext', encode('\xcccc'::bytea, 'base64'),
        'base_version', 0,
        'client_mutation_id', (select id from mut)::text,
        'deleted', false
    ))) -> 0 ->> 'status',
    'applied',
    'a replayed mutation id reports applied rather than conflict'
);

select is(
    (select version from public.records where id = '33333333-3333-3333-3333-333333333333'),
    1,
    'a replayed mutation does not bump the version twice'
);

-- ---------------------------------------------------------------------------
-- Tombstones
-- ---------------------------------------------------------------------------

select is(
    public.push_records(tests.workspace(), jsonb_build_array(jsonb_build_object(
        'id', '33333333-3333-3333-3333-333333333333',
        'entity_type', 'cycle',
        'ciphertext', encode('\xcccc'::bytea, 'base64'),
        'base_version', 1,
        'client_mutation_id', gen_random_uuid()::text,
        'deleted', true
    ))) -> 0 ->> 'status',
    'applied',
    'a delete is applied as a tombstone'
);

select isnt(
    (select deleted_at from public.records where id = '33333333-3333-3333-3333-333333333333'),
    null,
    'the tombstone sets deleted_at rather than removing the row'
);

-- ---------------------------------------------------------------------------
-- Pull
-- ---------------------------------------------------------------------------

select is(
    jsonb_array_length(public.pull_records(tests.workspace(), null, 200)),
    2,
    'pulling from the beginning returns every record, tombstones included'
);

select is(
    jsonb_array_length(public.pull_records(tests.workspace(), 99999999999999, 200)),
    0,
    'pulling from a future cursor returns nothing'
);

select is(
    jsonb_array_length(public.pull_records(tests.workspace(), null, 1)),
    1,
    'the pull limit is honoured'
);

-- ---------------------------------------------------------------------------
-- Outsiders
-- ---------------------------------------------------------------------------

select tests.act_as(tests.uid('mallory'));

select throws_ok(
    format(
        $$ select public.pull_records(%L::uuid, null, 200) $$,
        (select id from tests.ws limit 1)
    ),
    '42501',
    null,
    'an outsider cannot pull from a workspace they do not belong to'
);

select throws_ok(
    format(
        $$ select public.push_records(%L::uuid, '[]'::jsonb) $$,
        (select id from tests.ws limit 1)
    ),
    '42501',
    null,
    'an outsider cannot push to a workspace they do not belong to'
);

-- ---------------------------------------------------------------------------
-- Attribution cannot be rewritten
-- ---------------------------------------------------------------------------

select tests.act_as(tests.uid('topaz'));

select throws_ok(
    $$ update public.records
          set created_by = (select id from tests.ctx where name = 'topaz')
        where id = '22222222-2222-2222-2222-222222222222' $$,
    '42501',
    null,
    'a member cannot reassign authorship of an existing record'
);

select is(
    (select created_by from public.records where id = '22222222-2222-2222-2222-222222222222'),
    tests.uid('shahar'),
    'the original author is unchanged'
);

-- ---------------------------------------------------------------------------
-- The per-child entity types added with the feeding page (0007)
-- ---------------------------------------------------------------------------

select tests.act_as(tests.uid('shahar'));

select is(
    public.push_records(tests.workspace(), jsonb_build_array(jsonb_build_object(
        'id', '44444444-4444-4444-4444-444444444444',
        'entity_type', 'baby',
        'ciphertext', encode('\xdddd'::bytea, 'base64'),
        'base_version', 0,
        'client_mutation_id', gen_random_uuid()::text,
        'deleted', false
    ))) -> 0 ->> 'status',
    'applied',
    'a baby record pushes like any other entity type'
);

select is(
    public.push_records(tests.workspace(), jsonb_build_array(jsonb_build_object(
        'id', '55555555-5555-5555-5555-555555555555',
        'entity_type', 'feeding_entry',
        'ciphertext', encode('\xeeee'::bytea, 'base64'),
        'base_version', 0,
        'client_mutation_id', gen_random_uuid()::text,
        'deleted', false
    ))) -> 0 ->> 'status',
    'applied',
    'a feeding entry pushes like any other entity type'
);

select * from finish();

rollback;

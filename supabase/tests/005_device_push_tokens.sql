-- device_push_tokens: a member sees their workspace's rows and no one else's, a device can only
-- register itself, and nobody can change or delete another device's token. Run with
-- `supabase test db`.

begin;

create extension if not exists pgtap with schema extensions;

select plan(7);

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

create table tests.pt_ctx (name text primary key, id uuid not null);

insert into tests.pt_ctx
select name, gen_random_uuid() from (values ('shahar'), ('topaz'), ('mallory')) v(name);

insert into auth.users (instance_id, id, aud, role, email)
select '00000000-0000-0000-0000-000000000000', id, 'authenticated', 'authenticated',
       name || '@example.test'
  from tests.pt_ctx;

-- The couple's workspace, and a second one that has nothing to do with them.
insert into public.workspaces (id, created_by)
select '11111111-1111-1111-1111-111111111111', id from tests.pt_ctx where name = 'shahar';

insert into public.workspaces (id, created_by)
select '22222222-2222-2222-2222-222222222222', id from tests.pt_ctx where name = 'mallory';

insert into public.workspace_members (workspace_id, user_id)
select '11111111-1111-1111-1111-111111111111', id from tests.pt_ctx where name in ('shahar', 'topaz');

insert into public.workspace_members (workspace_id, user_id)
select '22222222-2222-2222-2222-222222222222', id from tests.pt_ctx where name = 'mallory';

-- Seeded as the table owner, so the rows exist before RLS is exercised against them.
insert into public.device_push_tokens (device_id, workspace_id, user_id, token)
select '33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', id, 'shahar-token'
  from tests.pt_ctx where name = 'shahar';

insert into public.device_push_tokens (device_id, workspace_id, user_id, token)
select '44444444-4444-4444-4444-444444444444', '22222222-2222-2222-2222-222222222222', id, 'mallory-token'
  from tests.pt_ctx where name = 'mallory';

grant usage on schema tests to authenticated, anon;
grant select on tests.pt_ctx to authenticated, anon;
grant execute on all functions in schema tests to authenticated, anon;

select tests.act_as((select id from tests.pt_ctx where name = 'topaz'));

select results_eq(
    $$ select token from public.device_push_tokens $$,
    $$ values ('shahar-token') $$,
    'a member sees their partner''s device and nothing from other workspaces'
);

-- Topaz registering her own phone into her own workspace: allowed.
select lives_ok(
    $$ insert into public.device_push_tokens (device_id, workspace_id, user_id, token)
       select '55555555-5555-5555-5555-555555555555',
              '11111111-1111-1111-1111-111111111111',
              id, 'topaz-token'
         from tests.pt_ctx where name = 'topaz' $$,
    'a member can register their own device'
);

-- Registering a device as somebody else: refused by the insert policy's user_id check.
select throws_ok(
    $$ insert into public.device_push_tokens (device_id, workspace_id, user_id, token)
       select '66666666-6666-6666-6666-666666666666',
              '11111111-1111-1111-1111-111111111111',
              id, 'forged-token'
         from tests.pt_ctx where name = 'shahar' $$,
    '42501',
    null,
    'a member cannot register a device in someone else''s name'
);

-- Registering into a workspace they are not in: refused by the membership check.
select throws_ok(
    $$ insert into public.device_push_tokens (device_id, workspace_id, user_id, token)
       select '77777777-7777-7777-7777-777777777777',
              '22222222-2222-2222-2222-222222222222',
              id, 'intruder-token'
         from tests.pt_ctx where name = 'topaz' $$,
    '42501',
    null,
    'a member cannot register into a workspace they do not belong to'
);

-- Visible, but not writable: the partner's token is theirs to rotate, not hers.
select is_empty(
    $$ update public.device_push_tokens set token = 'hijacked'
        where device_id = '33333333-3333-3333-3333-333333333333'
        returning device_id $$,
    'a member cannot overwrite their partner''s token'
);

select is_empty(
    $$ delete from public.device_push_tokens
        where device_id = '33333333-3333-3333-3333-333333333333'
        returning device_id $$,
    'a member cannot delete their partner''s token'
);

reset role;

select ok(
    not has_table_privilege('anon', 'public.device_push_tokens', 'SELECT'),
    'anon holds no privilege on the table at all'
);

select * from finish();

rollback;

-- workspace_partner_emails: a member sees the other member's email and not their own; an
-- outsider and anon see nothing. Run with `supabase test db`.

begin;

create extension if not exists pgtap with schema extensions;

select plan(5);

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

create table tests.pe_ctx (name text primary key, id uuid not null);

insert into tests.pe_ctx
select name, gen_random_uuid() from (values ('shahar'), ('topaz'), ('mallory')) v(name);

insert into auth.users (instance_id, id, aud, role, email)
select '00000000-0000-0000-0000-000000000000', id, 'authenticated', 'authenticated',
       name || '@example.test'
  from tests.pe_ctx;

insert into public.workspaces (id, created_by)
select '11111111-1111-1111-1111-111111111111', id from tests.pe_ctx where name = 'shahar';

insert into public.workspace_members (workspace_id, user_id)
select '11111111-1111-1111-1111-111111111111', id from tests.pe_ctx where name in ('shahar', 'topaz');

grant usage on schema tests to authenticated, anon;
grant select on tests.pe_ctx to authenticated, anon;
grant execute on all functions in schema tests to authenticated, anon;

select tests.act_as((select id from tests.pe_ctx where name = 'topaz'));

select results_eq(
    $$ select * from public.workspace_partner_emails('11111111-1111-1111-1111-111111111111') $$,
    $$ values ('shahar@example.test') $$,
    'a member sees the other member''s email'
);

select is_empty(
    $$ select e from public.workspace_partner_emails('11111111-1111-1111-1111-111111111111') e
        where e = 'topaz@example.test' $$,
    'a member never gets their own email back'
);

select tests.act_as((select id from tests.pe_ctx where name = 'mallory'));

select is_empty(
    $$ select * from public.workspace_partner_emails('11111111-1111-1111-1111-111111111111') $$,
    'an outsider gets nothing'
);

reset role;

select ok(
    not has_function_privilege('anon', 'public.workspace_partner_emails(uuid)', 'EXECUTE'),
    'anon cannot call it'
);

select ok(
    has_function_privilege('authenticated', 'public.workspace_partner_emails(uuid)', 'EXECUTE'),
    'authenticated can call it'
);

select * from finish();

rollback;

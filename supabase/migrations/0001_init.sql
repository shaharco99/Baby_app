-- Or Yareach — initial schema.
--
-- The server is a relay for ciphertext it cannot read. Record content lives in a single
-- `ciphertext` column; there are no title, date, category or filename columns anywhere,
-- because the client encrypts the whole payload under a key the server never sees.
--
-- Row Level Security is defence in depth beneath that: an outsider cannot even fetch the
-- ciphertext. Every table below has RLS enabled in this migration, never bolted on later.
--
-- Note on the privacy model: both members of a workspace see everything in it. There is no
-- per-item visibility column by design — see docs/architecture/005-data-privacy.md.

set check_function_bodies = off;

create extension if not exists "pgcrypto" with schema extensions;

-- ---------------------------------------------------------------------------
-- Profiles
-- ---------------------------------------------------------------------------

create table if not exists public.profiles (
    id          uuid primary key references auth.users (id) on delete cascade,
    -- Display name is low-sensitivity and needed to render "created by" without a round
    -- trip through the encrypted payload, so it is deliberately left in plaintext.
    display_name text,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- Workspaces and membership
-- ---------------------------------------------------------------------------

create table if not exists public.workspaces (
    id          uuid primary key default gen_random_uuid(),
    created_by  uuid not null references auth.users (id) on delete restrict,
    -- Hard cap: this product is for exactly two people.
    max_members smallint not null default 2 check (max_members = 2),
    created_at  timestamptz not null default now()
);

create table if not exists public.workspace_members (
    workspace_id uuid not null references public.workspaces (id) on delete cascade,
    user_id      uuid not null references auth.users (id) on delete cascade,
    joined_at    timestamptz not null default now(),
    primary key (workspace_id, user_id)
);

create index if not exists workspace_members_user_idx on public.workspace_members (user_id);

-- Membership lookup used by every policy below. SECURITY DEFINER so the policy on
-- workspace_members itself does not recurse when another table's policy calls it.
create or replace function public.is_workspace_member(ws uuid)
returns boolean
language sql
stable
security definer
set search_path = public, pg_catalog
as $$
    select exists (
        select 1
        from public.workspace_members m
        where m.workspace_id = ws
          and m.user_id = auth.uid()
    );
$$;

revoke execute on function public.is_workspace_member(uuid) from public;
grant execute on function public.is_workspace_member(uuid) to authenticated;

-- Enforce the two-member cap in the database, not just in the join RPC.
create or replace function public.enforce_workspace_member_cap()
returns trigger
language plpgsql
security definer
set search_path = public, pg_catalog
as $$
declare
    current_count integer;
    cap           smallint;
begin
    select max_members into cap from public.workspaces where id = new.workspace_id;
    select count(*) into current_count
      from public.workspace_members
     where workspace_id = new.workspace_id;

    if current_count >= cap then
        raise exception 'workspace % already has % members', new.workspace_id, cap
            using errcode = 'check_violation';
    end if;

    return new;
end;
$$;

drop trigger if exists workspace_member_cap on public.workspace_members;
create trigger workspace_member_cap
    before insert on public.workspace_members
    for each row execute function public.enforce_workspace_member_cap();

-- ---------------------------------------------------------------------------
-- Device keys and wrapped workspace keys
-- ---------------------------------------------------------------------------

create table if not exists public.device_keys (
    id           uuid primary key default gen_random_uuid(),
    user_id      uuid not null references auth.users (id) on delete cascade,
    workspace_id uuid not null references public.workspaces (id) on delete cascade,
    -- X25519 public key. Public by nature: it is what the other device seals to.
    public_key   bytea not null check (octet_length(public_key) = 32),
    label        text,
    created_at   timestamptz not null default now(),
    revoked_at   timestamptz
);

create index if not exists device_keys_workspace_idx on public.device_keys (workspace_id);

create table if not exists public.wrapped_workspace_keys (
    id            uuid primary key default gen_random_uuid(),
    workspace_id  uuid not null references public.workspaces (id) on delete cascade,
    device_key_id uuid not null references public.device_keys (id) on delete cascade,
    -- HPKE blob: [version][encapsulation][ciphertext]. Opaque to the server.
    blob          bytea not null,
    created_by    uuid not null references auth.users (id) on delete restrict,
    created_at    timestamptz not null default now(),
    unique (device_key_id)
);

-- ---------------------------------------------------------------------------
-- Couple invitations
-- ---------------------------------------------------------------------------

create table if not exists public.couple_invitations (
    id           uuid primary key default gen_random_uuid(),
    workspace_id uuid not null references public.workspaces (id) on delete cascade,
    created_by   uuid not null references auth.users (id) on delete cascade,
    -- Only the SHA-256 of the token is stored, so a database dump does not yield usable
    -- invitations. The plaintext token exists only in the inviter's hands.
    token_hash   bytea not null unique check (octet_length(token_hash) = 32),
    expires_at   timestamptz not null,
    accepted_at  timestamptz,
    accepted_by  uuid references auth.users (id) on delete set null,
    revoked_at   timestamptz,
    created_at   timestamptz not null default now()
);

create index if not exists couple_invitations_workspace_idx on public.couple_invitations (workspace_id);

-- ---------------------------------------------------------------------------
-- Encrypted records
-- ---------------------------------------------------------------------------

do $$ begin
    create type public.entity_type as enum (
        'task',
        'shopping_item',
        'important_date',
        'folder',
        'document',
        'cycle',
        'cycle_entry',
        'settings'
    );
exception
    when duplicate_object then null;
end $$;

create table if not exists public.records (
    id                 uuid primary key,
    workspace_id       uuid not null references public.workspaces (id) on delete cascade,
    entity_type        public.entity_type not null,
    -- The entire payload: [version][salt][ChaCha20-Poly1305 ciphertext || tag].
    ciphertext         bytea not null,
    -- Optimistic concurrency. The client sends the version it read; a mismatch is a conflict.
    version            integer not null default 1 check (version > 0),
    client_mutation_id uuid,
    created_by         uuid not null references auth.users (id) on delete restrict,
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),
    -- Tombstone rather than hard delete, so a delete propagates to the other device.
    deleted_at         timestamptz
);

-- The sync engine pulls by (workspace, updated_at) cursor; this index is that query.
create index if not exists records_sync_idx on public.records (workspace_id, updated_at);
create index if not exists records_type_idx on public.records (workspace_id, entity_type);

create table if not exists public.document_blobs (
    record_id           uuid primary key references public.records (id) on delete cascade,
    workspace_id        uuid not null references public.workspaces (id) on delete cascade,
    storage_path        text not null unique,
    -- SHA-256 over the *ciphertext*, so integrity can be checked without decrypting.
    sha256              bytea not null check (octet_length(sha256) = 32),
    size_bytes          bigint not null check (size_bytes >= 0),
    upload_completed_at timestamptz,
    created_at          timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- updated_at maintenance
-- ---------------------------------------------------------------------------

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists profiles_touch on public.profiles;
create trigger profiles_touch
    before update on public.profiles
    for each row execute function public.touch_updated_at();

drop trigger if exists records_touch on public.records;
create trigger records_touch
    before update on public.records
    for each row execute function public.touch_updated_at();

-- ---------------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------------

alter table public.profiles              enable row level security;
alter table public.workspaces            enable row level security;
alter table public.workspace_members     enable row level security;
alter table public.device_keys           enable row level security;
alter table public.wrapped_workspace_keys enable row level security;
alter table public.couple_invitations    enable row level security;
alter table public.records               enable row level security;
alter table public.document_blobs        enable row level security;

-- Profiles: your own, plus anyone you share a workspace with.
drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles
    for select to authenticated
    using (
        id = auth.uid()
        or exists (
            select 1
            from public.workspace_members mine
            join public.workspace_members theirs
              on theirs.workspace_id = mine.workspace_id
            where mine.user_id = auth.uid()
              and theirs.user_id = profiles.id
        )
    );

drop policy if exists profiles_insert_self on public.profiles;
create policy profiles_insert_self on public.profiles
    for insert to authenticated with check (id = auth.uid());

drop policy if exists profiles_update_self on public.profiles;
create policy profiles_update_self on public.profiles
    for update to authenticated using (id = auth.uid()) with check (id = auth.uid());

-- Workspaces: visible to members; creatable by anyone (they become the first member).
drop policy if exists workspaces_select on public.workspaces;
create policy workspaces_select on public.workspaces
    for select to authenticated using (public.is_workspace_member(id));

drop policy if exists workspaces_insert on public.workspaces;
create policy workspaces_insert on public.workspaces
    for insert to authenticated with check (created_by = auth.uid());

-- Membership rows are readable by members. Inserts happen only through
-- accept_invitation() or the workspace-creation RPC, both SECURITY DEFINER.
drop policy if exists workspace_members_select on public.workspace_members;
create policy workspace_members_select on public.workspace_members
    for select to authenticated using (public.is_workspace_member(workspace_id));

-- Device keys: readable by workspace members, since the inviter must seal the workspace
-- key to the joiner's public key. Writable only for your own devices.
drop policy if exists device_keys_select on public.device_keys;
create policy device_keys_select on public.device_keys
    for select to authenticated using (public.is_workspace_member(workspace_id));

drop policy if exists device_keys_insert on public.device_keys;
create policy device_keys_insert on public.device_keys
    for insert to authenticated
    with check (user_id = auth.uid() and public.is_workspace_member(workspace_id));

drop policy if exists device_keys_update_own on public.device_keys;
create policy device_keys_update_own on public.device_keys
    for update to authenticated
    using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Wrapped keys: readable by members (only the holder of the matching private key can
-- open one anyway), writable by members.
drop policy if exists wrapped_keys_select on public.wrapped_workspace_keys;
create policy wrapped_keys_select on public.wrapped_workspace_keys
    for select to authenticated using (public.is_workspace_member(workspace_id));

drop policy if exists wrapped_keys_insert on public.wrapped_workspace_keys;
create policy wrapped_keys_insert on public.wrapped_workspace_keys
    for insert to authenticated
    with check (created_by = auth.uid() and public.is_workspace_member(workspace_id));

-- Invitations: only the inviter sees or revokes them. Acceptance goes through the RPC,
-- so an invitee never needs (and never gets) select access to token hashes.
drop policy if exists invitations_select_own on public.couple_invitations;
create policy invitations_select_own on public.couple_invitations
    for select to authenticated using (created_by = auth.uid());

drop policy if exists invitations_insert_own on public.couple_invitations;
create policy invitations_insert_own on public.couple_invitations
    for insert to authenticated
    with check (created_by = auth.uid() and public.is_workspace_member(workspace_id));

drop policy if exists invitations_update_own on public.couple_invitations;
create policy invitations_update_own on public.couple_invitations
    for update to authenticated
    using (created_by = auth.uid()) with check (created_by = auth.uid());

-- Records: full access for workspace members, none for anyone else.
drop policy if exists records_select on public.records;
create policy records_select on public.records
    for select to authenticated using (public.is_workspace_member(workspace_id));

drop policy if exists records_insert on public.records;
create policy records_insert on public.records
    for insert to authenticated
    with check (created_by = auth.uid() and public.is_workspace_member(workspace_id));

drop policy if exists records_update on public.records;
create policy records_update on public.records
    for update to authenticated
    using (public.is_workspace_member(workspace_id))
    with check (public.is_workspace_member(workspace_id));

-- No delete policy: records are tombstoned via deleted_at so the delete can sync.

drop policy if exists document_blobs_select on public.document_blobs;
create policy document_blobs_select on public.document_blobs
    for select to authenticated using (public.is_workspace_member(workspace_id));

drop policy if exists document_blobs_insert on public.document_blobs;
create policy document_blobs_insert on public.document_blobs
    for insert to authenticated with check (public.is_workspace_member(workspace_id));

drop policy if exists document_blobs_update on public.document_blobs;
create policy document_blobs_update on public.document_blobs
    for update to authenticated
    using (public.is_workspace_member(workspace_id))
    with check (public.is_workspace_member(workspace_id));

-- ---------------------------------------------------------------------------
-- Privileges
--
-- RLS only filters rows within tables the role may already touch, so the grants below are
-- the other half of the boundary. `anon` is granted nothing: an unauthenticated caller is
-- refused outright rather than being handed an empty result set.
--
-- No table grants DELETE. Records are tombstoned with deleted_at so the deletion can sync
-- to the other device; a hard delete would simply vanish and reappear on the next pull.
-- ---------------------------------------------------------------------------

grant usage on schema public to authenticated;

grant select, insert, update on public.profiles               to authenticated;
grant select, insert         on public.workspaces             to authenticated;
grant select                 on public.workspace_members      to authenticated;
grant select, insert, update on public.device_keys            to authenticated;
grant select, insert         on public.wrapped_workspace_keys to authenticated;
grant select, insert, update on public.couple_invitations     to authenticated;
grant select, insert, update on public.records                to authenticated;
grant select, insert, update on public.document_blobs         to authenticated;

-- ---------------------------------------------------------------------------
-- RPCs
-- ---------------------------------------------------------------------------

-- Creating a workspace and joining it must be one atomic step, otherwise a workspace can
-- exist with no members and the RLS policies make it unreachable forever.
create or replace function public.create_workspace()
returns uuid
language plpgsql
security definer
set search_path = public, pg_catalog
as $$
declare
    ws uuid;
begin
    if auth.uid() is null then
        raise exception 'not authenticated' using errcode = 'insufficient_privilege';
    end if;

    insert into public.workspaces (created_by) values (auth.uid()) returning id into ws;
    insert into public.workspace_members (workspace_id, user_id) values (ws, auth.uid());

    return ws;
end;
$$;

revoke execute on function public.create_workspace() from public;
grant execute on function public.create_workspace() to authenticated;

-- Accepts a raw invitation token. Validates expiry, single use and revocation, then adds
-- the caller to the workspace. SECURITY DEFINER because the caller has no read access to
-- the invitations table by design.
create or replace function public.accept_invitation(raw_token text)
returns uuid
language plpgsql
security definer
set search_path = public, pg_catalog, extensions
as $$
declare
    invitation public.couple_invitations;
begin
    if auth.uid() is null then
        raise exception 'not authenticated' using errcode = 'insufficient_privilege';
    end if;

    select * into invitation
      from public.couple_invitations
     where token_hash = extensions.digest(convert_to(raw_token, 'UTF8'), 'sha256')
     for update;

    -- One error for every failure mode: a caller must not learn whether a token existed,
    -- had expired, or was already used.
    if invitation.id is null
       or invitation.revoked_at is not null
       or invitation.accepted_at is not null
       or invitation.expires_at <= now() then
        raise exception 'invalid invitation' using errcode = 'invalid_parameter_value';
    end if;

    if invitation.created_by = auth.uid() then
        raise exception 'invalid invitation' using errcode = 'invalid_parameter_value';
    end if;

    insert into public.workspace_members (workspace_id, user_id)
    values (invitation.workspace_id, auth.uid());

    update public.couple_invitations
       set accepted_at = now(), accepted_by = auth.uid()
     where id = invitation.id;

    return invitation.workspace_id;
end;
$$;

revoke execute on function public.accept_invitation(text) from public;
grant execute on function public.accept_invitation(text) to authenticated;

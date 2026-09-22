-- Who is actually here, right now.
--
-- The app already knows when the partner *wrote* something — every record carries a
-- `created_by` and an `updated_at`. What it never knew is whether they are holding their phone
-- at this moment, which is a different question and the one the "Book of Love" easter egg was
-- guessing at, badly: it read "edited a task in the last five minutes" as "is here".
--
-- So each device keeps a heartbeat while its app is on screen. Nothing about the couple, the
-- child or the workspace's contents is in this table -- a device id, whose it is, and when it
-- last said hello. There is nothing here to encrypt, and nothing that is worth anything to
-- anyone who is not already a member of the workspace.
--
-- No Realtime subscription, deliberately: the app has no websocket anywhere, and adding one for
-- a presence dot would mean a new transport to keep alive, reconnect and pay for in battery.
-- The heartbeat rides the foreground poll that already runs every 30 seconds.
--
-- Idempotent throughout, like every migration here -- these are applied by hand, sometimes onto
-- objects that already exist.

create table if not exists public.device_presence (
    -- The same device identifier `device_keys` and `device_push_tokens` are labelled by. One row
    -- per device, replaced on every heartbeat rather than appended to.
    device_id    uuid primary key,
    workspace_id uuid not null references public.workspaces (id) on delete cascade,
    user_id      uuid not null references auth.users (id) on delete cascade,
    -- The heartbeat itself. "Here" is a reading of this against now(), decided by the client.
    last_seen_at timestamptz not null default now(),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);

-- The only query: everyone else's heartbeat in my workspace. Also the column RLS filters on.
create index if not exists device_presence_workspace_idx
    on public.device_presence (workspace_id, last_seen_at desc);

drop trigger if exists device_presence_touch on public.device_presence;
create trigger device_presence_touch
    before update on public.device_presence
    for each row execute function public.touch_updated_at();

alter table public.device_presence enable row level security;

-- A member may see the heartbeats of their own workspace. That is the whole feature.
drop policy if exists device_presence_select on public.device_presence;
create policy device_presence_select on public.device_presence
    for select to authenticated
    using (public.is_workspace_member(workspace_id));

-- A device may only announce itself, and only into a workspace it belongs to. Without this a
-- member could claim to be another device, or another user could claim to be here.
drop policy if exists device_presence_insert on public.device_presence;
create policy device_presence_insert on public.device_presence
    for insert to authenticated
    with check (user_id = auth.uid() and public.is_workspace_member(workspace_id));

drop policy if exists device_presence_update_own on public.device_presence;
create policy device_presence_update_own on public.device_presence
    for update to authenticated
    using (user_id = auth.uid() and public.is_workspace_member(workspace_id))
    with check (user_id = auth.uid() and public.is_workspace_member(workspace_id));

-- Signing out or leaving takes the heartbeat with it, so a departed device does not read as
-- present until its row happens to go stale.
drop policy if exists device_presence_delete_own on public.device_presence;
create policy device_presence_delete_own on public.device_presence
    for delete to authenticated
    using (user_id = auth.uid());

-- 0008 revoked anon's table privileges, with a default that covers tables added later, so
-- nothing further is needed for `anon` here.

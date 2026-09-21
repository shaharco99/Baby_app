-- Waking the other phone.
--
-- Until now nothing on the server ever reached a device. A feed logged on one phone moved that
-- phone's reminder and sat in `records` until the other phone was opened — its pending alarm
-- kept the old due time, sometimes for hours. There is no Realtime subscription here, and a
-- background worker cannot be relied on: it is asleep in Doze, and on MIUI it may not run at
-- all.
--
-- So each device registers an FCM token, and after a client pushes its changes it asks the
-- `notify-workspace` edge function to ping the workspace's *other* devices. The ping carries
-- nothing but the workspace id — the woken device syncs and re-derives its own alarm from its
-- own decrypted database. The server still never learns what changed, which is the whole point
-- of the design and is why this is a wake-up and not a notification.

create table public.device_push_tokens (
    -- The device's own identifier, the same one `device_keys` are labelled by on the client.
    -- Primary key rather than a surrogate: a device has exactly one current token, and
    -- re-registering is an upsert rather than a row that accumulates.
    device_id    uuid primary key,
    workspace_id uuid not null references public.workspaces (id) on delete cascade,
    user_id      uuid not null references auth.users (id) on delete cascade,
    -- The FCM registration token. Opaque, rotated by the platform, and useless without the
    -- project's server credentials.
    token        text not null,
    platform     text not null default 'android' check (platform in ('android')),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);

-- The edge function's only query: every token in this workspace. Also the column RLS filters on.
create index device_push_tokens_workspace_idx on public.device_push_tokens (workspace_id);

create trigger device_push_tokens_touch
    before update on public.device_push_tokens
    for each row execute function public.touch_updated_at();

alter table public.device_push_tokens enable row level security;

-- A member may see which devices in their own workspace are registered. The token itself is of
-- no use to them, and seeing the row is what lets a device tell whether it is registered.
create policy device_push_tokens_select on public.device_push_tokens
    for select to authenticated
    using (public.is_workspace_member(workspace_id));

-- A device may only register itself, and only into a workspace it belongs to.
create policy device_push_tokens_insert on public.device_push_tokens
    for insert to authenticated
    with check (user_id = auth.uid() and public.is_workspace_member(workspace_id));

create policy device_push_tokens_update_own on public.device_push_tokens
    for update to authenticated
    using (user_id = auth.uid() and public.is_workspace_member(workspace_id))
    with check (user_id = auth.uid() and public.is_workspace_member(workspace_id));

-- Signing out, or losing a device, should take its token with it.
create policy device_push_tokens_delete_own on public.device_push_tokens
    for delete to authenticated
    using (user_id = auth.uid());

-- 0008 revoked anon's table privileges and set a default that covers tables added later, so
-- nothing further is needed for `anon` here. The edge function reads this table with the
-- service role, which RLS does not apply to.

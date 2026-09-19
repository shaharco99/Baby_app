-- Who else is in this workspace, by email.
--
-- The pairing-pending screen ("waiting for your partner to share the key") only said "you
-- joined the space", with nothing to confirm it was the right space. A joiner can now see which
-- account(s) they are waiting on.
--
-- SECURITY DEFINER because the emails live in auth.users, which `authenticated` cannot read.
-- Scoped the same way as every policy here: a caller who is not a member gets nothing, and the
-- caller's own address is left out. Emails only — nothing else about the account.

create or replace function public.workspace_partner_emails(ws uuid)
returns setof text
language sql
stable
security definer
set search_path = public, pg_catalog
as $$
    select u.email::text
      from public.workspace_members m
      join auth.users u on u.id = m.user_id
     where m.workspace_id = ws
       and m.user_id <> auth.uid()
       and public.is_workspace_member(ws)
     order by m.joined_at;
$$;

revoke execute on function public.workspace_partner_emails(uuid) from public, anon;
grant execute on function public.workspace_partner_emails(uuid) to authenticated;

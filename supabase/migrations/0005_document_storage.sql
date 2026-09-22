-- Document storage — the actual encrypted file bytes for `document_blobs`.
--
-- Objects are named `{workspace_id}/{record_id}`, so the workspace membership check needed
-- for RLS falls straight out of the path's first segment — no separate lookup table.
-- The bucket is private: nothing here is fetchable without a signed URL or an authenticated
-- request that passes RLS, and every byte in the object is ciphertext.

insert into storage.buckets (id, name, public)
values ('documents', 'documents', false)
on conflict (id) do nothing;

drop policy if exists documents_select on storage.objects;
create policy documents_select on storage.objects
    for select to authenticated
    using (
        bucket_id = 'documents'
        and public.is_workspace_member(((storage.foldername(name))[1])::uuid)
    );

drop policy if exists documents_insert on storage.objects;
create policy documents_insert on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'documents'
        and public.is_workspace_member(((storage.foldername(name))[1])::uuid)
    );

-- No update/delete policy: an uploaded document is immutable. Deletion propagates through
-- the record's tombstone, not by mutating the stored blob.

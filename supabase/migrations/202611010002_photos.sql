insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('animal-photos','animal-photos',false,1048576,array['image/jpeg'])
on conflict(id) do update set public=false,file_size_limit=1048576,allowed_mime_types=array['image/jpeg'];
create policy abrace_photos_read on storage.objects for select to authenticated
using(bucket_id='animal-photos' and private.can_read());
-- UUID/UUID.jpg, no overwrite: photos are immutable, and at most three references
-- per animal are checked by save_record. Cleanup is an administrator operation.
create function private.can_upload_photo(p_name text) returns boolean language sql stable security definer set search_path=public,pg_temp as $$
 select coalesce(private.current_role() in ('editor','admin'),false)
 and p_name ~ '^[a-f0-9-]{36}/[a-f0-9-]{36}\.jpg$'
$$;
revoke all on function private.can_upload_photo(text) from public,anon;
grant execute on function private.can_upload_photo(text) to authenticated;
create policy abrace_photos_insert on storage.objects for insert to authenticated
with check(bucket_id='animal-photos' and private.can_upload_photo(name));
create function private.is_admin() returns boolean language sql stable security definer set search_path=public,pg_temp as $$
 select coalesce(private.current_role()='admin',false)
$$;
revoke all on function private.is_admin() from public,anon;
grant execute on function private.is_admin() to authenticated;
create policy abrace_photos_delete on storage.objects for delete to authenticated
using(bucket_id='animal-photos' and private.is_admin());

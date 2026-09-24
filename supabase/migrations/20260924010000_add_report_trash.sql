-- Add a recoverable admin trash for flood reports.
-- Trashing a report keeps both the database row and its Storage image intact.

begin;

alter table public.flood_reports
  add column if not exists deleted_at timestamp with time zone,
  add column if not exists deleted_by uuid references auth.users(id) on delete set null;

create index if not exists idx_flood_reports_deleted_at
  on public.flood_reports (deleted_at desc)
  where deleted_at is not null;

-- Only the administrator can see trashed reports. Reporters and the public
-- continue to see active reports according to the existing visibility rules.
drop policy if exists "Authenticated can view all reports" on public.flood_reports;
drop policy if exists "Users view public and own reports" on public.flood_reports;

create policy "Users view public and own reports"
on public.flood_reports for select
to authenticated
using (
  lower(auth.email()) = 'floodwatchstaana@gmail.com'
  or (
    deleted_at is null
    and (
      upper(coalesce(status, '')) = 'VERIFIED'
      or auth.uid() = user_id
    )
  )
);

-- Remove client-side hard delete access. Service-role maintenance can still
-- perform an intentional permanent purge if one is ever required.
drop policy if exists "Allow delete for admin only" on public.flood_reports;

create or replace function public.soft_delete_report(p_report_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  if lower(coalesce(auth.email(), '')) <> 'floodwatchstaana@gmail.com' then
    raise exception 'Only the FloodWatch administrator can trash reports'
      using errcode = '42501';
  end if;

  update public.flood_reports
  set deleted_at = now(),
      deleted_by = auth.uid()
  where id = p_report_id
    and deleted_at is null;

  return found;
end;
$$;

create or replace function public.restore_trashed_report(p_report_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  if lower(coalesce(auth.email(), '')) <> 'floodwatchstaana@gmail.com' then
    raise exception 'Only the FloodWatch administrator can restore reports'
      using errcode = '42501';
  end if;

  update public.flood_reports
  set deleted_at = null,
      deleted_by = null
  where id = p_report_id
    and deleted_at is not null;

  return found;
end;
$$;

revoke all on function public.soft_delete_report(uuid) from public, anon;
revoke all on function public.restore_trashed_report(uuid) from public, anon;
grant execute on function public.soft_delete_report(uuid) to authenticated;
grant execute on function public.restore_trashed_report(uuid) to authenticated;

-- Prevent reporters from deleting an image after it has been attached to a
-- report, including reports that the administrator later moves to Trash.
create or replace function public.is_own_report_image_referenced(p_object_name text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.flood_reports r
    where r.user_id = auth.uid()
      and r.image_url is not null
      and right(
        split_part(r.image_url, '?', 1),
        length('/flood-reports/' || p_object_name)
      ) = '/flood-reports/' || p_object_name
  );
$$;

revoke all on function public.is_own_report_image_referenced(text) from public, anon;
grant execute on function public.is_own_report_image_referenced(text) to authenticated;

drop policy if exists "Users delete own flood report images" on storage.objects;
create policy "Users delete unreferenced own flood report images"
on storage.objects for delete to authenticated
using (
  bucket_id = 'flood-reports'
  and (storage.foldername(name))[1] = auth.uid()::text
  and not public.is_own_report_image_referenced(name)
);

commit;

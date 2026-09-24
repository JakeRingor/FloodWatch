-- Add an administrator-only archive that is separate from report history and
-- Trash. Archived reports and their Storage images remain recoverable.

begin;

alter table public.flood_reports
  add column if not exists archived_at timestamp with time zone,
  add column if not exists archived_by uuid references auth.users(id) on delete set null;

create index if not exists idx_flood_reports_archived_at
  on public.flood_reports (archived_at desc)
  where archived_at is not null;

drop policy if exists "Users view public and own reports" on public.flood_reports;
create policy "Users view public and own reports"
on public.flood_reports for select
to authenticated
using (
  lower(auth.email()) = 'floodwatchstaana@gmail.com'
  or (
    deleted_at is null
    and archived_at is null
    and (
      upper(coalesce(status, '')) = 'VERIFIED'
      or auth.uid() = user_id
    )
  )
);

create or replace function public.archive_report(p_report_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  if lower(coalesce(auth.email(), '')) <> 'floodwatchstaana@gmail.com' then
    raise exception 'Only the FloodWatch administrator can archive reports'
      using errcode = '42501';
  end if;

  update public.flood_reports
  set archived_at = now(),
      archived_by = auth.uid()
  where id = p_report_id
    and deleted_at is null
    and archived_at is null;

  return found;
end;
$$;

create or replace function public.unarchive_report(p_report_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  if lower(coalesce(auth.email(), '')) <> 'floodwatchstaana@gmail.com' then
    raise exception 'Only the FloodWatch administrator can unarchive reports'
      using errcode = '42501';
  end if;

  update public.flood_reports
  set archived_at = null,
      archived_by = null
  where id = p_report_id
    and deleted_at is null
    and archived_at is not null;

  return found;
end;
$$;

-- Moving an archived report to Trash removes it from Archive. Restoring it
-- later returns it to its original status in the normal report lists.
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
      deleted_by = auth.uid(),
      archived_at = null,
      archived_by = null
  where id = p_report_id
    and deleted_at is null;

  return found;
end;
$$;

revoke all on function public.archive_report(uuid) from public, anon;
revoke all on function public.unarchive_report(uuid) from public, anon;
grant execute on function public.archive_report(uuid) to authenticated;
grant execute on function public.unarchive_report(uuid) to authenticated;

commit;

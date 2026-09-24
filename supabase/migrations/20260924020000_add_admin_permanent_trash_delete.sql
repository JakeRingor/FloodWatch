-- Allow the administrator to permanently remove a report record, but only
-- after it has first been moved to Trash. Storage objects are intentionally
-- not manipulated from SQL.

begin;

create or replace function public.permanently_delete_trashed_report(p_report_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  if lower(coalesce(auth.email(), '')) <> 'floodwatchstaana@gmail.com' then
    raise exception 'Only the FloodWatch administrator can permanently delete reports'
      using errcode = '42501';
  end if;

  delete from public.flood_reports
  where id = p_report_id
    and deleted_at is not null;

  return found;
end;
$$;

revoke all on function public.permanently_delete_trashed_report(uuid) from public, anon;
grant execute on function public.permanently_delete_trashed_report(uuid) to authenticated;

commit;

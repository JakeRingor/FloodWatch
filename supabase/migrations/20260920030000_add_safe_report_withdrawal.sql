-- Allow a reporter to withdraw only their own still-pending report.
-- The row and image are deliberately retained as an audit trail.

begin;

alter table public.flood_reports
  add column if not exists withdrawn_at timestamp with time zone;

alter table public.flood_reports
  drop constraint if exists chk_report_status;

alter table public.flood_reports
  add constraint chk_report_status check (
    upper(status) in (
      'PENDING',
      'VERIFIED',
      'DISMISSED',
      'INVALID_IMAGE',
      'INVALID_INFORMATION',
      'WITHDRAWN'
    )
  );

create or replace function public.withdraw_own_pending_report(p_report_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  was_withdrawn boolean;
begin
  update public.flood_reports
  set status = 'WITHDRAWN',
      withdrawn_at = now()
  where id = p_report_id
    and user_id = auth.uid()
    and upper(status) = 'PENDING';

  was_withdrawn := found;
  return was_withdrawn;
end;
$$;

revoke all on function public.withdraw_own_pending_report(uuid) from public;
revoke all on function public.withdraw_own_pending_report(uuid) from anon;
grant execute on function public.withdraw_own_pending_report(uuid) to authenticated;

commit;

-- Switch moderation access to the new Sta. Ana administrator account,
-- restrict private report visibility, and ensure Realtime is available.
-- This migration does not delete or rewrite existing report data.

begin;

alter table public.flood_reports enable row level security;
alter table public.flood_alerts enable row level security;

-- Users can read public verified reports and every status of their own reports.
-- The configured administrator can read all reports for moderation.
drop policy if exists "Authenticated can view all reports" on public.flood_reports;
drop policy if exists "Users view public and own reports" on public.flood_reports;

create policy "Users view public and own reports"
on public.flood_reports for select
to authenticated
using (
  upper(coalesce(status, '')) = 'VERIFIED'
  or auth.uid() = user_id
  or lower(auth.email()) = 'floodwatchstaana@gmail.com'
);

-- Keep report creation owner-only and moderation admin-only.
drop policy if exists "Enable insert for authenticated users only" on public.flood_reports;
drop policy if exists "Authenticated users can insert reports" on public.flood_reports;
drop policy if exists "Authenticated users can insert own reports" on public.flood_reports;
drop policy if exists "Users can update own reports" on public.flood_reports;
drop policy if exists "Admin can update flood reports" on public.flood_reports;
drop policy if exists "Allow delete for admin only" on public.flood_reports;

create policy "Authenticated users can insert own reports"
on public.flood_reports for insert
to authenticated
with check (auth.uid() = user_id);

create policy "Admin can update flood reports"
on public.flood_reports for update
to authenticated
using (lower(auth.email()) = 'floodwatchstaana@gmail.com')
with check (lower(auth.email()) = 'floodwatchstaana@gmail.com');

create policy "Allow delete for admin only"
on public.flood_reports for delete
to authenticated
using (lower(auth.email()) = 'floodwatchstaana@gmail.com');

-- Flood alerts remain readable by signed-in mobile users and writable by admin only.
drop policy if exists "Authenticated users can view alerts" on public.flood_alerts;
drop policy if exists "Admin can insert alerts" on public.flood_alerts;
drop policy if exists "Admin can update alerts" on public.flood_alerts;
drop policy if exists "Admin can delete alerts" on public.flood_alerts;

create policy "Authenticated users can view alerts"
on public.flood_alerts for select
to authenticated
using (true);

create policy "Admin can insert alerts"
on public.flood_alerts for insert
to authenticated
with check (lower(auth.email()) = 'floodwatchstaana@gmail.com');

create policy "Admin can update alerts"
on public.flood_alerts for update
to authenticated
using (lower(auth.email()) = 'floodwatchstaana@gmail.com')
with check (lower(auth.email()) = 'floodwatchstaana@gmail.com');

create policy "Admin can delete alerts"
on public.flood_alerts for delete
to authenticated
using (lower(auth.email()) = 'floodwatchstaana@gmail.com');

-- Add the two tables to Supabase Realtime only when not already published.
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'flood_reports'
  ) then
    alter publication supabase_realtime add table public.flood_reports;
  end if;

  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'flood_alerts'
  ) then
    alter publication supabase_realtime add table public.flood_alerts;
  end if;
end
$$;

commit;

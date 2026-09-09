-- Security and consistency fixes for the FloodWatch schema.
-- This migration preserves existing tables and rows.

begin;

-- Policies have no effect unless RLS is explicitly enabled.
alter table public.user_profiles enable row level security;
alter table public.flood_reports enable row level security;
alter table public.flood_alerts enable row level security;

-- Remove duplicate profile policies and recreate one policy per operation.
drop policy if exists "Users can insert their own profile" on public.user_profiles;
drop policy if exists "Users can update own profile" on public.user_profiles;
drop policy if exists "Users can update their own profile" on public.user_profiles;
drop policy if exists "Users can view own profile" on public.user_profiles;
drop policy if exists "Users can view their own profile" on public.user_profiles;

create policy "Users can view own profile"
on public.user_profiles for select
to authenticated
using (auth.uid() = id);

create policy "Users can insert own profile"
on public.user_profiles for insert
to authenticated
with check (auth.uid() = id);

create policy "Users can update own profile"
on public.user_profiles for update
to authenticated
using (auth.uid() = id)
with check (auth.uid() = id);

-- A report must always belong to the authenticated user creating it.
drop policy if exists "Enable insert for authenticated users only" on public.flood_reports;
drop policy if exists "Authenticated users can insert reports" on public.flood_reports;
drop policy if exists "Users can update own reports" on public.flood_reports;

create policy "Authenticated users can insert own reports"
on public.flood_reports for insert
to authenticated
with check (auth.uid() = user_id);

-- Reading reports is needed for map markers; moderation remains admin-only.
-- Existing admin update/delete policies are intentionally retained.

-- Ordinary authenticated users must not be able to create or modify alerts.
drop policy if exists "Authenticated can manage alerts" on public.flood_alerts;

create policy "Authenticated users can view alerts"
on public.flood_alerts for select
to authenticated
using (true);

create policy "Admin can insert alerts"
on public.flood_alerts for insert
to authenticated
with check (auth.email() = 'floodwatchkingsville@gmail.com');

create policy "Admin can update alerts"
on public.flood_alerts for update
to authenticated
using (auth.email() = 'floodwatchkingsville@gmail.com')
with check (auth.email() = 'floodwatchkingsville@gmail.com');

create policy "Admin can delete alerts"
on public.flood_alerts for delete
to authenticated
using (auth.email() = 'floodwatchkingsville@gmail.com');

-- Use one canonical flood-level vocabulary across the app and database.
update public.flood_reports
set flood_level = 'MODERATE'
where lower(flood_level) in ('mid', 'medium', 'moderate');

alter table public.flood_reports drop constraint if exists chk_flood_level;
alter table public.flood_reports
add constraint chk_flood_level
check (upper(flood_level) in ('LOW', 'MODERATE', 'HIGH', 'CRITICAL'));

alter table public.flood_reports
alter column flood_level set default 'LOW';

-- Index common dashboard/map queries.
create index if not exists idx_flood_reports_status_created_at
on public.flood_reports (status, created_at desc);

create index if not exists idx_flood_alerts_active_created_at
on public.flood_alerts (is_active, created_at desc);

commit;

-- Manual rollback for 20260811010000_secure_and_align_floodwatch_schema.sql
-- Run only if the security/consistency migration must be reverted.

begin;

drop policy if exists "Users can view own profile" on public.user_profiles;
drop policy if exists "Users can insert own profile" on public.user_profiles;
drop policy if exists "Users can update own profile" on public.user_profiles;

create policy "Users can insert their own profile"
on public.user_profiles for insert
with check (auth.uid() = id);

create policy "Users can update own profile"
on public.user_profiles for update
using (auth.uid() = id);

create policy "Users can update their own profile"
on public.user_profiles for update
using (auth.uid() = id);

create policy "Users can view own profile"
on public.user_profiles for select
using (auth.uid() = id);

create policy "Users can view their own profile"
on public.user_profiles for select
using (auth.uid() = id);

drop policy if exists "Authenticated users can insert own reports" on public.flood_reports;

create policy "Authenticated users can insert reports"
on public.flood_reports for insert
to authenticated
with check (auth.uid() = user_id);

create policy "Enable insert for authenticated users only"
on public.flood_reports for insert
to authenticated
with check (true);

create policy "Users can update own reports"
on public.flood_reports for update
using (auth.uid() = user_id);

drop policy if exists "Authenticated users can view alerts" on public.flood_alerts;
drop policy if exists "Admin can insert alerts" on public.flood_alerts;
drop policy if exists "Admin can update alerts" on public.flood_alerts;
drop policy if exists "Admin can delete alerts" on public.flood_alerts;

create policy "Authenticated can manage alerts"
on public.flood_alerts
to authenticated
using (true)
with check (true);

update public.flood_reports
set flood_level = 'MEDIUM'
where upper(flood_level) = 'MODERATE';

alter table public.flood_reports drop constraint if exists chk_flood_level;
alter table public.flood_reports
add constraint chk_flood_level
check (lower(flood_level) = any (array['low', 'medium', 'high', 'critical']));

drop index if exists public.idx_flood_reports_status_created_at;
drop index if exists public.idx_flood_alerts_active_created_at;

commit;

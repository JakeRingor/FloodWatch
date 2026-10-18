-- Permanent Sta. Ana flood-susceptibility zones and cached server-side weather.

begin;

create table if not exists public.permanent_flood_zones (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  name text not null,
  nearest_street text not null,
  latitude double precision not null check (latitude between -90 and 90),
  longitude double precision not null check (longitude between -180 and 180),
  radius_meters integer not null check (radius_meters between 50 and 2000),
  noah_hazard_level text not null check (noah_hazard_level in ('LOW', 'MEDIUM', 'HIGH')),
  is_active boolean not null default true,
  rainfall_mm_per_hour double precision,
  precipitation_probability double precision,
  weather_state text not null default 'UNAVAILABLE'
    check (weather_state in ('NORMAL', 'ELEVATED', 'HEAVY', 'UNAVAILABLE')),
  weather_source text,
  weather_observed_at timestamptz,
  weather_updated_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.permanent_flood_zones enable row level security;

drop policy if exists "Authenticated users can read permanent flood zones"
  on public.permanent_flood_zones;
create policy "Authenticated users can read permanent flood zones"
  on public.permanent_flood_zones
  for select
  to authenticated
  using (is_active = true);

grant select on public.permanent_flood_zones to authenticated;
grant all on public.permanent_flood_zones to service_role;

insert into public.permanent_flood_zones
  (slug, name, nearest_street, latitude, longitude, radius_meters, noah_hazard_level)
values
  ('ba-cruz-salamat', 'B.A. Cruz-Salamat Flood Zone', 'B.A. Cruz Street / Salamat Street', 14.56595, 121.13108, 180, 'MEDIUM'),
  ('dupax-3-4', 'Dupax 3 and 4 Flood Zone', 'Dupax 3 and 4 Health Center vicinity', 14.56252, 121.12649, 160, 'HIGH'),
  ('p-ocampo-south', 'P. Ocampo-South Santa Ana Flood Zone', 'P. Ocampo Street, South Santa Ana', 14.56364, 121.12342, 170, 'HIGH'),
  ('santa-ana-central', 'Santa Ana Central Flood Zone', 'Santa Ana Elementary School / Narra Street', 14.56539, 121.12674, 150, 'MEDIUM'),
  ('ilang-ilang-pag-asa', 'Ilang-Ilang-B. Pag-asa Flood Zone', 'Ilang-Ilang Health Center / B. Pag-asa Street', 14.56882, 121.12608, 180, 'MEDIUM')
on conflict (slug) do update set
  name = excluded.name,
  nearest_street = excluded.nearest_street,
  latitude = excluded.latitude,
  longitude = excluded.longitude,
  radius_meters = excluded.radius_meters,
  noah_hazard_level = excluded.noah_hazard_level,
  is_active = true,
  updated_at = now();

create table if not exists public.weather_refresh_control (
  job_name text primary key,
  last_started_at timestamptz,
  last_completed_at timestamptz,
  last_error text
);

alter table public.weather_refresh_control enable row level security;
revoke all on public.weather_refresh_control from anon, authenticated;
grant all on public.weather_refresh_control to service_role;

insert into public.weather_refresh_control (job_name)
values ('permanent-flood-zones')
on conflict (job_name) do nothing;

create or replace function public.claim_weather_zone_refresh()
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  claimed boolean;
begin
  update public.weather_refresh_control
  set last_started_at = now(), last_error = null
  where job_name = 'permanent-flood-zones'
    and (last_started_at is null or last_started_at < now() - interval '25 minutes')
  returning true into claimed;
  return coalesce(claimed, false);
end;
$$;

revoke all on function public.claim_weather_zone_refresh() from public, anon, authenticated;
grant execute on function public.claim_weather_zone_refresh() to service_role;

do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'permanent_flood_zones'
  ) then
    alter publication supabase_realtime add table public.permanent_flood_zones;
  end if;
end;
$$;

commit;

create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;

do $$
declare
  existing_job bigint;
begin
  select jobid into existing_job
  from cron.job
  where jobname = 'refresh-permanent-flood-zone-weather';
  if existing_job is not null then
    perform cron.unschedule(existing_job);
  end if;
  perform cron.schedule(
    'refresh-permanent-flood-zone-weather',
    '*/30 * * * *',
    $job$
      select net.http_post(
        url := 'https://jhjkfkgixkqbofehwwtn.supabase.co/functions/v1/update-weather-zones',
        headers := '{"Content-Type":"application/json"}'::jsonb,
        body := '{}'::jsonb,
        timeout_milliseconds := 20000
      );
    $job$
  );
end;
$$;

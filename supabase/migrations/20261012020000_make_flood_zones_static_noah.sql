-- Permanent zone colors now come from the validated NOAH baseline rather than
-- per-zone weather polling. Verified deep reports may still override to dark red.

begin;

drop function if exists public.claim_weather_zone_refresh();
drop table if exists public.weather_refresh_control;

alter table public.permanent_flood_zones
  drop column if exists rainfall_mm_per_hour,
  drop column if exists precipitation_probability,
  drop column if exists weather_state,
  drop column if exists weather_source,
  drop column if exists weather_observed_at,
  drop column if exists weather_updated_at;

commit;

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
end;
$$;

-- Store structured depth/elevation values used by the map flood reference.

begin;

alter table public.flood_reports
  add column if not exists flood_depth_range text,
  add column if not exists elevation_meters double precision,
  add column if not exists elevation_accuracy_meters double precision,
  add column if not exists elevation_source text;

alter table public.flood_reports
  drop constraint if exists chk_report_elevation_meters,
  add constraint chk_report_elevation_meters check (
    elevation_meters is null or elevation_meters between -500 and 9000
  ),
  drop constraint if exists chk_report_elevation_accuracy,
  add constraint chk_report_elevation_accuracy check (
    elevation_accuracy_meters is null
    or elevation_accuracy_meters between 0 and 10000
  ),
  drop constraint if exists chk_report_elevation_source,
  add constraint chk_report_elevation_source check (
    elevation_source is null
    or elevation_source in ('TERRAIN_DEM', 'MSL', 'GPS', 'UNAVAILABLE')
  );

comment on column public.flood_reports.flood_depth_range is
  'Reporter-confirmed depth parameter, such as 16-30 cm.';
comment on column public.flood_reports.elevation_meters is
  'Terrain/MSL/GPS elevation captured when the report was submitted.';

commit;

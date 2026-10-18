-- One-time Open-Meteo terrain DEM lookup for the five permanent zones.

begin;

alter table public.permanent_flood_zones
  add column if not exists elevation_meters double precision,
  add column if not exists elevation_source text;

alter table public.permanent_flood_zones
  drop constraint if exists chk_permanent_zone_elevation,
  add constraint chk_permanent_zone_elevation check (
    elevation_meters is null or elevation_meters between -500 and 9000
  );

update public.permanent_flood_zones
set elevation_meters = case slug
    when 'ba-cruz-salamat' then 10.0
    when 'dupax-3-4' then 7.0
    when 'p-ocampo-south' then 4.0
    when 'santa-ana-central' then 9.0
    when 'ilang-ilang-pag-asa' then 8.0
  end,
  elevation_source = 'OPEN_METEO_TERRAIN_DEM',
  updated_at = now()
where slug in (
  'ba-cruz-salamat',
  'dupax-3-4',
  'p-ocampo-south',
  'santa-ana-central',
  'ilang-ilang-pag-asa'
);

comment on column public.permanent_flood_zones.elevation_meters is
  'Approximate terrain elevation in meters; not a survey-grade measurement.';
comment on column public.permanent_flood_zones.elevation_source is
  'Dataset/service used for the one-time terrain elevation lookup.';

commit;

-- Add private, user-owned Home Zone coordinates for proximity notifications.

begin;

alter table public.user_profiles
  add column if not exists home_zone_label text,
  add column if not exists home_latitude double precision,
  add column if not exists home_longitude double precision,
  add column if not exists home_radius_meters integer default 2000,
  add column if not exists home_zone_enabled boolean not null default false;

alter table public.user_profiles
  drop constraint if exists chk_user_profile_home_coordinates;
alter table public.user_profiles
  add constraint chk_user_profile_home_coordinates check (
    (home_latitude is null and home_longitude is null)
    or
    (
      home_latitude is not null
      and home_longitude is not null
      and home_latitude between -90 and 90
      and home_longitude between -180 and 180
    )
  );

alter table public.user_profiles
  drop constraint if exists chk_user_profile_home_radius;
alter table public.user_profiles
  add constraint chk_user_profile_home_radius check (
    home_radius_meters between 250 and 10000
  );

alter table public.user_profiles
  drop constraint if exists chk_user_profile_enabled_home_zone;
alter table public.user_profiles
  add constraint chk_user_profile_enabled_home_zone check (
    not home_zone_enabled
    or (home_latitude is not null and home_longitude is not null)
  );

-- Existing profile RLS policies already restrict SELECT and UPDATE to id = auth.uid().
-- Keep a partial index for service-role notification lookups.
create index if not exists idx_user_profiles_enabled_home_zone
  on public.user_profiles (id)
  where home_zone_enabled = true
    and home_latitude is not null
    and home_longitude is not null;

commit;

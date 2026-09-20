-- Prevent clients from self-verifying reports and provision the storage used
-- by the Android application. Existing public image URLs remain compatible.

begin;

update public.flood_reports
set status = 'PENDING'
where status is null;

update public.flood_reports
set severity = 1
where severity is null or severity < 1 or severity > 4;

alter table public.flood_reports
  alter column status set default 'PENDING',
  alter column status set not null,
  alter column severity set default 1,
  alter column severity set not null;

alter table public.flood_reports
  drop constraint if exists chk_report_severity;
alter table public.flood_reports
  add constraint chk_report_severity check (severity between 1 and 4);

-- NOT VALID avoids blocking deployment if legacy rows contain bad coordinates;
-- PostgreSQL still enforces these constraints for all new/updated rows.
alter table public.flood_reports
  drop constraint if exists chk_report_latitude;
alter table public.flood_reports
  add constraint chk_report_latitude
  check (latitude between -90 and 90) not valid;

alter table public.flood_reports
  drop constraint if exists chk_report_longitude;
alter table public.flood_reports
  add constraint chk_report_longitude
  check (longitude between -180 and 180) not valid;

drop policy if exists "Authenticated users can insert own reports"
  on public.flood_reports;

create policy "Authenticated users can insert own pending reports"
on public.flood_reports for insert
to authenticated
with check (
  auth.uid() = user_id
  and upper(status) = 'PENDING'
  and upper(flood_level) in ('LOW', 'MODERATE', 'HIGH', 'CRITICAL')
  and severity between 1 and 4
  and latitude between -90 and 90
  and longitude between -180 and 180
);

-- Auth signup metadata is the single source for the initial profile. This
-- avoids a race/duplicate insert from the client when email confirmation is on.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.user_profiles (id, full_name, phone_number)
  values (
    new.id,
    new.raw_user_meta_data->>'full_name',
    new.raw_user_meta_data->>'phone_number'
  )
  on conflict (id) do update
  set full_name = excluded.full_name,
      phone_number = excluded.phone_number;
  return new;
end;
$$;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('flood-reports', 'flood-reports', true, 10485760, array['image/jpeg']),
  ('profile-images', 'profile-images', true, 5242880, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "Users upload own flood report images" on storage.objects;
drop policy if exists "Users update own flood report images" on storage.objects;
drop policy if exists "Users delete own flood report images" on storage.objects;

create policy "Users upload own flood report images"
on storage.objects for insert to authenticated
with check (
  bucket_id = 'flood-reports'
  and (storage.foldername(name))[1] = auth.uid()::text
);

create policy "Users update own flood report images"
on storage.objects for update to authenticated
using (
  bucket_id = 'flood-reports'
  and (storage.foldername(name))[1] = auth.uid()::text
)
with check (
  bucket_id = 'flood-reports'
  and (storage.foldername(name))[1] = auth.uid()::text
);

create policy "Users delete own flood report images"
on storage.objects for delete to authenticated
using (
  bucket_id = 'flood-reports'
  and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists "Users upload own profile images" on storage.objects;
drop policy if exists "Users update own profile images" on storage.objects;
drop policy if exists "Users delete own profile images" on storage.objects;

create policy "Users upload own profile images"
on storage.objects for insert to authenticated
with check (
  bucket_id = 'profile-images'
  and (storage.foldername(name))[1] = auth.uid()::text
);

create policy "Users update own profile images"
on storage.objects for update to authenticated
using (
  bucket_id = 'profile-images'
  and (storage.foldername(name))[1] = auth.uid()::text
)
with check (
  bucket_id = 'profile-images'
  and (storage.foldername(name))[1] = auth.uid()::text
);

create policy "Users delete own profile images"
on storage.objects for delete to authenticated
using (
  bucket_id = 'profile-images'
  and (storage.foldername(name))[1] = auth.uid()::text
);

commit;

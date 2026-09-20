-- Remove dashboard-created demo policies that bypass owner-folder checks.
-- No objects, buckets, or image URLs are changed by this migration.
begin;

drop policy if exists "Allow All for Demo diku0g_0" on storage.objects;
drop policy if exists "Allow All for Demo diku0g_1" on storage.objects;
drop policy if exists "Allow All for Demo diku0g_2" on storage.objects;
drop policy if exists "Allow All for Demo diku0g_3" on storage.objects;
drop policy if exists "Authenticated users can upload images" on storage.objects;

-- These duplicate dashboard policies are superseded by the versioned
-- owner-folder INSERT/UPDATE policies from the preceding migration.
drop policy if exists "Users can upload own profile image" on storage.objects;
drop policy if exists "Users can update own profile image" on storage.objects;

-- Upsert needs SELECT as well as INSERT and UPDATE. Declare this explicitly
-- so profile replacement works on both existing and fresh deployments.
drop policy if exists "Users can view own profile image" on storage.objects;
create policy "Users can view own profile image"
on storage.objects for select to authenticated
using (
  bucket_id = 'profile-images'
  and (storage.foldername(name))[1] = auth.uid()::text
);

-- Keep report image reads compatible with the existing app/admin dashboard.
-- Public bucket downloads remain public; this does not grant write access.
drop policy if exists "Public can view flood report images" on storage.objects;
create policy "Public can view flood report images"
on storage.objects for select to public
using (bucket_id = 'flood-reports');

commit;

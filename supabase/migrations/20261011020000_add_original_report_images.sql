-- Keep a clean dashboard preview while retaining the annotated evidence image.

begin;

alter table public.flood_reports
  add column if not exists original_image_url text;

-- A reporter must not be able to delete either image after it is attached to
-- a report. This replaces the earlier single-image reference check.
create or replace function public.is_own_report_image_referenced(p_object_name text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.flood_reports r
    where r.user_id = auth.uid()
      and (
        (
          r.image_url is not null
          and right(
            split_part(r.image_url, '?', 1),
            length('/flood-reports/' || p_object_name)
          ) = '/flood-reports/' || p_object_name
        )
        or
        (
          r.original_image_url is not null
          and right(
            split_part(r.original_image_url, '?', 1),
            length('/flood-reports/' || p_object_name)
          ) = '/flood-reports/' || p_object_name
        )
      )
  );
$$;

revoke all on function public.is_own_report_image_referenced(text) from public, anon;
grant execute on function public.is_own_report_image_referenced(text) to authenticated;

commit;

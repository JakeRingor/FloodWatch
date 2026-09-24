begin;

alter table public.flood_reports
  add column if not exists wheel_count integer,
  add column if not exists wheel_confidence double precision,
  add column if not exists wheel_submerged_percent double precision,
  add column if not exists wheel_estimated_depth_cm double precision;

alter table public.flood_reports
  drop constraint if exists chk_report_wheel_count,
  add constraint chk_report_wheel_count
    check (wheel_count is null or wheel_count >= 0),
  drop constraint if exists chk_report_wheel_confidence,
  add constraint chk_report_wheel_confidence
    check (wheel_confidence is null or wheel_confidence between 0 and 1),
  drop constraint if exists chk_report_wheel_submerged_percent,
  add constraint chk_report_wheel_submerged_percent
    check (wheel_submerged_percent is null or wheel_submerged_percent between 0 and 100),
  drop constraint if exists chk_report_wheel_estimated_depth_cm,
  add constraint chk_report_wheel_estimated_depth_cm
    check (wheel_estimated_depth_cm is null or wheel_estimated_depth_cm between 0 and 300);

comment on column public.flood_reports.wheel_estimated_depth_cm is
  'Experimental wheel-based flood depth estimate; must be manually verified.';

commit;

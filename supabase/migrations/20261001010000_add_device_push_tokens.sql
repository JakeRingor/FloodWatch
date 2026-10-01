create table if not exists public.device_push_tokens (
  token text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  platform text not null default 'android'
    check (platform in ('android', 'ios')),
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_device_push_tokens_active_user
  on public.device_push_tokens (user_id)
  where enabled = true;

alter table public.device_push_tokens enable row level security;

create policy "Users can view their push tokens"
  on public.device_push_tokens for select
  to authenticated
  using ((select auth.uid()) = user_id);

create policy "Users can register their push tokens"
  on public.device_push_tokens for insert
  to authenticated
  with check ((select auth.uid()) = user_id);

create policy "Users can update their push tokens"
  on public.device_push_tokens for update
  to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

create policy "Users can remove their push tokens"
  on public.device_push_tokens for delete
  to authenticated
  using ((select auth.uid()) = user_id);

grant select, insert, update, delete on public.device_push_tokens to authenticated;
grant all on public.device_push_tokens to service_role;

create or replace function public.touch_device_push_token_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists touch_device_push_token_updated_at on public.device_push_tokens;
create trigger touch_device_push_token_updated_at
before update on public.device_push_tokens
for each row execute function public.touch_device_push_token_updated_at();

create table public.weather_request_log (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  family_id uuid not null references public.families(id) on delete cascade,
  created_at timestamptz not null default now()
);
create index weather_request_log_user_time
  on public.weather_request_log(user_id, created_at desc);
alter table public.weather_request_log enable row level security;

create function public.consume_weather_quota(p_family uuid) returns boolean
language plpgsql security definer set search_path=public,pg_temp as $$
declare actor uuid:=auth.uid(); request_count integer;
begin
 if actor is null or not public.is_family_member(p_family) then
   raise exception using errcode='42501',message='FAMILY_ACCESS_DENIED';
 end if;
 perform pg_advisory_xact_lock(hashtextextended(actor::text||':weather',0));
 delete from public.weather_request_log where created_at<now()-interval '1 day';
 select count(*) into request_count
 from public.weather_request_log
 where user_id=actor and created_at>now()-interval '1 hour';
 if request_count>=60 then return false; end if;
 insert into public.weather_request_log(user_id,family_id) values(actor,p_family);
 return true;
end $$;
revoke all on function public.consume_weather_quota(uuid) from public;
grant execute on function public.consume_weather_quota(uuid) to authenticated;

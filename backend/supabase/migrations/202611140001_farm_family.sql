-- FARM FAMILY baseline schema. Apply with Supabase CLI; auth.users is managed by Supabase.
create extension if not exists pgcrypto;

create type public.family_role as enum ('OWNER','EDITOR','VIEWER');
create type public.invitation_status as enum ('PENDING','ACCEPTED','REVOKED','EXPIRED');
create type public.sync_state as enum ('PENDING','SYNCING','SYNCED','FAILED','CONFLICT');
create type public.crop_purpose as enum ('SALE','HOUSEHOLD','BOTH');

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text,
  phone text,
  preferred_language text not null default 'en',
  created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create table public.families (
  id uuid primary key default gen_random_uuid(), name text not null check(length(trim(name)) between 1 and 160),
  currency char(3) not null default 'NPR', settings jsonb not null default '{}'::jsonb,
  created_by uuid not null references auth.users(id), created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(), deleted_at timestamptz, deleted_by uuid references auth.users(id),
  record_version bigint not null default 1 check(record_version > 0)
);
create table public.family_members (
  id uuid primary key default gen_random_uuid(), family_id uuid not null references public.families(id),
  user_id uuid not null references auth.users(id), role public.family_role not null,
  can_view boolean not null default true, can_add boolean not null default false,
  can_edit boolean not null default false, can_delete boolean not null default false,
  joined_at timestamptz not null default now(), removed_at timestamptz, created_by uuid references auth.users(id),
  unique(family_id,user_id)
);
create index family_members_user_active on public.family_members(user_id,family_id) where removed_at is null;
create table public.family_invitations (
  id uuid primary key default gen_random_uuid(), family_id uuid not null references public.families(id),
  inviter_id uuid not null references auth.users(id), intended_role public.family_role not null check(intended_role <> 'OWNER'),
  token_hash bytea not null unique, invite_code_hash bytea unique, expires_at timestamptz not null,
  status public.invitation_status not null default 'PENDING', accepted_by uuid references auth.users(id),
  accepted_at timestamptz, created_at timestamptz not null default now(), revoked_at timestamptz,
  check(expires_at > created_at)
);
create index invitations_family_status on public.family_invitations(family_id,status,expires_at);

-- SECURITY DEFINER helpers are deliberately fixed-search-path and expose only boolean checks.
create function public.is_family_member(p_family uuid) returns boolean language sql stable security definer set search_path=public,pg_temp as $$
 select exists(select 1 from public.family_members m where m.family_id=p_family and m.user_id=auth.uid() and m.removed_at is null and m.can_view)
$$;
create function public.has_family_permission(p_family uuid,p_permission text) returns boolean language sql stable security definer set search_path=public,pg_temp as $$
 select exists(select 1 from public.family_members m where m.family_id=p_family and m.user_id=auth.uid() and m.removed_at is null and
   case p_permission when 'view' then m.can_view when 'add' then m.can_add when 'edit' then m.can_edit when 'delete' then m.can_delete else false end)
$$;
create function public.is_family_owner(p_family uuid) returns boolean language sql stable security definer set search_path=public,pg_temp as $$
 select exists(select 1 from public.family_members m where m.family_id=p_family and m.user_id=auth.uid() and m.removed_at is null and m.role='OWNER')
$$;
revoke all on function public.is_family_member(uuid),public.has_family_permission(uuid,text),public.is_family_owner(uuid) from public;
grant execute on function public.is_family_member(uuid),public.has_family_permission(uuid,text),public.is_family_owner(uuid) to authenticated;

create function public.touch_version() returns trigger language plpgsql set search_path=public,pg_temp as $$
begin new.updated_at=now(); new.record_version=old.record_version+1; return new; end $$;
create function public.protect_member_role() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if auth.uid() is not null and not public.is_family_owner(old.family_id) then raise exception using errcode='42501',message='OWNER_REQUIRED'; end if;
 if old.role='OWNER' and (new.role<>'OWNER' or new.removed_at is not null) and
   (select count(*) from public.family_members where family_id=old.family_id and role='OWNER' and removed_at is null)<=1 then
   raise exception using errcode='23514',message='FAMILY_REQUIRES_OWNER'; end if;
 return new;
end $$;
create trigger family_touch before update on public.families for each row execute function public.touch_version();
create trigger member_protect before update on public.family_members for each row execute function public.protect_member_role();

-- Only this function provisions a family and its first OWNER atomically.
create function public.create_family(p_name text,p_currency char(3) default 'NPR') returns uuid
language plpgsql security definer set search_path=public,pg_temp as $$ declare f uuid;
begin
 if auth.uid() is null then raise exception using errcode='28000',message='AUTH_REQUIRED'; end if;
 insert into public.families(name,currency,created_by) values(trim(p_name),upper(p_currency),auth.uid()) returning id into f;
 insert into public.family_members(family_id,user_id,role,can_view,can_add,can_edit,can_delete,created_by)
 values(f,auth.uid(),'OWNER',true,true,true,true,auth.uid()); return f;
end $$;
revoke all on function public.create_family(text,char) from public;
grant execute on function public.create_family(text,char) to authenticated;

create table public.fields (
 id uuid primary key default gen_random_uuid(), family_id uuid not null references public.families(id), local_id text,
 name text not null, area numeric(14,4) check(area is null or area>0), area_unit text, location jsonb,
 notes text, sync_status public.sync_state not null default 'SYNCED', last_synced_at timestamptz,
 record_version bigint not null default 1, created_by uuid not null default auth.uid() references auth.users(id),
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 deleted_at timestamptz, deleted_by uuid references auth.users(id), unique(family_id,local_id), check(area_unit is null or length(area_unit)<=40)
);
create table public.crops (
 id uuid primary key default gen_random_uuid(), family_id uuid not null references public.families(id), local_id text,
 name text not null, variety text, notes text, sync_status public.sync_state not null default 'SYNCED',
 last_synced_at timestamptz, record_version bigint not null default 1, created_by uuid not null default auth.uid() references auth.users(id),
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(), deleted_at timestamptz, deleted_by uuid references auth.users(id), unique(family_id,local_id)
);
create table public.crop_seasons (
 id uuid primary key default gen_random_uuid(), family_id uuid not null references public.families(id), local_id text,
 field_id uuid not null references public.fields(id), crop_id uuid not null references public.crops(id), season_name text,
 purpose public.crop_purpose not null default 'SALE', area numeric(14,4) check(area is null or area>0), area_unit text,
 start_date date, end_date date, notes text, sync_status public.sync_state not null default 'SYNCED', last_synced_at timestamptz,
 record_version bigint not null default 1, created_by uuid not null default auth.uid() references auth.users(id),
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(), deleted_at timestamptz, deleted_by uuid references auth.users(id),
 unique(family_id,local_id), check(end_date is null or start_date is null or end_date>=start_date)
);

create table public.expense_categories (
 id uuid primary key default gen_random_uuid(), family_id uuid not null references public.families(id), name text not null,
 is_custom boolean not null default true, created_by uuid not null default auth.uid() references auth.users(id),
 created_at timestamptz not null default now(), deleted_at timestamptz, unique(family_id,name)
);

-- Transactional ledgers share family/version/soft-delete/offline fields. Quantities and totals are validated.
create table public.seed_records (id uuid primary key default gen_random_uuid(), family_id uuid not null references public.families(id), local_id text,
 crop_season_id uuid references public.crop_seasons(id), seed_name text not null, quantity numeric(14,3) check(quantity is null or quantity>=0), unit text,
 amount numeric(14,2) check(amount is null or amount>=0), currency char(3) not null default 'NPR', purchased_on date, notes text,
 sync_status public.sync_state not null default 'SYNCED', last_synced_at timestamptz, record_version bigint not null default 1,
 created_by uuid not null default auth.uid() references auth.users(id), created_at timestamptz not null default now(), updated_at timestamptz not null default now(), deleted_at timestamptz, deleted_by uuid references auth.users(id), unique(family_id,local_id));
create table public.fertilizer_records (id uuid primary key default gen_random_uuid(), family_id uuid not null references public.families(id), local_id text,
 crop_season_id uuid references public.crop_seasons(id), product_name text not null, quantity numeric(14,3) check(quantity is null or quantity>=0), unit text,
 amount numeric(14,2) check(amount is null or amount>=0), currency char(3) not null default 'NPR', applied_on date, notes text,
 sync_status public.sync_state not null default 'SYNCED', last_synced_at timestamptz, record_version bigint not null default 1,
 created_by uuid not null default auth.uid() references auth.users(id), created_at timestamptz not null default now(), updated_at timestamptz not null default now(), deleted_at timestamptz, deleted_by uuid references auth.users(id), unique(family_id,local_id));
create table public.labor_records (id uuid primary key default gen_random_uuid(), family_id uuid not null references public.families(id), local_id text,
 crop_season_id uuid references public.crop_seasons(id), work_date date not null, worker_count integer check(worker_count is null or worker_count>=0),
 hours numeric(10,2) check(hours is null or hours>=0), amount numeric(14,2) check(amount is null or amount>=0), currency char(3) not null default 'NPR', description text,
 sync_status public.sync_state not null default 'SYNCED', last_synced_at timestamptz, record_version bigint not null default 1,
 created_by uuid not null default auth.uid() references auth.users(id), created_at timestamptz not null default now(), updated_at timestamptz not null default now(), deleted_at timestamptz, deleted_by uuid references auth.users(id), unique(family_id,local_id));
create table public.machinery_usage (id uuid primary key default gen_random_uuid(), family_id uuid not null references public.families(id), local_id text,
 crop_season_id uuid references public.crop_seasons(id), service_name text not null, provider text, usage_date date, quantity numeric(14,3) check(quantity is null or quantity>=0), unit text,
 amount numeric(14,2) check(amount is null or amount>=0), currency char(3) not null default 'NPR', notes text,
 sync_status public.sync_state not null default 'SYNCED', last_synced_at timestamptz, record_version bigint not null default 1,
 created_by uuid not null default auth.uid() references auth.users(id), created_at timestamptz not null default now(), updated_at timestamptz not null default now(), deleted_at timestamptz, deleted_by uuid references auth.users(id), unique(family_id,local_id));
create table public.expenses (id uuid primary key default gen_random_uuid(), family_id uuid not null references public.families(id), local_id text,
 category_id uuid references public.expense_categories(id), crop_season_id uuid references public.crop_seasons(id), expense_date date not null,
 description text not null, quantity numeric(14,3) check(quantity is null or quantity>=0), unit_price numeric(14,2) check(unit_price is null or unit_price>=0),
 amount numeric(14,2) not null check(amount>=0), currency char(3) not null default 'NPR', notes text,
 sync_status public.sync_state not null default 'SYNCED', last_synced_at timestamptz, record_version bigint not null default 1,
 created_by uuid not null default auth.uid() references auth.users(id), created_at timestamptz not null default now(), updated_at timestamptz not null default now(), deleted_at timestamptz, deleted_by uuid references auth.users(id), unique(family_id,local_id));
create table public.harvest_records (id uuid primary key default gen_random_uuid(), family_id uuid not null references public.families(id), local_id text,
 crop_season_id uuid not null references public.crop_seasons(id), harvest_date date not null, quantity numeric(14,3) not null check(quantity>=0), unit text not null,
 grade text, notes text, sync_status public.sync_state not null default 'SYNCED', last_synced_at timestamptz, record_version bigint not null default 1,
 created_by uuid not null default auth.uid() references auth.users(id), created_at timestamptz not null default now(), updated_at timestamptz not null default now(), deleted_at timestamptz, deleted_by uuid references auth.users(id), unique(family_id,local_id));
create table public.sales (id uuid primary key default gen_random_uuid(), family_id uuid not null references public.families(id), local_id text,
 harvest_record_id uuid references public.harvest_records(id), crop_season_id uuid references public.crop_seasons(id), sold_on date not null,
 quantity numeric(14,3) not null check(quantity>=0), unit text not null, unit_price numeric(14,2) not null check(unit_price>=0), total_amount numeric(14,2) not null check(total_amount>=0),
 currency char(3) not null default 'NPR', buyer text, notes text, sync_status public.sync_state not null default 'SYNCED', last_synced_at timestamptz, record_version bigint not null default 1,
 created_by uuid not null default auth.uid() references auth.users(id), created_at timestamptz not null default now(), updated_at timestamptz not null default now(), deleted_at timestamptz, deleted_by uuid references auth.users(id), unique(family_id,local_id));
create table public.historical_prices (id uuid primary key default gen_random_uuid(), family_id uuid not null references public.families(id), crop_id uuid references public.crops(id), crop_name text not null,
 observed_on date not null, market text, quantity_unit text not null, price numeric(14,2) not null check(price>=0), currency char(3) not null default 'NPR', source text,
 created_by uuid not null default auth.uid() references auth.users(id), created_at timestamptz not null default now(), deleted_at timestamptz, deleted_by uuid references auth.users(id));

create table public.audit_logs (id bigint generated always as identity primary key, family_id uuid references public.families(id), user_id uuid references auth.users(id),
 entity_table text not null, entity_id text, action text not null, old_data jsonb, new_data jsonb, metadata jsonb not null default '{}'::jsonb, created_at timestamptz not null default now());
create table public.notifications (id uuid primary key default gen_random_uuid(), family_id uuid not null references public.families(id), user_id uuid references auth.users(id),
 kind text not null, content_key text not null, content_args jsonb not null default '{}'::jsonb, read_at timestamptz, created_at timestamptz not null default now());
create index notifications_inbox on public.notifications(user_id,created_at desc) where read_at is null;
create table public.backup_records (id uuid primary key default gen_random_uuid(), family_id uuid not null references public.families(id), requested_by uuid not null references auth.users(id),
 storage_path text not null, format text not null check(format in ('PDF','CSV','XLSX','ZIP')), status text not null default 'PENDING', metadata jsonb not null default '{}'::jsonb,
 created_at timestamptz not null default now(), expires_at timestamptz);
create table public.sync_conflicts (id uuid primary key default gen_random_uuid(), family_id uuid not null references public.families(id), entity_table text not null,
 entity_id uuid not null, client_version bigint not null, server_version bigint not null, client_data jsonb, server_data jsonb, user_id uuid not null default auth.uid() references auth.users(id),
 resolved_at timestamptz, resolution text, created_at timestamptz not null default now());
create table public.app_settings (key text primary key, value jsonb not null, updated_at timestamptz not null default now());

-- Generic immutable-to-client audit capture; actor context is never accepted from a request payload.
create function public.audit_row() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare oldj jsonb; newj jsonb; fam uuid; rid text; act text;
begin
 if tg_op='INSERT' then newj=to_jsonb(new); fam=(newj->>'family_id')::uuid; rid=newj->>'id'; act='CREATE';
 elsif tg_op='UPDATE' then oldj=to_jsonb(old); newj=to_jsonb(new); fam=coalesce((newj->>'family_id')::uuid,(oldj->>'family_id')::uuid); rid=coalesce(newj->>'id',oldj->>'id');
   act=case when oldj->>'deleted_at' is null and newj->>'deleted_at' is not null then 'DELETE' when oldj->>'deleted_at' is not null and newj->>'deleted_at' is null then 'RESTORE' else 'UPDATE' end;
 else oldj=to_jsonb(old); fam=(oldj->>'family_id')::uuid; rid=oldj->>'id'; act='DELETE'; end if;
 insert into public.audit_logs(family_id,user_id,entity_table,entity_id,action,old_data,new_data) values(fam,auth.uid(),tg_table_name,rid,act,oldj,newj);
 return coalesce(new,old);
end $$;

-- RLS: users see their profile; every family-owned row is fenced by family_id and explicit permissions.
alter table public.profiles enable row level security;
create policy profile_select on public.profiles for select to authenticated using(id=auth.uid());
create policy profile_update on public.profiles for update to authenticated using(id=auth.uid()) with check(id=auth.uid());
alter table public.families enable row level security;
create policy families_select on public.families for select to authenticated using(public.is_family_member(id));
create policy families_update on public.families for update to authenticated using(public.is_family_owner(id)) with check(public.is_family_owner(id));
alter table public.family_members enable row level security;
create policy members_select on public.family_members for select to authenticated using(public.is_family_member(family_id));
create policy members_insert on public.family_members for insert to authenticated with check(public.is_family_owner(family_id));
create policy members_update on public.family_members for update to authenticated using(public.is_family_owner(family_id)) with check(public.is_family_owner(family_id));
alter table public.family_invitations enable row level security;
create policy invitations_owner on public.family_invitations for select to authenticated using(public.is_family_owner(family_id));

do $$ declare t text; begin
 foreach t in array array['fields','crops','crop_seasons','expense_categories','seed_records','fertilizer_records','labor_records','machinery_usage','expenses','harvest_records','sales','historical_prices'] loop
  execute format('alter table public.%I enable row level security',t);
  execute format('create policy %I_select on public.%I for select to authenticated using(public.is_family_member(family_id))',t,t);
  execute format('create policy %I_insert on public.%I for insert to authenticated with check(public.has_family_permission(family_id,''add''))',t,t);
  execute format('create policy %I_update on public.%I for update to authenticated using(public.has_family_permission(family_id,''edit'')) with check(public.has_family_permission(family_id,''edit''))',t,t);
  execute format('create trigger %I_audit after insert or update or delete on public.%I for each row execute function public.audit_row()',t,t);
 end loop;
end $$;
alter table public.audit_logs enable row level security;
create policy audit_read on public.audit_logs for select to authenticated using(public.is_family_member(family_id));
-- No user write policy for audit logs; trigger writes execute as owner.
create trigger family_members_audit after insert or update or delete on public.family_members for each row execute function public.audit_row();
alter table public.notifications enable row level security;
create policy notifications_read on public.notifications for select to authenticated using(user_id=auth.uid() and public.is_family_member(family_id));
create policy notifications_update on public.notifications for update to authenticated using(user_id=auth.uid()) with check(user_id=auth.uid());
alter table public.backup_records enable row level security;
create policy backup_read on public.backup_records for select to authenticated using(public.is_family_owner(family_id));
create policy backup_owner_add on public.backup_records for insert to authenticated with check(public.is_family_owner(family_id) and requested_by=auth.uid());
alter table public.sync_conflicts enable row level security;
create policy conflicts_read on public.sync_conflicts for select to authenticated using(public.is_family_member(family_id));
create policy conflicts_add on public.sync_conflicts for insert to authenticated with check(public.has_family_permission(family_id,'edit') and user_id=auth.uid());
alter table public.app_settings enable row level security;

-- Version checked update: call before UPDATE; stale clients receive a structured SQLSTATE 40001 payload.
create function public.assert_record_version(p_table regclass,p_id uuid,p_family uuid,p_expected bigint) returns void
language plpgsql security definer set search_path=public,pg_temp as $$ declare actual bigint; rowj jsonb; table_name text; schema_name text;
begin
 select n.nspname,c.relname into schema_name,table_name from pg_class c join pg_namespace n on n.oid=c.relnamespace where c.oid=p_table;
 if schema_name<>'public' or table_name not in ('fields','crops','crop_seasons','seed_records','fertilizer_records','labor_records','machinery_usage','expenses','harvest_records','sales') then
   raise exception using errcode='42501',message='TABLE_NOT_ALLOWED'; end if;
 if not public.has_family_permission(p_family,'edit') then raise exception using errcode='42501',message='EDIT_PERMISSION_REQUIRED'; end if;
 execute format('select record_version,to_jsonb(x) from %s x where id=$1 and family_id=$2 and deleted_at is null',p_table) into actual,rowj using p_id,p_family;
 if actual is null then raise exception using errcode='P0002',message='RECORD_NOT_FOUND'; end if;
 if actual<>p_expected then raise exception using errcode='40001',message=jsonb_build_object('code','VERSION_CONFLICT','expected_version',p_expected,'actual_version',actual,'server_record',rowj)::text; end if;
end $$;
revoke all on function public.assert_record_version(regclass,uuid,uuid,bigint) from public;
grant execute on function public.assert_record_version(regclass,uuid,uuid,bigint) to authenticated;

create function public.soft_delete_record(p_table regclass,p_id uuid,p_family uuid,p_expected bigint) returns bigint
language plpgsql security definer set search_path=public,pg_temp as $$ declare next_version bigint; table_name text; schema_name text; actual bigint;
begin
 select n.nspname,c.relname into schema_name,table_name from pg_class c join pg_namespace n on n.oid=c.relnamespace where c.oid=p_table;
 if schema_name<>'public' or table_name not in ('fields','crops','crop_seasons','seed_records','fertilizer_records','labor_records','machinery_usage','expenses','harvest_records','sales') then raise exception using errcode='42501',message='TABLE_NOT_ALLOWED'; end if;
 if not public.has_family_permission(p_family,'delete') then raise exception using errcode='42501',message='DELETE_PERMISSION_REQUIRED'; end if;
 execute format('update %s set deleted_at=now(),deleted_by=$1 where id=$2 and family_id=$3 and record_version=$4 and deleted_at is null returning record_version',p_table)
 into next_version using auth.uid(),p_id,p_family,p_expected;
 if next_version is null then
  execute format('select record_version from %s where id=$1 and family_id=$2',p_table) into actual using p_id,p_family;
  if actual is null then raise exception using errcode='P0002',message='RECORD_NOT_FOUND'; end if;
  raise exception using errcode='40001',message=jsonb_build_object('code','VERSION_CONFLICT','expected_version',p_expected,'actual_version',actual)::text;
 end if;
 return next_version;
end $$;
create function public.restore_record(p_table regclass,p_id uuid,p_family uuid,p_expected bigint) returns bigint
language plpgsql security definer set search_path=public,pg_temp as $$ declare next_version bigint; table_name text; schema_name text; actual bigint;
begin
 select n.nspname,c.relname into schema_name,table_name from pg_class c join pg_namespace n on n.oid=c.relnamespace where c.oid=p_table;
 if schema_name<>'public' or table_name not in ('fields','crops','crop_seasons','seed_records','fertilizer_records','labor_records','machinery_usage','expenses','harvest_records','sales') then raise exception using errcode='42501',message='TABLE_NOT_ALLOWED'; end if;
 if not public.is_family_owner(p_family) then raise exception using errcode='42501',message='OWNER_REQUIRED'; end if;
 execute format('update %s set deleted_at=null,deleted_by=null where id=$1 and family_id=$2 and record_version=$3 and deleted_at is not null returning record_version',p_table)
 into next_version using p_id,p_family,p_expected;
 if next_version is null then
  execute format('select record_version from %s where id=$1 and family_id=$2',p_table) into actual using p_id,p_family;
  if actual is null then raise exception using errcode='P0002',message='RECORD_NOT_FOUND'; end if;
  raise exception using errcode='40001',message=jsonb_build_object('code','VERSION_CONFLICT','expected_version',p_expected,'actual_version',actual)::text;
 end if;
 return next_version;
end $$;
revoke all on function public.soft_delete_record(regclass,uuid,uuid,bigint),public.restore_record(regclass,uuid,uuid,bigint) from public;
grant execute on function public.soft_delete_record(regclass,uuid,uuid,bigint),public.restore_record(regclass,uuid,uuid,bigint) to authenticated;

-- Prevent family-mismatched references, regardless of client code.
create function public.check_family_links() returns trigger language plpgsql set search_path=public,pg_temp as $$
begin
 if tg_table_name='crop_seasons' then
   if not exists(select 1 from public.fields where id=new.field_id and family_id=new.family_id) or not exists(select 1 from public.crops where id=new.crop_id and family_id=new.family_id) then raise exception using errcode='23503',message='CROSS_FAMILY_REFERENCE'; end if;
 elsif tg_table_name in ('seed_records','fertilizer_records','labor_records','machinery_usage','expenses','harvest_records','sales') and new.crop_season_id is not null then
   if not exists(select 1 from public.crop_seasons where id=new.crop_season_id and family_id=new.family_id) then raise exception using errcode='23503',message='CROSS_FAMILY_REFERENCE'; end if;
 end if;
 if tg_table_name='expenses' and new.category_id is not null and not exists(select 1 from public.expense_categories where id=new.category_id and family_id=new.family_id) then raise exception using errcode='23503',message='CROSS_FAMILY_REFERENCE'; end if;
 if tg_table_name='sales' and new.harvest_record_id is not null and not exists(select 1 from public.harvest_records where id=new.harvest_record_id and family_id=new.family_id) then raise exception using errcode='23503',message='CROSS_FAMILY_REFERENCE'; end if;
 return new;
end $$;
create trigger crop_season_family_link before insert or update on public.crop_seasons for each row execute function public.check_family_links();
create trigger expense_family_link before insert or update on public.expenses for each row execute function public.check_family_links();
create trigger sales_family_link before insert or update on public.sales for each row execute function public.check_family_links();
create trigger harvest_family_link before insert or update on public.harvest_records for each row execute function public.check_family_links();
create trigger seed_family_link before insert or update on public.seed_records for each row execute function public.check_family_links();
create trigger fertilizer_family_link before insert or update on public.fertilizer_records for each row execute function public.check_family_links();
create trigger labor_family_link before insert or update on public.labor_records for each row execute function public.check_family_links();
create trigger machinery_family_link before insert or update on public.machinery_usage for each row execute function public.check_family_links();

-- Invitations return the bearer token once; only its SHA-256 digest is persisted.
create function public.create_family_invitation(p_family uuid,p_role public.family_role,p_valid_for interval default interval '7 days')
returns table(invitation_id uuid, token text, expires_at timestamptz) language plpgsql security definer set search_path=public,pg_temp as $$
declare raw_token text; iid uuid; expiry timestamptz;
begin
 if not public.is_family_owner(p_family) then raise exception using errcode='42501',message='OWNER_REQUIRED'; end if;
 if p_role='OWNER' or p_valid_for<=interval '0 seconds' or p_valid_for>interval '30 days' then raise exception using errcode='22023',message='INVALID_INVITATION'; end if;
 raw_token=encode(gen_random_bytes(32),'hex'); expiry=now()+p_valid_for;
 insert into public.family_invitations(family_id,inviter_id,intended_role,token_hash,expires_at)
 values(p_family,auth.uid(),p_role,digest(raw_token,'sha256'),expiry) returning id into iid;
 insert into public.audit_logs(family_id,user_id,entity_table,entity_id,action,new_data) values(p_family,auth.uid(),'family_invitations',iid::text,'INVITE_CREATE',jsonb_build_object('role',p_role,'expires_at',expiry));
 return query select iid,raw_token,expiry;
end $$;
create function public.accept_family_invitation(p_token text) returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare inv public.family_invitations%rowtype;
begin
 if auth.uid() is null then raise exception using errcode='28000',message='AUTH_REQUIRED'; end if;
 select * into inv from public.family_invitations where token_hash=digest(p_token,'sha256') for update;
 if not found or inv.status<>'PENDING' or inv.expires_at<=now() then raise exception using errcode='22023',message='INVITATION_INVALID_OR_EXPIRED'; end if;
 update public.family_invitations set status='ACCEPTED',accepted_by=auth.uid(),accepted_at=now() where id=inv.id;
 insert into public.family_members(family_id,user_id,role,can_view,can_add,can_edit,can_delete,created_by)
 values(inv.family_id,auth.uid(),inv.intended_role,true,inv.intended_role='EDITOR',inv.intended_role='EDITOR',false,inv.inviter_id)
 on conflict(family_id,user_id) do update set removed_at=null,role=excluded.role,can_view=true,can_add=excluded.can_add,can_edit=excluded.can_edit,can_delete=false;
 insert into public.audit_logs(family_id,user_id,entity_table,entity_id,action,new_data) values(inv.family_id,auth.uid(),'family_members',auth.uid()::text,'INVITE_ACCEPT',jsonb_build_object('role',inv.intended_role));
 return inv.family_id;
end $$;
revoke all on function public.create_family_invitation(uuid,public.family_role,interval),public.accept_family_invitation(text) from public;
grant execute on function public.create_family_invitation(uuid,public.family_role,interval),public.accept_family_invitation(text) to authenticated;

-- Apply consistent timestamps/version increments to mutable synchronized records.
create function public.bump_record_version() returns trigger language plpgsql set search_path=public,pg_temp as $$
begin
 if new.family_id<>old.family_id then raise exception using errcode='23514',message='FAMILY_ID_IMMUTABLE'; end if;
 new.updated_at=now(); new.record_version=old.record_version+1; return new;
end $$;
do $$ declare t text; begin
 foreach t in array array['fields','crops','crop_seasons','seed_records','fertilizer_records','labor_records','machinery_usage','expenses','harvest_records','sales'] loop
  execute format('create trigger %I_version before update on public.%I for each row execute function public.bump_record_version()',t,t);
 end loop;
end $$;

-- Aggregations obey caller RLS. Unknown costs are kept null rather than reporting fabricated profit.
create function public.family_season_summary(p_family uuid,p_from date default null,p_to date default null)
returns table(season_id uuid,crop_name text,field_name text,harvest_quantity numeric,sales_total numeric,expense_total numeric,known_profit numeric)
language sql stable security invoker set search_path=public,pg_temp as $$
 select cs.id,c.name,f.name,coalesce(h.qty,0),coalesce(s.total,0),e.total,
 case when e.total is null then null else coalesce(s.total,0)-e.total end
 from public.crop_seasons cs join public.crops c on c.id=cs.crop_id join public.fields f on f.id=cs.field_id
 left join lateral(select sum(quantity) qty from public.harvest_records x where x.crop_season_id=cs.id and x.deleted_at is null) h on true
 left join lateral(select sum(total_amount) total from public.sales x where x.crop_season_id=cs.id and x.deleted_at is null) s on true
 left join lateral(select sum(amount) total from public.expenses x where x.crop_season_id=cs.id and x.deleted_at is null) e on true
 where cs.family_id=p_family and cs.deleted_at is null and public.is_family_member(p_family)
 and (p_from is null or cs.start_date>=p_from) and (p_to is null or cs.start_date<=p_to)
$$;
grant execute on function public.family_season_summary(uuid,date,date) to authenticated;

-- Create profile row after Supabase Auth signup; email/password signup and any future auth provider share this path.
create function public.handle_new_user() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin insert into public.profiles(id,display_name) values(new.id,coalesce(new.raw_user_meta_data->>'full_name',new.raw_user_meta_data->>'name')); return new; end $$;
create trigger on_auth_user_created after insert on auth.users for each row execute function public.handle_new_user();

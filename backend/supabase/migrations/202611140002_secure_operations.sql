-- Harden direct writes: clients may INSERT under RLS; updates must use atomic RPCs.
alter table public.crop_seasons add column costs_complete boolean not null default false;
alter table public.crop_seasons add column revenue_complete boolean not null default false;
alter table public.expense_categories add column local_id text;
alter table public.expense_categories add column sync_status public.sync_state not null default 'SYNCED';
alter table public.expense_categories add column last_synced_at timestamptz default now();
alter table public.expense_categories add column record_version bigint not null default 1;
alter table public.expense_categories add column updated_at timestamptz not null default now();
alter table public.expense_categories add column deleted_by uuid references auth.users(id);
alter table public.historical_prices add column local_id text;
alter table public.historical_prices add column sync_status public.sync_state not null default 'SYNCED';
alter table public.historical_prices add column last_synced_at timestamptz default now();
alter table public.historical_prices add column record_version bigint not null default 1;
alter table public.historical_prices add column updated_at timestamptz not null default now();
create unique index expense_categories_local_id on public.expense_categories(family_id,local_id) where local_id is not null;
create unique index historical_prices_local_id on public.historical_prices(family_id,local_id) where local_id is not null;
create index expenses_family_date_active on public.expenses(family_id,expense_date desc) where deleted_at is null;
create index sales_family_date_active on public.sales(family_id,sold_on desc) where deleted_at is null;
create index harvest_family_date_active on public.harvest_records(family_id,harvest_date desc) where deleted_at is null;
create index historical_prices_family_date on public.historical_prices(family_id,observed_on desc) where deleted_at is null;
create index audit_logs_family_created on public.audit_logs(family_id,created_at desc);
create index backup_records_family_created on public.backup_records(family_id,created_at desc);
create trigger expense_categories_version before update on public.expense_categories for each row execute function public.bump_record_version();
create trigger historical_prices_version before update on public.historical_prices for each row execute function public.bump_record_version();

do $$ declare t text; begin
 foreach t in array array['fields','crops','crop_seasons','expense_categories','seed_records','fertilizer_records','labor_records','machinery_usage','expenses','harvest_records','sales','historical_prices'] loop
   execute format('drop policy if exists %I_update on public.%I',t,t);
   execute format('drop policy if exists %I_delete on public.%I',t,t);
 end loop;
drop policy if exists expense_categories_delete on public.expense_categories;
end $$;

create function public.guard_sync_metadata() returns trigger language plpgsql set search_path=public,pg_temp as $$
begin
 if tg_op='INSERT' then
   new.sync_status='SYNCED'; new.last_synced_at=now(); new.record_version=1;
   new.created_at=now(); new.updated_at=now(); new.deleted_at=null; new.deleted_by=null;
   if auth.uid() is not null and current_setting('farm.allow_import',true) is distinct from 'yes' then new.id=gen_random_uuid(); end if;
   if auth.uid() is not null then new.created_by=auth.uid(); end if;
 else
   if new.family_id<>old.family_id then raise exception using errcode='23514',message='FAMILY_ID_IMMUTABLE'; end if;
   if new.created_at<>old.created_at then raise exception using errcode='23514',message='CREATED_AT_IMMUTABLE'; end if;
   if new.sync_status<>old.sync_status or new.last_synced_at is distinct from old.last_synced_at then raise exception using errcode='42501',message='SYNC_METADATA_SERVER_MANAGED'; end if;
   if new.created_by is distinct from old.created_by then raise exception using errcode='42501',message='CREATED_BY_IMMUTABLE'; end if;
   if (new.deleted_at is distinct from old.deleted_at or new.deleted_by is distinct from old.deleted_by)
      and current_setting('farm.allow_soft_delete',true) is distinct from 'yes' then raise exception using errcode='42501',message='USE_SOFT_DELETE_RPC'; end if;
 end if;
 return new;
end $$;
create or replace function public.protect_member_role() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if auth.uid() is not null and not public.is_family_owner(old.family_id) then raise exception using errcode='42501',message='OWNER_REQUIRED'; end if;
 if new.family_id<>old.family_id or new.user_id<>old.user_id then raise exception using errcode='23514',message='MEMBER_IDENTITY_IMMUTABLE'; end if;
 if old.role='OWNER' and (new.role<>'OWNER' or new.removed_at is not null) and
   (select count(*) from public.family_members where family_id=old.family_id and role='OWNER' and removed_at is null)<=1 then
   raise exception using errcode='23514',message='FAMILY_REQUIRES_OWNER'; end if;
 return new;
end $$;
do $$ declare t text; begin
 foreach t in array array['fields','crops','crop_seasons','expense_categories','historical_prices','seed_records','fertilizer_records','labor_records','machinery_usage','expenses','harvest_records','sales'] loop
  execute format('create trigger %I_sync_guard before insert or update on public.%I for each row execute function public.guard_sync_metadata()',t,t);
 end loop;
end $$;
-- SQL UPDATE triggers fire alphabetically: guard runs before version bump; version bump remains authoritative.
create or replace function public.soft_delete_record(p_table regclass,p_id uuid,p_family uuid,p_expected bigint) returns bigint
language plpgsql security definer set search_path=public,pg_temp as $$ declare next_version bigint; table_name text; schema_name text; actual bigint;
begin
 select n.nspname,c.relname into schema_name,table_name from pg_class c join pg_namespace n on n.oid=c.relnamespace where c.oid=p_table;
 if schema_name<>'public' or table_name not in ('fields','crops','crop_seasons','expense_categories','historical_prices','seed_records','fertilizer_records','labor_records','machinery_usage','expenses','harvest_records','sales') then raise exception using errcode='42501',message='TABLE_NOT_ALLOWED'; end if;
 if not public.has_family_permission(p_family,'delete') then raise exception using errcode='42501',message='DELETE_PERMISSION_REQUIRED'; end if;
 perform set_config('farm.allow_soft_delete','yes',true);
 execute format('update %s set deleted_at=now(),deleted_by=$1 where id=$2 and family_id=$3 and record_version=$4 and deleted_at is null returning record_version',p_table)
 into next_version using auth.uid(),p_id,p_family,p_expected;
 if next_version is null then
  execute format('select record_version from %s where id=$1 and family_id=$2',p_table) into actual using p_id,p_family;
  if actual is null then raise exception using errcode='P0002',message='RECORD_NOT_FOUND'; end if;
  raise exception using errcode='40001',message=jsonb_build_object('code','VERSION_CONFLICT','expected_version',p_expected,'actual_version',actual)::text;
 end if;
 return next_version;
end $$;
create or replace function public.restore_record(p_table regclass,p_id uuid,p_family uuid,p_expected bigint) returns bigint
language plpgsql security definer set search_path=public,pg_temp as $$ declare next_version bigint; table_name text; schema_name text; actual bigint;
begin
 select n.nspname,c.relname into schema_name,table_name from pg_class c join pg_namespace n on n.oid=c.relnamespace where c.oid=p_table;
 if schema_name<>'public' or table_name not in ('fields','crops','crop_seasons','expense_categories','historical_prices','seed_records','fertilizer_records','labor_records','machinery_usage','expenses','harvest_records','sales') then raise exception using errcode='42501',message='TABLE_NOT_ALLOWED'; end if;
 if not public.is_family_owner(p_family) then raise exception using errcode='42501',message='OWNER_REQUIRED'; end if;
 perform set_config('farm.allow_soft_delete','yes',true);
 execute format('update %s set deleted_at=null,deleted_by=null where id=$1 and family_id=$2 and record_version=$3 and deleted_at is not null returning record_version',p_table)
 into next_version using p_id,p_family,p_expected;
 if next_version is null then
  execute format('select record_version from %s where id=$1 and family_id=$2',p_table) into actual using p_id,p_family;
  if actual is null then raise exception using errcode='P0002',message='RECORD_NOT_FOUND'; end if;
  raise exception using errcode='40001',message=jsonb_build_object('code','VERSION_CONFLICT','expected_version',p_expected,'actual_version',actual)::text;
 end if;
 return next_version;
end $$;

-- Table and field allowlists prevent identifier injection and prevent metadata/authority edits.
create function public.sync_update(p_table regclass,p_id uuid,p_family uuid,p_expected bigint,p_data jsonb)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare table_name text; schema_name text; qualified text; allowed text[]; key_name text; assignments text=''; rowj jsonb; actual bigint;
begin
 select n.nspname,c.relname into schema_name,table_name from pg_class c join pg_namespace n on n.oid=c.relnamespace where c.oid=p_table;
 if schema_name<>'public' then raise exception using errcode='42501',message='TABLE_NOT_ALLOWED'; end if;
 allowed=case table_name
  when 'fields' then array['name','area','area_unit','location','notes']
  when 'crops' then array['name','variety','notes']
  when 'crop_seasons' then array['field_id','crop_id','season_name','purpose','area','area_unit','start_date','end_date','notes','costs_complete','revenue_complete']
  when 'expense_categories' then array['name','is_custom']
  when 'historical_prices' then array['crop_id','crop_name','observed_on','market','quantity_unit','price','currency','source']
  when 'seed_records' then array['crop_season_id','seed_name','quantity','unit','amount','currency','purchased_on','notes']
  when 'fertilizer_records' then array['crop_season_id','product_name','quantity','unit','amount','currency','applied_on','notes']
  when 'labor_records' then array['crop_season_id','work_date','worker_count','hours','amount','currency','description']
  when 'machinery_usage' then array['crop_season_id','service_name','provider','usage_date','quantity','unit','amount','currency','notes']
  when 'expenses' then array['category_id','crop_season_id','expense_date','description','quantity','unit_price','amount','currency','notes']
  when 'harvest_records' then array['crop_season_id','harvest_date','quantity','unit','grade','notes']
  when 'sales' then array['harvest_record_id','crop_season_id','sold_on','quantity','unit','unit_price','total_amount','currency','buyer','notes']
  else null end;
 if allowed is null then raise exception using errcode='42501',message='TABLE_NOT_ALLOWED'; end if;
 if not public.has_family_permission(p_family,'edit') then raise exception using errcode='42501',message='EDIT_PERMISSION_REQUIRED'; end if;
 if p_data is null or jsonb_typeof(p_data)<>'object' or p_data='{}'::jsonb then raise exception using errcode='22023',message='EMPTY_UPDATE'; end if;
 if (p_data ? 'costs_complete' or p_data ? 'revenue_complete') and not public.is_family_owner(p_family) then raise exception using errcode='42501',message='OWNER_REQUIRED_FOR_COMPLETENESS'; end if;
 for key_name in select jsonb_object_keys(p_data) loop
   if not key_name=any(allowed) then raise exception using errcode='22023',message='FIELD_NOT_ALLOWED:'||key_name; end if;
   assignments=assignments||case when assignments='' then '' else ',' end||format('%I=(jsonb_populate_record(null::%I.%I,$1)).%I',key_name,schema_name,table_name,key_name);
 end loop;
 qualified=format('%I.%I',schema_name,table_name);
 execute format('update %s set %s where id=$2 and family_id=$3 and record_version=$4 and deleted_at is null returning to_jsonb(%I)',qualified,assignments,table_name)
 into rowj using p_data,p_id,p_family,p_expected;
 if rowj is not null then return jsonb_build_object('status','SYNCED','record',rowj); end if;
 execute format('select record_version,to_jsonb(x) from %s x where id=$1 and family_id=$2',qualified) into actual,rowj using p_id,p_family;
 if actual is null then raise exception using errcode='P0002',message='RECORD_NOT_FOUND'; end if;
 insert into public.sync_conflicts(family_id,entity_table,entity_id,client_version,server_version,client_data,server_data,user_id)
 values(p_family,table_name,p_id,p_expected,actual,p_data,rowj,auth.uid());
 return jsonb_build_object('status','CONFLICT','code','VERSION_CONFLICT','expected_version',p_expected,'actual_version',actual,'server_record',rowj);
end $$;
revoke all on function public.sync_update(regclass,uuid,uuid,bigint,jsonb) from public;
grant execute on function public.sync_update(regclass,uuid,uuid,bigint,jsonb) to authenticated;

-- Owner-only membership administration, including explicit permission bits.
create function public.set_family_member(p_family uuid,p_user uuid,p_role public.family_role,p_can_view boolean,p_can_add boolean,p_can_edit boolean,p_can_delete boolean)
returns void language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if not public.is_family_owner(p_family) then raise exception using errcode='42501',message='OWNER_REQUIRED'; end if;
 if p_role='OWNER' and not (p_can_view and p_can_add and p_can_edit and p_can_delete) then raise exception using errcode='22023',message='OWNER_PERMISSIONS_REQUIRED'; end if;
 update public.family_members set role=p_role,can_view=p_can_view,can_add=p_can_add,can_edit=p_can_edit,can_delete=p_can_delete
 where family_id=p_family and user_id=p_user and removed_at is null;
 if not found then raise exception using errcode='P0002',message='ACTIVE_MEMBER_NOT_FOUND'; end if;
end $$;
create function public.remove_family_member(p_family uuid,p_user uuid) returns void language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if not public.is_family_owner(p_family) then raise exception using errcode='42501',message='OWNER_REQUIRED'; end if;
 if p_user=auth.uid() then raise exception using errcode='22023',message='OWNER_CANNOT_REMOVE_SELF'; end if;
 update public.family_members set removed_at=now() where family_id=p_family and user_id=p_user and removed_at is null;
 if not found then raise exception using errcode='P0002',message='ACTIVE_MEMBER_NOT_FOUND'; end if;
end $$;
create function public.restore_family_member(p_family uuid,p_user uuid) returns void language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if not public.is_family_owner(p_family) then raise exception using errcode='42501',message='OWNER_REQUIRED'; end if;
 update public.family_members set removed_at=null where family_id=p_family and user_id=p_user and removed_at is not null;
 if not found then raise exception using errcode='P0002',message='REMOVED_MEMBER_NOT_FOUND'; end if;
end $$;
create function public.revoke_family_invitation(p_invitation uuid) returns void language plpgsql security definer set search_path=public,pg_temp as $$
declare f uuid;
begin
 select family_id into f from public.family_invitations where id=p_invitation;
 if f is null then raise exception using errcode='P0002',message='INVITATION_NOT_FOUND'; end if;
 if not public.is_family_owner(f) then raise exception using errcode='42501',message='OWNER_REQUIRED'; end if;
 update public.family_invitations set status='REVOKED',revoked_at=now() where id=p_invitation and status='PENDING';
 if not found then raise exception using errcode='22023',message='INVITATION_NOT_PENDING'; end if;
 insert into public.audit_logs(family_id,user_id,entity_table,entity_id,action) values(f,auth.uid(),'family_invitations',p_invitation::text,'INVITE_REVOKE');
end $$;
revoke all on function public.set_family_member(uuid,uuid,public.family_role,boolean,boolean,boolean,boolean),public.remove_family_member(uuid,uuid),public.restore_family_member(uuid,uuid),public.revoke_family_invitation(uuid) from public;
grant execute on function public.set_family_member(uuid,uuid,public.family_role,boolean,boolean,boolean,boolean),public.remove_family_member(uuid,uuid),public.restore_family_member(uuid,uuid),public.revoke_family_invitation(uuid) to authenticated;

-- Additional security-invoker reports. Every result is scoped by caller RLS and authorization.
create function public.family_finance_summary(p_family uuid,p_from date default null,p_to date default null)
returns table(currency char(3),revenue numeric,cost numeric,profit numeric,cost_is_complete boolean)
language sql stable security invoker set search_path=public,pg_temp as $$
 select coalesce(s.currency,e.currency,'NPR'),coalesce(s.total,0),coalesce(e.total,0),
 case when coalesce(c.complete,false) then coalesce(s.total,0)-coalesce(e.total,0) else null end,coalesce(c.complete,false)
 from (select currency,sum(total_amount) total from public.sales where family_id=p_family and deleted_at is null and (p_from is null or sold_on>=p_from) and (p_to is null or sold_on<=p_to) group by currency) s
 full join (select currency,sum(amount) total from public.expenses where family_id=p_family and deleted_at is null and (p_from is null or expense_date>=p_from) and (p_to is null or expense_date<=p_to) group by currency) e using(currency)
 left join (select bool_and(cs.costs_complete and cs.revenue_complete) complete from public.crop_seasons cs where cs.family_id=p_family and cs.deleted_at is null and (p_from is null or cs.start_date>=p_from) and (p_to is null or cs.start_date<=p_to)) c on true
 where public.is_family_member(p_family)
$$;
create function public.family_crop_report(p_family uuid,p_crop uuid default null)
returns table(crop_id uuid,crop_name text,seasons bigint,harvest_quantity numeric,sales_total numeric)
language sql stable security invoker set search_path=public,pg_temp as $$
 select c.id,c.name,count(distinct cs.id),h.quantity,s.total
 from public.crops c left join public.crop_seasons cs on cs.crop_id=c.id and cs.deleted_at is null
 left join lateral(select case when count(distinct x.unit)<=1 then sum(x.quantity) else null end quantity from public.harvest_records x join public.crop_seasons y on y.id=x.crop_season_id where y.crop_id=c.id and y.family_id=p_family and x.deleted_at is null) h on true
 left join lateral(select case when count(distinct x.currency)<=1 then sum(x.total_amount) else null end total from public.sales x join public.crop_seasons y on y.id=x.crop_season_id where y.crop_id=c.id and y.family_id=p_family and x.deleted_at is null) s on true
 where c.family_id=p_family and c.deleted_at is null and (p_crop is null or c.id=p_crop) and public.is_family_member(p_family)
 group by c.id,c.name,h.quantity,s.total
$$;
grant execute on function public.family_finance_summary(uuid,date,date),public.family_crop_report(uuid,uuid) to authenticated;

create function public.family_search(p_family uuid,p_query text,p_limit integer default 30)
returns table(entity_table text,entity_id uuid,record jsonb)
language plpgsql stable security invoker set search_path=public,pg_temp as $$
declare t text; result record;
begin
 if length(trim(coalesce(p_query,'')))<2 or length(p_query)>160 then raise exception using errcode='22023',message='INVALID_SEARCH'; end if;
 if p_limit<1 or p_limit>50 then raise exception using errcode='22023',message='INVALID_LIMIT'; end if;
 if not public.is_family_member(p_family) then raise exception using errcode='42501',message='FAMILY_ACCESS_DENIED'; end if;
 foreach t in array array['fields','crops','crop_seasons','expenses','harvest_records','sales','historical_prices'] loop
  return query execute format('select %L,x.id,to_jsonb(x) from public.%I x where x.family_id=$1 and coalesce(x.deleted_at is null,false) and to_jsonb(x)::text ilike $2 order by x.created_at desc limit $3',t,t)
    using p_family,'%'||p_query||'%',p_limit;
 end loop;
end $$;
create function public.family_year_summary(p_family uuid,p_year integer)
returns table(year integer,revenue numeric,cost numeric,profit numeric,harvest_quantity numeric)
language sql stable security invoker set search_path=public,pg_temp as $$
 select p_year,coalesce((select sum(total_amount) from public.sales where family_id=p_family and deleted_at is null and extract(year from sold_on)=p_year),0),
 coalesce((select sum(amount) from public.expenses where family_id=p_family and deleted_at is null and extract(year from expense_date)=p_year),0),
 case when coalesce((select bool_and(costs_complete and revenue_complete) from public.crop_seasons where family_id=p_family and deleted_at is null and extract(year from start_date)=p_year),false)
 then coalesce((select sum(total_amount) from public.sales where family_id=p_family and deleted_at is null and extract(year from sold_on)=p_year),0)-coalesce((select sum(amount) from public.expenses where family_id=p_family and deleted_at is null and extract(year from expense_date)=p_year),0) else null end,
 (select case when count(distinct unit)<=1 then sum(quantity) else null end from public.harvest_records where family_id=p_family and deleted_at is null and extract(year from harvest_date)=p_year)
 where public.is_family_member(p_family) and p_year between 1900 and 2200
$$;
create function public.family_historical_price_report(p_family uuid,p_crop text default null,p_from date default null,p_to date default null)
returns table(crop_name text,observed_on date,market text,quantity_unit text,price numeric,currency char(3),source text)
language sql stable security invoker set search_path=public,pg_temp as $$
 select crop_name,observed_on,market,quantity_unit,price,currency,source from public.historical_prices
 where family_id=p_family and deleted_at is null and (p_crop is null or crop_name ilike '%'||p_crop||'%')
 and (p_from is null or observed_on>=p_from) and (p_to is null or observed_on<=p_to) and public.is_family_member(p_family)
 order by observed_on desc limit 500
$$;
grant execute on function public.family_search(uuid,text,integer),public.family_year_summary(uuid,integer),public.family_historical_price_report(uuid,text,date,date) to authenticated;
create function public.family_field_report(p_family uuid)
returns table(field_id uuid,field_name text,area numeric,area_unit text,season_count bigint)
language sql stable security invoker set search_path=public,pg_temp as $$
 select f.id,f.name,f.area,f.area_unit,count(cs.id)
 from public.fields f left join public.crop_seasons cs on cs.field_id=f.id and cs.family_id=p_family and cs.deleted_at is null
 where f.family_id=p_family and f.deleted_at is null and public.is_family_member(p_family)
 group by f.id,f.name,f.area,f.area_unit order by f.name
$$;
create function public.family_activity_report(p_family uuid,p_kind text,p_from date default null,p_to date default null,p_limit integer default 500)
returns table(entity_table text,record jsonb)
language plpgsql stable security invoker set search_path=public,pg_temp as $$
declare t text; date_col text;
begin
 if not public.is_family_member(p_family) then raise exception using errcode='42501',message='FAMILY_ACCESS_DENIED'; end if;
 if p_limit<1 or p_limit>500 then raise exception using errcode='22023',message='INVALID_LIMIT'; end if;
 t=case p_kind when 'expenses' then 'expenses' when 'sales' then 'sales' when 'harvest' then 'harvest_records' when 'labor' then 'labor_records' when 'seeds' then 'seed_records' when 'fertilizer' then 'fertilizer_records' when 'machinery' then 'machinery_usage' else null end;
 if t is null then raise exception using errcode='22023',message='REPORT_TYPE_NOT_ALLOWED'; end if;
 date_col=case t when 'expenses' then 'expense_date' when 'sales' then 'sold_on' when 'harvest_records' then 'harvest_date' when 'labor_records' then 'work_date' when 'seed_records' then 'purchased_on' when 'fertilizer_records' then 'applied_on' else 'usage_date' end;
 return query execute format('select %L,to_jsonb(x) from public.%I x where x.family_id=$1 and x.deleted_at is null and ($2 is null or x.%I >= $2) and ($3 is null or x.%I <= $3) order by x.%I desc nulls last limit $4',t,t,date_col,date_col,date_col)
 using p_family,p_from,p_to,p_limit;
end $$;
grant execute on function public.family_field_report(uuid),public.family_activity_report(uuid,text,date,date,integer) to authenticated;

create function public.notify_membership_change() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare evt text; key text; target uuid; fam uuid; uid uuid;
begin
 fam=coalesce(new.family_id,old.family_id); uid=coalesce(new.user_id,old.user_id);
 if tg_op='INSERT' then evt='MEMBER_JOINED';key='family.member_joined';
 elsif old.removed_at is null and new.removed_at is not null then evt='MEMBER_REMOVED';key='family.member_removed';
 elsif old.removed_at is not null and new.removed_at is null then evt='MEMBER_RESTORED';key='family.member_restored';
 else return new; end if;
 for target in select user_id from public.family_members where family_id=fam and removed_at is null and user_id<>uid loop
  insert into public.notifications(family_id,user_id,kind,content_key,content_args) values(fam,target,evt,key,jsonb_build_object('member_user_id',uid,'role',coalesce(new.role,old.role)));
 end loop;
 return coalesce(new,old);
end $$;
create trigger notify_membership_change after insert or update on public.family_members for each row execute function public.notify_membership_change();
create function public.notify_conflict() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare target uuid;
begin
 for target in select user_id from public.family_members where family_id=new.family_id and removed_at is null loop
  insert into public.notifications(family_id,user_id,kind,content_key,content_args) values(new.family_id,target,'SYNC_CONFLICT','sync.conflict',jsonb_build_object('entity_table',new.entity_table,'entity_id',new.entity_id,'conflict_id',new.id));
 end loop;
 return new;
end $$;
create trigger notify_sync_conflict after insert on public.sync_conflicts for each row execute function public.notify_conflict();
create function public.notify_backup_event() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
 insert into public.notifications(family_id,user_id,kind,content_key,content_args)
 select new.family_id,user_id,'BACKUP_READY','backup.ready',jsonb_build_object('backup_id',new.id,'format',new.format)
 from public.family_members where family_id=new.family_id and removed_at is null and role='OWNER';
 return new;
end $$;
create trigger notify_backup_event after insert on public.backup_records for each row execute function public.notify_backup_event();
create function public.notify_record_activity() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare fam uuid; rid uuid; evt text; key text; target uuid; old_del boolean; new_del boolean;
begin
 fam=coalesce(new.family_id,old.family_id); rid=coalesce(new.id,old.id);
 if tg_op='INSERT' then evt='RECORD_ADDED';key='record.added';
 else
  old_del=(to_jsonb(old)->>'deleted_at') is not null; new_del=(to_jsonb(new)->>'deleted_at') is not null;
  if not old_del and new_del then evt='RECORD_DELETED';key='record.deleted';
  elsif old_del and not new_del then evt='RECORD_RESTORED';key='record.restored';
  else evt='RECORD_CHANGED';key='record.changed'; end if;
 end if;
 for target in select user_id from public.family_members where family_id=fam and removed_at is null and user_id<>auth.uid() loop
  insert into public.notifications(family_id,user_id,kind,content_key,content_args) values(fam,target,evt,key,jsonb_build_object('entity_table',tg_table_name,'entity_id',rid));
 end loop;
 return coalesce(new,old);
end $$;
do $$ declare t text; begin
 foreach t in array array['fields','crop_seasons','expenses','sales','harvest_records'] loop
  execute format('create trigger %I_notify after insert or update on public.%I for each row execute function public.notify_record_activity()',t,t);
 end loop;
end $$;

create or replace function public.create_family(p_name text,p_currency char(3) default 'NPR') returns uuid
language plpgsql security definer set search_path=public,pg_temp as $$ declare f uuid;
begin
 if auth.uid() is null then raise exception using errcode='28000',message='AUTH_REQUIRED'; end if;
 insert into public.families(name,currency,created_by) values(trim(p_name),upper(p_currency),auth.uid()) returning id into f;
 insert into public.family_members(family_id,user_id,role,can_view,can_add,can_edit,can_delete,created_by) values(f,auth.uid(),'OWNER',true,true,true,true,auth.uid());
 insert into public.audit_logs(family_id,user_id,entity_table,entity_id,action,new_data) values(f,auth.uid(),'families',f::text,'FAMILY_CREATE',jsonb_build_object('name',trim(p_name),'currency',upper(p_currency)));
 return f;
end $$;
create function public.audit_family_settings() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin insert into public.audit_logs(family_id,user_id,entity_table,entity_id,action,old_data,new_data) values(new.id,auth.uid(),'families',new.id::text,'FAMILY_UPDATE',to_jsonb(old),to_jsonb(new)); return new; end $$;
create trigger family_settings_audit after update on public.families for each row execute function public.audit_family_settings();

-- Database-backed AI rate limit: maximum 15 requests per user in a rolling 10-minute window.
create table public.ai_request_log(id bigint generated always as identity primary key,user_id uuid not null references auth.users(id),family_id uuid not null references public.families(id),created_at timestamptz not null default now());
create index ai_request_log_user_time on public.ai_request_log(user_id,created_at desc);
alter table public.ai_request_log enable row level security;
create function public.consume_ai_quota(p_family uuid) returns boolean language plpgsql security definer set search_path=public,pg_temp as $$
declare n integer; actor uuid:=auth.uid();
begin
 if actor is null or not public.is_family_member(p_family) then raise exception using errcode='42501',message='FAMILY_ACCESS_DENIED'; end if;
 perform pg_advisory_xact_lock(hashtextextended(actor::text||p_family::text,0));
 delete from public.ai_request_log where created_at<now()-interval '1 day';
 select count(*) into n from public.ai_request_log where user_id=actor and created_at>now()-interval '10 minutes';
 if n>=15 then return false; end if;
 insert into public.ai_request_log(user_id,family_id) values(actor,p_family);
 insert into public.audit_logs(family_id,user_id,entity_table,action,metadata) values(p_family,actor,'ai-assistant','AI_REQUEST',jsonb_build_object('window','10m'));
 return true;
end $$;
revoke all on function public.consume_ai_quota(uuid) from public;
grant execute on function public.consume_ai_quota(uuid) to authenticated;

create table public.backup_import_sessions(id uuid primary key default gen_random_uuid(),family_id uuid not null references public.families(id),requested_by uuid not null references auth.users(id),
 payload jsonb not null,status text not null default 'PREVIEWED' check(status in ('PREVIEWED','COMPLETED','FAILED','EXPIRED')),created_at timestamptz not null default now(),expires_at timestamptz not null default now()+interval '30 minutes',completed_at timestamptz);
create index backup_import_sessions_expiry on public.backup_import_sessions(expires_at);
alter table public.backup_import_sessions enable row level security;
create function public.stage_family_import(p_family uuid,p_payload jsonb) returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare sid uuid; t text; rows jsonb; n int; dupe int; existing_n int; counts jsonb='{}'::jsonb; overwrites jsonb='{}'::jsonb;
begin
 if not public.is_family_owner(p_family) then raise exception using errcode='42501',message='OWNER_REQUIRED'; end if;
 if octet_length(p_payload::text)>45000000 or jsonb_typeof(p_payload)<>'object' or coalesce(p_payload->'metadata'->>'family_id','')<>p_family::text then raise exception using errcode='22023',message='INVALID_BACKUP'; end if;
 if coalesce(p_payload->'metadata'->>'database_schema_version','')<>'202611140002' or coalesce((p_payload->'metadata'->>'backup_version')::int,0)<>1 then raise exception using errcode='22023',message='UNSUPPORTED_SCHEMA_VERSION'; end if;
 foreach t in array array['fields','crops','expense_categories','crop_seasons','seed_records','fertilizer_records','labor_records','machinery_usage','expenses','harvest_records','sales','historical_prices'] loop
  rows=coalesce(p_payload->t,'[]'::jsonb); if jsonb_typeof(rows)<>'array' then raise exception using errcode='22023',message='INVALID_RECORD_ARRAY:'||t; end if;
  select count(*) into n from jsonb_array_elements(rows) x;
  if n>50000 then raise exception using errcode='22023',message='IMPORT_ROW_LIMIT'; end if;
  if exists(select 1 from jsonb_array_elements(rows) x where x->>'family_id'<>p_family::text or coalesce(x->>'id','') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$') then raise exception using errcode='22023',message='INVALID_OR_CROSS_FAMILY_ROW:'||t; end if;
  select count(*) into dupe from (select x->>'id' from jsonb_array_elements(rows) x group by x->>'id' having count(*)>1) d;
  if dupe>0 then raise exception using errcode='22023',message='DUPLICATE_IDS:'||t; end if;
  execute format('select count(*) from jsonb_array_elements($1) x join public.%I existing on existing.id=(x->>''id'')::uuid where existing.family_id<>$2',t) into existing_n using rows,p_family;
  if existing_n>0 then raise exception using errcode='23505',message='SERVER_ID_BELONGS_TO_ANOTHER_FAMILY:'||t; end if;
  execute format('select count(*) from jsonb_array_elements($1) x join public.%I existing on existing.id=(x->>''id'')::uuid and existing.family_id=$2',t) into existing_n using rows,p_family;
  overwrites=overwrites||jsonb_build_object(t,existing_n);
  counts=counts||jsonb_build_object(t,n);
 end loop;
 insert into public.backup_import_sessions(family_id,requested_by,payload) values(p_family,auth.uid(),p_payload) returning id into sid;
 insert into public.audit_logs(family_id,user_id,entity_table,entity_id,action,metadata) values(p_family,auth.uid(),'backup_import_sessions',sid::text,'IMPORT_PREVIEW',jsonb_build_object('counts',counts));
 return jsonb_build_object('import_id',sid,'status','PREVIEWED','counts',counts,'matching_server_ids_to_update',overwrites,'expires_at',now()+interval '30 minutes','mode','MERGE_BY_SERVER_ID');
end $$;

create function public.confirm_family_import(p_import uuid) returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare sess public.backup_import_sessions%rowtype; t text; columns text[]; col text; inscols text; selcols text; updates text; rows_count int; counts jsonb='{}'::jsonb; dest text;
begin
 select * into sess from public.backup_import_sessions where id=p_import for update;
 if not found then raise exception using errcode='P0002',message='IMPORT_NOT_FOUND'; end if;
 if not public.is_family_owner(sess.family_id) then raise exception using errcode='42501',message='OWNER_REQUIRED'; end if;
 if sess.requested_by<>auth.uid() then raise exception using errcode='42501',message='IMPORT_REQUESTER_MISMATCH'; end if;
 if sess.status<>'PREVIEWED' or sess.expires_at<=now() then raise exception using errcode='22023',message='IMPORT_PREVIEW_EXPIRED_OR_USED'; end if;
 perform set_config('farm.allow_import','yes',true);
 foreach t in array array['fields','crops','expense_categories','crop_seasons','seed_records','fertilizer_records','labor_records','machinery_usage','expenses','harvest_records','sales','historical_prices'] loop
  columns=case t
   when 'fields' then array['name','area','area_unit','location','notes','local_id']
   when 'crops' then array['name','variety','notes','local_id']
   when 'expense_categories' then array['name','is_custom','local_id']
   when 'crop_seasons' then array['field_id','crop_id','season_name','purpose','area','area_unit','start_date','end_date','notes','costs_complete','revenue_complete','local_id']
   when 'seed_records' then array['crop_season_id','seed_name','quantity','unit','amount','currency','purchased_on','notes','local_id']
   when 'fertilizer_records' then array['crop_season_id','product_name','quantity','unit','amount','currency','applied_on','notes','local_id']
   when 'labor_records' then array['crop_season_id','work_date','worker_count','hours','amount','currency','description','local_id']
   when 'machinery_usage' then array['crop_season_id','service_name','provider','usage_date','quantity','unit','amount','currency','notes','local_id']
   when 'expenses' then array['category_id','crop_season_id','expense_date','description','quantity','unit_price','amount','currency','notes','local_id']
   when 'harvest_records' then array['crop_season_id','harvest_date','quantity','unit','grade','notes','local_id']
   when 'sales' then array['harvest_record_id','crop_season_id','sold_on','quantity','unit','unit_price','total_amount','currency','buyer','notes','local_id']
   else array['crop_id','crop_name','observed_on','market','quantity_unit','price','currency','source','local_id'] end;
  inscols='id,family_id'; selcols='(r).id,(r).family_id'; updates='';
  foreach col in array columns loop
   inscols=inscols||','||quote_ident(col); selcols=selcols||',(r).'||quote_ident(col);
   updates=updates||case when updates='' then '' else ',' end||format('%I=excluded.%I',col,col);
  end loop;
  dest=format('public.%I',t);
  execute format('insert into %s as target (%s) select %s from jsonb_populate_recordset(null::%s,$1->%L) r where (r).family_id=$2 on conflict(id) do update set %s where target.family_id=$2',dest,inscols,selcols,dest,t,updates)
   using sess.payload,sess.family_id;
  get diagnostics rows_count=row_count; counts=counts||jsonb_build_object(t,rows_count);
 end loop;
 update public.backup_import_sessions set status='COMPLETED',completed_at=now(),payload='{}'::jsonb where id=p_import;
 insert into public.audit_logs(family_id,user_id,entity_table,entity_id,action,metadata) values(sess.family_id,auth.uid(),'backup_import_sessions',p_import::text,'IMPORT_CONFIRM',jsonb_build_object('counts',counts));
 return jsonb_build_object('import_id',p_import,'status','COMPLETED','counts',counts);
end $$;
revoke all on function public.stage_family_import(uuid,jsonb),public.confirm_family_import(uuid) from public;
grant execute on function public.stage_family_import(uuid,jsonb),public.confirm_family_import(uuid) to authenticated;

-- Private object storage; only family owners can create/read objects under their family UUID prefix.
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('family-backups','family-backups',false,52428800,array['application/zip','text/csv','application/vnd.openxmlformats-officedocument.spreadsheetml.sheet','application/pdf'])
on conflict(id) do update set public=false,file_size_limit=52428800;
create policy family_backup_object_read on storage.objects for select to authenticated using(bucket_id='family-backups' and public.is_family_owner((storage.foldername(name))[1]::uuid));
create policy family_backup_object_add on storage.objects for insert to authenticated with check(bucket_id='family-backups' and public.is_family_owner((storage.foldername(name))[1]::uuid));
create policy family_backup_object_delete on storage.objects for delete to authenticated using(bucket_id='family-backups' and public.is_family_owner((storage.foldername(name))[1]::uuid));
create trigger backup_records_audit after insert or update or delete on public.backup_records for each row execute function public.audit_row();

-- Narrow profile and notification writes to fields the user is allowed to manage. Membership mutations stay RPC-only.
revoke update on public.profiles from authenticated;
grant update(display_name,phone,preferred_language) on public.profiles to authenticated;
revoke insert,update,delete on public.family_members from authenticated;
revoke update on public.notifications from authenticated;
grant update(read_at) on public.notifications to authenticated;

-- Historical-price rows must never reference a crop in another family.
create function public.check_price_family() returns trigger language plpgsql set search_path=public,pg_temp as $$
begin if new.crop_id is not null and not exists(select 1 from public.crops where id=new.crop_id and family_id=new.family_id) then raise exception using errcode='23503',message='CROSS_FAMILY_REFERENCE'; end if; return new; end $$;
create trigger historical_price_family_link before insert or update on public.historical_prices for each row execute function public.check_price_family();

create or replace function public.family_season_summary(p_family uuid,p_from date default null,p_to date default null)
returns table(season_id uuid,crop_name text,field_name text,harvest_quantity numeric,sales_total numeric,expense_total numeric,known_profit numeric)
language sql stable security invoker set search_path=public,pg_temp as $$
 select cs.id,c.name,f.name,coalesce(h.qty,0),case when s.currency_count<=1 then s.total else null end,
 case when e.currency_count<=1 then e.total else null end,
 case when cs.costs_complete and cs.revenue_complete and s.currency_count<=1 and e.currency_count<=1
   and (s.currency_count=0 or e.currency_count=0 or s.currency=e.currency) then coalesce(s.total,0)-coalesce(e.total,0) else null end
 from public.crop_seasons cs join public.crops c on c.id=cs.crop_id join public.fields f on f.id=cs.field_id
 left join lateral(select sum(quantity) qty from public.harvest_records x where x.crop_season_id=cs.id and x.deleted_at is null) h on true
 left join lateral(select sum(total_amount) total,count(distinct currency) currency_count,min(currency) currency from public.sales x where x.crop_season_id=cs.id and x.deleted_at is null) s on true
 left join lateral(select sum(amount) total,count(distinct currency) currency_count,min(currency) currency from public.expenses x where x.crop_season_id=cs.id and x.deleted_at is null) e on true
 where cs.family_id=p_family and cs.deleted_at is null and public.is_family_member(p_family)
 and (p_from is null or cs.start_date>=p_from) and (p_to is null or cs.start_date<=p_to)
$$;
create or replace function public.family_year_summary(p_family uuid,p_year integer)
returns table(year integer,revenue numeric,cost numeric,profit numeric,harvest_quantity numeric)
language sql stable security invoker set search_path=public,pg_temp as $$
 with rev as(select sum(total_amount) total,count(distinct currency) currencies,min(currency) currency from public.sales where family_id=p_family and deleted_at is null and extract(year from sold_on)=p_year),
 costrows as(select sum(amount) total,count(distinct currency) currencies,min(currency) currency from public.expenses where family_id=p_family and deleted_at is null and extract(year from expense_date)=p_year),
 complete as(select coalesce(bool_and(costs_complete and revenue_complete),false) yes from public.crop_seasons where family_id=p_family and deleted_at is null and extract(year from start_date)=p_year)
 select p_year,case when rev.currencies<=1 then coalesce(rev.total,0) else null end,case when costrows.currencies<=1 then coalesce(costrows.total,0) else null end,
 case when complete.yes and rev.currencies<=1 and costrows.currencies<=1 and (rev.currencies=0 or costrows.currencies=0 or rev.currency=costrows.currency) then coalesce(rev.total,0)-coalesce(costrows.total,0) else null end,
 coalesce((select sum(quantity) from public.harvest_records where family_id=p_family and deleted_at is null and extract(year from harvest_date)=p_year),0)
 from rev,costrows,complete where public.is_family_member(p_family) and p_year between 1900 and 2200
$$;

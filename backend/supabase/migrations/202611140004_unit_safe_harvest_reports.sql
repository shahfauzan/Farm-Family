create or replace function public.check_family_links() returns trigger language plpgsql set search_path=public,pg_temp as $$
begin
 if tg_table_name='crop_seasons' then
   if not exists(select 1 from public.fields where id=new.field_id and family_id=new.family_id)
      or not exists(select 1 from public.crops where id=new.crop_id and family_id=new.family_id) then
     raise exception using errcode='23503',message='CROSS_FAMILY_REFERENCE';
   end if;
 elsif tg_table_name in ('seed_records','fertilizer_records','labor_records','machinery_usage','expenses','harvest_records','sales') then
   if new.crop_season_id is not null and not exists(select 1 from public.crop_seasons where id=new.crop_season_id and family_id=new.family_id) then
     raise exception using errcode='23503',message='CROSS_FAMILY_REFERENCE';
   end if;
   if tg_table_name='expenses' then
     if new.category_id is not null and not exists(select 1 from public.expense_categories where id=new.category_id and family_id=new.family_id) then
       raise exception using errcode='23503',message='CROSS_FAMILY_REFERENCE';
     end if;
   end if;
   if tg_table_name='sales' then
     if new.harvest_record_id is not null and not exists(select 1 from public.harvest_records where id=new.harvest_record_id and family_id=new.family_id) then
       raise exception using errcode='23503',message='CROSS_FAMILY_REFERENCE';
     end if;
   end if;
 end if;
 return new;
end $$;

create or replace function public.family_season_summary(p_family uuid,p_from date default null,p_to date default null)
returns table(season_id uuid,crop_name text,field_name text,harvest_quantity numeric,sales_total numeric,expense_total numeric,known_profit numeric)
language sql stable security invoker set search_path=public,pg_temp as $$
 select cs.id,c.name,f.name,case when h.unit_count>1 then null else coalesce(h.qty,0) end,
 case when s.currency_count<=1 then s.total else null end,
 case when e.currency_count<=1 then e.total else null end,
 case when cs.costs_complete and cs.revenue_complete and s.currency_count<=1 and e.currency_count<=1
   and (s.currency_count=0 or e.currency_count=0 or s.currency=e.currency) then coalesce(s.total,0)-coalesce(e.total,0) else null end
 from public.crop_seasons cs join public.crops c on c.id=cs.crop_id join public.fields f on f.id=cs.field_id
 left join lateral(select sum(quantity) qty,count(distinct unit) unit_count from public.harvest_records x where x.crop_season_id=cs.id and x.deleted_at is null) h on true
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
 complete as(select coalesce(bool_and(costs_complete and revenue_complete),false) yes from public.crop_seasons where family_id=p_family and deleted_at is null and extract(year from start_date)=p_year),
 harvest as(select case when count(distinct unit)>1 then null else coalesce(sum(quantity),0) end total from public.harvest_records where family_id=p_family and deleted_at is null and extract(year from harvest_date)=p_year)
 select p_year,case when rev.currencies<=1 then coalesce(rev.total,0) else null end,case when costrows.currencies<=1 then coalesce(costrows.total,0) else null end,
 case when complete.yes and rev.currencies<=1 and costrows.currencies<=1 and (rev.currencies=0 or costrows.currencies=0 or rev.currency=costrows.currency) then coalesce(rev.total,0)-coalesce(costrows.total,0) else null end,
 harvest.total
 from rev,costrows,complete,harvest where public.is_family_member(p_family) and p_year between 1900 and 2200
$$;

-- Older installations may still have the original check_family_links body,
-- which referenced expense-only columns in a combined boolean expression.
-- Replace it with table-specific branches so NEW only reads columns present
-- on that table.
create or replace function public.check_family_links()
returns trigger
language plpgsql
set search_path=public,pg_temp
as $$
begin
  if tg_table_name='crop_seasons' then
    if not exists (
      select 1 from public.fields
      where id=new.field_id and family_id=new.family_id
    ) or not exists (
      select 1 from public.crops
      where id=new.crop_id and family_id=new.family_id
    ) then
      raise exception using errcode='23503',message='CROSS_FAMILY_REFERENCE';
    end if;
  elsif tg_table_name in ('seed_records','fertilizer_records','labor_records','machinery_usage','expenses','harvest_records','sales') then
    if new.crop_season_id is not null and not exists (
      select 1 from public.crop_seasons
      where id=new.crop_season_id and family_id=new.family_id
    ) then
      raise exception using errcode='23503',message='CROSS_FAMILY_REFERENCE';
    end if;
    if tg_table_name='expenses' then
      if new.category_id is not null and not exists (
        select 1 from public.expense_categories
        where id=new.category_id and family_id=new.family_id
      ) then
        raise exception using errcode='23503',message='CROSS_FAMILY_REFERENCE';
      end if;
    end if;
    if tg_table_name='sales' then
      if new.harvest_record_id is not null and not exists (
        select 1 from public.harvest_records
        where id=new.harvest_record_id and family_id=new.family_id
      ) then
        raise exception using errcode='23503',message='CROSS_FAMILY_REFERENCE';
      end if;
    end if;
  end if;
  return new;
end
$$;

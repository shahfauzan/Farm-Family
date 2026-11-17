-- Family preferences are saved by a narrowly scoped owner-only RPC.
create function public.update_family_settings(
  p_family uuid,
  p_name text,
  p_currency text,
  p_default_area_unit text
) returns jsonb
language plpgsql security definer set search_path=public,pg_temp as $$
declare updated public.families%rowtype;
begin
  if not public.is_family_owner(p_family) then
    raise exception using errcode='42501',message='OWNER_REQUIRED';
  end if;
  if p_name is null or length(trim(p_name)) not between 1 and 160 then
    raise exception using errcode='22023',message='INVALID_FAMILY_NAME';
  end if;
  if p_currency is distinct from 'NPR' then
    raise exception using errcode='22023',message='FAMILY_CURRENCY_MUST_BE_NPR';
  end if;
  if p_default_area_unit not in ('Bigha','Kattha','Dhur','Acre','Hectare','Square metre') then
    raise exception using errcode='22023',message='INVALID_AREA_UNIT';
  end if;
  update public.families
    set name=trim(p_name),currency='NPR',
        settings=settings||jsonb_build_object('default_area_unit',p_default_area_unit),
        updated_at=now(),record_version=record_version+1
    where id=p_family and deleted_at is null
    returning * into updated;
  if not found then raise exception using errcode='P0002',message='FAMILY_NOT_FOUND'; end if;
  return jsonb_build_object('id',updated.id,'name',updated.name,'currency',updated.currency,'settings',updated.settings,'record_version',updated.record_version);
end $$;

revoke all on function public.update_family_settings(uuid,text,text,text) from public,anon;
grant execute on function public.update_family_settings(uuid,text,text,text) to authenticated;

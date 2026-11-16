-- Return member names without exposing profile rows through a broad table join.
create or replace function public.family_member_roster(p_family uuid, p_include_removed boolean default false)
returns table(user_id uuid, display_name text, role public.family_role,
              can_view boolean, can_add boolean, can_edit boolean, can_delete boolean,
              removed_at timestamptz)
language plpgsql stable security invoker set search_path=public,pg_temp as $$
begin
  if not public.is_family_member(p_family) then
    raise exception using errcode='42501', message='FAMILY_ACCESS_DENIED';
  end if;
  if p_include_removed and not public.is_family_owner(p_family) then
    raise exception using errcode='42501', message='OWNER_REQUIRED';
  end if;
  return query
    select m.user_id, p.display_name, m.role, m.can_view, m.can_add,
           m.can_edit, m.can_delete, m.removed_at
    from public.family_members m
    left join public.profiles p on p.id=m.user_id
    where m.family_id=p_family and (p_include_removed or m.removed_at is null)
    order by m.joined_at asc;
end $$;
revoke all on function public.family_member_roster(uuid,boolean) from public,anon;
grant execute on function public.family_member_roster(uuid,boolean) to authenticated;

-- Let a member read names of people in the same family without exposing the
-- profiles table broadly; removed members remain visible only to an owner.
create policy profile_family_select on public.profiles for select to authenticated
using (exists (
  select 1
  from public.family_members mine
  join public.family_members target on target.family_id=mine.family_id
  where mine.user_id=auth.uid()
    and mine.removed_at is null and mine.can_view
    and target.user_id=profiles.id
    and (target.removed_at is null or mine.role='OWNER')
));

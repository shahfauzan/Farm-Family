-- Supabase installs pgcrypto in `extensions`; privileged invitation RPCs use a
-- locked search_path and must therefore qualify crypto functions explicitly.
create or replace function public.create_family_invitation(
  p_family uuid,
  p_role public.family_role,
  p_valid_for interval default interval '7 days'
)
returns table(invitation_id uuid, token text, expires_at timestamptz)
language plpgsql security definer set search_path=public,pg_temp as $$
declare raw_token text; iid uuid; expiry timestamptz;
begin
  if not public.is_family_owner(p_family) then
    raise exception using errcode='42501',message='OWNER_REQUIRED';
  end if;
  if p_role='OWNER' or p_valid_for<=interval '0 seconds' or p_valid_for>interval '30 days' then
    raise exception using errcode='22023',message='INVALID_INVITATION';
  end if;
  raw_token=encode(extensions.gen_random_bytes(32),'hex');
  expiry=now()+p_valid_for;
  insert into public.family_invitations(family_id,inviter_id,intended_role,token_hash,expires_at)
  values(p_family,auth.uid(),p_role,extensions.digest(raw_token,'sha256'),expiry)
  returning id into iid;
  insert into public.audit_logs(family_id,user_id,entity_table,entity_id,action,new_data)
  values(p_family,auth.uid(),'family_invitations',iid::text,'INVITE_CREATE',jsonb_build_object('role',p_role,'expires_at',expiry));
  return query select iid,raw_token,expiry;
end $$;

create or replace function public.accept_family_invitation(p_token text)
returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare inv public.family_invitations%rowtype;
begin
  if auth.uid() is null then raise exception using errcode='28000',message='AUTH_REQUIRED'; end if;
  select * into inv from public.family_invitations
    where token_hash=extensions.digest(p_token,'sha256') for update;
  if not found or inv.status<>'PENDING' or inv.expires_at<=now() then
    raise exception using errcode='22023',message='INVITATION_INVALID_OR_EXPIRED';
  end if;
  update public.family_invitations set status='ACCEPTED',accepted_by=auth.uid(),accepted_at=now() where id=inv.id;
  insert into public.family_members(family_id,user_id,role,can_view,can_add,can_edit,can_delete,created_by)
  values(inv.family_id,auth.uid(),inv.intended_role,true,inv.intended_role='EDITOR',inv.intended_role='EDITOR',false,inv.inviter_id)
  on conflict(family_id,user_id) do update set removed_at=null,role=excluded.role,can_view=true,
    can_add=excluded.can_add,can_edit=excluded.can_edit,can_delete=false;
  insert into public.audit_logs(family_id,user_id,entity_table,entity_id,action,new_data)
  values(inv.family_id,auth.uid(),'family_members',auth.uid()::text,'INVITE_ACCEPT',jsonb_build_object('role',inv.intended_role));
  return inv.family_id;
end $$;

revoke all on function public.create_family_invitation(uuid,public.family_role,interval),
  public.accept_family_invitation(text) from public,anon,authenticated;
grant execute on function public.create_family_invitation(uuid,public.family_role,interval),
  public.accept_family_invitation(text) to authenticated;

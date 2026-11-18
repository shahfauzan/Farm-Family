alter table public.family_invitations
  add column if not exists qr_expires_at timestamptz;

create table public.invitation_code_attempts (
  user_id uuid primary key references auth.users(id) on delete cascade,
  window_started_at timestamptz not null default now(),
  attempts integer not null default 0 check (attempts >= 0)
);
alter table public.invitation_code_attempts enable row level security;
revoke all on public.invitation_code_attempts from public, anon, authenticated;

drop function public.create_family_invitation(uuid, public.family_role, interval);
create function public.create_family_invitation(
  p_family uuid,
  p_role public.family_role,
  p_valid_for interval default interval '7 days'
)
returns table(invitation_id uuid, token text, code text, qr_expires_at timestamptz, expires_at timestamptz)
language plpgsql security definer set search_path=public,pg_temp as $$
declare
  raw_token text;
  raw_code text;
  iid uuid;
  expiry timestamptz;
  qr_expiry timestamptz;
  code_number bigint;
begin
  if not public.is_family_owner(p_family) then
    raise exception using errcode='42501',message='OWNER_REQUIRED';
  end if;
  if p_role='OWNER' or p_valid_for<=interval '0 seconds' or p_valid_for>interval '30 days' then
    raise exception using errcode='22023',message='INVALID_INVITATION';
  end if;

  expiry := now() + p_valid_for;
  qr_expiry := clock_timestamp() + interval '1 minute';
  loop
    code_number := ('x' || encode(extensions.gen_random_bytes(4), 'hex'))::bit(32)::bigint;
    if code_number >= 4200000000 then
      continue;
    end if;
    raw_code := lpad((code_number % 100000000)::text, 8, '0');
    raw_token := encode(extensions.gen_random_bytes(32), 'hex');
    begin
      insert into public.family_invitations(
        family_id, inviter_id, intended_role, token_hash, invite_code_hash, qr_expires_at, expires_at
      )
      values(
        p_family, auth.uid(), p_role,
        extensions.digest(raw_token, 'sha256'),
        extensions.digest(raw_code, 'sha256'),
        qr_expiry, expiry
      )
      returning id into iid;
      exit;
    exception when unique_violation then
      null;
    end;
  end loop;

  insert into public.audit_logs(family_id,user_id,entity_table,entity_id,action,new_data)
  values(p_family,auth.uid(),'family_invitations',iid::text,'INVITE_CREATE',
    jsonb_build_object('role',p_role,'expires_at',expiry,'qr_expires_at',qr_expiry));
  return query select iid,raw_token,raw_code,qr_expiry,expiry;
end $$;

create or replace function public.accept_family_invitation(p_token text)
returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare
  inv public.family_invitations%rowtype;
  code_attempts public.invitation_code_attempts%rowtype;
  is_code boolean := p_token ~ '^[0-9]{8}$';
begin
  if auth.uid() is null then
    raise exception using errcode='28000',message='AUTH_REQUIRED';
  end if;

  if is_code then
    insert into public.invitation_code_attempts(user_id) values(auth.uid())
      on conflict(user_id) do nothing;
    select * into code_attempts
      from public.invitation_code_attempts
      where user_id=auth.uid()
      for update;
    if code_attempts.window_started_at <= now() - interval '1 minute' then
      update public.invitation_code_attempts
        set window_started_at=now(),attempts=0
        where user_id=auth.uid();
      code_attempts.attempts := 0;
    end if;
    if code_attempts.attempts >= 5 then
      return null;
    end if;
    update public.invitation_code_attempts
      set attempts=attempts+1
      where user_id=auth.uid();

    select * into inv
      from public.family_invitations
      where invite_code_hash=extensions.digest(p_token,'sha256')
      for update;
    if not found or inv.status<>'PENDING' or inv.expires_at<=now() then
      return null;
    end if;
    update public.invitation_code_attempts
      set attempts=0,window_started_at=now()
      where user_id=auth.uid();
  else
    select * into inv
      from public.family_invitations
      where token_hash=extensions.digest(p_token,'sha256')
      for update;
    if not found or inv.status<>'PENDING' or inv.expires_at<=now()
      or (inv.qr_expires_at is not null and inv.qr_expires_at<=now()) then
      raise exception using errcode='22023',message='INVITATION_INVALID_OR_EXPIRED';
    end if;
  end if;

  update public.family_invitations
    set status='ACCEPTED',accepted_by=auth.uid(),accepted_at=now()
    where id=inv.id;
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

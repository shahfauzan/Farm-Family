-- Restrict privileged public RPCs after the original migrations relied on
-- Supabase's default function grants to anon/authenticated.
begin;

alter default privileges for role postgres in schema public
  revoke execute on functions from public, anon, authenticated;

do $$
declare
  target record;
begin
  for target in
    select p.oid::regprocedure as signature
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.prosecdef
  loop
    execute format(
      'revoke execute on function %s from public, anon, authenticated',
      target.signature
    );
  end loop;
end;
$$;

-- These operations are called by authenticated clients and independently
-- validate auth.uid() plus family ownership/permissions in their bodies.
grant execute on function public.accept_family_invitation(text),
  public.confirm_family_import(uuid),
  public.consume_ai_quota(uuid),
  public.create_family(text, character),
  public.create_family_invitation(uuid, public.family_role, interval),
  public.has_family_permission(uuid, text),
  public.is_family_member(uuid),
  public.is_family_owner(uuid),
  public.remove_family_member(uuid, uuid),
  public.restore_family_member(uuid, uuid),
  public.restore_record(regclass, uuid, uuid, bigint),
  public.revoke_family_invitation(uuid),
  public.set_family_member(uuid, uuid, public.family_role, boolean, boolean, boolean, boolean),
  public.soft_delete_record(regclass, uuid, uuid, bigint),
  public.stage_family_import(uuid, jsonb),
  public.sync_update(regclass, uuid, uuid, bigint, jsonb)
to authenticated;

commit;

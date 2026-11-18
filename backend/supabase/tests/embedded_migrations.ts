import { PGlite } from 'npm:@electric-sql/pglite@0.5.16';

const db = new PGlite();
const q = (sql: string, params?: unknown[]) => db.query(sql, params) as Promise<any>;
try {
  await db.exec(`
    create role anon;
    create role authenticated;
    create role service_role;
    create schema auth;
    create schema extensions;
    create table auth.users(id uuid primary key, raw_user_meta_data jsonb not null default '{}'::jsonb);
    create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
    create schema storage;
    create table storage.buckets(id text primary key,name text,public boolean,file_size_limit bigint,allowed_mime_types text[]);
    create table storage.objects(bucket_id text,name text);
    alter table storage.objects enable row level security;
    create function storage.foldername(text) returns text[] language sql immutable as $$ select string_to_array($1,'/') $$;
    create function public.digest(bytea,text) returns bytea language sql immutable as $$ select convert_to(md5(encode($1,'hex')),'UTF8') $$;
    create function public.digest(text,text) returns bytea language sql immutable as $$ select public.digest(convert_to($1,'UTF8'),$2) $$;
    create function public.gen_random_bytes(integer) returns bytea language sql volatile as $$ select decode(substr(repeat(md5(random()::text),ceil($1/16.0)::integer),1,$1*2),'hex') $$;
    create function extensions.digest(bytea,text) returns bytea language sql immutable as $$ select public.digest($1,$2) $$;
    create function extensions.digest(text,text) returns bytea language sql immutable as $$ select public.digest($1,$2) $$;
    create function extensions.gen_random_bytes(integer) returns bytea language sql volatile as $$ select public.gen_random_bytes($1) $$;
  `);
  for (const name of ['202611140001_farm_family.sql','202611140002_secure_operations.sql','202611140004_unit_safe_harvest_reports.sql','202611140005_finance_currency_validation.sql','20261117032903_family_member_roster.sql','20261117072159_harden_security_definer_privileges.sql','20261117073707_restore_weather_quota_schema.sql','20261117084500_family_area_preferences.sql','20261117090000_reload_postgrest_schema.sql','20261117100000_seed_expense_categories.sql','20261117110000_fix_family_link_category_id_trigger.sql','20261117120000_fix_invitation_crypto_search_path.sql','20261118100000_qr_and_code_invitations.sql']) {
    const source = await Deno.readTextFile(`supabase/migrations/${name}`);
    const sql = source.replace(/^create extension if not exists pgcrypto;\s*/m, '-- pgcrypto stubbed for embedded migration syntax validation.\n');
    await db.exec(sql);
    console.log(`PASS migration DDL: ${name}`);
  }
  const rpcAcl = (await q(`
    select
      has_function_privilege('anon','public.create_family(text,character)','EXECUTE') as anon_create,
      has_function_privilege('authenticated','public.create_family(text,character)','EXECUTE') as user_create,
      has_function_privilege('authenticated','public.is_family_member(uuid)','EXECUTE') as policy_helper,
      has_function_privilege('authenticated','public.audit_row()','EXECUTE') as trigger_only,
      has_function_privilege('anon','public.update_family_settings(uuid,text,text,text)','EXECUTE') as anon_family_settings,
      has_function_privilege('authenticated','public.update_family_settings(uuid,text,text,text)','EXECUTE') as user_family_settings
  `)).rows[0];
  if (rpcAcl.anon_create || !rpcAcl.user_create || !rpcAcl.policy_helper || rpcAcl.trigger_only || rpcAcl.anon_family_settings || !rpcAcl.user_family_settings) {
    throw new Error(`Unexpected SECURITY DEFINER function grants: ${JSON.stringify(rpcAcl)}`);
  }
  console.log('PASS SECURITY DEFINER RPC grants');
  const a='11111111-1111-4111-8111-111111111111';
  const b='22222222-2222-4222-8222-222222222222';
  const c='33333333-3333-4333-8333-333333333333';
  await db.exec(`insert into auth.users(id) values ('${a}'),('${b}'),('${c}'); grant usage on schema auth to authenticated; grant execute on function auth.uid() to authenticated; grant usage on schema storage to authenticated; grant all on all tables in schema public to authenticated; grant usage,select on all sequences in schema public to authenticated; grant select on storage.objects to authenticated;`);
  const actor=async(id:string)=>q(`select set_config('request.jwt.claim.sub',$1,false)`,[id]);
  const expectReject=async(label:string,fn:()=>Promise<unknown>)=>{let rejected=false;try{await fn()}catch{rejected=true}if(!rejected)throw new Error(`Expected rejection: ${label}`);console.log(`PASS ${label}`)};
  await actor(a); await db.exec('set role authenticated');
  const familyA=(await q(`select public.create_family('Family A','NPR') id`)).rows[0].id as string;
  const seededCategories=(await q(`select count(*)::int n from public.expense_categories where family_id=$1 and is_custom=false`,[familyA])).rows[0].n;
  if(seededCategories!==10)throw new Error(`New family did not receive its 10 default expense categories (found ${seededCategories})`);
  console.log('PASS new families receive usable default expense categories');
  await db.exec('reset role'); await actor(b); await db.exec('set role authenticated');
  const familyB=(await q(`select public.create_family('Family B','NPR') id`)).rows[0].id as string;
  await db.exec('reset role'); await actor(a); await db.exec('set role authenticated');
  await q(`select public.update_family_settings($1,'Family A Updated','NPR','Kattha')`,[familyA]);
  const savedSettings=(await q(`select name,settings->>'default_area_unit' area_unit,currency from public.families where id=$1`,[familyA])).rows[0];
  if(savedSettings.name!=='Family A Updated'||savedSettings.area_unit!=='Kattha'||savedSettings.currency!=='NPR')throw new Error('Family preferences did not persist');
  console.log('PASS owner family settings persist while preserving NPR');
  await expectReject('non-owner cannot update family settings',()=>q(`select public.update_family_settings($1,'Not Allowed','NPR','Acre')`,[familyB]));
  for(let i=0;i<60;i++){
    const allowed=(await q(`select public.consume_weather_quota($1) allowed`,[familyA])).rows[0].allowed;
    if(!allowed)throw new Error(`Weather quota rejected request ${i+1} below its limit`);
  }
  const weatherLimited=(await q(`select public.consume_weather_quota($1) allowed`,[familyA])).rows[0].allowed;
  if(weatherLimited)throw new Error('Weather quota did not limit the 61st request');
  await expectReject('weather quota rejects non-member family access',()=>q(`select public.consume_weather_quota($1)`,[familyB]));
  console.log('PASS weather quota allows 60 requests/hour, limits the next request, and enforces family membership');
  await db.exec('reset role'); await actor(b); await db.exec('set role authenticated');
  const fieldB=(await q(`insert into public.fields(family_id,name) values($1,'B field') returning id`,[familyB])).rows[0].id as string;
  const cropB=(await q(`insert into public.crops(family_id,name) values($1,'B crop') returning id`,[familyB])).rows[0].id as string;
  await q(`select * from public.create_family_invitation($1,'EDITOR',interval '1 day')`,[familyB]);
  await db.exec('reset role');
  await q(`insert into public.notifications(family_id,user_id,kind,content_key) values($1,$2,'TEST','test')`,[familyB,b]);
  await q(`insert into public.backup_records(family_id,requested_by,storage_path,format,status) values($1,$2,$3,'ZIP','COMPLETED')`,[familyB,b,`${familyB}/backup.zip`]);
  await q(`insert into public.audit_logs(family_id,user_id,entity_table,action) values($1,$2,'fields','TEST')`,[familyB,b]);
  await q(`insert into storage.objects(bucket_id,name) values('family-backups',$1)`,[`${familyB}/backup.zip`]);
  await actor(a); await db.exec('set role authenticated');
  const field=(await q(`insert into public.fields(family_id,name) values($1,'A field') returning id`,[familyA])).rows[0].id as string;
  const cropA=(await q(`insert into public.crops(family_id,name) values($1,'A crop') returning id`,[familyA])).rows[0].id as string;
  const seasonA=(await q(`insert into public.crop_seasons(family_id,field_id,crop_id,season_name,start_date) values($1,$2,$3,'2026 season','2026-01-01') returning id`,[familyA,field,cropA])).rows[0].id as string;
  await q(`insert into public.harvest_records(family_id,crop_season_id,harvest_date,quantity,unit) values($1,$2,'2026-10-01',2,'kg'),($1,$2,'2026-10-02',3,'kg')`,[familyA,seasonA]);
  const seasonHarvest=(await q(`select harvest_quantity from public.family_season_summary($1,null,null) where season_id=$2`,[familyA,seasonA])).rows[0].harvest_quantity;
  const yearHarvest=(await q(`select harvest_quantity from public.family_year_summary($1,2026)`,[familyA])).rows[0].harvest_quantity;
  if(Number(seasonHarvest)!==5||Number(yearHarvest)!==5)throw new Error('Same-unit harvest aggregation failed');
  await q(`insert into public.harvest_records(family_id,crop_season_id,harvest_date,quantity,unit) values($1,$2,'2026-10-03',1,'quintal')`,[familyA,seasonA]);
  const mixedSeasonHarvest=(await q(`select harvest_quantity from public.family_season_summary($1,null,null) where season_id=$2`,[familyA,seasonA])).rows[0].harvest_quantity;
  const mixedYearHarvest=(await q(`select harvest_quantity from public.family_year_summary($1,2026)`,[familyA])).rows[0].harvest_quantity;
  if(mixedSeasonHarvest!==null||mixedYearHarvest!==null)throw new Error('Harvest reports combined incompatible units');
  console.log('PASS season/year harvest reports avoid combining different measurement units');
  await q(`update public.crop_seasons set costs_complete=true,revenue_complete=true where id=$1`,[seasonA]);
  await q(`insert into public.sales(family_id,crop_season_id,sold_on,quantity,unit,unit_price,total_amount,currency) values($1,$2,'2026-10-15',10,'kg',10,200,'NPR')`,[familyA,seasonA]);
  await q(`insert into public.expenses(family_id,crop_season_id,expense_date,description,amount,currency) values($1,$2,'2026-10-17','Seed purchase',150,'USD')`,[familyA,seasonA]);
  const mixedCurrencySummary=(await q(`select currency,revenue,cost,profit from public.family_finance_summary($1,null,null) order by currency`,[familyA])).rows;
  if(mixedCurrencySummary.some((row:any)=>row.profit !== null))throw new Error('Finance summary combined mismatched currencies');
  console.log('PASS finance summary keeps profit null when revenue and cost currencies do not match');
  const category=(await q(`insert into public.expense_categories(family_id,name) values($1,'Test category') returning id`,[familyA])).rows[0].id as string;
  const ownCount=(await q(`select count(*)::int n from public.fields where family_id=$1`,[familyA])).rows[0].n;
  const foreignCount=(await q(`select count(*)::int n from public.fields where family_id=$1`,[familyB])).rows[0].n;
  if(ownCount!==1||foreignCount!==0)throw new Error('Family SELECT isolation failed');
  console.log('PASS family A reads own rows and cannot read family B rows');
  const isolatedCounts=(await q(`select (select count(*) from public.notifications where family_id=$1)::int notifications,(select count(*) from public.backup_records where family_id=$1)::int backups,(select count(*) from public.family_invitations where family_id=$1)::int invitations,(select count(*) from public.audit_logs where family_id=$1)::int audits,(select count(*) from storage.objects where name like $2)::int objects`,[familyB,`${familyB}/%`])).rows[0];
  if(Object.values(isolatedCounts).some(n=>n!==0))throw new Error('Family resource isolation failed');
  console.log('PASS family A cannot read B notifications, backups, invitations, audit logs, or private objects');
  await expectReject('family A cannot insert into family B',()=>q(`insert into public.fields(family_id,name) values($1,'forbidden')`,[familyB]));
  const crossUpdate=(await q(`update public.fields set name='attack' where id=$1 returning id`,[fieldB])).rows;
  const crossDelete=(await q(`delete from public.fields where id=$1 returning id`,[fieldB])).rows;
  if(crossUpdate.length||crossDelete.length)throw new Error('Cross-family update/delete was not blocked');
  console.log('PASS family A cannot update or hard-delete family B rows');
  await expectReject('cross-family crop-season relation is rejected',()=>q(`insert into public.crop_seasons(family_id,field_id,crop_id) values($1,$2,$3)`,[familyA,fieldB,cropB]));
  const bypass=(await q(`update public.fields set name='bypass' where id=$1 returning id`,[field])).rows;
  const protectedName=(await q(`select name from public.fields where id=$1`,[field])).rows[0].name;
  if(bypass.length!==0||protectedName!=='A field')throw new Error('Direct record update bypassed RPC controls');
  console.log('PASS direct record UPDATE is denied by RLS');
  const categoryBypass=(await q(`update public.expense_categories set name='bypass' where id=$1 returning id`,[category])).rows;
  if(categoryBypass.length)throw new Error('Direct expense category update bypassed version control');
  const categorySync=(await q(`select public.sync_update('public.expense_categories'::regclass,$1,$2,1,'{"name":"Synced category"}'::jsonb) result`,[category,familyA])).rows[0].result;
  if(categorySync.status!=='SYNCED'||categorySync.record.record_version!==2)throw new Error('Expense category atomic update failed');
  console.log('PASS expense category updates use the atomic version RPC');
  const sync=(await q(`select public.sync_update('public.fields'::regclass,$1,$2,1,'{"name":"Synced"}'::jsonb) result`,[field,familyA])).rows[0].result;
  if(sync.status!=='SYNCED'||sync.record.record_version!==2)throw new Error('Atomic sync update failed');
  console.log('PASS atomic version update increments the version');
  const conflict=(await q(`select public.sync_update('public.fields'::regclass,$1,$2,1,'{"name":"stale"}'::jsonb) result`,[field,familyA])).rows[0].result;
  if(conflict.status!=='CONFLICT'||conflict.actual_version!==2)throw new Error('Stale write was not detected');
  console.log('PASS stale version returns conflict and keeps server data');
  const invitation=(await q(`select * from public.create_family_invitation($1,'VIEWER',interval '1 day')`,[familyA])).rows[0];
  if(!/^[0-9]{8}$/.test(invitation.code))throw new Error('Invitation code must contain exactly 8 digits');
  const qrWindow=(await q(`select qr_expires_at>now() and qr_expires_at<=now()+interval '61 seconds' valid from public.family_invitations where id=$1`,[invitation.invitation_id])).rows[0].valid;
  if(!qrWindow)throw new Error('QR invitation must expire within one minute');
  console.log('PASS invitation returns an 8-digit code and a one-minute QR expiry');
  await db.exec('reset role'); await actor(b); await db.exec('set role authenticated');
  const joined=(await q(`select public.accept_family_invitation($1) family_id`,[invitation.token])).rows[0].family_id;
  if(joined!==familyA)throw new Error('Invitation acceptance failed');
  await expectReject('invitation replay is rejected',()=>q(`select public.accept_family_invitation($1)`,[invitation.token]));
  await db.exec('reset role'); await actor(a); await db.exec('set role authenticated');
  const codeInvitation=(await q(`select * from public.create_family_invitation($1,'EDITOR',interval '1 day')`,[familyA])).rows[0];
  await db.exec('reset role');
  let invalidCode='00000000';
  while((await q(`select exists(select 1 from public.family_invitations where invite_code_hash=extensions.digest($1,'sha256')) exists`,[invalidCode])).rows[0].exists) {
    invalidCode=String(Number(invalidCode)+1).padStart(8,'0');
  }
  await actor(c); await db.exec('set role authenticated');
  for(let i=0;i<5;i++){
    const invalid=(await q(`select public.accept_family_invitation($1) family_id`,[invalidCode])).rows[0].family_id;
    if(invalid!==null)throw new Error('An invalid invitation code was accepted');
  }
  const rateLimited=(await q(`select public.accept_family_invitation($1) family_id`,[codeInvitation.code])).rows[0].family_id;
  if(rateLimited!==null)throw new Error('Sixth invitation-code attempt was not rate limited');
  await db.exec('reset role');
  await q(`update public.invitation_code_attempts set window_started_at=now()-interval '2 minutes' where user_id=$1`,[c]);
  await actor(c); await db.exec('set role authenticated');
  const joinedByCode=(await q(`select public.accept_family_invitation($1) family_id`,[codeInvitation.code])).rows[0].family_id;
  if(joinedByCode!==familyA)throw new Error('Eight-digit invitation code acceptance failed');
  const codeReplay=(await q(`select public.accept_family_invitation($1) family_id`,[codeInvitation.code])).rows[0].family_id;
  if(codeReplay!==null)throw new Error('A used invitation code was accepted again');
  console.log('PASS invitation code acceptance, replay protection, and attempt throttling');
  await actor(b);
  await expectReject('viewer cannot escalate own role',()=>q(`select public.set_family_member($1,$2,'OWNER',true,true,true,true)`,[familyA,b]));
  await expectReject('invalid invitation token is rejected',()=>q(`select public.accept_family_invitation('ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff')`));
  await db.exec('reset role');
  await actor(a); await db.exec('set role authenticated');
  const revoked=(await q(`select * from public.create_family_invitation($1,'EDITOR',interval '1 day')`,[familyA])).rows[0];
  await q(`select public.revoke_family_invitation($1)`,[revoked.invitation_id]);
  const expired=(await q(`select * from public.create_family_invitation($1,'EDITOR',interval '1 day')`,[familyA])).rows[0];
  const expiredQr=(await q(`select * from public.create_family_invitation($1,'EDITOR',interval '1 day')`,[familyA])).rows[0];
  await db.exec('reset role');
  await q(`update public.family_invitations set created_at=now()-interval '2 days',expires_at=now()-interval '1 day' where id=$1`,[expired.invitation_id]);
  await q(`update public.family_invitations set qr_expires_at=now()-interval '1 second' where id=$1`,[expiredQr.invitation_id]);
  await actor(b); await db.exec('set role authenticated');
  await expectReject('revoked invitation is rejected',()=>q(`select public.accept_family_invitation($1)`,[revoked.token]));
  await expectReject('expired invitation is rejected',()=>q(`select public.accept_family_invitation($1)`,[expired.token]));
  await expectReject('expired QR invitation is rejected',()=>q(`select public.accept_family_invitation($1)`,[expiredQr.token]));
  const expiredCode=(await q(`select public.accept_family_invitation($1) family_id`,[expired.code])).rows[0].family_id;
  if(expiredCode!==null)throw new Error('An expired invitation code was accepted');
  await expectReject('non-owner cannot create invitation',()=>q(`select * from public.create_family_invitation($1,'EDITOR',interval '1 day')`,[familyA]));
  await db.exec('reset role');
  await actor(a); await db.exec('set role authenticated');
  const staged=(await q(`select public.stage_family_import($1::uuid,jsonb_build_object('metadata',jsonb_build_object('family_id',($1::uuid)::text,'backup_version',1,'database_schema_version','202611140002'),'fields',jsonb_build_array(jsonb_build_object('id',($2::uuid)::text,'family_id',($1::uuid)::text,'name','Imported name')))) result`,[familyA,field])).rows[0].result;
  await db.exec('reset role'); await actor(b); await db.exec('set role authenticated');
  await expectReject('non-owner cannot confirm backup restore',()=>q(`select public.confirm_family_import($1)`,[staged.import_id]));
  await db.exec('reset role'); await actor(a); await db.exec('set role authenticated');
  const imported=(await q(`select public.confirm_family_import($1) result`,[staged.import_id])).rows[0].result;
  if(imported.status!=='COMPLETED')throw new Error('Backup import confirmation failed');
  const importedName=(await q(`select name from public.fields where id=$1`,[field])).rows[0].name;
  if(importedName!=='Imported name')throw new Error('Backup merge did not restore data');
  await expectReject('backup import confirmation cannot be replayed',()=>q(`select public.confirm_family_import($1)`,[staged.import_id]));
  await expectReject('owner cannot remove the only owner',()=>q(`select public.remove_family_member($1,$2)`,[familyA,a]));
  await q(`select public.remove_family_member($1,$2)`,[familyA,b]);
  await q(`select public.restore_family_member($1,$2)`,[familyA,b]);
  const deletedVersion=(await q(`select public.soft_delete_record('public.fields'::regclass,$1,$2,3) version`,[field,familyA])).rows[0].version;
  if(deletedVersion!==4)throw new Error('Soft delete version failed');
  const restoredVersion=(await q(`select public.restore_record('public.fields'::regclass,$1,$2,4) version`,[field,familyA])).rows[0].version;
  if(restoredVersion!==5)throw new Error('Restore version failed');
  console.log('PASS owner-only membership restoration and versioned soft delete/restore');
  const malformed=(await q(`select public.stage_family_import($1::uuid,jsonb_build_object('metadata',jsonb_build_object('family_id',($1::uuid)::text,'backup_version',1,'database_schema_version','202611140002'),'fields',jsonb_build_array(jsonb_build_object('id',($2::uuid)::text,'family_id',($1::uuid)::text,'name','Should roll back')),'crops',jsonb_build_array(jsonb_build_object('id','33333333-3333-4333-8333-333333333333','family_id',($1::uuid)::text)))) result`,[familyA,field])).rows[0].result;
  await expectReject('malformed backup confirmation fails atomically',()=>q(`select public.confirm_family_import($1)`,[malformed.import_id]));
  const afterRollback=(await q(`select name from public.fields where id=$1`,[field])).rows[0].name;
  if(afterRollback!=='Imported name')throw new Error('Failed import left partial changes');
  console.log('PASS failed backup import rolls back earlier table changes');
  await db.exec('reset role');
  console.log('PASS one-time invitation, replay rejection, and role-escalation checks');
  console.log('PASS revoked/expired invitation checks and transactional owner-only backup import');
  console.log('PASS all migration files executed in PGlite with mocked Supabase auth/storage schemas');
} catch (error) {
  console.error(error);
  Deno.exitCode = 1;
} finally {
  await db.close();
}

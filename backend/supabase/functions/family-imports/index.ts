import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';
import JSZip from 'npm:jszip';
import { parse } from 'npm:csv-parse/sync';

const headers={'Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'authorization,apikey,content-type','Content-Type':'application/json'};
const json=(status:number,body:unknown)=>new Response(JSON.stringify(body),{status,headers});
const files:Record<string,string>={fields:'fields',crops:'crops',crop_seasons:'crop_seasons',seeds:'seed_records',fertilizers:'fertilizer_records',labor:'labor_records',machinery:'machinery_usage',expenses:'expenses',harvests:'harvest_records',sales:'sales',historical_prices:'historical_prices',expense_categories:'expense_categories'};
const numeric=new Set(['area','quantity','amount','price','unit_price','total_amount','worker_count','hours','record_version']);
const boolean=new Set(['is_custom','costs_complete','revenue_complete']);
const jsonColumns=new Set(['location']);

Deno.serve(async(req)=>{
 if(req.method==='OPTIONS')return new Response('ok',{headers});
 if(req.method!=='POST')return json(405,{error:{code:'METHOD_NOT_ALLOWED'}});
 const auth=req.headers.get('Authorization');const url=Deno.env.get('SUPABASE_URL');const anon=Deno.env.get('SUPABASE_ANON_KEY');
 if(!auth||!url||!anon)return json(503,{error:{code:'SERVICE_UNAVAILABLE'}});
 const length=Number(req.headers.get('content-length')??0);if(length>4096)return json(413,{error:{code:'PAYLOAD_TOO_LARGE'}});
 let body:any;try{const reader=req.body?.getReader();if(!reader)return json(400,{error:{code:'INVALID_JSON'}});let raw='';while(true){const {done,value}=await reader.read();if(done)break;raw+=new TextDecoder().decode(value);if(raw.length>4096){await reader.cancel();return json(413,{error:{code:'PAYLOAD_TOO_LARGE'}})}}body=JSON.parse(raw)}catch{return json(400,{error:{code:'INVALID_JSON'}})}
 const db=createClient(url,anon,{global:{headers:{Authorization:auth}},auth:{persistSession:false}});
 const {data:{user},error:userError}=await db.auth.getUser();if(userError||!user)return json(401,{error:{code:'UNAUTHENTICATED'}});
 if(body.action==='confirm'){
  if(typeof body.import_id!=='string')return json(400,{error:{code:'INVALID_REQUEST'}});
  const {data,error}=await db.rpc('confirm_family_import',{p_import:body.import_id});
  if(error)return json(error.code==='42501'?403:400,{error:{code:error.message||'IMPORT_FAILED',sqlstate:error.code}});
  return json(200,data);
 }
 if(body.action!=='preview'||(typeof body.backup_id!=='string'&&!(typeof body.family_id==='string'&&typeof body.storage_path==='string')))return json(400,{error:{code:'INVALID_REQUEST'}});
 let familyId:string;let storagePath:string;let backupId:string|undefined;
 if(typeof body.backup_id==='string'){
  const {data:backup,error:backupError}=await db.from('backup_records').select('id,family_id,storage_path,format,status').eq('id',body.backup_id).maybeSingle();
  if(backupError||!backup)return json(404,{error:{code:'BACKUP_NOT_FOUND'}});
  if(backup.format!=='ZIP'||backup.status!=='COMPLETED')return json(422,{error:{code:'UNSUPPORTED_BACKUP_FORMAT'}});
  familyId=backup.family_id;storagePath=backup.storage_path;backupId=backup.id;
 } else {
  familyId=body.family_id;storagePath=body.storage_path;
  if(!storagePath.startsWith(`${familyId}/imports/`)||!storagePath.toLowerCase().endsWith('.zip'))return json(400,{error:{code:'INVALID_IMPORT_PATH'}});
 }
 const {data:owner,error:ownerError}=await db.rpc('is_family_owner',{p_family:familyId});if(ownerError||!owner)return json(403,{error:{code:'OWNER_REQUIRED'}});
 const {data:file,error:fileError}=await db.storage.from('family-backups').download(storagePath);
 if(fileError||!file)return json(400,{error:{code:'BACKUP_FILE_UNAVAILABLE'}});
 if(file.size>50*1024*1024)return json(413,{error:{code:'BACKUP_SIZE_LIMIT_EXCEEDED'}});
 try{
  const zip=await JSZip.loadAsync(await file.arrayBuffer());
  const metaFile=zip.file('metadata.json');if(!metaFile)return json(422,{error:{code:'BACKUP_METADATA_MISSING'}});
  const metadata=JSON.parse(await metaFile.async('string'));
  if(metadata.family_id!==familyId)return json(403,{error:{code:'BACKUP_FAMILY_MISMATCH'}});
  if(metadata.backup_version!==1||metadata.database_schema_version!=='202611140002')return json(422,{error:{code:'UNSUPPORTED_SCHEMA_VERSION'}});
  const payload:any={metadata};
  for(const [fileName,table] of Object.entries(files)){
   const entry=zip.file(`${fileName}.csv`);if(!entry){payload[table]=[];continue;}
   const text=await entry.async('string');const rows=parse(text,{columns:true,skip_empty_lines:true,relax_quotes:false,max_record_size:1048576}) as Record<string,string>[];
   payload[table]=rows.map(row=>Object.fromEntries(Object.entries(row).map(([key,value])=>{
    if(value==='')return[key,null];if(numeric.has(key))return[key,Number(value)];if(boolean.has(key))return[key,value==='true'];if(jsonColumns.has(key))return[key,JSON.parse(value)];return[key,value];
   })));
  }
  const {data:preview,error:stageError}=await db.rpc('stage_family_import',{p_family:familyId,p_payload:payload});
  if(stageError)return json(stageError.code==='42501'?403:422,{error:{code:stageError.message||'BACKUP_VALIDATION_FAILED',sqlstate:stageError.code}});
  return json(200,{...preview,backup_id:backupId??null,warning:'Confirming merges rows with matching server IDs. Validation and row constraints run again transactionally during confirmation.'});
 }catch{return json(422,{error:{code:'MALFORMED_BACKUP'}})}
});

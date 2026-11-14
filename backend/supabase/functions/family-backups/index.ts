import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';
import JSZip from 'npm:jszip';
import ExcelJS from 'npm:exceljs';
import { PDFDocument, StandardFonts } from 'npm:pdf-lib';

const headers = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'authorization, apikey, content-type', 'Content-Type': 'application/json' };
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers });
const EXPORTS: Record<string,string> = { fields:'fields.csv', crops:'crops.csv', crop_seasons:'crop_seasons.csv', seed_records:'seeds.csv', fertilizer_records:'fertilizers.csv', labor_records:'labor.csv', machinery_usage:'machinery.csv', expenses:'expenses.csv', harvest_records:'harvests.csv', sales:'sales.csv', historical_prices:'historical_prices.csv', expense_categories:'expense_categories.csv' };
const csvCell = (v: unknown) => { const s = typeof v === 'string' ? v : JSON.stringify(v ?? ''); return `"${s.replaceAll('"','""')}"`; };
const toCsv = (rows: Record<string,unknown>[]) => {
  const keys = [...new Set(rows.flatMap(r => Object.keys(r)))];
  if (!keys.length) return 'id\n';
  return [keys.map(csvCell).join(','), ...rows.map(r => keys.map(k => csvCell(r[k])).join(','))].join('\r\n');
};

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers });
  if (req.method !== 'POST') return json(405,{error:{code:'METHOD_NOT_ALLOWED'}});
  const auth=req.headers.get('Authorization');
  const url=Deno.env.get('SUPABASE_URL'); const anon=Deno.env.get('SUPABASE_ANON_KEY');
  if (!auth||!url||!anon) return json(503,{error:{code:'SERVICE_UNAVAILABLE'}});
  const declared=Number(req.headers.get('content-length')??0); if(declared>4096) return json(413,{error:{code:'PAYLOAD_TOO_LARGE'}});
  let raw=''; try { const reader=req.body?.getReader(); if(!reader) return json(400,{error:{code:'INVALID_JSON'}}); const chunks:Uint8Array[]=[]; let size=0; while(true){const {done,value}=await reader.read();if(done)break;size+=value.byteLength;if(size>4096){await reader.cancel();return json(413,{error:{code:'PAYLOAD_TOO_LARGE'}})}chunks.push(value);} const all=new Uint8Array(size);let offset=0;for(const c of chunks){all.set(c,offset);offset+=c.length;} raw=new TextDecoder().decode(all); } catch{return json(400,{error:{code:'INVALID_JSON'}})}
  let body:any; try { body=JSON.parse(raw); } catch { return json(400,{error:{code:'INVALID_JSON'}}); }
  const db=createClient(url,anon,{global:{headers:{Authorization:auth}},auth:{persistSession:false}});
  const {data:{user},error:userError}=await db.auth.getUser();
  if(userError||!user) return json(401,{error:{code:'UNAUTHENTICATED'}});
  if(body.action==='download') {
    if(typeof body.backup_id!=='string') return json(400,{error:{code:'INVALID_REQUEST'}});
    const {data:record,error}=await db.from('backup_records').select('id,family_id,storage_path,expires_at,status').eq('id',body.backup_id).maybeSingle();
    if(error||!record) return json(404,{error:{code:'BACKUP_NOT_FOUND'}});
    const {data:owner}=await db.rpc('is_family_owner',{p_family:record.family_id});
    if(!owner) return json(403,{error:{code:'OWNER_REQUIRED'}});
    if(record.expires_at&&new Date(record.expires_at)<new Date()) return json(410,{error:{code:'BACKUP_EXPIRED'}});
    const {data:signed,error:signError}=await db.storage.from('family-backups').createSignedUrl(record.storage_path,60);
    if(signError||!signed) return json(500,{error:{code:'SIGNED_URL_FAILED'}});
    return json(200,{backup_id:record.id,url:signed.signedUrl,expires_in_seconds:60});
  }
  if(body.action!=='export'||typeof body.family_id!=='string'||!['CSV','XLSX','PDF','ZIP'].includes(body.format)) return json(400,{error:{code:'INVALID_REQUEST'}});
  const {data:owner,error:ownerError}=await db.rpc('is_family_owner',{p_family:body.family_id});
  if(ownerError||!owner) return json(403,{error:{code:'OWNER_REQUIRED'}});
  const tableRows:Record<string,Record<string,unknown>[]>={}; let total=0;
  try {
    for(const table of Object.keys(EXPORTS)) {
      const all:Record<string,unknown>[]=[];
      for(let offset=0;offset<10000;offset+=1000) {
        const {data,error}=await db.from(table).select('*').eq('family_id',body.family_id).is('deleted_at',null).range(offset,offset+999);
        if(error) throw new Error('EXPORT_QUERY_FAILED');
        all.push(...(data??[])); if((data??[]).length<1000) break;
      }
      total+=all.length; if(total>50000) return json(413,{error:{code:'EXPORT_ROW_LIMIT_EXCEEDED'}});
      tableRows[table]=all;
    }
    const schemaVersion='202611140002'; const exportedAt=new Date().toISOString();
    const metadata={backup_version:1,family_id:body.family_id,export_date:exportedAt,app_version:'1.0.0',database_schema_version:schemaVersion,record_count:total,format:body.format};
    let bytes:Uint8Array; let mime:string; let extension:string;
    if(body.format==='ZIP') {
      const zip=new JSZip(); for(const [table,name] of Object.entries(EXPORTS)) zip.file(name,toCsv(tableRows[table]??[]));
      zip.file('metadata.json',JSON.stringify(metadata,null,2)); bytes=await zip.generateAsync({type:'uint8array',compression:'DEFLATE'}); mime='application/zip'; extension='zip';
    } else if(body.format==='CSV') {
      const all=Object.entries(tableRows).flatMap(([table,rows])=>rows.map(row=>({table,row:JSON.stringify(row)})));
      bytes=new TextEncoder().encode(toCsv(all)); mime='text/csv'; extension='csv';
    } else if(body.format==='XLSX') {
      const wb=new ExcelJS.Workbook(); wb.creator='FARM FAMILY';
      for(const [table,rows] of Object.entries(tableRows)) { const ws=wb.addWorksheet(table.slice(0,31)); const keys=[...new Set(rows.flatMap(r=>Object.keys(r)))]; if(keys.length){ws.addRow(keys); for(const row of rows) ws.addRow(keys.map(k=>typeof row[k]==='object'?JSON.stringify(row[k]):row[k]));} }
      wb.addWorksheet('metadata').addRow(['metadata',JSON.stringify(metadata)]);
      bytes=new Uint8Array(await wb.xlsx.writeBuffer() as ArrayBuffer); mime='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'; extension='xlsx';
    } else {
      const pdf=await PDFDocument.create(); let page=pdf.addPage(); const font=await pdf.embedFont(StandardFonts.Helvetica); let y=page.getHeight()-50;
      const lines=['FARM FAMILY Backup Summary',`Family: ${body.family_id}`,`Export date: ${exportedAt}`,`Schema version: ${schemaVersion}`,`Records: ${total}`,...Object.entries(tableRows).map(([table,rows])=>`${table}: ${rows.length}`)];
      for(const line of lines){ if(y<45){page=pdf.addPage();y=page.getHeight()-50;} page.drawText(line,{x:45,y,size:11,font});y-=20; }
      bytes=await pdf.save(); mime='application/pdf'; extension='pdf';
    }
    if(bytes.byteLength>50*1024*1024) return json(413,{error:{code:'BACKUP_SIZE_LIMIT_EXCEEDED'}});
    const backupId=crypto.randomUUID(); const path=`${body.family_id}/${backupId}.${extension}`;
    const {error:uploadError}=await db.storage.from('family-backups').upload(path,bytes,{contentType:mime,upsert:false});
    if(uploadError) return json(500,{error:{code:'BACKUP_UPLOAD_FAILED'}});
    const {error:recordError}=await db.from('backup_records').insert({id:backupId,family_id:body.family_id,requested_by:user.id,storage_path:path,format:body.format,status:'COMPLETED',metadata:{...metadata,size_bytes:bytes.byteLength}});
    if(recordError){await db.storage.from('family-backups').remove([path]); return json(500,{error:{code:'BACKUP_METADATA_FAILED'}});}
    return json(200,{backup_id:backupId,status:'COMPLETED',format:body.format,record_count:total,metadata});
  } catch { return json(500,{error:{code:'BACKUP_EXPORT_FAILED'}}); }
});

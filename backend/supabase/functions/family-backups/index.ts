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
const romanizeHinglish = (input: unknown) => {
  const consonants:Record<string,string>={क:'k',ख:'kh',ग:'g',घ:'gh',ङ:'ng',च:'ch',छ:'chh',ज:'j',झ:'jh',ञ:'ny',ट:'t',ठ:'th',ड:'d',ढ:'dh',ण:'n',त:'t',थ:'th',द:'d',ध:'dh',न:'n',प:'p',फ:'ph',ब:'b',भ:'bh',म:'m',य:'y',र:'r',ल:'l',व:'v',श:'sh',ष:'sh',स:'s',ह:'h'};
  const vowels:Record<string,string>={अ:'a',आ:'aa',इ:'i',ई:'ee',उ:'u',ऊ:'oo',ऋ:'ri',ए:'e',ऐ:'ai',ओ:'o',औ:'au'};
  const signs:Record<string,string>={'ा':'aa','ि':'i','ी':'ee','ु':'u','ू':'oo','ृ':'ri','े':'e','ै':'ai','ो':'o','ौ':'au'};
  const chars=[...String(input??'')]; let output='';
  for(let i=0;i<chars.length;i++) {
    const char=chars[i];
    if(consonants[char]) {
      output+=consonants[char];
      if(signs[chars[i+1]]) output+=signs[chars[++i]];
      else if(chars[i+1]==='्') i++;
      else output+='a';
    } else if(vowels[char]) output+=vowels[char];
    else if(signs[char]) output+=signs[char];
    else if(char==='ं'||char==='ँ') output+='n';
    else if(char==='ः') output+='h';
    else if(char==='़'||char==='्') continue;
    else output+=/^[\x20-\xFF]$/.test(char)?char:'?';
  }
  return output;
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
    const {data:record,error}=await db.from('backup_records').select('id,family_id,storage_path,expires_at,status,format,created_at').eq('id',body.backup_id).maybeSingle();
    if(error||!record) return json(404,{error:{code:'BACKUP_NOT_FOUND'}});
    const {data:owner}=await db.rpc('is_family_owner',{p_family:record.family_id});
    if(!owner) return json(403,{error:{code:'OWNER_REQUIRED'}});
    if(record.expires_at&&new Date(record.expires_at)<new Date()) return json(410,{error:{code:'BACKUP_EXPIRED'}});
    const {data:family,error:familyError}=await db.from('families').select('name').eq('id',record.family_id).maybeSingle();
    if(familyError||!family) return json(404,{error:{code:'FAMILY_NOT_FOUND'}});
    const familySlug=romanizeHinglish(family?.name??'Farm-Family').replace(/[^a-zA-Z0-9]+/g,'-').replace(/^-|-$/g,'').toLowerCase()||'farm-family';
    const exportDate=String(record.created_at??new Date().toISOString()).slice(0,10);
    const extension=String(record.format??'ZIP').toLowerCase();
    const downloadName=`${familySlug}-${exportDate}.${extension}`;
    const {data:signed,error:signError}=await db.storage.from('family-backups').createSignedUrl(record.storage_path,60,{download:downloadName});
    if(signError||!signed) return json(500,{error:{code:'SIGNED_URL_FAILED'}});
    return json(200,{backup_id:record.id,url:signed.signedUrl,expires_in_seconds:60});
  }
  if(body.action!=='export'||typeof body.family_id!=='string'||!['CSV','XLSX','PDF','ZIP'].includes(body.format)) return json(400,{error:{code:'INVALID_REQUEST'}});
  const {data:owner,error:ownerError}=await db.rpc('is_family_owner',{p_family:body.family_id});
  if(ownerError||!owner) return json(403,{error:{code:'OWNER_REQUIRED'}});
  const {data:familyInfo,error:familyError}=await db.from('families').select('name').eq('id',body.family_id).maybeSingle();
  if(familyError||!familyInfo) return json(404,{error:{code:'FAMILY_NOT_FOUND'}});
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
      const pdf=await PDFDocument.create(); let page=pdf.addPage(); const font=await pdf.embedFont(StandardFonts.Helvetica);
      const logoBytes=await Deno.readFile(new URL('./farm-family-mark.png',import.meta.url));
      const logo=await pdf.embedPng(logoBytes);
      page.drawImage(logo,{x:40,y:page.getHeight()-82,width:34,height:34});
      page.drawText('FARM FAMILY',{x:84,y:page.getHeight()-61,size:17,font});
      page.drawText(`Parivar: ${romanizeHinglish(familyInfo.name)}`,{x:84,y:page.getHeight()-78,size:9,font});
      let avatarImage:Awaited<ReturnType<typeof pdf.embedJpg>>|null=null;
      try {
        const userMetadata=user.user_metadata as Record<string,unknown>|undefined;
        const avatarPath=typeof userMetadata?.avatar_path==='string'?userMetadata.avatar_path:null;
        if(avatarPath===`${user.id}/avatar.jpg`) {
          const avatarResponse=await fetch(`${url}/functions/v1/profile-avatars`,{method:'POST',headers:{Authorization:auth,apikey:anon,'Content-Type':'application/json'},body:JSON.stringify({action:'signed_url',path:avatarPath}),signal:AbortSignal.timeout(5000)});
          if(avatarResponse.ok) {
            const avatarJson=await avatarResponse.json();
            if(typeof avatarJson.url==='string') {
              const imageResponse=await fetch(avatarJson.url,{signal:AbortSignal.timeout(5000)});
              if(imageResponse.ok&&Number(imageResponse.headers.get('content-length')??0)<=1_500_000) avatarImage=await pdf.embedJpg(new Uint8Array(await imageResponse.arrayBuffer()));
            }
          }
        }
      } catch { avatarImage=null; }
      if(avatarImage) page.drawImage(avatarImage,{x:page.getWidth()-78,y:page.getHeight()-86,width:38,height:38});
      let y=page.getHeight()-110;
      const headings:Record<string,string>={fields:'Khet aur zameen',crops:'Fasal',crop_seasons:'Fasal ke mausam',seed_records:'Beej',fertilizer_records:'Khaad',labor_records:'Mazdoori',machinery_usage:'Machine ka istemal',expenses:'Kharch',harvest_records:'Fasal ki paidawar',sales:'Bikri',historical_prices:'Bazaar ke bhav',expense_categories:'Kharch ki categories'};
      const columnLabels:Record<string,string>={name:'naam',area:'raqabah',area_unit:'zameen ki ikai',location_label:'jagah',season_name:'fasal ka mausam',purpose:'istemal',start_date:'shuru tareekh',end_date:'aakhri tareekh',crop_name:'fasal',seed_name:'beej ka naam',product_name:'khaad ka naam',quantity:'matra',unit:'ikai',amount:'raashi',currency:'mudra',purchased_on:'khareed tareekh',applied_on:'lagane ki tareekh',work_date:'kaam ki tareekh',worker_count:'mazdooron ki sankhya',hours:'ghante',description:'vivaran',service_name:'machine ki seva',provider:'sewa dene wala',expense_date:'kharch ki tareekh',harvest_date:'katai ki tareekh',grade:'darja',sold_on:'bikri ki tareekh',buyer:'khareedne wala',total_amount:'kul raashi',unit_price:'prati ikai daam',observed_on:'bhav ki tareekh',market:'bazaar',price:'daam',source:'srot',notes:'tippani',is_custom:'custom category'};
      const lines=[`Tayyar ki tareekh: ${exportedAt}`,`Kul records: ${total}`];
      for(const [table,rows] of Object.entries(tableRows)) {
        lines.push('',`${headings[table]??table} (${rows.length})`);
        for(const row of rows) {
          const values=Object.entries(row).filter(([key,value])=>!['id','family_id','user_id','recorded_by','deleted_at','created_at','updated_at','version'].includes(key)&&!key.endsWith('_id')&&value!==null&&value!==undefined&&value!=='').map(([key,value])=>`${columnLabels[key]??key.replaceAll('_',' ')}: ${romanizeHinglish(typeof value==='object'?JSON.stringify(value):String(value))}`);
          if(values.length) lines.push(`- ${values.join(' | ')}`);
        }
      }
      for(const rawLine of lines){
        const line=romanizeHinglish(rawLine);
        for(let offset=0;offset<line.length;offset+=105){ if(y<45){page=pdf.addPage();y=page.getHeight()-50;} page.drawText(line.slice(offset,offset+105),{x:40,y,size:8,font,maxWidth:page.getWidth()-80});y-=13; }
      }
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

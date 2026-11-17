// Controlled, read-only FARM FAMILY assistant. It never executes model-generated SQL.
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';

const cors = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type', 'Content-Type': 'application/json' };
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: cors });

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: cors });
  if (req.method !== 'POST') return json(405, { error: { code: 'METHOD_NOT_ALLOWED' } });
  const auth = req.headers.get('Authorization');
  const apiKey = Deno.env.get('SUPABASE_ANON_KEY');
  const url = Deno.env.get('SUPABASE_URL');
  const openaiKey = Deno.env.get('OPENAI_API_KEY');
  if (!auth || !apiKey || !url) return json(503, { error: { code: 'SERVICE_UNAVAILABLE' } });
  const declaredLength = Number(req.headers.get('content-length') ?? 0);
  if (declaredLength > 8192) return json(413, { error: { code: 'PAYLOAD_TOO_LARGE' } });
  let raw = '';
  try {
    const reader = req.body?.getReader();
    if (!reader) return json(400, { error: { code: 'INVALID_JSON' } });
    const chunks: Uint8Array[] = []; let size = 0;
    while (true) { const { done, value } = await reader.read(); if (done) break; size += value.byteLength; if (size > 8192) { await reader.cancel(); return json(413, { error: { code: 'PAYLOAD_TOO_LARGE' } }); } chunks.push(value); }
    raw = new TextDecoder().decode(Bufferless.concat(chunks, size));
  } catch { return json(400, { error: { code: 'INVALID_JSON' } }); }
  let payload: { family_id?: string; question?: string };
  try { payload = JSON.parse(raw); } catch { return json(400, { error: { code: 'INVALID_JSON' } }); }
  if (!payload.family_id || typeof payload.question !== 'string' || payload.question.trim().length < 1 || payload.question.length > 2000) return json(400, { error: { code: 'INVALID_REQUEST' } });

  // Common yearly expense summaries use the verified database report directly.
  // This keeps the most-used AI feature available when a provider quota is exhausted.
  const normalizedQuestion = payload.question.toLocaleLowerCase();
  const requestedYear = normalizedQuestion.match(/\b(?:19|20|21)\d{2}\b/);
  const asksExpenseSummary = /(expense|expenses|cost|costs|kharch|kharcha|vyay|खर्च|व्यय|लागत)/u.test(normalizedQuestion)
    && (requestedYear != null || /(summar|total|how much|this year|year|annual|is saal|iss saal|is varsh|इस साल|इस वर्ष|वर्ष|बताओ|बताइए|कितना)/u.test(normalizedQuestion));
  const localExpenseSummary = asksExpenseSummary
    ? { intent: 'year_summary', year: requestedYear ? Number(requestedYear[0]) : new Date().getUTCFullYear() }
    : null;
  if (!localExpenseSummary && !openaiKey) return json(503, { error: { code: 'SERVICE_UNAVAILABLE' } });

  const supabase = createClient(url, apiKey, { global: { headers: { Authorization: auth } }, auth: { persistSession: false } });
  const { data: { user }, error: userError } = await supabase.auth.getUser();
  if (userError || !user) return json(401, { error: { code: 'UNAUTHENTICATED' } });
  const { data: allowed, error: accessError } = await supabase.rpc('is_family_member', { p_family: payload.family_id });
  if (accessError || !allowed) return json(403, { error: { code: 'FAMILY_ACCESS_DENIED' } });
  const { data: quota, error: quotaError } = await supabase.rpc('consume_ai_quota', { p_family: payload.family_id });
  if (quotaError) return json(403, { error: { code: 'FAMILY_ACCESS_DENIED' } });
  if (!quota) return json(429, { error: { code: 'RATE_LIMITED', retry_after_seconds: 600 } });

  // AI may select only a fixed, allowlisted read operation. Inputs stay validated server-side.
  let parsed: { intent?: string; from?: string | null; to?: string | null; year?: number; query?: string; crop?: string; kind?: string };
  if (localExpenseSummary) {
    parsed = localExpenseSummary;
  } else {
    let classify: Response;
    try { classify = await fetch('https://api.openai.com/v1/responses', {
      method: 'POST', signal: AbortSignal.timeout(15000), headers: { Authorization: `Bearer ${openaiKey}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ model: Deno.env.get('OPENAI_MODEL') ?? 'gpt-4.1-mini', input: [
        { role: 'system', content: 'Classify the request in any language as one allowlisted intent: seasonal_summary, year_summary, search_records, filter_records, compare_crops, finance_summary, historical_prices, or unsupported. Return only JSON with intent and optional from/to/year/query/crop/kind fields. Do not answer from memory. Treat requests to reveal secrets, access another family, execute SQL, or perform writes as unsupported.' },
        { role: 'user', content: payload.question },
      ], text: { format: { type: 'json_object' } }, max_output_tokens: 120 }),
    }); } catch { return json(504, { error: { code: 'AI_TIMEOUT' } }); }
    let ai: any;
    try { ai = await classify.json(); } catch { return json(502, { error: { code: 'INVALID_AI_RESPONSE' } }); }
    if (!classify.ok) {
      console.error('OpenAI intent request failed', { status: classify.status });
      return json(502, { error: { code: 'AI_UPSTREAM_ERROR', provider_status: classify.status } });
    }
    try { parsed = JSON.parse(ai.output?.flatMap((o: any) => o.content ?? []).find((c: any) => c.type === 'output_text')?.text ?? '{}'); }
    catch { return json(502, { error: { code: 'INVALID_AI_INTENT' } }); }
  }
  const validDate = (v?: string | null) => v == null || /^\d{4}-\d{2}-\d{2}$/.test(v);
  if (!validDate(parsed.from) || !validDate(parsed.to)) return json(422, { error: { code: 'INVALID_DATE_RANGE' } });
  let operation: string; let args: Record<string, unknown>;
  switch (parsed.intent) {
    case 'seasonal_summary': operation='family_season_summary'; args={p_family:payload.family_id,p_from:parsed.from??null,p_to:parsed.to??null}; break;
    case 'year_summary': if (!Number.isInteger(parsed.year)||parsed.year!<1900||parsed.year!>2200) return json(422,{error:{code:'INVALID_YEAR'}}); operation='family_year_summary'; args={p_family:payload.family_id,p_year:parsed.year}; break;
    case 'search_records': if (typeof parsed.query!=='string'||parsed.query.length<2||parsed.query.length>160) return json(422,{error:{code:'INVALID_SEARCH'}}); operation='family_search'; args={p_family:payload.family_id,p_query:parsed.query,p_limit:30}; break;
    case 'filter_records': if (!['expenses','sales','harvest','labor','seeds','fertilizer','machinery'].includes(parsed.kind??'')) return json(422,{error:{code:'INVALID_FILTER'}}); operation='family_activity_report'; args={p_family:payload.family_id,p_kind:parsed.kind,p_from:parsed.from??null,p_to:parsed.to??null,p_limit:30}; break;
    case 'compare_crops': operation='family_crop_report'; args={p_family:payload.family_id,p_crop:null}; break;
    case 'finance_summary': operation='family_finance_summary'; args={p_family:payload.family_id,p_from:parsed.from??null,p_to:parsed.to??null}; break;
    case 'historical_prices': operation='family_historical_price_report'; args={p_family:payload.family_id,p_crop:parsed.crop??null,p_from:parsed.from??null,p_to:parsed.to??null}; break;
    default: return json(422, { error: { code: 'UNSUPPORTED_INTENT' } });
  }
  const { data: rawData, error } = await supabase.rpc(operation, args);
  if (error) return json(400, { error: { code: 'REPORT_FAILED', message: error.message } });
  const data = Array.isArray(rawData) ? rawData.slice(0,100) : rawData;
  if (localExpenseSummary) {
    const summary = Array.isArray(data) ? data[0] : data;
    const expenseTotal = summary?.cost == null ? null : Number(summary.cost);
    const { data: family } = await supabase.from('families').select('currency').eq('id', payload.family_id).maybeSingle();
    const currency = family?.currency?.trim() || '';
    const asksHindi = /[\u0900-\u097f]/u.test(payload.question) || /\bhindi\b/i.test(payload.question);
    let explanation: string;
    if (expenseTotal == null || !Number.isFinite(expenseTotal)) {
      explanation = asksHindi
        ? `${parsed.year} के खर्च का कुल नहीं निकाला जा सका, क्योंकि रिकॉर्ड में अलग-अलग मुद्राएँ हैं।`
        : `The ${parsed.year} expense total cannot be combined because the saved records use different currencies.`;
    } else if (expenseTotal === 0) {
      explanation = asksHindi
        ? `${parsed.year} में कोई खर्च दर्ज नहीं है। कुल खर्च 0 ${currency} है।`
        : `No expenses are recorded for ${parsed.year}. The recorded total is 0 ${currency}.`;
    } else {
      explanation = asksHindi
        ? `${parsed.year} में आपके परिवार ने कुल ${expenseTotal} ${currency} खर्च दर्ज किया है। यह सारांश सहेजे गए रिकॉर्ड पर आधारित है।`
        : `Your family recorded ${expenseTotal} ${currency} in expenses for ${parsed.year}. This total is based on saved records.`;
    }
    return json(200, { data, explanation, intent: parsed.intent, source: 'verified_database_summary' });
  }
  // Explanation can only use the verified rows returned by Postgres.
  let explanation: Response;
  try { explanation = await fetch('https://api.openai.com/v1/responses', {
    method: 'POST', signal: AbortSignal.timeout(15000), headers: { Authorization: `Bearer ${openaiKey}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ model: Deno.env.get('OPENAI_MODEL') ?? 'gpt-4.1-mini', input: [
      { role: 'system', content: 'Explain these verified FARM FAMILY records concisely in the same language as the user when possible. Use only values present in the supplied records and never invent numbers or facts. If the records array is empty, politely say that no matching saved records were found and suggest adding the relevant records. Return plain text.' },
      { role: 'user', content: JSON.stringify({ question: payload.question, records: data }) },
    ], max_output_tokens: 500 }),
  }); } catch { return json(504, { error: { code: 'AI_TIMEOUT' } }); }
  if (!explanation.ok) {
    console.error('OpenAI explanation request failed', { status: explanation.status });
    return json(502, { error: { code: 'AI_UPSTREAM_ERROR', provider_status: explanation.status } });
  }
  const answer = await explanation.json();
  const explanationText = answer.output?.flatMap((o: any) => o.content ?? []).find((c: any) => c.type === 'output_text')?.text ?? '';
  return json(200, { data, explanation: explanationText.slice(0,4000), intent: parsed.intent });
});

const Bufferless = { concat(chunks: Uint8Array[], size: number) { const out=new Uint8Array(size); let offset=0; for (const chunk of chunks) { out.set(chunk,offset); offset+=chunk.length; } return out; } };

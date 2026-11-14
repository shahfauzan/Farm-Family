import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';
const headers = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'authorization, apikey, content-type', 'Content-Type': 'application/json' };
Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers });
  if (req.method !== 'POST') return new Response(JSON.stringify({ error: { code: 'METHOD_NOT_ALLOWED' } }), { status: 405, headers });
  const auth = req.headers.get('Authorization');
  let body: { token?: string };
  try { body = await req.json(); } catch { return new Response(JSON.stringify({ error: { code: 'INVALID_JSON' } }), { status: 400, headers }); }
  if (!auth || typeof body.token !== 'string' || body.token.length !== 64) return new Response(JSON.stringify({ error: { code: 'INVALID_REQUEST' } }), { status: 400, headers });
  const db = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_ANON_KEY')!, { global: { headers: { Authorization: auth } }, auth: { persistSession: false } });
  const { data: { user }, error: userError } = await db.auth.getUser();
  if (userError || !user) return new Response(JSON.stringify({ error: { code: 'UNAUTHENTICATED' } }), { status: 401, headers });
  const { data, error } = await db.rpc('accept_family_invitation', { p_token: body.token });
  if (error) return new Response(JSON.stringify({ error: { code: error.code === '22023' ? 'INVITATION_INVALID_OR_EXPIRED' : 'INVITATION_ACCEPT_FAILED' } }), { status: 400, headers });
  return new Response(JSON.stringify({ family_id: data }), { status: 200, headers });
});

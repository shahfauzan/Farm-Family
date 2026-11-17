import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';

const headers = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, apikey, content-type',
  'Content-Type': 'application/json',
};
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers });
const bucket = 'profile-avatars';
const maxImageBytes = 1_500_000;
const maxRequestBytes = 2_050_000;

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers });
  if (req.method !== 'POST') return json(405, { error: { code: 'METHOD_NOT_ALLOWED' } });
  const authorization = req.headers.get('Authorization');
  const url = Deno.env.get('SUPABASE_URL');
  const anonKey = Deno.env.get('SUPABASE_ANON_KEY');
  const serviceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  if (!authorization || !url || !anonKey || !serviceKey) return json(503, { error: { code: 'SERVICE_UNAVAILABLE' } });
  const declaredLength = Number(req.headers.get('content-length') ?? 0);
  if (declaredLength > maxRequestBytes) return json(413, { error: { code: 'IMAGE_TOO_LARGE' } });
  let body: { action?: string; image_base64?: string; path?: string };
  try {
    const reader = req.body?.getReader();
    if (!reader) return json(400, { error: { code: 'INVALID_JSON' } });
    const chunks: Uint8Array[] = [];
    let size = 0;
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > maxRequestBytes) { await reader.cancel(); return json(413, { error: { code: 'IMAGE_TOO_LARGE' } }); }
      chunks.push(value);
    }
    const combined = new Uint8Array(size);
    let offset = 0;
    for (const chunk of chunks) { combined.set(chunk, offset); offset += chunk.length; }
    body = JSON.parse(new TextDecoder().decode(combined));
  } catch { return json(400, { error: { code: 'INVALID_JSON' } }); }

  const userClient = createClient(url, anonKey, { global: { headers: { Authorization: authorization } }, auth: { persistSession: false } });
  const { data: { user }, error: authError } = await userClient.auth.getUser();
  if (authError || !user) return json(401, { error: { code: 'UNAUTHENTICATED' } });
  const admin = createClient(url, serviceKey, { auth: { persistSession: false } });
  const path = `${user.id}/avatar.jpg`;

  if (body.action === 'upload') {
    if (typeof body.image_base64 !== 'string') return json(400, { error: { code: 'INVALID_IMAGE' } });
    let binary: Uint8Array;
    try { binary = Uint8Array.from(atob(body.image_base64), (character) => character.charCodeAt(0)); }
    catch { return json(400, { error: { code: 'INVALID_IMAGE' } }); }
    if (!binary.length || binary.length > maxImageBytes) return json(413, { error: { code: 'IMAGE_TOO_LARGE' } });
    const bucketState = await admin.storage.getBucket(bucket);
    if (bucketState.error) {
      const created = await admin.storage.createBucket(bucket, { public: false, fileSizeLimit: maxImageBytes, allowedMimeTypes: ['image/jpeg'] });
      if (created.error && !/already exists/i.test(created.error.message)) return json(500, { error: { code: 'AVATAR_STORAGE_UNAVAILABLE' } });
    }
    const { error: uploadError } = await admin.storage.from(bucket).upload(path, binary, { contentType: 'image/jpeg', upsert: true, cacheControl: '3600' });
    if (uploadError) return json(500, { error: { code: 'AVATAR_UPLOAD_FAILED' } });
    return json(200, { path });
  }

  if (body.action === 'signed_url') {
    if (body.path !== path) return json(403, { error: { code: 'AVATAR_ACCESS_DENIED' } });
    const { data, error } = await admin.storage.from(bucket).createSignedUrl(path, 3600);
    if (error || !data) return json(404, { error: { code: 'AVATAR_NOT_FOUND' } });
    return json(200, { url: data.signedUrl, expires_in_seconds: 3600 });
  }
  if (body.action === 'remove') {
    const { error } = await admin.storage.from(bucket).remove([path]);
    if (error && !/not found/i.test(error.message)) return json(500, { error: { code: 'AVATAR_REMOVE_FAILED' } });
    return json(200, { removed: true });
  }
  return json(400, { error: { code: 'INVALID_REQUEST' } });
});

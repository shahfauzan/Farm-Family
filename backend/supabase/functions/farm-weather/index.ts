import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';
import { parseWeatherRequest } from './validation.ts';
import { normalizeCurrentWeather, normalizeWeatherForecast } from './responses.ts';

const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Content-Type': 'application/json',
};
const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: cors });
const MAX_BODY_BYTES = 2048;
const OPENWEATHER_BASE = 'https://api.openweathermap.org/data/2.5';

async function readJson(req: Request): Promise<unknown | Response> {
  const declaredLength = Number(req.headers.get('content-length') ?? 0);
  if (declaredLength > MAX_BODY_BYTES) return json(413, { error: { code: 'PAYLOAD_TOO_LARGE' } });
  const reader = req.body?.getReader();
  if (!reader) return json(400, { error: { code: 'INVALID_JSON' } });
  const chunks: Uint8Array[] = [];
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_BODY_BYTES) {
        await reader.cancel();
        return json(413, { error: { code: 'PAYLOAD_TOO_LARGE' } });
      }
      chunks.push(value);
    }
  } catch {
    return json(400, { error: { code: 'INVALID_JSON' } });
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  try {
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return json(400, { error: { code: 'INVALID_JSON' } });
  }
}

function isResponse(value: unknown): value is Response {
  return value instanceof Response;
}

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: cors });
  if (req.method !== 'POST') return json(405, { error: { code: 'METHOD_NOT_ALLOWED' } });

  const auth = req.headers.get('Authorization');
  const url = Deno.env.get('SUPABASE_URL');
  const anonKey = Deno.env.get('SUPABASE_ANON_KEY');
  const weatherKey = Deno.env.get('OPENWEATHER_API_KEY');
  if (!auth || !url || !anonKey || !weatherKey) {
    return json(503, { error: { code: 'SERVICE_UNAVAILABLE' } });
  }

  const body = await readJson(req);
  if (isResponse(body)) return body;
  const input = parseWeatherRequest(body);
  if (!input) return json(400, { error: { code: 'INVALID_REQUEST' } });

  const db = createClient(url, anonKey, {
    global: { headers: { Authorization: auth } },
    auth: { persistSession: false },
  });
  const { data: { user }, error: userError } = await db.auth.getUser();
  if (userError || !user) return json(401, { error: { code: 'UNAUTHENTICATED' } });

  const { data: isMember, error: membershipError } = await db.rpc('is_family_member', {
    p_family: input.family_id,
  });
  if (membershipError) return json(503, { error: { code: 'AUTHORIZATION_CHECK_FAILED' } });
  if (!isMember) return json(403, { error: { code: 'FAMILY_ACCESS_DENIED' } });

  const { data: withinQuota, error: quotaError } = await db.rpc('consume_weather_quota', {
    p_family: input.family_id,
  });
  if (quotaError) return json(503, { error: { code: 'WEATHER_LIMIT_UNAVAILABLE' } });
  if (!withinQuota) {
    return json(429, { error: { code: 'RATE_LIMITED', retry_after_seconds: 3600 } });
  }

  const path = input.mode === 'forecast' ? 'forecast' : 'weather';
  const endpoint = new URL(`${OPENWEATHER_BASE}/${path}`);
  endpoint.searchParams.set('lat', String(input.latitude));
  endpoint.searchParams.set('lon', String(input.longitude));
  endpoint.searchParams.set('units', 'metric');
  endpoint.searchParams.set('appid', weatherKey);
  if (input.mode === 'forecast') endpoint.searchParams.set('cnt', '8');

  let upstream: Response;
  try {
    upstream = await fetch(endpoint, { signal: AbortSignal.timeout(8000) });
  } catch {
    return json(504, { error: { code: 'WEATHER_PROVIDER_TIMEOUT' } });
  }
  if (!upstream.ok) {
    if (upstream.status === 401 || upstream.status === 403) {
      return json(503, { error: { code: 'WEATHER_PROVIDER_CONFIGURATION' } });
    }
    if (upstream.status === 429) {
      return json(503, { error: { code: 'WEATHER_PROVIDER_LIMITED' } });
    }
    return json(502, { error: { code: 'WEATHER_PROVIDER_UNAVAILABLE' } });
  }

  let providerData: unknown;
  try {
    providerData = await upstream.json();
  } catch {
    return json(502, { error: { code: 'INVALID_WEATHER_RESPONSE' } });
  }
  const data = input.mode === 'forecast'
    ? normalizeWeatherForecast(providerData, input.latitude, input.longitude)
    : normalizeCurrentWeather(providerData, input.latitude, input.longitude);
  if (!data) return json(502, { error: { code: 'INVALID_WEATHER_RESPONSE' } });
  return json(200, { provider: 'openweathermap', mode: input.mode, data });
});

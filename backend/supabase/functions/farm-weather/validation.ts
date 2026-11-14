export type WeatherRequest = {
  family_id: string;
  latitude: number;
  longitude: number;
  mode: 'current' | 'forecast';
};

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function parseWeatherRequest(value: unknown): WeatherRequest | null {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) return null;
  const body = value as Record<string, unknown>;
  if (
    typeof body.family_id !== 'string' ||
    !uuidPattern.test(body.family_id) ||
    typeof body.latitude !== 'number' ||
    !Number.isFinite(body.latitude) ||
    body.latitude < -90 ||
    body.latitude > 90 ||
    typeof body.longitude !== 'number' ||
    !Number.isFinite(body.longitude) ||
    body.longitude < -180 ||
    body.longitude > 180
  ) return null;

  const mode = body.mode ?? 'current';
  if (mode !== 'current' && mode !== 'forecast') return null;
  return {
    family_id: body.family_id,
    latitude: body.latitude,
    longitude: body.longitude,
    mode,
  };
}

import { parseWeatherRequest } from './validation.ts';
import { normalizeCurrentWeather, normalizeWeatherForecast } from './responses.ts';

function assertEquals(actual: unknown, expected: unknown) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`Expected ${JSON.stringify(expected)}, received ${JSON.stringify(actual)}`);
  }
}

Deno.test('weather request defaults to current observations', () => {
  assertEquals(parseWeatherRequest({
    family_id: '11111111-1111-4111-8111-111111111111',
    latitude: 27.7,
    longitude: 85.3,
  }), {
    family_id: '11111111-1111-4111-8111-111111111111',
    latitude: 27.7,
    longitude: 85.3,
    mode: 'current',
  });
});

Deno.test('weather request accepts forecast mode and geographic boundaries', () => {
  assertEquals(parseWeatherRequest({
    family_id: '11111111-1111-4111-8111-111111111111',
    latitude: -90,
    longitude: 180,
    mode: 'forecast',
  })?.mode, 'forecast');
});

Deno.test('weather request rejects invalid family, coordinates, and modes', () => {
  const family_id = '11111111-1111-4111-8111-111111111111';
  for (const payload of [
    { family_id: 'not-a-uuid', latitude: 0, longitude: 0 },
    { family_id, latitude: 90.01, longitude: 0 },
    { family_id, latitude: 0, longitude: -180.01 },
    { family_id, latitude: Number.NaN, longitude: 0 },
    { family_id, latitude: 0, longitude: 0, mode: 'history' },
    null,
    [],
  ]) {
    assertEquals(parseWeatherRequest(payload), null);
  }
});

Deno.test('weather responses normalize provider data and reject malformed payloads', () => {
  const current = normalizeCurrentWeather({
    name: 'Test City',
    dt: 1794614400,
    main: { temp: 18, feels_like: 17, temp_min: 16, temp_max: 19, humidity: 60, pressure: 1012 },
    weather: [{ id: 800, main: 'Clear', description: 'clear sky', icon: '01d' }],
  }, 27.7, 85.3);
  assertEquals(current?.location, {
    name: 'Test City',
    country: null,
    latitude: 27.7,
    longitude: 85.3,
  });
  assertEquals(normalizeCurrentWeather(null, 0, 0), null);
  assertEquals(normalizeWeatherForecast({ city: {}, list: [null] }, 0, 0), null);
});

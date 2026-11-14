type JsonObject = Record<string, unknown>;

function object(value: unknown): JsonObject | null {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as JsonObject
    : null;
}

function finiteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function condition(value: unknown): JsonObject | null {
  const entry = object(value);
  if (
    !entry ||
    !finiteNumber(entry.id) ||
    typeof entry.main !== 'string' ||
    typeof entry.description !== 'string' ||
    typeof entry.icon !== 'string'
  ) return null;
  return entry;
}

function timestamp(seconds: unknown): string | null {
  if (!finiteNumber(seconds)) return null;
  const milliseconds = seconds * 1000;
  if (!Number.isFinite(milliseconds)) return null;
  const date = new Date(milliseconds);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

function hasNumbers(value: JsonObject, keys: string[]): boolean {
  return keys.every((key) => finiteNumber(value[key]));
}

export function normalizeCurrentWeather(value: unknown, latitude: number, longitude: number) {
  const weather = object(value);
  const main = object(weather?.main);
  const conditions = Array.isArray(weather?.weather) ? weather.weather : [];
  const currentCondition = condition(conditions[0]);
  const observedAt = timestamp(weather?.dt);
  if (
    !weather ||
    !main ||
    !currentCondition ||
    !observedAt ||
    !hasNumbers(main, ['temp', 'feels_like', 'temp_min', 'temp_max', 'humidity', 'pressure'])
  ) return null;

  const system = object(weather.sys);
  const wind = object(weather.wind);
  const clouds = object(weather.clouds);
  return {
    location: {
      name: typeof weather.name === 'string' ? weather.name : '',
      country: typeof system?.country === 'string' ? system.country : null,
      latitude,
      longitude,
    },
    observed_at: observedAt,
    temperature_c: main.temp,
    feels_like_c: main.feels_like,
    minimum_temperature_c: main.temp_min,
    maximum_temperature_c: main.temp_max,
    humidity_percent: main.humidity,
    pressure_hpa: main.pressure,
    wind_speed_mps: finiteNumber(wind?.speed) ? wind.speed : null,
    wind_direction_degrees: finiteNumber(wind?.deg) ? wind.deg : null,
    cloud_cover_percent: finiteNumber(clouds?.all) ? clouds.all : null,
    condition: {
      id: currentCondition.id,
      main: currentCondition.main,
      description: currentCondition.description,
      icon: currentCondition.icon,
    },
  };
}

export function normalizeWeatherForecast(value: unknown, latitude: number, longitude: number) {
  const forecast = object(value);
  const city = object(forecast?.city);
  if (!forecast || !city || !Array.isArray(forecast.list) || !finiteNumber(city.timezone)) return null;

  const items = forecast.list.slice(0, 8).flatMap((value) => {
    const item = object(value);
    const main = object(item?.main);
    const conditions = Array.isArray(item?.weather) ? item.weather : [];
    const itemCondition = condition(conditions[0]);
    const forecastAt = timestamp(item?.dt);
    const precipitation = item?.pop === undefined ? 0 : item.pop;
    if (
      !item ||
      !main ||
      !itemCondition ||
      !forecastAt ||
      !finiteNumber(precipitation) ||
      precipitation < 0 ||
      precipitation > 1 ||
      !hasNumbers(main, ['temp', 'feels_like', 'temp_min', 'temp_max', 'humidity'])
    ) return [];
    const wind = object(item.wind);
    return [{
      forecast_at: forecastAt,
      temperature_c: main.temp,
      feels_like_c: main.feels_like,
      minimum_temperature_c: main.temp_min,
      maximum_temperature_c: main.temp_max,
      humidity_percent: main.humidity,
      precipitation_probability_percent: Math.round(precipitation * 100),
      wind_speed_mps: finiteNumber(wind?.speed) ? wind.speed : null,
      condition: {
        id: itemCondition.id,
        main: itemCondition.main,
        description: itemCondition.description,
        icon: itemCondition.icon,
      },
    }];
  });
  if (items.length === 0) return null;

  return {
    location: {
      name: typeof city.name === 'string' ? city.name : '',
      country: typeof city.country === 'string' ? city.country : null,
      latitude,
      longitude,
    },
    timezone_offset_seconds: city.timezone,
    items,
  };
}

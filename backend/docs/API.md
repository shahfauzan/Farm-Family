# FARM FAMILY backend contract

## Authentication

Use Supabase Auth email/password only. The auth trigger creates `profiles`; clients must treat the JWT subject (`sub`) as the profile id. No Google OAuth provider or Google credentials are required for this backend. No service-role key belongs in the app.

## Family lifecycle

- `rpc('create_family', { p_name, p_currency })` atomically creates a family and its OWNER membership. Default currency is NPR.
- `rpc('create_family_invitation', { p_family, p_role, p_valid_for })` is OWNER-only and returns `{ invitation_id, token, expires_at }`. Show/share the token as a one-time link or QR payload; only SHA-256 is stored. The caller must protect the returned bearer token.
- `POST /functions/v1/accept-invitation` with `{ "token": "..." }` and a user JWT accepts once and returns `{ "family_id": "uuid" }`.
- OWNER administers memberships with `set_family_member(p_family,p_user,p_role,p_can_view,p_can_add,p_can_edit,p_can_delete)`, `remove_family_member(p_family,p_user)`, and `restore_family_member(p_family,p_user)`. `revoke_family_invitation(p_invitation)` revokes a pending invite. These actions are owner-checked and audit logged.

## Family data and sync

Tables are queried through Supabase PostgREST with `family_id` as a query convenience only; RLS is the authorization boundary. Most synchronized records expose `id` (server id), `local_id`, `sync_status`, `last_synced_at`, `record_version`, timestamps, and soft-delete fields. Send a stable `local_id` unique within the family for idempotent client reconciliation.

For versioned updates, call `rpc('sync_update', { p_table, p_id, p_family, p_expected, p_data })`. `p_table` must be allowlisted and `p_data` accepts only columns permitted for that relation. Success returns `{status:"SYNCED",record:{...}}`; a stale write returns `{status:"CONFLICT",code:"VERSION_CONFLICT",expected_version,actual_version,server_record}` and persists a conflict row. The conditional UPDATE and version check are one SQL statement. Direct PostgREST UPDATE is disabled by RLS. Inserts use table RLS and server-generated ids/sync metadata. Use `soft_delete_record(p_table,p_id,p_family,p_expected)` and owner-only `restore_record(...)` for deletion lifecycle.

Soft deletion is `deleted_at`/`deleted_by`; restoration clears both. These fields are included on the main farm records. Audit logging captures writes on the principal record tables. Do not grant client writes to `audit_logs`.

## Reports and AI

Reports include `family_season_summary(p_family,p_from,p_to)`, `family_year_summary(p_family,p_year)`, `family_crop_report(p_family,p_crop)`, `family_field_report(p_family)`, `family_activity_report(p_family,p_kind,p_from,p_to,p_limit)`, `family_finance_summary(p_family,p_from,p_to)`, `family_search(p_family,p_query,p_limit)`, and `family_historical_price_report(p_family,p_crop,p_from,p_to)`. Activity kinds are allowlisted (`expenses`, `sales`, `harvest`, `labor`, `seeds`, `fertilizer`, `machinery`). Profit is nullable until the OWNER marks season costs and revenue complete. Mixed currencies or measurement units produce null aggregate totals where the report cannot safely combine them. These financial semantics still need runtime review.

`POST /functions/v1/ai-assistant` with `{ "family_id":"uuid", "question":"..." }` is authenticated and read-only. It supports allowlisted seasonal/year summaries, search, crop comparison, finance summaries, and historical-price queries. Server code selects fixed RPCs; model SQL is never executed. Requests are capped at 8 KiB, questions at 2,000 characters, results at 100 rows, and database quota at 15 per user per 10 minutes. Writes and deletions are unsupported. Configure `OPENAI_API_KEY` and optional `OPENAI_MODEL` as Edge Function secrets.

## Weather

`POST /functions/v1/farm-weather` proxies OpenWeather current conditions or a short forecast. The client must send a valid Supabase user JWT and belong to the requested family. The backend accepts only latitude/longitude coordinates, constructs a fixed OpenWeather URL, and never returns the provider API key. Requests are limited to 60 per authenticated user per rolling hour across families.

Current conditions request:

```http
POST /functions/v1/farm-weather
Authorization: Bearer <user_access_token>
Content-Type: application/json
```

```json
{
  "family_id": "11111111-1111-4111-8111-111111111111",
  "latitude": 27.7172,
  "longitude": 85.324
}
```

Forecast request uses the same body with `"mode": "forecast"`. Coordinates must be numeric and within latitude `[-90,90]` and longitude `[-180,180]`. If omitted, `mode` defaults to `current`.

Current conditions response:

```json
{
  "provider": "openweathermap",
  "mode": "current",
  "data": {
    "location": { "name": "Kathmandu", "country": "NP", "latitude": 27.7172, "longitude": 85.324 },
    "observed_at": "2026-11-14T00:00:00.000Z",
    "temperature_c": 18.4,
    "feels_like_c": 18.1,
    "minimum_temperature_c": 17.2,
    "maximum_temperature_c": 19.1,
    "humidity_percent": 65,
    "pressure_hpa": 1013,
    "wind_speed_mps": 1.2,
    "wind_direction_degrees": 180,
    "cloud_cover_percent": 20,
    "condition": { "id": 800, "main": "Clear", "description": "clear sky", "icon": "01d" }
  }
}
```

Forecast responses contain `location`, `timezone_offset_seconds`, and up to eight `items` with `forecast_at`, temperatures, humidity, precipitation probability, wind speed, and condition. Provider values use metric units; timestamps are UTC ISO-8601.

Errors: `400 INVALID_JSON|INVALID_REQUEST`, `401 UNAUTHENTICATED`, `403 FAMILY_ACCESS_DENIED`, `413 PAYLOAD_TOO_LARGE`, `429 RATE_LIMITED` (includes `retry_after_seconds`), `502 WEATHER_PROVIDER_UNAVAILABLE|INVALID_WEATHER_RESPONSE`, `503 SERVICE_UNAVAILABLE|AUTHORIZATION_CHECK_FAILED|WEATHER_LIMIT_UNAVAILABLE|WEATHER_PROVIDER_CONFIGURATION|WEATHER_PROVIDER_LIMITED`, and `504 WEATHER_PROVIDER_TIMEOUT`.

Configure `OPENWEATHER_API_KEY` as a server-side Edge Function secret. Never put it in Android configuration.

`POST /functions/v1/family-backups` supports `{action:"export",family_id,format:"CSV"|"XLSX"|"PDF"|"ZIP"}` and `{action:"download",backup_id}`. Both operations are OWNER-only. Objects are private in `family-backups` under `<family_id>/<backup_id>.<ext>` and downloads return a 60-second signed URL. ZIP includes table CSV files and `metadata.json`; CSV is a combined table/JSON-row export; XLSX has one worksheet per table; PDF is a summary/count report. Export caps are 50,000 rows and 50 MiB.

`POST /functions/v1/family-imports` supports `{action:"preview",backup_id}` for a prior ZIP backup, or `{action:"preview",family_id,storage_path}` after an OWNER uploads a local ZIP to the private path `<family_id>/imports/<name>.zip`. It returns `import_id`, per-table counts, same-ID overwrite counts, and a 30-minute expiry. Explicit confirmation is `{action:"confirm",import_id}` and returns per-table result counts. CSV/XLSX/PDF are not accepted for restore.

## Stable record shape (example)

```json
{
  "id": "server-uuid",
  "family_id": "family-uuid",
  "local_id": "room-row-123",
  "name": "North field",
  "sync_status": "PENDING",
  "last_synced_at": null,
  "record_version": 1,
  "created_at": "2026-11-14T00:00:00Z",
  "updated_at": "2026-11-14T00:00:00Z",
  "deleted_at": null,
  "deleted_by": null
}
```

Dates in storage/API are Gregorian ISO dates. Land unit is a value plus unit label (for example `bigha`); the service does not apply a universal conversion. Currency is ISO 4217 style and defaults to NPR.

## Backup and restore

Export, metadata history, private storage and signed downloads are implemented. ZIP import preview stages a validated family snapshot; explicit OWNER confirmation calls one transactional merge RPC. It rejects unsupported schema versions, cross-family IDs, duplicate IDs, malformed record arrays, invalid foreign keys and database constraint violations. Same-family matching server IDs are updated, while new rows are inserted; rows absent from the backup are not deleted. A failed import rolls back the whole RPC. Import accepts this backend's ZIP schema only, not arbitrary CSV/XLSX/PDF files. Never expose the bucket publicly.

## Error shape

Edge endpoints return `{ "error": { "code": "...", "message": "..." } }`; Postgres RPC errors also include SQLSTATE. Map `40001` to offline conflict UI and do not silently retry with overwrite.

## Configuration and secrets

- `SUPABASE_URL`, `SUPABASE_ANON_KEY`: Edge Function runtime values. Android uses project URL and publishable/anon key with user JWT; these are not secrets.
- `OPENAI_API_KEY`: required only by AI Edge Function; server secret, never Android.
- `OPENAI_MODEL`: optional server-side model override.
- `OPENWEATHER_API_KEY`: required by the `farm-weather` Edge Function; server secret, never Android.
- `SUPABASE_SERVICE_ROLE_KEY`: these Edge Functions do not require it. Never place it in Android.

## Android integration status

The sibling `../frontend` Android app uses Supabase Auth and the existing family/field contracts. It caches field records in local SQLite and queues field inserts for retry on refresh; other farm-data modules, background synchronization, and conflict resolution are not integrated. Live Supabase and on-device end-to-end behavior have not been verified.

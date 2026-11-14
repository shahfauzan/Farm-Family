# Architecture

```text
Android frontend
      |
      | Supabase client/API (integration planned)
      v
Supabase
  ├── Auth
  ├── PostgreSQL
  ├── Storage
  └── Edge Functions
```

The Android application is a Kotlin/Jetpack Compose preview backed by an in-memory mock repository. It does not currently authenticate with Supabase or make backend requests. The backend/API shapes are documented in [backend/docs/API.md](../backend/docs/API.md).

Supabase is the system of record. PostgreSQL stores family and farm data, with database migrations and row-level security policies under `backend/supabase/migrations`. Supabase Auth supplies user identity; private Storage supports family backups; Edge Functions provide invitation acceptance, AI analysis, backup/export/import, and weather access.

GitHub contains source code and runs CI/release automation. It is not a database or a replacement for Supabase. The release workflow deploys migrations and Edge Functions; frontend distribution is currently an unsigned Android APK attached to a GitHub Release.

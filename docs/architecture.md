# Architecture

```text
Android Compose
      |
      | Supabase Auth email/password (implemented)
      v
Supabase Auth ── family create/join + membership lookup (implemented)
      |
      └── family fields and current season via PostgREST (online, implemented)

Room cache, ViewModels, offline queue, conflict resolution, reports, and remaining Edge Function clients: pending
```

The Android app authenticates against Supabase Auth using email/password, restores or refreshes the saved session, and encrypts the session with Android Keystore. It requires the project URL and publishable/anon key at build time. Authenticated users can create or join a family, load memberships, and create/reload fields from the authorized Supabase family. Home and field states use live backend responses; the former in-memory mock screens are not the production path. Room persistence, ViewModels, offline sync, conflict resolution, reports, AI, weather, notifications, and backup/import are still pending. The backend/API shapes are documented in [backend/docs/API.md](../backend/docs/API.md).

Supabase is the system of record. PostgreSQL stores family and farm data, with database migrations and row-level security policies under `backend/supabase/migrations`. Supabase Auth supplies user identity; private Storage supports family backups; Edge Functions provide invitation acceptance, AI analysis, backup/export/import, and weather access.

GitHub contains source code and runs CI/release automation. It is not a database or a replacement for Supabase. The release workflow deploys migrations and Edge Functions; frontend distribution is currently an unsigned Android APK attached to a GitHub Release.

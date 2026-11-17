# FARM FAMILY backend baseline

Supabase/PostgreSQL backend foundation for FARM FAMILY (Grow Together. Prosper Together.). This baseline uses Supabase Auth, PostgreSQL migrations/RLS, and Edge Functions.

## Setup

1. Install Supabase CLI and Docker, then run `supabase start`.
2. Apply all migrations with `supabase db reset`.
3. Run pgTAP schema checks with `supabase test db`.
4. Configure email/password auth in Supabase Auth and enable confirmation email/SMTP as needed for your environment.
5. Set server secrets with `supabase secrets set OPENAI_API_KEY=... OPENWEATHER_API_KEY=... OPENAI_MODEL=...` (`OPENAI_MODEL` is optional).
6. Deploy `accept-invitation`, `ai-assistant`, `family-backups`, `family-imports`, and `farm-weather` after deploying migrations.

Auth uses Supabase email/password only. Configure production redirect URLs, confirmation email, SMTP, Storage, backups, and secret rotation in the hosted project. See [docs/API.md](docs/API.md) for frontend shapes and limitations.

## Current scope

Included: profiles, families/memberships, one-time hashed invitations and revocation, owner-only member RPCs, farm/crop/input/finance/production tables, RLS, cross-family relationship checks, audit capture, notifications, server-controlled sync metadata, atomic version-checked updates, soft delete/restore, reports, private CSV/XLSX/PDF/ZIP backup export, transactional ZIP import, rate-limited AI analysis, and authenticated OpenWeather current/forecast access.

The sibling `../frontend` provides authenticated family setup, member and invitation management, online CRUD across farm modules, reports, read-only AI, weather, notifications, profile/settings, backups, and import preview/confirmation. Offline create queues are implemented; offline edits/deletes and background synchronization remain incomplete. The hosted project has live tables and Edge Functions, but its migration history does not record repository migrations 001–005. Reconcile that history before automated production releases. See [security testing](docs/SECURITY_TESTING.md) for local-stack and deployed-project checks still required.

## Required configuration

- `SUPABASE_URL`, `SUPABASE_ANON_KEY`: Supabase Edge Function runtime and Android client settings (anon/publishable key is public; user JWT authorizes calls).
- `OPENAI_API_KEY`: Edge Function secret, only for AI; never Android.
- `OPENAI_MODEL`: optional Edge Function secret override.
- `OPENWEATHER_API_KEY`: Edge Function secret for the authenticated `farm-weather` endpoint; never Android.
- `SUPABASE_SERVICE_ROLE_KEY`: not used by these Edge Functions. Never put it in Android.
- Supabase local tests require Docker. Hosted migration deployment requires project link/access and database credentials.

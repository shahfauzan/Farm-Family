# Database and migrations

## System of record

Production data is stored in Supabase PostgreSQL. GitHub stores source and deployment automation only.

## Migration workflow

Migrations live in `backend/supabase/migrations/` and use timestamped, chronological filenames. The existing migration files are:

- `202611140001_farm_family.sql`
- `202611140002_secure_operations.sql`
- `202611140003_weather_quota.sql`
- `202611140004_unit_safe_harvest_reports.sql`
- `202611140005_finance_currency_validation.sql`
- `202611160006_family_member_roster.sql`

Do not edit, delete, rename, reorder, squash, or manually mark existing migrations as applied. Add new schema changes as new timestamped migrations.

The CI embedded test applies all five migrations to PGlite with mocked Supabase Auth/Storage dependencies. This catches many SQL and contract regressions, but does not prove behavior on hosted Supabase. Use the local Supabase stack and `supabase test db` for deeper checks.

## Security

The schema uses PostgreSQL row-level security and database-side authorization for family data and operations. Keep service-role credentials and provider API keys server-side; never include them in Android builds or source control. Review policy and function changes carefully and run isolation tests with separate users and families.

## Production changes and backups

Production migrations are applied only by the release workflow, after a database dry run verifies that the GitHub runner can connect and authenticate. A failed migration stops the release before Edge Function deployment or GitHub Release creation. Never bypass Supabase migration tracking or apply destructive SQL without explicit authorization.

Maintain Supabase-managed backups and verify restore procedures independently. A database backup is not a substitute for testing migrations and restore paths.

## Hosted project state (2026-11-17)

The connected `farm-family` project is active. The family member roster migration is applied and its function and profile policy were verified. The hosted migration history is incomplete: it contains only the plugin-applied roster entry `20261117032903` and does not contain repository migrations `202611140001` through `202611140005` or the local version `202611160006`. Reconcile the hosted migration history with the existing schema before using the release workflow; otherwise it may attempt to replay baseline DDL against tables that already exist. Do not treat this project as ready for automated migration releases until that reconciliation has been completed.

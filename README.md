# Farm Family

Farm Family is a family-oriented farm management application. This repository brings together its Android frontend, Supabase backend, database migrations, Edge Functions, and CI/release automation.

## Features

- Android app preview for farm records, fields, expenses, reports, family activity, settings, and AI assistance.
- Supabase backend foundation for family accounts, farm records, invitations, synchronization, reports, backups/imports, AI, and weather.
- PostgreSQL migrations with row-level security and database-side operations.

The Android app currently uses mock, in-memory data. Its Supabase API contract is documented, but authentication, synchronization, and backend calls are not integrated yet.

## Architecture

- **Frontend:** Kotlin, Jetpack Compose, Android SDK 36.
- **Backend:** Supabase Auth, PostgreSQL, Storage, and five Supabase Edge Functions.
- **Database:** PostgreSQL remains hosted by Supabase; GitHub is used for source control and automation only.
- **Automation:** GitHub Actions validates changes and runs manually/tag-triggered production releases.

See [architecture](docs/architecture.md), [database](docs/database.md), and [deployment](docs/deployment.md).

## Repository layout

```text
backend/       Supabase configuration, migrations, functions, and tests
frontend/      Native Android application
docs/          Architecture, database, and deployment guides
.github/       CI and production release workflows
```

## Local development

### Backend

Requirements: Node.js 22, Deno 2, and Docker Desktop (for the local Supabase stack).

```powershell
npm.cmd ci
cd backend
deno test supabase/functions/farm-weather/validation.test.ts
deno run --allow-read supabase/tests/embedded_migrations.ts
npx.cmd --no-install supabase start
npx.cmd --no-install supabase db reset
npx.cmd --no-install supabase test db
```

The embedded migration test does not replace tests against a real Supabase stack. See [backend security testing](backend/docs/SECURITY_TESTING.md) for known validation limits.

### Android frontend

Requirements: Android Studio, Android SDK 36, and Java 17+. Open `frontend/` in Android Studio or build from that directory:

```powershell
.\gradlew.bat --no-daemon assembleDebug
```

The Gradle wrapper pins the project Gradle version. CI also builds the debug APK.
Release builds require the release keystore and signing environment variables documented in [the frontend README](frontend/README.md).

## Configuration and secrets

Copy [backend/.env.example](backend/.env.example) to `backend/.env` for local configuration and replace placeholders locally. Never commit `.env` files, database passwords, access tokens, service-role keys, or third-party API keys. Client builds must use only the Supabase project URL and publishable/anon key; server-only keys belong in Supabase Edge Function secrets.

Production deployment uses the GitHub `production` Environment:

- Secrets: `SUPABASE_ACCESS_TOKEN`, `SUPABASE_DB_PASSWORD`
- Variable: `SUPABASE_PROJECT_REF`

Configure required Edge Function API keys directly as Supabase secrets. Do not put their values in GitHub workflow files or logs.
Android release signing uses the repository Actions Secrets `ANDROID_RELEASE_KEYSTORE_BASE64`, `ANDROID_RELEASE_KEYSTORE_PASSWORD`, `ANDROID_RELEASE_KEY_ALIAS`, and `ANDROID_RELEASE_KEY_PASSWORD`. Never commit the keystore or signing credentials.

## Testing and CI

The root [build workflow](.github/workflows/build.yml) runs Edge Function validation tests, embedded migration/backend contract tests, and an Android debug build on pushes, pull requests, and manual runs. It does not deploy to production.

## Production releases

Push a semantic version tag such as `v1.2.3`, or manually run the [release workflow](.github/workflows/release.yml) with a semantic version. The workflow validates first, builds an Android release APK, checks database connectivity with a migration dry run, applies pending migrations, deploys Edge Functions, and then creates a GitHub Release.

The release workflow verifies the signed APK and attaches it to the GitHub Release. APK signing does not imply Play Store readiness. The release workflow has not yet been verified against the hosted Supabase project.

Do not declare production readiness until the production deployment and real Supabase runtime checks have succeeded.

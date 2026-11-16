# FARM FAMILY Android frontend

A native Android frontend for FARM FAMILY built with Kotlin and Jetpack Compose.

## Implemented

- Supabase email/password sign-in, sign-up, password recovery, and sign-out.
- Session restore/refresh with tokens encrypted by Android Keystore.
- Email confirmation and recovery deep links back into the app.
- Family create/join and authorized family membership loading.
- Figma-aligned home, family setup, and field list/create screens backed by Supabase.
- Field creation is server-authorized and uses a stable local ID so an uncertain network response can be reconciled safely.
- Family-scoped SQLite cache for fields, plus a durable insert queue that retries on refresh and distinguishes offline pending writes from server errors.

## Supabase configuration

Set the project URL and publishable/anon key using environment variables, Gradle properties, or `frontend/local.properties`:

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-publishable-or-anon-key
```

Only the client-safe publishable/anon key belongs in the Android app. Never use a service-role key. The URL must use HTTPS. Without configuration the app reports the missing settings and does not bypass authentication.

## Required build environment

Install the Android SDK, JDK 17+, and Gradle wrapper before running the project.

## Build commands

From the frontend folder:

```bash
./gradlew assembleDebug
```

The project must be launched with a real Android emulator or device after a successful debug assemble.

Release builds require `FARM_FAMILY_RELEASE_STORE_FILE`, `FARM_FAMILY_RELEASE_STORE_PASSWORD`, `FARM_FAMILY_RELEASE_KEY_ALIAS`, and `FARM_FAMILY_RELEASE_KEY_PASSWORD` in the build environment. The GitHub Actions workflows read the keystore and credentials from repository Actions Secrets; never commit the keystore or signing credentials.

```bash
./gradlew assembleRelease
```

The signed APK is written to `app/build/outputs/apk/release/app-release.apk`.

## Current implementation boundary

Offline persistence and retry are currently limited to field records. Queued inserts are retried when records are refreshed; there is no background connectivity worker, general-purpose sync engine, edit/delete queue, or version-conflict resolution UI. Family setup, authentication, and crop-season reads still require network access. Members/invitations, other record modules, reports, AI, weather, notifications, backup/import/export, and most designed screens remain unimplemented. The Android screens have not been runtime-tested against a live Supabase project or compared visually on a device. Do not treat the APK as production-ready. See [the frontend contract](FRONTEND_CONTRACT.md).

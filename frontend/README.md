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

The app now includes authenticated family setup and membership, family roster and invitation management, online CRUD for the farm record modules, reports, read-only AI, weather, notifications, profile/family settings, backup/export, and ZIP import preview/confirmation. New records can be queued locally and retried when connectivity returns. Offline edits/deletes, background synchronization, and a dedicated conflict-resolution screen remain incomplete. Automated Android unit tests are not present, and runtime/visual verification on a device remains outstanding. See [the frontend contract](FRONTEND_CONTRACT.md) and the [feature matrix](../docs/frontend-rebuild-matrix.md) for the current scope.

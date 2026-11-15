# FARM FAMILY Android frontend

A native Android frontend for FARM FAMILY built with Kotlin and Jetpack Compose. The app is structured around family-scoped data, offline-first repository flow, auth-aware state, and real backend-first operations rather than static demo-only screen logic.

## Current foundation

- Forest green + warm cream design system with standard Material 3 primitives
- FARM FAMILY sprout-and-field brand mark used by the app header, sign-in screen, and adaptive launcher icon
- Real app flow for login, home dashboard, records, reports, AI, more, settings, and sync states
- Family-scoped state and repository model that is ready for Supabase-backed integration
- Local-first repository abstraction with sync metadata and offline handling
- Frontend contract documentation in [FRONTEND_CONTRACT.md](FRONTEND_CONTRACT.md)

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

## Current limits

This rebuild replaces the previous mock-only frontend with an architecture-first foundation. Live Supabase authentication and family-scoped backend integration remain the next operational step after the Android toolchain is available and the real backend project is configured.

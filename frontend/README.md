# FARM FAMILY Android frontend

A native Android frontend for FARM FAMILY built with Kotlin and Jetpack Compose. The app is structured around family-scoped data, offline-first repository flow, auth-aware state, and real backend-first operations rather than static demo-only screen logic.

## Current foundation

- Forest green + warm cream design system with standard Material 3 primitives
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

## Current limits

This rebuild replaces the previous mock-only frontend with an architecture-first foundation. Live Supabase authentication and family-scoped backend integration remain the next operational step after the Android toolchain is available and the real backend project is configured.

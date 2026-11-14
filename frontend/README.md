# FARM FAMILY Android frontend

An independent native Android frontend project for FARM FAMILY, built with Kotlin and Jetpack Compose. It uses a local mock repository and UI state so screens can be reviewed before integration with the finalized backend.

## Open and run

Open this folder in Android Studio with Android SDK 36 and JDK 17 or newer installed, then run the `app` configuration on an emulator or Android device. This workspace currently has no Java, Gradle, Android SDK, or ADB command available, so an APK could not be built here.

The repository-root GitHub Actions workflow builds a debug APK on pushes, pull requests, and manual runs. The production release workflow builds an unsigned release APK after the required Supabase deployment steps succeed. The APK is not signed for Play Store distribution.

## Current frontend foundation

- Forest green and warm cream design system, shared Compose components, and an internal design-system reference screen.
- Five primary destinations: Home, Records, Reports, AI, and More.
- Dashboard with family/date context, seasonal crop, farm KPIs, activity, backup status, AI shortcut, and an offline preview toggle.
- Category-based farm records, field and expense lists, add-field and add-expense forms, search, reports, AI response mock, activity, family members, settings, profile, and backup/export/import UI.
- Centralized app labels and language selection structure for English, Nepali, Awadhi, Hindi, and Urdu.
- Mock data is held behind `FarmRepository`; no backend or database implementation is included.

## Current limits

This is the first implementation pass, not the completed product described in the full specification. The environment has no Android build/runtime tools, so compilation and device/emulator verification remain outstanding. Some sections use local preview interactions and sample content; authentication, syncing, exports/imports, voice input, QR invites, and AI service calls are not connected.

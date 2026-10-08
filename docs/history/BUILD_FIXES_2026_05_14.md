# Project Handoff: Build System & Resource Fixes

This document summarizes the changes made to stabilize the project build and fix resource-related errors.

## Changes Made

### 1. Build System & Plugin Configuration
- **Fixed Plugin Naming Collisions:** Renamed plugin aliases in `gradle/libs.versions.toml` from `android-application` to `androidApp` and `kotlin-android` to `kotlinAndroid`. 
    - *Why:* In Kotlin DSL (`build.gradle.kts`), common names like `android` or `kotlin` can collide with built-in extensions. Using camelCase (e.g., `libs.plugins.androidApp`) avoids these conflicts.
- **AGP Version Adjustment:** Set Android Gradle Plugin (AGP) to `8.7.3` for stability with the current environment.
- **Enabled AndroidX:** Added `android.useAndroidX=true` to `gradle.properties`.
    - *Why:* Modern Android libraries (including Jetpack Compose) require AndroidX to be explicitly enabled.

### 2. SDK & Manifest Updates
- **Increased `minSdk` to 26:** 
    - *Why:* Your `ic_launcher.xml` uses `<adaptive-icon>`, which requires at least API 26. Setting `minSdk` to 26 resolves build errors related to resource linking.
- **Standardized SDK Versions:** Set `compileSdk` and `targetSdk` to 35 to ensure compatibility with modern Android features and Google Play requirements.

### 3. Themes & Resources
- **Material 3 Integration:**
    - Added `com.google.android.material:material` to dependencies.
    - Updated `themes.xml` (both default and night) to use `Theme.Material3.DayNight.NoActionBar`.
    - *Why:* The previous `Theme.DeviceDefault` parent was causing resource linking failures. Material 3 is the current standard for Compose-based apps.

## Recommendations to Avoid Future Issues

- **Plugin Aliases:** Always use camelCase for plugin aliases in `libs.versions.toml` (e.g., `myPlugin` instead of `my-plugin`) to avoid syntax ambiguity in Kotlin build scripts.
- **Sync After Version Changes:** Whenever you modify `libs.versions.toml`, perform a "Gradle Sync" immediately. The IDE needs this to generate the type-safe accessors (like `libs.plugins...`).
- **Check Resource SDK Requirements:** If you use modern XML features (like Adaptive Icons or Vector Drawables with certain attributes), ensure your `minSdk` in `build.gradle.kts` matches or exceeds the required API level.
- **Keep `gradle.properties` Updated:** Ensure `android.useAndroidX=true` is always present for any modern project.
- **Theme Consistency:** When using Compose, ensure your XML themes (used for the splash screen or Activity entry point) inherit from a stable library theme like `Theme.Material3` or `Theme.AppCompat`.

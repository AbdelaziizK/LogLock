# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run lint checks
./gradlew lint

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Clean build artifacts
./gradlew clean
```

## Architecture

LogLock is an Android app (Kotlin, API 26-34) that logs lock screen PIN/password attempts via an accessibility service.

**Core components:**

- **`LockAccessibilityService`** — Background accessibility service; registers a `BroadcastReceiver` for `ACTION_SCREEN_OFF` and `ACTION_USER_PRESENT`, intercepts `TYPE_VIEW_TEXT_CHANGED` events to count PIN input attempts, then persists each session to Room via coroutines.

- **`LockSessionManager`** — Singleton holding mutable state for the current in-progress lock/unlock session (attempt counts, start time).

- **`MainActivity`** — Observes `LockEvent` LiveData (last 7 days) from Room and drives the `RecyclerView`; shows a banner prompting users to enable the accessibility service when it's not active.

- **Data layer** (`data/`) — Room database (`loglock.db`), single `lock_events` entity (`LockEvent`), and a DAO (`LockEventDao`) with a LiveData query over a date range.

- **`LogAdapter`** — `RecyclerView.Adapter` with `DiffUtil`; color-codes cards (blue = locked, amber = multiple attempts, red = never unlocked, white = normal).

**Key stack:** Kotlin · Room 2.6.1 · Kotlin Coroutines 1.7.3 · Lifecycle/LiveData 2.7.0 · View Binding · Material Design 1.11.0 · KSP 1.9.23-1.0.20

**Accessibility service config** is declared in `app/src/main/res/xml/accessibility_service_config.xml` and registered in `AndroidManifest.xml` with `BIND_ACCESSIBILITY_SERVICE` permission.

## Project Configuration

- `compileSdk 34`, `minSdk 26`, `targetSdk 34`
- Java 8 target compatibility
- View Binding enabled (`buildFeatures { viewBinding true }`)
- Non-transitive R class enabled (`android.nonTransitiveRClass=true`)
- Local SDK path in `local.properties` (machine-specific, not versioned)

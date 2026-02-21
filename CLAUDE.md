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

- **`LockAccessibilityService`** — Background accessibility service; registers a `BroadcastReceiver` for `ACTION_SCREEN_OFF` and `ACTION_USER_PRESENT`, then detects PIN attempts via three parallel paths and persists each session to Room via coroutines. See **PIN detection** below.

- **`LockSessionManager`** — Singleton holding mutable state for the current in-progress lock/unlock session (attempt counts, start time).

- **`MainActivity`** — Observes `LockEvent` LiveData (last 7 days) from Room and drives the `RecyclerView`; shows a banner prompting users to enable the accessibility service when it's not active.

- **Data layer** (`data/`) — Room database (`loglock.db`), single `lock_events` entity (`LockEvent`), and a DAO (`LockEventDao`) with a LiveData query over a date range.

- **`LogAdapter`** — `RecyclerView.Adapter` with `DiffUtil`; color-codes cards (blue = locked, amber = multiple attempts, red = never unlocked, white = normal).

**Key stack:** Kotlin · Room 2.6.1 · Kotlin Coroutines 1.7.3 · Lifecycle/LiveData 2.7.0 · View Binding · Material Design 1.11.0 · KSP 1.9.23-1.0.20

**Accessibility service config** is declared in `app/src/main/res/xml/accessibility_service_config.xml` and registered in `AndroidManifest.xml` with `BIND_ACCESSIBILITY_SERVICE` permission. The service sets `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` so it can search all windows, not just the active one.

## PIN Detection

Three parallel paths run in `onAccessibilityEvent`; whichever fires first wins:

- **Path A — `TYPE_VIEW_CLICKED`** (primary on EMUI/Huawei and similar OEMs): digit button presses increment `lastPinTextLength`; any non-digit, non-delete click while digits are entered is treated as an OK/submit button.
- **Path B — `TYPE_VIEW_TEXT_CHANGED`**: reacts only to a clear-to-zero event (field reset after wrong PIN on devices that expose text-change events). Does **not** update `lastPinTextLength` for non-zero lengths to avoid interfering with Path A.
- **Path C — `TYPE_WINDOW_CONTENT_CHANGED`**: first tries to find a `isPassword && !isEditable` node (the dot display) and read its text length; if the view is not in the tree (common on EMUI), falls back to a **timing heuristic** — a content-change event arriving 200–4 000 ms after the last digit click is treated as a wrong-PIN response.

**Timing fix in `onScreenUnlocked`:** `ACTION_USER_PRESENT` fires before the PIN field clears on auto-submit keypads, so the service records one final attempt if `lastPinTextLength > 0` before capturing the session state. This ensures a single correct-PIN unlock is logged as "1 attempt" rather than "Unlocked directly".

Tested on: Huawei Nova 3i (EMUI 8.1 / Android 8.1) with a 6-digit auto-submit custom PIN.

## Project Configuration

- `compileSdk 34`, `minSdk 26`, `targetSdk 34`
- Java 8 target compatibility
- View Binding enabled (`buildFeatures { viewBinding true }`)
- Non-transitive R class enabled (`android.nonTransitiveRClass=true`)
- Local SDK path in `local.properties` (machine-specific, not versioned)

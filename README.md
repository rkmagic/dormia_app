# Dormia Android App

Dormia is an Android sleep-tracking companion app for the Dormia ecosystem.
It uses Google Sign-In + Firebase Auth, then syncs player presence and sleep state to Firebase so the web app can render live player status on the map view.

Web app live view reference: `https://dormia.vercel.app/live`

## What This App Does

- Authenticates users with Google Sign-In and Firebase Auth.
- Creates/maintains a player profile in Firestore (`players/{uid}`).
- Mirrors live player status to Realtime Database (`dormia/players/{uid}`).
- Starts background sleep detection using Android Sleep APIs.
- Updates sleep state (`isAsleep`, last sleep window) based on:
  - Manual UI actions (`Fall Asleep` / `Wake Up`)
  - Sleep classify + segment events from Google Play Services.
- Detects location (GPS first, IP fallback) and stores continent/country/city + coordinates.

## Tech Stack

- Kotlin + Jetpack Compose
- AndroidX Navigation + ViewModel
- Firebase:
  - Firebase Authentication
  - Cloud Firestore
  - Realtime Database
- Google Play Services:
  - Google Sign-In
  - Location Services
  - Sleep APIs (classify + segment events)

## Firebase Data Model

The app writes to both Firestore and Realtime Database so long-lived profile data and live map/presence data can stay in sync.

### Firestore

Collection: `players`

Document ID: Firebase Auth `uid`

Typical fields:

- `uid`, `displayName`, `email`
- `sleepTargetHours`
- `xp`, `credits`, `currentStreak`, `longestStreak`
- `isAsleep`
- `hasAndroidApk`
- `continent`, `country`, `city`
- `lat`, `lng`
- `lastSleepStart`, `lastSleepEnd` (timestamps)
- `createdAt` (server timestamp)

### Realtime Database

Path: `dormia/players/{uid}`

Typical fields:

- `displayName`
- `isAsleep`
- `continent`, `country`, `city`
- `lat`, `lng`

## How It Connects To The Web App

The Android app and web app share the same Firebase project.

- Android updates player profile and live presence in Firebase.
- Web app reads from the same Firebase paths/collections.
- Result: when Android updates sleep state/location, web clients can reflect those changes live (for example in the `/live` map view).

Integration contract to keep aligned across Android + web:

- Firestore doc key is always `players/{uid}`.
- RTDB live path is always `dormia/players/{uid}`.
- Shared field names should stay consistent (`isAsleep`, `continent`, `country`, `city`, `lat`, `lng`).

## Local Setup

### 1) Prerequisites

- Android Studio (recent version)
- JDK 11+
- Android device or emulator (API 29+)
- A Firebase project with:
  - Authentication (Google provider enabled)
  - Firestore enabled
  - Realtime Database enabled

### 2) Firebase Config

1. Register Android app in Firebase with package name:
   - `com.example.dormia`
2. Download `google-services.json`.
3. Place it at:
   - `app/google-services.json`
4. In Firebase Auth, enable Google sign-in.
5. Ensure your OAuth web client is set so `default_web_client_id` is generated in resources.

### 3) Build & Run

From Android Studio:

- Sync Gradle
- Select device/emulator
- Run app

From terminal:

```bash
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

## Runtime Permissions

The app requests:

- `ACTIVITY_RECOGNITION`
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`
- `POST_NOTIFICATIONS` (Android 13+)
- `INTERNET`
- Foreground service permissions for continuous sleep tracking

Without these, automatic sleep detection and location updates may be limited.

## Main User Flow

1. User opens app.
2. Sign in with Google.
3. App ensures `players/{uid}` exists.
4. App marks `hasAndroidApk = true`.
5. App detects location and syncs to Firestore + RTDB.
6. If onboarding is incomplete, user sets sleep target + location.
7. Main screen starts sleep tracking service/subscription.
8. Sleep events/manual actions update Firebase.
9. Web app observes same Firebase data to show live player state.

## Notes

- Keep Firebase rules in sync with this data model.
- If you rename fields used by web or Android, update both clients together.
- Avoid committing secrets; treat Firebase configuration and API keys carefully.

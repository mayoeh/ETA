# ETA

ETA tells you exactly when to leave for your next plan, based on where you are and how long the trip really takes. CS 4720 Fall 2026 final project (Kotlin, Jetpack Compose, Firebase).

## What works in this build (Show & Tell 2)

| Screen | Works |
| --- | --- |
| Login | Email/password sign in and create account (Firebase Auth), password reset email |
| Today | Live plans from Firestore, day-by-day paging, next-plan card with a leave-in countdown from the Routes API, "I'm leaving" share |
| Edit plan | Name, category, Places autocomplete or map pin, be-there-by time, buffer, repeat days or one-time, Walk / Bike / Bus, share toggle, delete |
| Follow a route | Map with the Routes API path, live position, distance walked and left along the path, remaining time, Share ETA, reroute |
| Settings | DataStore: default travel mode, arrive-early buffer, distance units, simulate-walk demo switch; sign out |

Not built yet: Friends, Calendar, Record a route / saved routes, step-count pace, Google sign-in, leave-now notification.

## Setup

1. **Firebase:** create a project, add an Android app with package `edu.virginia.cs4720.eta`, download `google-services.json` into `app/`, add your debug SHA-1, enable Email/Password auth, create Firestore in production mode.
2. **Rules:** paste `firestore.rules` into Firestore → Rules → Publish.
3. **Google Cloud (same project):** attach billing, enable **Maps SDK for Android**, **Routes API** and **Places API (New)**, create an API key restricted to the Android app (package + SHA-1) and those three APIs.
4. Add the key to `local.properties` (already gitignored):
   ```
   MAPS_API_KEY=your_key_here
   ```
5. Open in Android Studio, sync Gradle, run. Build → Build APK(s) for the APK.

Get your SHA-1 with `./gradlew signingReport` (the `debug` variant).

## Demoing GPS without walking

- **Emulator (main option):** Extended controls (the `...` on the emulator toolbar) → Location.
  - *Single points:* search a starting place and click **Set location**.
  - *Routes:* pick a start and an end, **Save route**, then **Play route** (1x to 5x speed). The app sees real location updates as you "walk".
- **Phone or emulator, backup:** Settings → turn on **Simulate walk on the Route screen**, then on Follow a route tap **Simulate walk (demo)**. It moves your dot along the real Routes API path at about 10x walking speed.
- A phone on Wi-Fi only is fine: Wi-Fi covers the APIs, and GPS doesn't need mobile data.

## Data

- `users/{uid}`: profile (`displayName`, `email`, lowercase copies for search)
- `users/{uid}/plans/{planId}`: one plan (see `data/Plan.kt` for fields)
- Settings live on the device in DataStore, not Firestore.

## AI usage

<!-- Course policy: describe how AI was used. Edit this to be accurate. -->
Initial project scaffold generated with Claude (Anthropic), then reviewed, run and modified by me.

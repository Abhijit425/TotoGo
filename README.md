# TotoGo 🛺⚡
**Your Town. Your Toto. Anytime.** · *আপনার শহর। আপনার টোটো। যখন খুশি।*

Ola/Uber-style electric Toto (e-rickshaw) booking for small Indian towns — launching in **Nalhati, Birbhum, West Bengal**.

| Piece | Where | What it is |
|---|---|---|
| **TotoGo Passenger** | `passenger/` | Android app (Kotlin, Compose, Material 3) |
| **TotoGo Driver** | `driver/` | Android app with loud ride-request alarm |
| **Shared core** | `core/` | Models, demo engine, repositories (demo + Firebase), Room, DataStore, theme, components, key-free town map |
| **Backend** | `backend/` | Cloud Functions (TypeScript), Firestore rules & indexes |
| **Admin dashboard** | `admin/index.html` | Single-file web dashboard (demo data or live Firebase) |

---

## 1. Run it right now (DEMO mode — no API keys)

Both apps start in **DEMO mode**: 10 simulated Totos drive around a stylised Nalhati map, simulated passengers send requests to the driver app, and every simulated person, ride and number is labelled **DEMO**.

### Option A — GitHub Actions (no Android Studio needed)
1. Push this folder to a GitHub repo.
2. The workflow `.github/workflows/build-apk.yml` builds both debug APKs on every push.
3. Open **Actions → latest run → Artifacts → `TotoGo-debug-apks`**, download, unzip, install:
   - `TotoGo-Passenger-debug.apk`
   - `TotoGo-Driver-debug.apk`

Both use the shared debug key in `keystore/`, so newer builds install over older ones.

### Option B — Android Studio
1. Open the `TotoGo` folder (Android Studio Ladybug or newer, JDK 17).
2. Let Gradle sync, choose the `passenger` or `driver` run configuration, press ▶.
3. CLI: `./gradlew :passenger:assembleDebug :driver:assembleDebug`

### Demo walkthrough
**Passenger:** splash → sign in with any 10-digit number, OTP **123456** → pick a destination (type `station`, `bazar`, `হাসপাতাল`, or tap a landmark on the map) → adjust fare → **Request Toto** → watch the radar → driver accepts → live tracking → pay (Cash/UPI) & rate.

**Driver:** sign in (OTP 123456) → onboarding (photos, Toto number, licence) → DEMO auto-verification → flip the big **ONLINE** switch → within seconds a request rings with the TotoGo alarm → **ACCEPT** (≈12% of the time another simulated driver wins first — that's the atomic lock in action) → slide **Arrived → Start → Complete** → earnings update.

Tips: Passenger → Profile → *Busy town simulation* shows the 2 → 4 → 6 km radius expansion. *Use my real GPS* switches from simulated to your real location.

> Demo time runs ~6× faster so a ride finishes in about a minute. Demo landmark coordinates are approximate — correct them before launch (`core/.../data/TownData.kt` and the admin *Service areas* page).

---

## 2. Architecture

```
            ┌──────────── core (Android library) ────────────┐
passenger ──┤ model · geo · TownData (EN/বাংলা landmarks, road │
driver ─────┤ graph, Dijkstra) · FareCalculator · repo/*       │
            │ demo/* (DemoWorld, Demo*Repository)              │
            │ firebase/* (Firebase*Repository, PhoneAuth)      │
            │ db (Room) · prefs (DataStore) · notify · ui/*    │
            └──────────────────────────────────────────────────┘
MVVM: Screen (Compose) → ViewModel (StateFlow) → Repository interface → Demo | Firebase
```

- **Repository interfaces** (`PassengerRideRepository`, `DriverRepository`, `AuthRepository`) each have a working demo implementation and a Firebase implementation. The app picks one at start-up.
- **Maps:** `SmartMap` uses Google Maps (maps-compose) when `MAPS_API_KEY` is set, otherwise the animated key-free `TownMap` (glowing roads, railway, landmarks, animated route, custom Toto markers, search-radius ring, pinch/drag).
- **Location:** `FusedLocationProvider` (real) / `SimulatedLocationProvider` (demo). Drivers share GPS only while **online**, through `DriverLocationService` (foreground service, `location` type). Going offline stops the service and deletes `driverLocations/{uid}`.
- **Room** caches booking/fare history; **DataStore** holds settings; **WorkManager** (`HistorySyncWorker`) uploads offline ratings and prunes old demo rows.
- **Languages:** English + Bengali, switchable instantly (`tr("…","…")`, Bengali digits). Search matches Bengali script, English and common romanisations.

### Matching (server: `backend/functions/src/index.ts`)
1. `createRide` — validates pickup/destination inside the service area, recomputes the suggested fare, clamps the offer (70–300%), prevents duplicate active bookings with a transactional `passengerLocks/{uid}`.
2. `dispatchRound` — eligible drivers = online + verified + not suspended + no active ride + GPS fresher than 2 min + within radius + **not already notified for this ride**. Creates `rideRequests` and sends a high-priority FCM data message.
3. No acceptance within `roundTimeoutSec` (default 20 s) → Cloud Task `expandSearch` runs the next radius (2 → 4 → 6 km). If every driver in a round rejects, it expands immediately.
4. `acceptRide` — Firestore **transaction**: only the first driver to see `status == SEARCHING` wins; everyone else gets `ride_taken`.
5. `updateRideStatus` enforces `ACCEPTED → ARRIVED → IN_PROGRESS → COMPLETED` for the assigned driver only; completion writes a `payments` doc and frees the driver.
6. `cancelRide` (passenger) — free until the driver arrives; later cancellations are counted. `sweepStale` (every 5 min) is a safety net.

Radius, timeout, fare formula and service area live in `config/*` and are edited from the admin dashboard via `updateConfig`.

### Data model (Firestore)
`users` (userId, name, phone, role, photo, createdAt, fcmTokens) · `drivers` (driverId, name, phone, vehicleNumber, isVerified, isOnline, rating, activeRideId …) · `driverLocations` (driverId → lat, lng, heading, updatedAt) · `rides` (rideId, passengerId, driverId, pickup, destination, offeredFare, finalFare, status, createdAt, acceptedAt, completedAt …) · `rideRequests` (requestId, rideId, driverId, status, expiresAt) · `payments` (paymentId, rideId, amount, method, status) · `driverApplications`, `config`, `complaints`, `landmarks`.

### Security (`backend/firestore.rules`)
Clients can read only what they're party to and **cannot write** rides, requests, payments or driver records — fare, availability and acceptance are decided by Cloud Functions. Drivers may write only their own location, only while online, only allowed fields, with a server timestamp. A passenger can read a driver's location only during their active ride with that driver.

---

## 3. Production configuration

### 3.1 Firebase
1. Create a Firebase project (Blaze plan is required for Cloud Functions + Cloud Tasks; the free tier covers a small town's usage generously).
2. Add two Android apps: `com.totogo.passenger` and `com.totogo.driver`. Add your debug and release SHA-1/SHA-256 (needed for phone auth):
   `keytool -list -v -keystore keystore/debug.keystore -storepass android`
3. Download each `google-services.json` into `passenger/` and `driver/` respectively. The build applies the Google Services plugin automatically when the file exists.
   *For GitHub Actions:* store each file base64-encoded as a repository secret and decode it in a workflow step before building (never commit them).
4. **Authentication → Sign-in method → Phone**: enable. Add test numbers for QA.
5. **Firestore**: create in `asia-south1` (Mumbai).
6. Deploy the backend:
   ```bash
   npm i -g firebase-tools && firebase login
   cd backend && sed -i 's/YOUR_FIREBASE_PROJECT_ID/<your-id>/' .firebaserc
   (cd functions && npm install)
   firebase deploy --only firestore:rules,firestore:indexes,functions
   ```
   The first deploy may ask you to enable Cloud Tasks / Cloud Scheduler APIs — accept.
7. **First admin:** in Google Cloud Shell or a local admin script, set the custom claim once:
   ```js
   admin.auth().setCustomUserClaims("<your-uid>", { admin: true })
   ```
   Later admins can be added via the `setAdmin` callable.
8. In each app: Profile/Settings → **Production backend (Firebase)** → restart the app → sign in with a real phone number.

### 3.2 Google Maps (optional — the town map works without it)
1. Google Cloud console → enable **Maps SDK for Android** → create an API key restricted to the two package names + SHA-1.
2. Add to `local.properties` (never commit): `MAPS_API_KEY=AIza...` — or set the `MAPS_API_KEY` environment variable / Actions secret.

### 3.3 Notifications & the ride alarm
- Channel `ride_requests_v1` (importance HIGH) uses the custom sound `core/src/main/res/raw/totogo_alarm.wav` and a full-screen intent so the request appears over the lock screen.
- The dispatcher sends **data-only, priority=high** FCM messages to drivers; `DriverMessagingService` raises the alarm notification even when the app is in the background.
- Android limits: Android 13+ needs the notification permission; Android 14+ may require the user to allow **full-screen notifications** (Settings → Apps → TotoGo Driver → Notifications); aggressive battery savers on some phones (Vivo, Xiaomi, Oppo) can delay FCM — ask drivers to set TotoGo Driver to **No restrictions** in battery settings. Replace the alarm by swapping the WAV (bump the channel id if you change it, since channels are immutable).

### 3.4 Masked phone numbers
Numbers are shown masked. For true masking, sign up with a call-proxy provider (e.g. Exotel/Knowlarity) and set `config/telephony.proxyNumber`; `acceptRide` then hands out the proxy number instead of the real one.

### 3.5 Admin dashboard
Open `admin/index.html` in a browser for demo mode. For production, fill `FIREBASE_CONFIG` at the top of the script, add an authorised domain in Firebase Auth, and host it (`firebase deploy --only hosting` serves `admin/`). Sign in with an account that has the `admin` claim.

---

## 4. Testing
- **Backend:** `cd backend/functions && npm run build` (type-check) and `npm run serve` (Firestore/Auth/Functions emulators).
- **Android:** `./gradlew :passenger:assembleDebug :driver:assembleDebug`; manual flows listed in §1.
- Suggested next tests: unit tests for `FareCalculator`, `TownData.route`, `PlaceSearch`; emulator tests for `acceptRide` race (two drivers accepting the same request concurrently must produce exactly one winner).

## 5. Known limits / next steps
- Demo landmark positions are approximate — survey and correct them.
- Driver document photos are stored locally in this version; wire Cloud Storage uploads + a Storage rule before launch.
- UPI uses a standard `upi://pay` intent with the driver's UPI ID; no payment is processed by TotoGo.
- Release signing: create a proper release keystore (do not use `keystore/debug.keystore` for Play Store builds).

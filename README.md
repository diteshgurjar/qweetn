# Qweet Rider — Android app

Minimal native Kotlin app for your rider API (`RIDER_API.md`). Scope, on purpose,
is only: log in → go online → receive an assigned delivery → accept/decline →
navigate → mark picked up → mark delivered. No extra screens/features.

## Before you open it in Android Studio

1. Open the project folder in Android Studio (Hedgehog/Iguana or newer). It will
   download the Gradle wrapper + dependencies on first sync — needs internet.
2. Set your live API domain in `app/build.gradle.kts`:
   ```kotlin
   buildConfigField("String", "API_BASE_URL", "\"https://kanu.rf.gd/api/v1/rider/\"")
   ```
   Change `kanu.rf.gd` to your real domain if it's different. Must end in
   `/api/v1/rider/`.
3. Build → Run on a device/emulator with Google Play services (needed for the
   fused location provider and to launch Google Maps navigation).

## How it maps to your API

| App action | Endpoint |
|---|---|
| Login screen | `POST login.php` → stores the bearer token in an EncryptedSharedPreferences file (Keystore-backed, per your API doc's recommendation) |
| Online toggle | `POST toggle-online.php` |
| While online | starts a **foreground service** (`LocationService`, type `location`) that reads GPS via `FusedLocationProviderClient` and calls `POST update-location.php` every ~20s. This is what keeps it "always working" — Android won't kill a foreground service the way it kills a background timer/JobScheduler task. |
| Order polling | `GET orders.php` every 10s while online (your API doc notes push notifications aren't implemented yet, so this is the documented fallback) |
| Accept | no separate endpoint — your backend auto-assigns, so "Accept" just dismisses the incoming-order card; declining is the real action |
| Decline | `POST order-action.php` `{action:"decline", reason}` |
| Navigate button | opens Google Maps turn-by-turn navigation via an Android `Intent` (no Maps API key needed) to the pickup while `assigned`, or the customer address once `picked_up` |
| Mark picked up / delivered | `POST order-action.php` `{action:"advance", new_status:...}` |
| Logout | stops the location service, clears the token |

## Permissions

Fine + coarse + **background** location (so tracking survives minimizing the
app), foreground-service, and notifications (Android 13+ requires this to show
the "you're online" persistent notification the foreground service needs).
The app requests these at first launch; background location is requested as a
second step after foreground location is granted (required by Android 10+).

## What was intentionally left out

Matches what you asked for — no earnings/history/profile/KYC/reviews screens,
even though those endpoints exist in your API. Easy to add later since the
Retrofit interface (`data/RiderApiService.kt`) already documents them all.

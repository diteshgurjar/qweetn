# Push Notifications (FCM) — one-time setup

This app is wired for push notifications from your own QWEET server, but it
needs one file from you before it will build: `google-services.json`.

## Steps

1. Go to https://console.firebase.google.com and create a project (free,
   no billing account needed).
2. Inside the project: **Add app > Android**.
   - Package name: `com.qweet.rider` (must match exactly)
   - Nickname / SHA-1: optional, skip if unsure
3. Firebase will offer you a `google-services.json` file to download.
   Put it here: `app/google-services.json` (same folder as this file).
4. That's it for the app side — rebuild, and the Firebase plugin will pick
   it up automatically.

## Also needed: backend setup

The Android side only *receives* pushes — your PHP backend is what
*decides and sends* them. See `config/fcm.php` in the backend project for
the matching 5-step setup (service account key, project ID, enabling it).

Until both sides are set up, the app will still work completely normally —
it just won't receive push notifications yet (in-app Notifications section
still fills in as before).

## Without this file

If you build the app before adding `google-services.json`, Gradle will
fail with an error like:

    File google-services.json is missing from module root folder

That's expected — add the file and rebuild.

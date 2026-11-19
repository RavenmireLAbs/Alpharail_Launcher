# Still — Android launcher concept

Still is a browser prototype for a minimal Android launcher. The first visit shows a welcome panel. Continue to one vertically scrolling, alphabetical app list, or tap a letter to jump. Favorites and optional demo widgets sit above A on the same screen. App entries are simulated; this is not an installable Android launcher yet.

Run locally with `python3 -m http.server 8000` and open `http://localhost:8000`.

## Android app

The native launcher lives in `android/`. Install the signed APK from Releases on Android 8.0 or later, open Still, and choose **Set as Home** to make it the default launcher. The star above A jumps to Favorites, where you can pin apps and add widgets from installed providers. You can switch back under Android Settings → Default apps → Home app.

Build a local test APK with JDK 17, Gradle 8.9 and Android SDK 35: `gradle -p android assembleDebug`. The Android APK workflow builds and checks the APK signature. Release signing uses the repository secrets `STILL_KEYSTORE_BASE64` and `STILL_STORE_PASSWORD`; reuse the same key for updates.

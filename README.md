# Still — Android launcher concept

Still is a browser prototype for a minimal Android launcher. The first visit shows a welcome panel. Continue to one vertically scrolling, alphabetical app list, or tap a letter to jump. Favorites and optional demo widgets sit above A on the same screen. App entries and widgets in this preview are simulated; the installable launcher is described below.

Run locally with `python3 -m http.server 8000` and open `http://localhost:8000`.

## Android app

The native launcher lives in `android/`. Install the signed APK from Releases on Android 8.0 or later, open Still, and choose **Set as Home** to make it the default launcher. The star above A jumps to Favorites, where you can pin apps and add widgets from installed providers. You can switch back under Android Settings → Default apps → Home app.

On the launcher screen:

- **Search apps** filters the same list. Clearing search returns to your previous scroll position.
- **Hold an empty area** below the search box or elsewhere on the launcher to open a bottom panel with **Widgets** and **Launcher settings**.
- **Launcher settings** control icon style, size and shape, font size and typeface, Hidden apps, and Favorites order. Hiding apps does not uninstall them.
- **Widgets** lets you add or manage widgets. Resize changes the height when the provider supports vertical resizing.
- **App lock** uses the device PIN, pattern, or password to manage locked apps and open them from Still. It does not lock apps system-wide. The browser only simulates unlocking.
- **Dark mode** follows Android's system appearance, or the browser's color preference in the preview.

Build a local test APK with JDK 17, Gradle 8.9 and Android SDK 35: `gradle -p android assembleDebug`. The Android APK workflow builds and checks the APK signature. If release signing secrets are unavailable, the workflow produces an installable APK signed with a temporary development key. A later APK signed with a different key requires uninstalling this build first. Release signing uses the repository secrets `STILL_KEYSTORE_BASE64` and `STILL_STORE_PASSWORD`; reuse the same key for updates.

On Linux x86-64 without an Android SDK installation, `android/scripts/package-apk.sh build/Still-v0.2.0-debug.apk` packages the app with pinned tools. It uses Java 21 (a JDK or JRE), Python 3, curl, OpenSSL, and standard shell utilities. GitHub Actions uses this path to publish v0.2.0 after merge.

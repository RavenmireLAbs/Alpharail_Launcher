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
- **Launcher settings → Theme** lets you choose White (the default) or Black, independently of the Android system theme.

Build a local test APK with JDK 17, Gradle 8.9 and Android SDK 35: `gradle -p android assembleDebug`. The Android APK workflow builds and checks the APK signature. If release signing secrets are unavailable, the workflow produces an installable APK signed with a temporary development key. A later APK signed with a different key requires uninstalling this build first. Release signing uses the repository secrets `STILL_KEYSTORE_BASE64` and `STILL_STORE_PASSWORD`; reuse the same key for updates.

On Linux x86-64 without an Android SDK installation, `android/scripts/package-apk.sh build/Still-v0.3.0-debug.apk` packages the app with pinned tools. It uses Java 21 (a JDK or JRE), Python 3, curl, OpenSSL, and standard shell utilities. GitHub Actions uses this same packaging path.

## Search, long-press, and backups

- Press **Enter** or the keyboard's **Search** key to launch the first visible search result. App Lock still applies.
- Tap or drag along the **alphabet rail** to move through the same app list. The letter indicator follows your selection and respects reduced-motion settings.
- Tap **Undo** after hiding apps or removing a Favorite to restore it without opening Settings.
- Hold an app to open its Android **App info** or one of its supported shortcuts. Shortcuts require Still to be the default Home app and the installed app to expose them.
- Under **Launcher settings → App list → Alphabet side**, choose **Left** or **Right**.
- **Launcher settings → Backup and restore** exports Favorites in order, hidden apps, appearance, Collections, custom app names, and the widget stacking preference. Older backups still import. Appearance presets can be exported and imported separately without sharing app lists. Import asks before replacing those settings. Android backups also remember widget providers, order, and heights. Use **Widgets → Recover widgets** after importing to re-add them through Android’s normal permission and configuration screens. Still shows how many are left, suggests the next widget, and lets you choose another or skip an unavailable widget. Widget contents, accounts, and App Lock are not included. Browser preview backups are separate from Android backups.

## Persistent release signing

The published development builds up through v0.2.0 used temporary keys. Switching to a persistent key requires a one-time uninstall; export settings first if the installed build offers that feature. Never commit or share the signing key. Generate and retain one PKCS#12 keystore with alias `still`, for example with `keytool -genkeypair -storetype PKCS12 -keystore still-release.p12 -alias still -keyalg RSA -keysize 3072 -validity 10000`. Store a secure offline backup.

In GitHub Actions settings, configure `STILL_KEYSTORE_BASE64` (the base64-encoded keystore) and `STILL_STORE_PASSWORD` as repository secrets, plus `STILL_CERTIFICATE_SHA256` as a repository variable. The fingerprint is the `SHA256` certificate fingerprint printed by `keytool -list -v -storetype PKCS12 -keystore still-release.p12 -alias still`. Keep the same certificate pinned across releases. After bumping `versionCode` and `versionName` in `android/app/build.gradle`, run **Android APK** on `main` with **publish** enabled. The workflow refuses to publish without persistent signing and rejects a certificate fingerprint mismatch. Ordinary builds do not publish, except for the explicitly labeled v0.3.0 Preview 3 prerelease on main.

## Optional Chats inbox

Tap **Chats**, choose **Enable Chats**, and grant **Still Chats** notification access in Android settings. Return to Still and open Chats. It shows active message notifications, not conversation history. Use **Refresh** for new or dismissed notifications. **Reply** appears only when the source notification supports a text reply. **Open chat** uses a supported notification link; **Open app** launches the messaging app directly, including when the link is unavailable. Still does not store or upload message contents. Previews are off by default, hidden and App Locked apps are excluded, and **Chat settings** lets you filter supported messaging apps or turn Chats off. Revoke Still Chats notification access in Android settings when you no longer need it. The browser preview uses fictional messages.

### Compact Favorites and home shortcuts

- Hold empty space and choose **Widgets → Stack widgets** to share one spot in Favorites. Swipe horizontally or use the previous/next controls; vertical swipes still scroll the launcher. Switch back to show widgets separately at any time.
- Create named app groups under **Launcher settings → App list → Collections**. Collections appear in Favorites, follow your hidden-app and App Lock preferences, and do not affect the alphabetical list. Backups preserve Collections and the stacking preference, not live widget instances.
- Choose **Icons → Icon style → Dots** for a quieter app list, or open **Theme** to preview the White and Black palettes before applying.
- Tap the date to open Calendar where supported, or tap the time to open Clock. No calendar access is required.

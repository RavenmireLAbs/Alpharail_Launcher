# Alpharail

A minimal Android launcher built around **one alphabetical app list**. Drag the A–Z rail to jump to a letter, tap **★** for Favorites, or search without opening a separate app drawer.

**[Download the latest signed APK](https://github.com/RavenmireLAbs/Alpharail_Launcher/releases/latest)** · Android 8.0+ · `com.ravenmire.alpharail`

## Get started

1. Download the `.apk` from the latest release and install it. Android may ask you to allow installs from the app that opened the file.
2. Open **Alpharail** and choose **Set as Home**. You can switch back at any time in **Android Settings → Default apps → Home app**.
3. Long-press empty space to open **Widgets** or **Launcher settings**. Long-press an app to rename it, hide it, add it to a Collection, or open its Android app info and supported shortcuts.

## Features

- **A–Z navigation and offline search.** Tap or drag the alphabet rail, place it on either side, or press Enter to open the top visible search result. Clearing search returns you to your prior scroll position.
- **Favorites, Collections, and widgets.** Pin apps above A, organize them into named groups, and show widgets separately or as a swipeable stack. Widgets can be resized when their provider supports it.
- **Appearance.** White is the default; preview Black before applying. Choose your Android wallpaper or one of bundled wallpapers from the Abstract collection (five light and nine dark) or the Wuxia & Xianxia collection (ten designs) via **Launcher settings → Home wallpaper**. When applying a bundled wallpaper, choose **Alpharail only**, **Android Home screen**, or **Android Home and Lock screens**. Android controls how system wallpaper is displayed in Recent Apps. Customize icon style, size, and shape, or change the typeface and font size. Appearance presets can be shared without exporting app lists.
- **Home shortcuts.** Tap the clock to open Clock or the date to open Calendar on supported devices.
- **Privacy controls.** Hiding an app doesn’t uninstall it. App Lock uses Android screen-lock authentication for launches from Alpharail; it doesn’t lock apps system-wide.

### Optional Chats inbox

Open **Chats → Enable Chats**, then grant **Alpharail Chats** notification access in Android settings. The inbox updates while open and groups active notifications into conversations when the messaging app supplies a stable conversation ID. It doesn’t retrieve old chat history or save or upload message contents.

Use **App: All** to filter the active inbox by messaging app. Each row shows the source app icon. If previews are enabled and the source supplies a compatible sender picture, Alpharail displays it too. Open a thread to pin it when Android provides a stable conversation ID; pinned threads move to the top when active. Pins stay local and can be cleared in **Chat settings**.

Previews are off by default. Hidden and App Locked apps are excluded. **Reply** and **Open conversation** only appear when a compatible notification action is available; **Open app** is separate. Use **Chat settings** to filter messaging apps or turn Chats off. You can revoke notification access in Android settings. **Chat settings → Screenshot protection** controls whether Android blocks screenshots and screen recordings of Chats. Protection is on by default.

### Updates

Open **Launcher settings → Updates** to check for a newer signed APK or enable automatic checks. With automatic checks on, Alpharail checks GitHub at most once a day when opened. Downloads open in your browser and Android asks you to confirm installation; updates are not silently downloaded or installed.

### Backups and upgrades

**Launcher settings → Backup and restore** exports Favorites, hidden apps, appearance, Collections, custom app names, and widget layout information. After importing, use **Widgets → Recover widgets** to re-add widgets through Android. Backups don’t include widget contents, accounts, App Lock, or Chats notification access.

- **Updating from signed Alpharail v0.3.4 or newer:** Install the newer APK over the existing app when it uses the same signing certificate.
- **Updating from an old debug-signed Alpharail build:** Export settings first. Switching to the permanent signing key requires a one-time uninstall and reinstall.
- **Moving from Still:** Alpharail installs separately. Export settings from Still, import them into Alpharail, and set Alpharail as your Home app.

## Browser demo and development

The browser demo uses simulated apps, widgets, and messages. Its backups are separate from Android backups. To run it locally, use `python3 -m http.server 8000` and open `http://localhost:8000`.

- **Android SDK build:** Run `gradle -p android assembleDebug` with JDK 17, Gradle 8.9, and Android SDK 35.
- **Linux x86-64 without the Android SDK:** Run `android/scripts/package-apk.sh build/Alpharail-debug.apk` with Java 21, Python 3, curl, and OpenSSL. This is also the packaging path used by CI.

### Release signing

Alpharail v0.3.4 introduced permanent signing. Keep the PKCS#12 keystore and its password backed up securely, and never commit either. The current workflow expects key alias `still` and the same password for the key and keystore.

Configure repository Actions secrets `STILL_KEYSTORE_BASE64` and `STILL_STORE_PASSWORD`, and the Actions variable `STILL_CERTIFICATE_SHA256` using the public SHA-256 certificate fingerprint from `keytool -list -v`. Bump `versionCode` and `versionName` in `android/app/build.gradle`, then run **Android APK** on `main` with **publish** enabled. The workflow checks the certificate fingerprint before publishing. Ordinary CI builds do not publish releases.

## License

Alpharail is licensed under the [MIT License](LICENSE).

# Alpharail

A minimal Android launcher built around **one alphabetical app list**. Drag the A–Z rail to jump to a letter, tap **★** for Favorites, or search without opening a separate app drawer.

**[Download the latest signed APK](https://github.com/RavenmireLAbs/Alpharail_Launcher/releases/latest)** · Android 8.0+ · `com.ravenmire.alpharail`

## Get started

1. Download the `.apk` from the latest release and install it. Android may ask you to allow installs from the app that opened the file.
2. Open **Alpharail**, read the quick guide, and tap **Open launcher**. Choose Alpharail as your default Home app using **Launcher settings → System → Set as Home**. Switch back at any time in **Android Settings → Default apps → Home app**.
3. Fresh installs show Home over your existing Android wallpaper without replacing it. Existing users keep their saved appearance choices.
4. Long-press empty space to open **Widgets** or **Launcher settings**. Long-press an app for app info, renaming, hiding, Collections, and supported app shortcuts.

## Features

- **One scrolling app list.** No separate app drawer. Tap or drag the A–Z rail; apps whose names do not begin with A–Z appear under **#**, after the letter sections.
- **Offline app search.** Search by name or initials and press Enter to launch the top result. Clearing search restores the previous scroll position. When no app matches, search the web or Play Store, or open a valid web link; these actions use external apps and may require internet access.
- **Favorites, Collections, and widgets.** Pin apps above A, collapse Favorites by tapping its heading, organize apps into named Collections, and display widgets separately or as a swipeable stack. Widgets can be resized when their provider supports it.
- **Wallpaper and themes.** Keep your Android wallpaper or choose from **14 bundled Abstract wallpapers** (five light and nine dark). Apply bundled images to Android **Home only** or **Home & Lock**; Alpharail then follows the Android wallpaper automatically. White/Black themes are available when Home wallpaper is off. Wallpaper-aware panels use translucent backgrounds and hide Home content behind them.
- **Scheduled appearance.** Schedule White and Black backgrounds with minute-accurate AM/PM times, including overnight schedules. Enabling scheduling while wallpaper is on asks permission to switch Alpharail to solid backgrounds without replacing Android’s wallpaper. Scheduling pauses while wallpaper mode is on; sunset scheduling is not included.
- **Appearance customization.** Save wallpaper presets, customize icon style/size/shape, and choose a typeface and font size. Set the alphabet rail side and use the wallpaper’s primary color or one of **30 labeled color swatches** for the selected letter and popup. A theme accent is used when wallpaper colors are unavailable.
- **Clock controls.** Choose a compact clock, hide the date, or show the next alarm time. Tap the clock or date to open Clock or Calendar on supported devices.
- **Optional work apps.** Show available apps from an existing Android work profile in a separate Home section. Alpharail cannot create managed profiles or move personal apps into one. Use Collections for custom groups of personal apps.
- **Optional notification dots.** Show dots for active, non-ongoing personal-profile notifications, with per-app exclusions. Requires Android notification access. Dots are not unread-message counts.
- **Accessibility.** Larger alphabet labels, reduced letter-popup animations, and stronger wallpaper text shadows.
- **Privacy controls.** Hiding an app does not uninstall it. App Lock uses Android screen-lock authentication for launches from Alpharail; it does not lock apps system-wide.

### Settings organization

Settings pages use full-screen panels with Back navigation. Specialized pickers, previews, and confirmation dialogs can remain compact.

| Section | Controls |
| --- | --- |
| **Appearance** | Wallpaper, wallpaper presets, White/Black themes, theme schedule, icons, fonts |
| **Home & apps** | Clock, widgets, Favorites order, Collections, alphabet side/color, work apps |
| **Notifications & Chats** | Notification dots, exclusions, Chats, Android notification access |
| **Privacy** | Hidden apps, App Lock |
| **Accessibility** | Alphabet size, reduced animations, text contrast |
| **Backup and restore** | Settings and appearance export/import |
| **System** | Update checks, default Home selection |

### New in v0.3.26

- **Settings search:** open **Search settings** at the top of Launcher settings, then type a setting name such as “clock,” “alphabet,” or “schedule” to find its controls.
- **App shortcuts:** app-provided actions in the long-press menu use an **↗** marker. Available pinned shortcuts are queried alongside dynamic and static shortcuts. Actions depend on app support and Android shortcut-host permissions; App Lock authentication still applies.
- **New app indicator:** apps installed after the feature is initialized receive a subtle **New** label until successfully launched from Alpharail. Existing apps are not all marked new, and ordinary app updates do not reset the label. Toggle it in **Home & apps → New app indicator**. Launches outside Alpharail are not tracked.

### Optional Chats inbox

Open **Chats → Enable Chats**, then grant **Alpharail Chats** notification access in Android settings. The inbox updates while open and groups active notifications into conversations when the messaging app supplies a stable conversation ID. It doesn’t retrieve old chat history or save or upload message contents.

Use **App: All** to filter the active inbox by messaging app. Each row shows the source app icon. If previews are enabled and the source supplies a compatible sender picture, Alpharail displays it too. Open a thread to pin it when Android provides a stable conversation ID; pinned threads move to the top when active. Pins stay local and can be cleared in **Chat settings**.

Previews are off by default. Hidden and App Locked apps are excluded. **Reply** and **Open conversation** only appear when a compatible notification action is available; **Open app** is separate. Use **Chat settings** to filter messaging apps, mute new-message dots or hide previews per app, or turn Chats off. You can revoke notification access in Android settings. **Chat settings → Screenshot protection** controls whether Android blocks screenshots and screen recordings of Chats. Protection is on by default.

### Updates

Open **Launcher settings → System → Updates** to check for a newer signed APK or enable automatic checks. With automatic checks on, Alpharail checks GitHub at most once a day when opened. Downloads open in your browser and Android asks you to confirm installation; updates are not silently downloaded or installed.

### Backups and upgrades

**Launcher settings → Backup and restore** exports Favorites, hidden apps, appearance, Collections, custom app names, and widget layout information. After importing, use **Widgets → Recover widgets** to re-add widgets through Android. Backups don’t include widget contents, accounts, App Lock, or Chats notification access.

- **Updating from signed Alpharail v0.3.4 or newer:** Install the newer APK over the existing app when it uses the same signing certificate.
- **Updating from an old debug-signed Alpharail build:** Export settings first. Switching to the permanent signing key requires a one-time uninstall and reinstall.
- **Moving from Still:** Alpharail installs separately. Export settings from Still, import them into Alpharail, and set Alpharail as your Home app.

## Browser demo and development

The browser demo uses simulated apps, widgets, and messages. It does not yet mirror every native feature or the latest settings organization. Its backups are separate from Android backups. To run it locally, use `python3 -m http.server 8000` and open `http://localhost:8000`.

- **Android SDK build:** Run `gradle -p android assembleDebug` with JDK 17, Gradle 8.9, and Android SDK 35.
- **Linux x86-64 without the Android SDK:** Run `android/scripts/package-apk.sh build/Alpharail-debug.apk` with Java 21, Python 3, curl, and OpenSSL. This is also the packaging path used by CI.

### Release signing

Alpharail v0.3.4 introduced permanent signing. Keep the PKCS#12 keystore and its password backed up securely, and never commit either. The current workflow expects key alias `still` and the same password for the key and keystore.

Configure repository Actions secrets `STILL_KEYSTORE_BASE64` and `STILL_STORE_PASSWORD`, and the Actions variable `STILL_CERTIFICATE_SHA256` using the public SHA-256 certificate fingerprint from `keytool -list -v`. Bump `versionCode` and `versionName` in `android/app/build.gradle`, then run **Android APK** on `main` with **publish** enabled. The workflow checks the certificate fingerprint before publishing. Ordinary CI builds do not publish releases.

## License

Alpharail is licensed under the [MIT License](LICENSE).

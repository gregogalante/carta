# Carta

Your Android screen, as calm as a printed page.

Carta makes a phone look and feel closer to an e-ink reader. You pick the effects you want once; the big card in the app (or the home screen widget) turns them all on and off together, and puts your phone back exactly as it was.

## Effects

| Effect | What it does | Needs |
|---|---|---|
| Grayscale | Removes color from the whole screen, using Android's own color correction. | System permission (one adb command) |
| Warm light | Filters out blue light with Android's Night Light, at a paper-like warmth. A warmer Night Light of yours is kept. | System permission (one adb command) |
| Extra dim | Dims whites below the usual minimum brightness, at the strength set in Android. Android 12+. | System permission (one adb command) |
| Bold text | Android's bold text everywhere, for full, print-like letters. Android 12+. | System permission (one adb command) |
| Still motion | Turns off system animations, so screens change instantly. | System permission (one adb command) |
| Paper texture | A paper grain over every app and slightly softened blacks, with adjustable intensity or an automatic one that follows the light/dark theme. | Accessibility service |
| Neutral wallpaper | Paper-colored wallpaper with the light theme, ink with the dark one. Your wallpaper is saved and restored. | All files access |

Also included:

- **Widget**, resizable from 1×1 to wide, following the system light/dark theme.
- **Languages**: English, Italian, Spanish, French, German, Portuguese, selectable per app in Android's language settings.
- The app's accent follows your Material You color on Android 12+.

## Install

Requires Android 11 or newer.

1. Download `carta-<version>.apk` from [Releases](https://github.com/gregogalante/carta/releases) and install it.
2. Grant the system permission once, from a computer with [adb](https://developer.android.com/tools/adb) and USB debugging enabled:

   ```bash
   adb shell pm grant com.gregoriogalante.carta android.permission.WRITE_SECURE_SETTINGS
   ```

3. Open Carta and follow the **Setup** section: enable the texture service among the accessibility services, and grant file access for the neutral wallpaper.

Each permission only unlocks its own effects; skip any you do not need.

## Privacy

Carta has no internet permission and collects nothing. The accessibility service is used only to draw the texture above other apps: it does not read screen content and listens to no events. File access is used only to copy your current wallpaper into the app's private storage, so it can be restored.

## Build

JDK 17 and the Android SDK (API 36) are required.

```bash
./gradlew assembleDebug
```

Release builds are signed when `signing/keystore.properties` exists (the folder is gitignored):

```properties
storeFile=/absolute/path/to/carta-release.jks
storePassword=...
keyAlias=carta
keyPassword=...
```

```bash
./gradlew assembleRelease
```

## Credits

Typeset in [Literata](https://github.com/googlefonts/literata), licensed under the SIL Open Font License 1.1 (see [`licenses/Literata-OFL.txt`](licenses/Literata-OFL.txt)).

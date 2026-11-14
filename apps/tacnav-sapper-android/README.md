# Tac Nav Sapper Android

Native English-only Android client for package `com.tacnav.sapper`. A player uses it
to lay, detect, reveal, and clear minefields on the Tac Nav map. It supports Android 9
(API 28) and newer with Google Play Services and builds against API 37.

This is a separate project from `apps/tacnav-mortar-android/` and
`apps/tacnav-object-android/`. It shares no code with them at build time. Activation,
PIN, encrypted storage, the QR scanner, the compass, and the theme were copied from
Tac Nav Object and adapted. The application has no Firebase and never registers for
push messages.

## Toolchain

Use the same versions as Tac Nav Mortar and Tac Nav Object:

- Android Studio 2026.2.1 with embedded JDK 17 or newer
- Android SDK Platform 37.0, Build Tools 36.0.0, Platform Tools 37.0.1
- Gradle Wrapper 9.6.0, Android Gradle Plugin 9.4.0, Kotlin 2.4.10

All direct dependency and plugin versions are in `gradle/libs.versions.toml`.
Every configuration uses dependency locking. Refresh the tracked lock state after
a version change, and review it before committing:

```powershell
.\gradlew.bat dependencies :app:dependencies --write-locks
```

## Build and test

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

`src/test` covers every game rule with a fake clock, fake fixes, and a fake server:

- geometry;
- the search cadence;
- detector levels, signals, and hysteresis;
- the Mark minefield, Lay mines, and Clear mines holds;
- limits and the outbox;
- the generated tone;
- the controller end to end.

`src/androidTest` needs a connected phone. It covers the HTTP contract of every
request, encrypted storage, the repository, the screens, the screen-awake flag, and
the absence of services and background location. Screen tests run in the debug-only
`ComposeTestHost`, so a locked phone does not block them. That activity is not in
release builds.

On a machine short of memory, Gradle's test worker can fail to start. The same test
APKs can then run directly:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb install -r -t app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w com.tacnav.sapper.test/androidx.test.runner.AndroidJUnitRunner
```

## Icon

`art/icon-source.png` is the supplied 512 px icon. Launchers show only the central
72 dp of a 108 dp adaptive foreground, so the whole icon is scaled to 72 % of
`drawable-*/ic_launcher_foreground.png`. The margin uses background colour `#3F3D24`,
taken from the icon. Without that margin the mask clips the N and S letters. Legacy
`mipmap-*/ic_launcher.png` files are the whole icon at 48 dp.

## Release signing

Sign with the shared Tac Nav release keystore, kept outside Git with two encrypted
backups. Never place a keystore, signing password, service-account file, bearer
credential, or activation code in this project.

In Android Studio use **Build > Generate Signed App Bundle or APK > APK**, choose
`release`, and upload the APK on the Sapper tab of Device applications.

## Phone setup

- Allow precise location and the camera when asked.
- Before the first activation, Settings opens with PIN `4321` and offers manual
  activation by server URL and one-time code. The server URL must use HTTPS.
- Keep the application open while detecting or holding a button. It works only on
  screen, keeps the display on, and pauses every hold when it leaves the screen.
- Set the alarm volume. Detector signals play on the alarm channel, so media volume
  does not mute them.

## Limits by design

Game decisions are made on the phone, under the platform's client-trust model.

- The detector checks the latest search results about once a second. A field laid or
  cleared elsewhere shows up at the next search: within about 5 seconds while walking,
  or 30 seconds while standing.
- The heading uses the compass as it reads, corrected to true north. There is no
  calibration. The screen warns while Android reports the compass as unreliable.
- Signals are short 1 kHz tones generated at run time. The screen never shows the
  distance or the direction to a field.
- Completed writes wait in an encrypted outbox and are retried every 5 seconds.
  Creates carry an idempotency key, so a retry never stores a second minefield or
  marker.
- Reset connection forgets the credential on the phone only. The server activation
  stays until staff revoke it or a new activation replaces it.

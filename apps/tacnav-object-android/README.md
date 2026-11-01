# Tac Nav Object Android

Native English-only Android client for package `com.tacnav.beacon`. It turns a
phone into a guarded, destructible map objective. It supports Android 9 (API 28)
and newer with Google Play Services and builds against API 37.

The package is not `com.tacnav.object` because `object` is a Kotlin keyword.

This is a separate project from `apps/tacnav-mortar-android/`. They share no code
at build time and run on separate phones.

## Toolchain

Use the same versions as Tac Nav Mortar:

- Android Studio 2026.2.1 with embedded JDK 17 or newer
- Android SDK Platform 37.0, Build Tools 36.0.0, Platform Tools 37.0.1
- Gradle Wrapper 9.6.0, Android Gradle Plugin 9.4.0, Kotlin 2.4.10

All direct dependency and plugin versions are in `gradle/libs.versions.toml`.
Every configuration uses dependency locking. Refresh the tracked lock state after
a version change, and review it before committing:

```powershell
.\gradlew.bat :app:dependencies --write-locks
```

## Build and test

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

`src/test` covers every game rule with fake clocks and a fake GPS. `src/androidTest`
needs a connected phone and covers the HTTP contract, encrypted storage, the main
screen, the foreground service, and the screen-awake flag. Screen tests run in the
debug-only `ComposeTestHost`, so a locked phone does not block them. That activity
is not in release builds.

## Firebase

`app/google-services.json` is the public Firebase Android client configuration for
project `tac-nav`, and it lists both Tac Nav packages. It is not a service-account
key. The WordPress sender uses one project-wide service account, so this application
needs no server credential of its own.

## Release signing

Sign with the shared Tac Nav release keystore, kept outside Git with two encrypted
backups. Never place a keystore, signing password, service-account file, bearer
credential, or activation code in this project. Losing the keystore means every
phone must uninstall and reinstall, and then activate again.

In Android Studio use **Build > Generate Signed App Bundle or APK > APK**, choose
`release`, and upload the APK on the Object tab of Device applications.

## Phone setup

- Allow precise location and notifications when asked.
- Open the application once after every reboot. Android allows a location service
  to start only from a visible action, so automatic activation and the downtime
  countdown cannot start themselves after a reboot.
- Exclude the application from battery optimization. Some manufacturers stop
  foreground services more aggressively than Android requires.

Field results on named phones belong here once the field tests are done.

## Limits by design

Game decisions are made on the phone, under the platform's client-trust model.

- A phone that is off cannot destroy itself. Its object leaves the map within about
  60 seconds and the wreck appears at the next open.
- Airplane mode hides the object after the same lease, and hits arrive on reconnect.
- A changed phone clock can skew downtime measured across a restart.
- The rectangle uses the compass as it reads. There is no calibration.

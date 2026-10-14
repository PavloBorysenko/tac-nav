# Tac Nav Mortar Android

Native English-only Android client for package `com.tacnav.mortar`. It supports
Android 9 (API 28) and newer and builds against API 37.

## Toolchain

Use only the versions approved in the OpenSpec design:

- Android Studio 2026.2.1 with embedded JDK 17 or newer
- Android SDK Platform 37.0, Build Tools 36.0.0, Platform Tools 37.0.1
- Gradle Wrapper 9.6.0, Android Gradle Plugin 9.4.0, Kotlin 2.4.10, and KSP 2.3.10

All direct dependency and plugin versions are centralized in
`gradle/libs.versions.toml`. Do not add a dependency without updating the
approved list in the OpenSpec design first.

## Dependency locking

Every configuration is configured for Gradle dependency locking. After the
approved SDK and JDK are available, generate or refresh the tracked lock state
from this directory:

```powershell
.\gradlew.bat :app:dependencies --write-locks
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Review lockfile changes before committing. Never hand-edit resolved versions.

## Local and secret files

`local.properties`, build output, APK/AAB files, and signing stores stay
untracked. `app/google-services.json` is the public Firebase Android client
configuration for project `tac-nav`; it is not a Firebase service-account key.
Never place a service-account file, bearer credential, activation code, or
signing password in this project.

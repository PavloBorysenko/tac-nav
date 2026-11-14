# Tasks

## 1. Server type `sapper`

- [x] 1.1 Add the `SAPPER` entry to `App_Types` from design decision 11. Make `canonical()` map `object` and `sapper` to themselves, and dispatch Sapper reference validation. Verify with PHPUnit tests for canonical values, including a legacy free-text value, for package matching of all three packages, and for `filter_rows()` keeping Sapper records off the Mortar tab.
- [x] 1.2 Add `Validation::sapper_reference_defaults()` and `sapper_reference_settings()` per the provisioning spec. Verify with tests for the defaults, a negative number, a zero radius, a 400 degree sector, a three-digit PIN, and 0 meaning unlimited.
- [x] 1.3 Make `Admin::page_query()` append `&app=` for every type other than Mortar. Verify with tests that Sapper and Object redirects carry their slug and Mortar's address is unchanged.
- [x] 1.4 Add `Apps/Sapper_Page`, `templates/apps/sapper.php` with catalog-title icon selects, and the `App_Pages::all()` entry after Object. Verify with an App_Pages test that the Sapper tab lists only Sapper applications and the Sapper release.
- [x] 1.5 Bump the `tacnav-devices` version so the `/tacnav-sapper/` and `/tacnav-sapper.apk` rewrites flush. Verify with a Release test for the Sapper paths, the option, and device release metadata for the Sapper type.
- [x] 1.6 Add a Sapper app button linking to `/tacnav-sapper/` after Object app in `wp-content/themes/tacnav/templates/front-page.html`. Verify that the template contains the link.
- [x] 1.7 Run phpcs, PHPStan, and PHPUnit on the changed files. Verify that they pass with no new baseline entries.
- [x] 1.8 Update the `tacnav-devices` documents whose catalog conditions match: provisioning and the type list. Update the theme documentation if it describes the front-page controls. Verify that each catalog link condition still matches its document.

## 2. Android project scaffold

- [x] 2.1 Create `apps/tacnav-sapper-android/`:
  - Gradle wrapper, version catalog, and lockfiles at the pinned toolchain, without Firebase;
  - package `com.tacnav.sapper`, app name `Tac Nav Sapper`, version 0.1.0 (versionCode 1), minSdk 28, compile and target SDK 37;
  - permissions: internet, fine location, and camera, with no background location.

  Verify that `.\gradlew.bat :app:assembleDebug` builds.
- [x] 2.2 Copy the supplied icon to `art/icon-source.png` and generate the adaptive foreground, the background colour, and the legacy mipmaps. Verify that the debug APK's badging lists the launcher icon and that the icon renders on the connected phone's launcher.
- [x] 2.3 Write `README.md` covering toolchain, build and test commands, phone setup, and limits by design. Verify that it names every Gradle command used in sections 4 to 6.

## 3. Activation, storage, and settings

- [x] 3.1 Adapt Object's activation sources for QR and manual entry, sending `package_id` `com.tacnav.sapper`. Verify with a payload-parser unit test and an instrumented HTTP contract test for the activation request.
- [x] 3.2 Add the encrypted state store, the PIN policy (`4321` before activation, delays after failures), and `SapperSettings` with the reference keys from design decision 10. Activation replaces settings and clears counters and progress. Reset connection wipes the store. Verify with unit and instrumented storage tests.
- [x] 3.3 Build the PIN-gated settings screens: map and team limited to scopes, numeric fields, catalog icon pickers, manual activation, and Reset connection. Verify with a Compose UI test for the PIN gate and Reset connection.

## 4. Game logic (pure, unit-tested)

- [x] 4.1 Add the geometry helpers: local metre projection, circle and polygon distance, point in polygon, the nearest point with its bearing, and the rectangle from centre, heading, length, and width. Verify with unit tests, including a 40 degree rectangle.
- [x] 4.2 Add `SearchPolicy` (5 s after moving more than the fix accuracy, otherwise 30 s). Verify with unit tests for a still phone and a walking phone.
- [x] 4.3 Add `DetectorMachine` with the states, signals, and hysteresis from design decision 5, and with Mark minefield and No mines availability. Verify with unit tests for every Detect and Mark scenario in `specs/tacnav-sapper-android/spec.md`.
- [x] 4.4 Add `MarkHold`. Verify with unit tests that release and GPS loss reset it and that completion produces a visibility union.
- [x] 4.5 Add `LayingMachine`. Verify with unit tests for the first-press record, release and resume, the moved-away lock, Reset laying, completion geometry, the limit, and restart persistence.
- [x] 4.6 Add `ClearingMachine`. Verify with unit tests for the nearest target, the nearest centre when inside several fields, per-field progress, discarding a vanished field, and completion.
- [x] 4.7 Add `Limits` and `Outbox` with the write rules from design decision 8. Verify with unit tests for idempotent create keys, the 409 recompute for marks, 404 counting as done, and counters rising only on acceptance.

## 5. Android shell

- [x] 5.1 Add foreground-only fused location, the rotation-vector heading copied from Mortar with declination, and keep-screen-on. Verify with an instrumented test that the screen-on flag is set and that no service is declared.
- [x] 5.2 Add the `AudioTrack` beep generator on `USAGE_ALARM` and the screen flash. Verify with a unit test for the generated tone length and for the two-beep pattern.
- [x] 5.3 Add the network client for role search, create, and partial update. Verify with an instrumented HTTP contract test for each request body.
- [x] 5.4 Add `SapperController`, which wires the machines to the sensors, network, store, and ticker. Verify with unit tests driven by a fake clock, a fake location, and a fake API.
- [x] 5.5 Build the screens:
  - Home in both connection states;
  - Detect: themed, with offline, GPS, and compass warnings, the flash, the blinking red Mark minefield hold, and No mines;
  - Lay mines: hold, progress, Reset laying, and the limit message;
  - Clear mines: search state, hold, progress, the safe message, and No mines;
  - Back controls on every mode screen.

  Verify with Compose UI tests for the main states of each screen.

## 6. Verification

- [x] 6.1 Run `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug`. Verify that all pass.
- [x] 6.2 Run `:app:connectedDebugAndroidTest` on the connected phone. Verify that every instrumented test passes.

  Result (2026-11-08): the debug and androidTest APKs passed 28 of 28 on the moto g53 (Android 14). Gradle could not start its test worker because the machine was out of memory, so the same APKs were installed with adb and run with `am instrument`, as the README describes.
- [ ] 6.3 Install the debug build on the connected phone and activate it against a site the phone can reach, using a Sapper application. Then:
  - lay a minefield;
  - detect it from outside;
  - mark it with a second team's activation;
  - clear it;
  - place a No mines marker.

  Verify each result on the server and record it.

  Server replay (2026-11-08, local site, Sapper applications for Blue (16) and Red (15) on map 19). The phone could not reach the site: the application requires HTTPS, and the Local Live Link is password-protected. Results:
  - An Object package with a Sapper code was refused 403 `tacnav_activation_package`, and the same code then activated Sapper.
  - Blue laid a minefield: role `minefield`, owner Blue, visible to Blue. A retry with the same key returned the same id.
  - Red found it with the 20 m role search and marked it: visible to Blue and Red, owner still Blue. A stale stamp returned 409.
  - Red cleared it with `ttl_seconds: 1`, and it left search.
  - Red placed No mines: ordinary role, visible to Red, no expiry.

  The test objects were deleted. The on-phone run is still open.
- [x] 6.4 Run `openspec validate add-tacnav-sapper --strict`. Verify that it passes.

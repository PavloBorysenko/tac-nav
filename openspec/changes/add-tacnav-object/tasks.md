# Tasks

## 1. Map objects in tacnav-maps

- [x] 1.1 Add failing PHPUnit tests for a not-searchable object absent from search, a default-searchable row found as before, a staff edit preserving the flag, a repeat create with one key storing one object, and equal keys from two applications storing two objects.
- [x] 1.2 Raise `Activator::DB_VERSION` to 5 and add columns `searchable` and `client_key` with a unique key on `(application_id, client_key)`; verify an upgraded database keeps every row searchable.
- [x] 1.3 Carry `searchable` and `client_key` through `Geo_Store` insert, update, and row mapping, keeping stored values when a payload omits them; verify the staff-edit test passes.
- [x] 1.4 Filter `Geo_Store::search()` to searchable rows and verify the search tests pass.
- [x] 1.5 Accept `searchable` and `idempotency_key` in `Application_Write::normalize()` and return the stored row from `REST::create_object()` on a repeated key without republishing the picture; verify the idempotency tests pass.
- [x] 1.6 Include `searchable` in picture rows and change `addObjectLayer()` in `assets/canvas.js` so a not-searchable iconless circle or polygon has no centre pin and binds its title to the shape; verify `npm --prefix tools/js-lint run lint` on the file and the Jest suite if one covers it.

## 2. Type-aware tacnav-devices

- [x] 2.1 Add failing PHPUnit tests for Object validation rules and defaults, Mortar validation unchanged, list filtering by type, and activation refused without consuming the code in both directions.
- [x] 2.2 Add the `App_Types` definition with each type's package id, release option, file name, public slug, and validator; verify unit tests cover both types and an unknown type.
- [x] 2.3 Add `Validation::object_reference_defaults()` and `object_reference_settings()` with cross-field rules, and dispatch `Application_Store::save()` on type; verify the validation tests pass.
- [x] 2.4 Filter `Application_Store::all()` by type, with the Mortar list showing every non-`object` record, and make the Mortar form's type a fixed value that preserves an existing record's type; verify the list tests pass.
- [x] 2.5 Compare `package_id` to the application's type in `Application_Store::activate()` before consuming the code, treating a missing value as Mortar; verify the activation tests pass and the existing activation tests stay green.
- [x] 2.6 Parameterize `Release` and `Admin::release_metadata()` by type, keeping Mortar's option, file, and routes, and add `/tacnav-object/` and `/tacnav-object.apk`; extend the release tests to prove the two releases are independent.
- [x] 2.7 Make the device `/release` route use the authenticated type and make `templates/download.php` application-neutral; verify with a test for each type.
- [x] 2.8 Add `App\Object_Page`, `templates/apps/object.php`, and the entry in `App_Pages::all()`, with the default-PIN warning beside the PIN field; extend `TacNav_Devices_App_Pages_Test` to cover the tab, filtering, and staff-only access.
- [x] 2.9 Carry the `app` slug through `Admin` redirects so Object actions return to the Object tab and a missing slug still lands on Mortar; verify with a redirect test.
- [x] 2.10 Add the Object app control to `wp-content/themes/tacnav/templates/front-page.html` and bump the plugin version so rewrites flush; verify `/tacnav-object/` resolves in a test.

## 3. Android project

- [x] 3.1 Create `apps/tacnav-object-android/` with its own Gradle wrapper, version catalog, lockfiles, package `com.tacnav.beacon`, minimum SDK 28, and the launcher resources; verify `gradlew assembleDebug` builds an empty application.
- [x] 3.2 Declare location, notification, camera, vibrate, and foreground-location permissions and a `location`-type foreground service; verify the merged manifest contains no microphone permission.
- [x] 3.3 Implement activation by QR and manual entry, sending `package_id`, with credential storage in encrypted local state; verify parser and activation unit tests.
- [x] 3.4 Implement the HTTP API for objects including description, delete, `searchable`, and `idempotency_key`, plus icons, FCM, inbox, and acknowledgement; verify against fake-server unit tests.
- [x] 3.5 Implement the local PIN with attempt delays and the settings screens with map, team, and catalog icon selection; verify PIN and settings unit tests, including that edits made during a session apply only at the next activation.

## 4. Game logic

- [x] 4.1 Implement the pure state machine for Waiting, Deploying, Active, and Destroyed with persisted state and effects; verify state-transition unit tests with a fake clock.
- [x] 4.2 Implement GPS usability, the activation gate, and cancellation on degraded accuracy; verify unit tests for each spec scenario.
- [x] 4.3 Implement shape generation for point, circle, and heading-oriented rectangle; verify rectangle corner geometry at several headings and latitudes.
- [x] 4.4 Implement the shared movement model for still detection and automatic deactivation, including arm and disarm rules for both modes; verify unit tests including jitter within accuracy.
- [x] 4.5 Implement the hint area with the `R * u^(1/3)` offset, stable centre, and re-roll on exit; verify a seeded statistical test of the distribution and a stability test.
- [x] 4.6 Implement the publisher with the 5-second moving, 30-second still cadence, 60-second lease, Deploying and Active visibility rules, and create-on-404; verify write-count unit tests for moving and still objects.
- [x] 4.7 Implement mailbox reading and hit counting with atomic persisted event ids and current-life object ids; verify tests for duplicates, crash before acknowledgement, late delivery after deactivation, and old-life events.
- [x] 4.8 Implement destruction with a fixed wreck position, new-id wreck creation under a persisted key, retry, and later live-object deletion; verify tests for offline destruction and a lost create response.
- [x] 4.9 Implement the downtime countdown, pause, uninterrupted reset, warnings, and destruction on expiry; verify every downtime spec scenario with a fake clock.
- [x] 4.10 Implement protected reset and QR reset, including a new wreck key; verify unit tests for both.
- [x] 4.11 Implement recovery after process death or reboot, including gap deduction and displacement; verify short-restart, long-off, and moved-while-off tests.

## 5. Android runtime and interface

- [x] 5.1 Implement the foreground service driving ticks, fixes, and mailbox reads in every running state; verify with instrumented tests that start and stop it.
- [x] 5.2 Implement the main screen with state, session timer, hit count, GPS status, progress bar, Activate, Arm, Deactivate, and downtime counters; verify with Compose UI tests.
- [x] 5.3 Keep the display awake while an activity is open; verify with a UI test on the window flag.
- [x] 5.4 Implement alarm-channel signals with vibration and the 5-second torch pulse, and add generated hit, warning, and siren sounds; verify unit tests that torch failure does not stop destruction.
- [x] 5.5 Implement FCM registration and wake-up handling; verify that a wake-up triggers a mailbox read in a unit test.

## 6. Documentation

- [x] 6.1 Update `tacnav-maps` docs `data-model.md`, `hooks-and-apis.md`, and `development.md` for the new columns, write fields, and canvas rule; verify against the catalog conditions.
- [x] 6.2 Update `tacnav-devices` docs `provisioning-and-messaging.md`, `architecture.md`, `integrations.md`, and `human-overview.md`, removing the single-application limits; verify no stale limit remains.
- [x] 6.3 Add `apps/tacnav-object-android/README.md` with build, Firebase, signing, and supported-device notes, and update `docs/architecture.md` for the new application; verify the steps reproduce a build.

## 7. Release and field validation

- [ ] 7.1 Project owner registers `com.tacnav.beacon` in Firebase project `tac-nav` and supplies `google-services.json`; verify a wake-up reaches a test phone.
- [x] 7.2 Run all WordPress gates on changed files, Android unit, instrumented, lint, and release-build gates, and `openspec validate add-tacnav-object --strict`; fix introduced findings.
- [ ] 7.3 Regression-test Mortar with its existing APK against the updated server: activation, shot, hit delivery, and download page; record the result.
- [ ] 7.4 Field-test end to end with a Mortar phone: deploy, hit during Deploying, hit while Active, hint area exclusion, destruction signals, wreck, reset, and second wreck; confirm server and phone agree.
- [ ] 7.5 Field-test screen-off service, battery restrictions, background torch and alarm audio, reboot downtime, and real GPS at the 15 metre limit on one Android 9 device and one current device; record the constraints in the README.
- [ ] 7.6 Sign the release APK with the shared Tac Nav release keystore, kept outside Git with two encrypted backups, publish it on the Object tab, and verify clean-device download, install, and QR activation with no private credential in the package.

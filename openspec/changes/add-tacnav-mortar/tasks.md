# Tasks

## 1. Repository and component setup

- [x] 1.1 Update the repository architecture map and `.gitignore` for first-party `tacnav-devices`, `apps/tacnav-mortar-android`, Gradle output, local SDK files, APKs, signing stores, and private Firebase credentials; verify only intended source/config paths appear in `git status`.
- [x] 1.2 Scaffold the first-party `tacnav-devices` plugin through the WordPress component-creation workflow with its confirmed slug, prefix, namespace, dependency on `tacnav-maps`, and documentation catalog; verify activation fails clearly when its dependency is unavailable.
- [x] 1.3 Record and obtain approval for the exact new PHP and Android dependency list before installing anything; verify licenses, supported platform versions, and lockfile changes are documented.

## 2. Geo lifetime and spatial foundation

- [x] 2.1 Add focused database capability tests for the installed MySQL/MariaDB spatial syntax, geometry projection, and index plan; verify an unsupported environment fails clearly instead of falling back to a full JSON scan.
- [x] 2.2 Replace internal/API `ttl_minutes` handling with `ttl_seconds`, migrate quick-preset metadata once, and convert existing player form choices and self-points to seconds; verify expiry unit tests cover 30 seconds, 60 seconds, zero, omission on update, and rejection of `ttl_minutes`.
- [x] 2.3 Extend the geo-object schema with application origin, position accuracy, and spatial projection/index fields and backfill existing normalized geometry; verify every existing row receives a valid projection and canonical JSON remains unchanged.
- [x] 2.4 Update geo writes so canonical geometry and its spatial projection change atomically for marker, circle, polyline, and polygon; verify focused store tests cover create and update for every kind.
- [x] 2.5 Add an application-principal integration boundary to `tacnav-maps` without changing staff/player/guest access behavior; verify access tests cover own-application edits, cross-application refusal, arbitrary existing catalog icons, public application markers, and scope rejection.
- [x] 2.6 Implement map-scoped geometry search with indexed candidate selection, exact intersection, visibility bypass, expiry exclusion, and minimal raw results containing id, kind, geometry, and optional position accuracy; verify tests cover hidden objects, cross-map exclusion, circles, lines, polygons, omitted ownership metadata, and absence of `distance_m` or game decisions.
- [x] 2.7 Update `tacnav-maps` documentation for the schema, second-based API, application integration hooks, and visibility-independent search; verify documentation links from the plugin catalog resolve.
- [x] 2.8 Run `tacnav-maps` PHPCS, PHPStan, focused PHPUnit tests, the full PHPUnit suite, JS lint, and the required harness review/browser gates; fix introduced failures and record any pre-existing formatting-only debt separately.

## 3. Device provisioning plugin

- [x] 3.1 Create versioned device-plugin tables for application definitions, allowed maps/teams, installations, hashed activation codes, and object bindings; verify activation/upgrade tests create required keys and unique constraints.
- [x] 3.2 Implement staff administration for application type, name, reference settings, scopes, default map/team, revocation, and current APK metadata; verify a Player is refused and invalid default/scope combinations cannot be saved.
- [x] 3.3 Implement expiring single-use activation generation plus QR/manual activation response; verify successful use consumes the code and returns identity, bearer credential, scopes, defaults, reference settings, and service configuration.
- [x] 3.4 Implement one-active-phone replacement and explicit revocation using hashed opaque bearer credentials; verify reactivation invalidates the old bearer and delivery registration immediately.
- [x] 3.5 Resolve authenticated application context for `tacnav-maps`, including current map/team validation against allowed scopes and map membership; verify local request tampering cannot expand authorization.
- [x] 3.6 Add authenticated complete-icon-catalog and Android-release metadata responses; verify icons outside the team palette appear and private signing/Firebase credentials never appear.
- [x] 3.7 Add the activation/download handoff UI with QR, manual URL/code instructions, package `com.tacnav.mortar`, version, and APK link; verify staff can use it and non-staff cannot inspect activation data.

## 4. Durable messaging and Firebase

- [x] 4.1 Add event and delivery tables with cursor ordering, source/shot/recipient uniqueness, acknowledgement state, and retention indexes; verify duplicate insertion and cursor tests exercise the database constraints.
- [x] 4.2 Implement same-map batch submission that server-stamps source identity and creates deliveries only for active object bindings; verify ordinary marks, stale bindings, and cross-map ids cannot expose a mailbox.
- [x] 4.3 Implement authorized inbox cursor reads and idempotent acknowledgement; verify only the active bound application can read and repeated submission/acknowledgement produces one logical delivery.
- [x] 4.4 Implement one-hour acknowledged and 24-hour pending retention with opportunistic and scheduled cleanup; verify time-controlled tests preserve a 12-hour offline hit and remove expired deliveries.
- [x] 4.5 Configure Firebase HTTP v1 through an external least-privilege service-account credential path and send user-visible high-priority wake references only after delivery commit; verify mocked push failure leaves the mailbox pending.
- [x] 4.6 Add FCM token registration, rotation, revocation, and stale-token cleanup for the active installation; verify a reactivated phone stops notifications to the former token.
- [x] 4.7 Document provisioning, bearer storage, mailbox contracts, Firebase configuration, secret placement, retention, and incident rotation; verify no private key or bearer token is tracked by Git.
- [x] 4.8 Run device-plugin PHPCS, PHPStan, focused PHPUnit tests, the full suite, and required harness review/browser gates; fix introduced failures before Android integration.

## 5. Android project and activation

- [x] 5.1 Create the Kotlin Android project at `apps/tacnav-mortar-android` with application name Tac Nav Mortar, package `com.tacnav.mortar`, minimum API 28, English resources, and approved dependencies; verify debug assembly and unit tests run from a clean checkout.
- [ ] 5.2 Add the matching `google-services.json` client configuration for Firebase project `tac-nav` and Firebase Messaging wiring; verify the debug app obtains a registration token on a Google Play Services device without containing a server private key.
- [ ] 5.3 Convert the supplied 512px artwork into legacy, adaptive foreground/background, and monochrome launcher resources; verify Android launcher previews do not crop the N/S letters or mortar legs across masks.
- [ ] 5.4 Implement QR scanning and manual server URL plus one-time-code activation through the same API contract; verify a valid code activates once and invalid/reused codes show English errors.
- [ ] 5.5 Store bearer identity securely and implement logout/reactivation that clears local settings, calibration, pending service state, and old credentials; verify reactivation loads the server reference and the old phone receives authorization failure.
- [ ] 5.6 Implement local PIN handling with default `4321`, PIN change, throttled failed attempts, and separation between protected gameplay settings and unprotected calibration/player mode selection; verify UI tests cover protected and unprotected paths.
- [ ] 5.7 Implement locally selected map/team from activated scopes and the complete online icon picker with offline selected-icon cache; verify unallowed contexts are unavailable and icons outside the team palette can be selected.
- [x] 5.8 Implement protected non-empty checkbox sets for button/acoustic firing and free/precise/approximate aiming plus protected approximate radius and elevation-reference angle; verify defaults are 30 metres and 60 degrees and empty sets cannot be saved.

## 6. Android sensors and active session

- [ ] 6.1 Implement permission/status handling for precise location, camera, microphone, notifications, and Google Play Services; verify every denial produces the specified English degraded behavior.
- [x] 6.2 Implement current location with configurable maximum horizontal error and age; verify unit/UI tests block an 18-metre fix at a 10-metre threshold and block a fix older than five seconds.
- [ ] 6.3 Implement fused orientation, geomagnetic true-north correction, phone-axis convention, sensor-quality reporting, and known-point yaw calibration; verify deterministic sensor tests apply declination and saved offsets correctly.
- [x] 6.4 Implement one-action elevation calibration against the protected reference angle, defaulting to 60 degrees, and linear angle-to-range/inverse-range functions; verify offset, endpoint, and intermediate-angle tests.
- [ ] 6.5 Implement the user-started foreground session service with required location/microphone types and persistent notification; verify screen-off retains an active test session and reboot does not silently arm listening.
- [x] 6.6 Implement acoustic level display, configurable dBFS threshold, impulse duration, debounce, arming, and disarming using microphone input; verify recorded/synthetic impulses produce one trigger and microphone denial leaves button firing available.
- [ ] 6.7 Implement five-second torch feedback after an accepted button shot with deadline restart and service-stop cleanup; verify repeated shots extend the period and missing/busy torch hardware warns without cancelling the shot.
- [ ] 6.8 Field-test the mounted phone 8–10 cm from the tube at multiple headings and elevations; record measured magnetic/GPS behavior and verify calibration warnings/defaults are adjusted without changing the specified firing model.

## 7. Shot state and resolution

- [x] 7.1 Implement trigger-time sensor validation and immutable shot snapshots with UUIDs; verify each missing/stale/inaccurate input blocks with a specific English reason and valid input stores one shot.
- [x] 7.2 Implement durable concurrent shot states and flight scheduling using monotonic in-session timing plus restart recovery; verify two overlapping shots remain independent through screen-off and process restart.
- [x] 7.3 Implement geodesic impact calculation from captured origin, calibrated true bearing, and linear range; verify known coordinate fixtures stay within the selected numeric tolerance.
- [x] 7.4 Implement due-shot offline queuing and connectivity recovery; verify an overdue offline shot searches positions current when connectivity returns rather than trigger-time positions.
- [ ] 7.5 Integrate circle search before public point-impact creation with configured title, icon, visibility, and `ttl_seconds`; verify the new impact is absent from its own search and visible to all intended map viewers.
- [x] 7.6 Implement local nearest-distance calculation for marker, circle, polyline, and polygon geometry plus per-object linear probability, one persisted secure random roll per shot/object, no team/type/id filtering, and local miss records; verify geometry, center, boundary, intermediate, friendly, and retry cases without relying on server `distance_m`.
- [ ] 7.7 Submit successful recipients as one idempotent hit event with shot facts and retain retry state across partial failures; verify response loss does not reroll or duplicate a recipient delivery.

## 8. Aiming and application UI

- [ ] 8.1 Build the primary English firing screen with server/session state, GPS accuracy, true heading, elevation, calculated range, fire control, acoustic ready control, concurrent flight countdowns, and mode selectors shown only when multiple allowed choices exist; verify one-mode and multi-mode UI states.
- [ ] 8.2 Implement free and precise-coordinate guidance for required bearing/elevation, live numeric turn/tilt correction, predicted impact, miss offset, and out-of-range indication; verify firing while misaligned still uses actual sensors.
- [ ] 8.3 Implement approximate-coordinate guidance with pre-shot direction-only/in-zone feedback and post-shot exact left-right/add-drop metres; verify no exact magnitude or predicted coordinate leaks before firing and a shot outside the zone remains valid.
- [ ] 8.4 Implement settings screens for all reference gameplay fields, firing/aiming checkbox lists, sound controls, approximate radius, elevation reference, current context, icon, and PIN plus separate quick calibration screens; verify protected settings remain local and reactivation resets them.
- [ ] 8.5 Add local diagnostic/history visibility for pending/resolved shots, sensor snapshots, search results, probability/roll, and delivery state sufficient to troubleshoot retries; verify private bearer values are never displayed or logged.

## 9. End-to-end verification and release

- [ ] 9.1 Exercise staff creation, QR activation, manual activation, reactivation, scope switching, icon selection, revocation, and APK metadata against a test WordPress deployment; verify every provisioning scenario in the specs.
- [ ] 9.2 Run an end-to-end button/torch and acoustic firing test with mode restrictions, precise and approximate guidance, 60-degree calibration, multiple in-flight shots, a moving target, screen-off, temporary network loss, public impact expiry, successful mailbox delivery, FCM wake, and acknowledgement; verify server logs and device state agree without duplicate hits.
- [ ] 9.3 Test on at least one Android 9 device and one current Android device with Google Play Services, including battery restrictions and force-stop recovery limits; document supported setup instructions and observed constraints.
- [ ] 9.4 Generate the release keystore outside Git, create two encrypted backups with separately retained credentials, and sign a release APK; verify Android accepts an upgrade signed by the same key without clearing local configuration.
- [ ] 9.5 Run all WordPress gates, Android unit/instrumented/lint/release-build gates, OpenSpec strict validation, documentation checks, and final isolated code review; fix introduced findings before publishing the APK.
- [ ] 9.6 Publish the signed APK and version metadata on the staff handoff page and perform a clean-device download, install, QR activation, and first shot; verify no private Firebase or signing credential ships in the package or repository.

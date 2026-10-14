# Proposal

## Why

Tac Nav needs a field-device application that turns a mounted Android phone's location, heading, and elevation into a simulated mortar impact while keeping game decisions on participating devices. The existing map API can store marks but has no application identity, geometry search, addressed mailbox, sleeping-device delivery, or installable Android client.

## What Changes

- Add a first-party `tacnav-devices` WordPress plugin, dependent on `tacnav-maps`, for device definitions, one-time QR/manual activation, revocable credentials, allowed map/team scopes, APK distribution metadata, addressed mailboxes, and Firebase Cloud Messaging wake-up signals.
- Add application-authenticated geo-object access and map-scoped spatial search to `tacnav-maps`; search returns the ids and complete geometries of all intersecting unexpired objects regardless of team visibility while leaving derived measurements to the calling application.
- **BREAKING**: replace REST and stored preset input `ttl_minutes` with `ttl_seconds`; continue storing the absolute UTC expiry in the existing `expires_at` column and preserve an existing expiry when an update does not choose a new duration.
- Add indexed spatial data alongside the canonical object geometry so map searches do not scan and decode every geometry row.
- Add the native English-language Android application **Tac Nav Mortar**, package `com.tacnav.mortar`, supporting Android 9 and newer, local PIN-protected gameplay settings, unprotected rapid calibration, button and sound-trigger firing, multiple in-flight shots, coordinate aiming assistance, offline queues, impact markers, per-object hit calculations, and mailbox delivery.
- Configure Firebase project `tac-nav` as the wake-up transport while retaining the WordPress mailbox as the source of truth and reconciliation path.
- Keep server-defined mortar settings as activation defaults; later PIN-authorized edits remain local, and reactivation resets them to the server reference.
- Keep `application_id`, credentials, and allowed map/team scopes server-controlled; let a PIN-authorized user select the current map and team from those allowed scopes.
- Add a top-level Android application area to this repository and keep generated APKs, Gradle output, signing keys, and private Firebase service credentials out of Git.

## Capabilities

### New Capabilities

- `tacnav-application-provisioning`: Staff-managed device definitions, reference settings, scoped one-device activation by QR or manual code, credential revocation, icon catalog access, and APK handoff.
- `tacnav-geo-object-api`: Application-authenticated object writes, second-based lifetimes, indexed map-scoped geometry search, expiry filtering, and raw geometry results for device-side calculations.
- `tacnav-device-messaging`: Object-addressed durable mailboxes, idempotent acknowledgement, retention, FCM wake-up signals, and catch-up after missed delivery.
- `tacnav-mortar-android`: Sensor validation and calibration, range and aiming calculations, button and acoustic firing, concurrent flight timing, impact publication, independent per-object hit rolls, offline recovery, and local settings.

### Modified Capabilities

- None. Existing player-visible duration choices and remaining-expiry behavior stay unchanged while their internal payloads and stored preset durations migrate to seconds.

## Impact

- WordPress: `tacnav-maps` schema, expiry handling, object services, REST API, tests, and player/staff form wiring.
- New first-party component: `wp-content/plugins/tacnav-devices/`, with administration, provisioning, device authentication, mailbox storage, Firebase HTTP v1 integration, and APK download metadata.
- Android: new `apps/tacnav-mortar-android/` Kotlin project using package `com.tacnav.mortar`, minimum API 28, foreground location/microphone work, Room persistence, camera QR scanning, and Firebase Messaging.
- Repository: `.gitignore`, first-party component documentation, QA configuration where applicable, and secure deployment configuration for APK signing and Firebase service credentials.
- External systems: Firebase project `tac-nav`, Google Play Services on supported devices, and HTTPS on the WordPress site.

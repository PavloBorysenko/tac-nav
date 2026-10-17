# Design

## Context

See `proposal.md` for motivation. `tacnav-maps` currently stores canonical geometry as JSON in a `LONGTEXT` column, indexes map and expiry fields, stores absolute UTC expiry in `expires_at`, and accepts duration at REST/form boundaries as whole minutes. Its REST permissions resolve WordPress staff, listed players, and guests; there is no non-user application principal. The canvas obtains current pictures through conditional HTTP polling.

The new system crosses a WordPress map plugin, a dependent device-management plugin, Firebase, and a native Android client. Game outcomes must remain client-owned. The site may validate identity, scope, geometry, expiry, routing, and idempotency, but must not decide whether an object was hit or how a recipient reacts.

## Goals / Non-Goals

**Goals:**

- Keep map geometry and object persistence generic enough for mortar, drone, and later device scenarios.
- Give applications revocable credentials without pretending they are WordPress users.
- Make geometry search map-scoped, visibility-independent, and index-assisted.
- Make mailbox state authoritative while Firebase is only a wake-up transport.
- Let Tac Nav Mortar continue active shots through screen-off, process temporary disconnection, and preserve one random result across retries.
- Keep the Android source in this repository without putting mobile build files inside a runtime WordPress plugin.

**Non-Goals:**

- Server-side hit probability, target filtering, destruction, or other game decisions.
- Guaranteed processing after Android force-stop, device power loss, or permanent network loss.
- iOS, Android devices older than API 28, or devices without Google Play Services.
- Google Play distribution in the first release.
- Building or signing APKs dynamically from WordPress.
- A shared Android framework extracted for hypothetical future apps before a second application needs it.

## Decisions

### 1. Keep a monorepo with separate deployable components

Android source will live at `apps/tacnav-mortar-android/`. Device server functionality will live in a new first-party `wp-content/plugins/tacnav-devices/` plugin that declares `tacnav-maps` as a runtime dependency. `tacnav-maps` remains the owner of catalog icons, geo objects, expiry, and search.

This keeps one history and coordinated specifications without placing Gradle sources in `uploads` or plugin release packages. Generated Gradle output, local SDK paths, APKs, signing stores, and private Firebase credentials remain ignored. Staff upload one built APK to the live WordPress site; that file becomes the single latest download at `/tacnav-mortar/` for every player. The front page exposes that page as **Mortar app**. Players never enter wp-admin. WordPress does not build or sign the package.

### 2. Device authentication is an application principal, not a WordPress user

`tacnav-devices` will issue an opaque random bearer credential at successful activation and store only its hash. It will resolve a request context containing application id, active installation id, application type, allowed maps, allowed teams, and current submitted map/team. `tacnav-maps` will expose an integration boundary that accepts this resolved context in its REST permission and service layers without depending back on `tacnav-devices`.

Activation codes are random, hashed, expiring, and single-use. QR and manual entry carry only the HTTPS server base URL and activation code. A new activation transaction revokes the old installation, bearer hash, and Firebase registration before returning a new credential and the reference configuration.

Alternatives rejected:

- WordPress Application Passwords identify users rather than one game device and do not model one-active-phone activation or map/team scopes.
- Guest HMAC tokens are read-only, map/team specific, and intentionally lack revocation records.
- Sending application identity in an unsigned request would let a client impersonate another device.

### 3. Server reference settings and local settings have distinct ownership

The application definition stores activation reference JSON plus allowed/default map and team relations. Activation returns a snapshot. After that, PIN-authorized gameplay changes stay in encrypted/local Android storage and are not patched back to WordPress. Reactivation discards them by applying a fresh reference snapshot.

Identity, bearer credential, server URL, and allowed scopes are not local gameplay settings. Current map and team are local choices constrained by the activated scopes and revalidated on every server request.

### 4. Migrate duration inputs to seconds but retain absolute UTC expiry

`expires_at DATETIME` remains the only persisted object expiry. API and internal preset duration fields become `ttl_seconds`; `ttl_minutes` is rejected. Player-facing labels may continue to offer minute durations, but their payload values are seconds. Existing quick-preset metadata is migrated once by multiplying old minute values by 60 and replacing the old key. Self-points use 60 seconds.

On create, positive seconds become `server_now + seconds`, zero clears expiry, and omission produces no expiry. On update, omission leaves the stored absolute value untouched, positive seconds replace it relative to acceptance time, and zero clears it. Client `expires_at` is never trusted.

This preserves the already-correct absolute-expiry concurrency behavior while allowing a 30-second impact and avoiding dual units.

### 5. Retain canonical JSON and add an indexable spatial projection

Canonical REST geometry remains the existing normalized JSON because it represents circles with a centre and metric radius and is already consumed by Leaflet. The geo table gains:

- an application-origin identifier;
- optional `position_accuracy_m`;
- an indexable MySQL/MariaDB spatial projection derived from canonical geometry;
- a spatial index suitable for bounding-box candidate selection.

Markers project to points, paths to line strings, polygons to polygons, and circles to a generated bounding/approximation geometry. Search first constrains `map_id` and unexpired rows, then uses spatial bounding intersection to select candidates. A shared geometry service performs exact intersection checks against normalized geometry before returning raw results. This two-stage approach avoids decoding every map row while avoiding the false precision of a polygon-approximated circle.

The migration first detects the installed MySQL/MariaDB spatial syntax and SRID behavior, creates the compatible projection/index, and backfills every valid existing row. Writes update canonical JSON and projection atomically. Activation must fail safely rather than silently running an unindexed full-table search if the required spatial capability is unavailable.

Alternatives rejected:

- Searching every JSON row is simple but scales with all objects on a map for every shot and drone scan.
- Replacing JSON outright with one native geometry loses the current circle model and creates a risky all-at-once compatibility migration.
- A spatial index alone cannot represent exact metric circles consistently across supported database engines, hence exact post-filtering remains necessary.

### 6. Search is a geometry service, not a target service

`POST /tacnav/v1/maps/{map_id}/search` accepts normalized query geometry. It authenticates an application principal, validates current map/team scope, excludes expired rows, ignores team visibility and origin, and never crosses the map id. Each result is deliberately minimal: object id, kind, full normalized geometry, and optional position accuracy.

The response does not derive distance, disclose ownership/application metadata, receive edge probability, roll randomness, allowed target ids, or accept a message instruction. The calling application owns all measurements and game decisions. This keeps search reusable while reducing unnecessary disclosure of hidden-object metadata.

Application object writes reuse `/objects`. They are stamped with application origin and id. An application can mutate only its own ordinary objects. Existing staff and player policies remain separate. Application icons need only exist in the global catalog; team palette membership remains a player-form concern.

### 7. Use event plus delivery rows for the mailbox

The device plugin will use separate event and recipient-delivery storage:

- Event: source application, source team, map, client event/shot id, type, opaque payload, accepted time.
- Delivery: event id, recipient object, bound recipient application/installation, monotonic database id cursor, pending/acknowledged timestamps, expiry.

A uniqueness constraint on source application, client event id, and recipient object makes sender retries idempotent. The server validates that each object is on the source map and creates a delivery only for an active object binding. It does not inspect hit probability or target behavior.

Inbox reads are ordered by delivery cursor and authorized against the active application’s bound objects. Acknowledgement is idempotent. Pending deliveries expire after 24 hours; acknowledged rows become purgeable after one hour. Cleanup runs opportunistically on mailbox traffic and through scheduled maintenance so it does not depend exclusively on low-traffic WP-Cron.

### 8. Firebase wakes; the mailbox delivers

After a delivery commits, `tacnav-devices` attempts an FCM HTTP v1 data/notification message containing only a delivery reference. High-priority messages must create a user-visible notification to avoid Android deprioritization. The phone then fetches authoritative mailbox data and acknowledges only after local processing.

The Android `google-services.json` for Firebase project `tac-nav` belongs in the app module and matches `com.tacnav.mortar`; it contains client identifiers rather than the server private key. The WordPress sender uses a dedicated least-privilege Firebase service account through an external credential-file path/environment setting. The private JSON key is never stored in Git, WordPress media, an APK, or a public plugin option.

App start, connectivity restoration, foreground resume, and a low-frequency active-session reconciliation also read the mailbox. FCM failure therefore affects latency, not durability.

Alternatives considered:

- Three-second polling is acceptable for a prototype but creates continuous WordPress load and still needs Android battery exemptions.
- MQTT is a credible later transport for self-hosting but requires a broker, TLS operations, and another production service. The mailbox contract allows adding it later.
- WebSocket/SSE directly from PHP would hold web workers and is not suitable as the primary WordPress deployment model.

### 9. Native Android architecture uses a durable state machine

Tac Nav Mortar is a Kotlin application with a modern native UI and minimum SDK 28. The implementation will separate:

- activation/API client and credential storage;
- local settings and calibration;
- sensor/location/audio acquisition;
- persistent shot state;
- foreground session service;
- impact resolver and mailbox sender;
- Firebase registration/wake handling.

Room persists every accepted shot and its per-object roll before network side effects. States distinguish at least `IN_FLIGHT`, `DUE`, `SEARCHED`, `IMPACT_CREATED`, `MESSAGES_SENT`, and terminal failure/completion. Every remote write uses stable ids/idempotency keys so process death or response loss resumes rather than rerolls.

The foreground service is started by visible user action and declares the required location and microphone service types. It displays a persistent active-session notification. Android permission denial degrades the corresponding feature: no location blocks firing, no microphone disables acoustic trigger, no camera requires manual activation, and no notification permission prevents a valid active configuration until resolved.

Protected local settings store two non-empty allowed-mode sets: `button`/`acoustic` for firing and `free`/`precise_coordinates`/`approximate_coordinates` for aiming. The active choice is ordinary player state rather than protected configuration. The UI omits a selector for a one-item set and exposes an unprotected selector only when multiple choices are allowed.

An accepted button-triggered shot asks `CameraManager` to enable the torch and schedules shutdown after five seconds; another accepted button shot replaces that deadline. Torch cleanup also runs when the active service stops. Torch errors are surfaced as feedback after the durable shot is accepted and never roll back that shot.

### 10. Shot timing snapshots sensors once and resolves current targets later

At trigger, the client validates and freezes location, location age/accuracy, fused orientation, calibration offsets, and time. Range is linear between configurable endpoint angles/distances. A geodesic destination calculation produces the immutable impact point. A monotonic elapsed clock drives an uninterrupted in-session delay; persisted wall/server-correlated times allow recovery after process restart.

When due, the resolver searches first, then creates the public impact marker, preventing that marker from appearing in its own search. If offline, it waits and searches after connectivity returns, intentionally using target positions current at delayed resolution.

For each returned object, Android computes nearest distance from the impact point and raw geometry in a local metre-scale projection: point distance for markers, centre distance minus radius for circles, nearest segment for polylines, and point-in-polygon plus nearest boundary for polygons. Probability is then computed locally from that distance and edge probability. One cryptographically strong random roll is persisted per shot/object. Successful recipients are sent in one addressed event; failures remain local. Friendly objects and object types are not filtered.

### 11. Calibration favors fast field setup but exposes magnetic uncertainty

Android’s rotation-vector sensors provide orientation. `GeomagneticField` converts magnetic heading to true heading at the current location/time. Known-point calibration stores a yaw offset between measured and calculated true bearing. Elevation calibration presents the protected reference angle, initially 60 degrees, and one unprotected action stores `referenceAngle - measuredElevation` after the player sets the physical plumb indicator. Performing either calibration does not require the settings PIN.

Because a steel tube 8–10 cm away can cause heading-dependent distortion rather than a constant offset, the UI shows sensor accuracy and keeps recalibration directly accessible. Physical mounting adjustment is allowed, but software offsets remain necessary. Field validation at multiple headings is a release gate; no algorithm can promise that one offset removes nonlinear magnetic interference.

### 12. Aiming modes change guidance but never control the impact

Free aiming displays live pose, range, and predicted impact without a coordinate target. Precise-coordinate guidance converts entered coordinates to target distance/bearing and inverse-linear required elevation, then displays numeric turn, tilt, predicted impact, and miss offset.

Approximate-coordinate guidance uses a protected radius initially set to 30 metres. Before firing it projects the live predicted impact and target into a local tangent plane, exposes only the correction signs (`LEFT`, `RIGHT`, `ADD`, `DROP`) and inside/outside-zone state, and withholds coordinates and magnitudes. After an accepted shot it freezes that shot’s predicted impact, resolves the same local cross-range and along-range components, and reveals their metre magnitudes. All aiming modes use the same actual sensor snapshot and none gates firing or substitutes the entered point.

### 13. Signing and release ownership stay outside WordPress

The release keystore is created once for `com.tacnav.mortar`, owned by the project owner, ignored by Git, and backed up in at least two encrypted locations with separately retained credentials. WordPress hosts or links only the signed APK and public version metadata. A new staff upload replaces the previous latest file. Losing the keystore is treated as a non-recoverable update-path failure requiring uninstall/reinstall.

The supplied 512px source icon is converted into legacy, adaptive foreground/background, and monochrome resources; the adaptive foreground is inset so the compass letters and mortar legs survive launcher masks.

### 14. Pin the approved implementation toolchain and dependencies

The approved Android toolchain is Android Studio 2026.2.1 with its embedded JDK 25 runtime (AGP requires JDK 17 or newer), Android SDK Command-Line Tools build 15859902, Android SDK Platform 37.0, Build Tools 36.0.0, Platform Tools 37.0.1, Gradle Wrapper 9.6.0, Android Gradle Plugin 9.4.0, Google Services plugin 4.5.0, Kotlin Compose plugin 2.4.10, and KSP 2.3.10. The application keeps `minSdk` 28 and uses `compileSdk`/`targetSdk` 37. This coordinated update retains Coil 3.6.3, whose Kotlin 2.4.10 metadata is incompatible with the earlier Kotlin 2.2.10 compiler.

Approved direct runtime dependencies are Compose BOM 2026.09.00 (UI, Material 3, and tooling), Core KTX 1.19.1, Activity Compose 1.13.0, Lifecycle 2.11.0, Navigation Compose 2.10.2, Room 2.8.5, WorkManager 2.12.0, DataStore 1.2.1, CameraX 1.6.2, bundled ML Kit barcode scanning 17.3.0, Play Services Location 21.4.0, Firebase BOM 35.0.0 with Messaging, OkHttp 4.12.0, Coil 3.6.3, and Kotlin Coroutines 1.10.2.

Approved direct test dependencies are JUnit 4.13.2, Coroutines Test 1.10.2, AndroidX Test Core and Runner 1.7.0, AndroidX JUnit extension 1.3.0, Espresso 3.7.0, and Compose UI tests from BOM 2026.09.00. Gradle lockfiles and the version catalog are tracked; downloaded SDKs, caches, and outputs are not.

No new PHP package is required. Firebase OAuth JWT signing uses PHP OpenSSL and WordPress HTTP APIs, preserving the plugin's PHP 7.2 floor. The approved libraries use compatible open-source licenses (predominantly Apache-2.0; JUnit uses EPL-1.0), while the Android SDK remains subject to Google's SDK license. Google Play Services is required on supported devices.

## Risks / Trade-offs

- **[Metal causes nonlinear compass error]** → Validate the physical mount at multiple bearings, expose quality warnings, and make known-point recalibration fast; do not claim a constant offset guarantees accuracy.
- **[GPS error exceeds a small damage radius]** → Block shots above configurable accuracy/age limits and return target position accuracy for inspection without secretly changing probability.
- **[FCM is delayed or unavailable]** → Commit mailbox first and reconcile on lifecycle/network events.
- **[Android force-stop defeats background work]** → Document that an active session cannot override explicit force-stop and recover pending shots on the next launch.
- **[Acoustic trigger differs between microphones]** → Use a live relative dBFS meter, configurable threshold/duration/debounce, and field calibration rather than claiming calibrated sound-pressure decibels.
- **[Spatial SQL differs between MySQL and MariaDB versions]** → Add an environment capability check and focused integration tests before schema activation; fail clearly when indexing cannot be installed.
- **[Visibility-independent search reveals hidden objects]** → Restrict it to activated scoped applications, keep guests/players out, and document that this is an intentional application API.
- **[Client-owned rolls can be modified by a hostile APK]** → Accept this as the chosen trust model; preserve source identity and audit facts but do not move game authority to the server.
- **[Local settings are lost on reactivation]** → Make the reset explicit before activation and retain server reference settings as a predictable baseline.
- **[Private Firebase or signing credentials leak]** → Keep them outside web/public/Git paths, apply least privilege, rotate Firebase credentials, and maintain encrypted signing-key backups.

## Migration Plan

1. Confirm database spatial support and add failing compatibility tests before changing production schema.
2. Migrate `tacnav-maps` duration keys and geo projection columns in a versioned upgrade; backfill projections and verify counts before enabling search.
3. Deploy revised object APIs and existing canvas behavior, then run PHP, JavaScript, PHPUnit, and browser gates.
4. Install and activate `tacnav-devices`; configure Firebase credentials outside the public root; create mailbox tables and an initial mortar reference.
5. Build the Android project with the registered Firebase client file, generate and back up the release keystore, and exercise activation against a test site.
6. Validate sensor axes, known-point calibration, GPS blocking, screen-off service, acoustic triggering, concurrent/offline shots, spatial results, idempotent retries, and FCM catch-up on physical Android 9 and current Android devices.
7. Publish the signed APK metadata only after the end-to-end test passes.

Rollback disables application activation/search/messaging routes and the new plugin while leaving additive device/mailbox/spatial data in place. Existing canonical geo JSON and absolute expiry remain usable by the old canvas. Restoring pre-migration code requires either a compatibility shim for migrated preset seconds or restoring the affected preset metadata from backup because `ttl_minutes` is intentionally removed.

## Open Questions

- Exact default acoustic threshold, impulse duration, and debounce will be selected from field measurements with the intended firecracker and phone models.

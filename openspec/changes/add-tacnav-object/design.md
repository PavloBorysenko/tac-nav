# Design

## Context

See `proposal.md` for motivation. The relevant platform already exists in code on this branch but its specs are unarchived in `add-tacnav-mortar`:

- `tacnav-maps` accepts application object create, update, and delete with `ttl_seconds`, `description`, `icon_id`, and `visible_team_ids`, and search ignores visibility.
- `tacnav-devices` provides activation, bearer context, object bindings, the mailbox, and FCM wake-up. Every application-written object is bound automatically through `tacnav_application_object_saved`.
- A Mortar search returns any object it intersects, and Mortar treats any point inside a circle or polygon as distance 0, probability 1.0.
- Every object write runs `Picture_Publisher::publish()`, rebuilding the staff, staff-expired, and per-team lists, and open canvases poll every 3 to 6 seconds.
- `canvas.js` draws an icon halo at the centre of every marker, circle, and polygon.
- `tacnav-devices` is single-application in four places documented in `provisioning-and-messaging.md`, and the Mortar form lets staff type `application_type` as free text.
- Tac Nav Mortar already sends `package_id` on activation, and the server ignores it.

## Goals / Non-Goals

**Goals:**

- Keep every game decision on the phone. The server stores, searches, routes, and never judges.
- Survive process death without double-counting a hit, losing a wreck, or forgetting downtime.
- Leave Tac Nav Mortar's APK, settings, routes, and release untouched.
- Bound the map write load from always-on objects.

**Non-Goals:**

- Sharing Android code with Tac Nav Mortar. A shared module can follow once Mortar's field tests and release are finished.
- Compass calibration in Tac Nav Object. Rectangle orientation uses the phone's compass as it reads.
- Defending against a modified APK, airplane mode, a changed phone clock, or a phone switched off to avoid a wreck. This is the platform's existing client-trust model.
- Server-side detection of a dead or expired phone.
- More than one object per phone.

## Decisions

### 1. A separate Android project

The application lives in `apps/tacnav-object-android/` with its own Gradle wrapper, version catalog, and lockfiles, at the toolchain versions pinned in `add-tacnav-mortar`. Activation, PIN handling, HTTP, mailbox reading, and theming are written for this project using Mortar as a reference, without a Gradle dependency between them.

Refactoring a shared module now was rejected because it would move code under an application with six field and release tasks still open. The cost is duplication, accepted until Mortar ships.

The package is `com.tacnav.beacon` because `object` is a Kotlin hard keyword and would need escaping in every package declaration. The display name is Tac Nav Object. The `application_type` value and tab slug are `object`. The foreground service type is `location` only, with no microphone. Torch uses `CameraManager.setTorchMode()`.

### 2. A pure, persisted state machine

One pure class owns state and takes location fixes, clock ticks, deliveries, and user actions as inputs, returning new state plus effects such as publish, signal, or create wreck. The foreground service and API client execute effects. This makes life cycle, downtime, and hit rules unit-testable with a fake clock.

Persisted state: life-cycle state, hit count, applied event ids, ids of objects from the current life, wreck key, pending wreck position, session start, deployment heading, anchor fix, area centre, downtime remaining, full-reset progress start, last activity time, armed flag, and context. It lives in encrypted local storage written before any network effect.

The foreground service starts from a visible action, because Android does not allow a location service to start from the background. It stays running through Waiting when armed or while downtime counts, so that automatic activation and warnings work with the screen off. After a reboot the user must open the application, and the spec's recovery rules apply.

### 3. Downtime uses a wall-clock record

`SystemClock.elapsedRealtime()` drives live timers. Each tick also persists the last activity time from the wall clock because the monotonic clock resets on reboot. On restore, the gap is deducted from remaining downtime. This closes the loophole of powering off instead of deactivating, at the cost that a changed phone clock can skew it, which is within the trust model.

### 4. Hits are de-duplicated by client event id

`client_event_id` is the shot identity, so it is the dedupe key rather than delivery id. The hit is applied and the id persisted atomically before acknowledgement. A crash after that but before acknowledgement redelivers the event, and the persisted id drops it. Mortar's in-memory `seen` set is not sufficient here because a lost count is game state. Ids are kept until reset, so the set is bounded by shots received in one life.

Deliveries whose `recipient_object_id` is not in the current life's object ids are acknowledged and dropped. Every object created since the last reset belongs to that list so a shot landing just before deactivation still counts.

### 5. The server gets one display flag and one idempotency key

`tacnav-maps` DB version 5 adds `searchable TINYINT(1) NOT NULL DEFAULT 1` and `client_key VARCHAR(64) NULL` with a unique key on `(application_id, client_key)`, where NULL values do not collide. `Geo_Store::search()` adds `AND searchable = 1`. `Application_Write::normalize()` accepts `searchable` and `idempotency_key`. Staff and player payloads never touch these columns, so edits preserve them, and `Geo_Store::update()` must therefore not overwrite them from a payload that omits them.

The alternative of letting Mortar find the area and having the Object phone ignore the delivery was rejected. Mortar diagnostics would show a false hit on every shot into the area, and each one would push-wake the phone.

A new `idempotency_key` is used instead of honouring `client_object_id` because Mortar sends constant values such as `mortar-position` in that field, and deduplicating them would return stale objects.

The wreck is a new object created with a key generated at activation or reset, per the product decision. The live object and area are deleted only after the wreck is accepted, so the map never shows an empty gap. If the key already exists, `create_object` returns the stored row with status 200 and does not republish the picture.

### 6. The canvas omits the pin only for an iconless area

The rule is `searchable === false && icon_id === 0 && kind in (circle, polygon)`. The icon cannot key on `searchable` alone because wrecks are not searchable and must show their icon. In this case the title tooltip binds to the shape. A point wreck without an icon keeps its default dot, so a wreck never disappears.

### 7. Writes are bounded

Tick policy: write when the position moved more than the fix accuracy, at most every 5 seconds, or when 30 seconds have passed since the last write. `ttl_seconds` is 60 on every write, making the lifetime a lease. Time on the map is at minute precision so a still object produces one write every 30 seconds. An update returning 404 falls back to create. Cadence is a constant, not a setting.

Alternatives: a fixed write every 5 seconds was rejected because a still object would rebuild every audience's picture constantly. A rate-limit on the server was rejected as unnecessary for the expected device count.

### 8. The hint area favours the edge

The centre is the object position moved along a uniform random bearing by `r = R * u^(1/3)` using a secure random source. This gives areal density rising with distance from the centre. Uniform area sampling, `sqrt(u)`, would give constant density, which does not satisfy the requirement. The centre is rolled only on entering Active and on leaving the area, because re-rolling on every update would let observers average centres back to the true position.

The area is a separate object written on the same cadence so its session time matches. It is created after the real object is restricted, so opponents never briefly see the real object.

### 9. Rectangles are polygons in a local metre projection

Four corners are computed from the centre, heading, and half-sides, then sent as a polygon. Heading is the phone-top true bearing from the rotation vector and geomagnetic declination, read once when Deploying starts and persisted. A live heading was rejected because compass jitter would turn into constant writes. The existing polygon spatial projection and canvas centre icon work without change.

### 10. Auto activation and deactivation share one movement model

Usable fixes are those no older than 15 seconds and within the accuracy limit. The still radius is `max(auto_deactivate_m, 2 * accuracy_limit)`. Automatic deactivation requires three consecutive usable fixes with `distance - fix_accuracy - anchor_accuracy > auto_deactivate_m`. The anchor is the best fix seen during Deploying, or the activation fix when deployment is instant.

### 11. Device plugin becomes type-aware without migration

- **Types:** `mortar` and `object` are the known types. A new `App_Types` definition holds each type's package id, release option, file name, public slug, and reference validator.
- **Lists:** `Application_Store::all()` takes a type. The Object tab shows `object`, and the Mortar tab shows everything else, so legacy free-text records stay reachable.
- **Validation:** Saving dispatches on type. Object rules live in `Validation::object_reference_settings()`. The Mortar form's type input becomes a fixed value, and an existing record's type is preserved.
- **Activation:** `activate()` compares the request's `package_id` with the application's type after finding the code and before consuming it. A missing `package_id` is treated as `com.tacnav.mortar`.
- **Release:** `Release` methods take a type. Mortar keeps option `tacnav_devices_release`, its file, and routes. Object uses `tacnav_devices_release_object`, `tacnav-object.apk`, and `/tacnav-object/`. The device `/release` route uses the authenticated type. The rewrite rules change, so the plugin version bump flushes them.
- **Admin:** Handlers read the posted `app` slug and include it in redirects. A missing value keeps the current redirect, which lands on Mortar.
- **PIN:** `settings_pin` is part of Object reference settings. It is a shared game PIN rather than an account secret, and it is stored plainly in the staff-only reference and returned only over activation.

### 12. Settings and signals

Staff and local settings use the same field names. Locally saved settings apply at the next activation, so a session runs on a frozen snapshot taken when Deploying starts.

Signals use `AudioAttributes.USAGE_ALARM`, because `USAGE_GAME` follows media volume. The project includes three generated sounds: hit, warning, and a roughly 5-second destruction siren. Files can be replaced later without changing code.

## Default behavior

These defaults were chosen during exploration without the owner objecting. Each can change without altering the approach.

| Situation | Behavior |
| --- | --- |
| Shell in flight when the object deactivates | No hit, because Mortar searches at impact |
| Friendly fire | Counts, because teams are not filtered |
| No internet while Active | Stays Active, leaves the map after the lease, hits arrive later, downtime paused |
| Many destructions | Wrecks accumulate, and staff delete old ones |
| Moving during a button deployment | Object follows the phone; movement cancels only an automatic deployment |
| QR on a new phone while the old one is Active | Old credential is revoked, its object expires by lease, old wrecks stay |
| Bad GPS blocks activation while downtime runs | Downtime keeps running |

## Risks / Trade-offs

- **[OEM battery management kills the foreground service]** → Document the setup steps, recover on next open, and field-test named devices.
- **[Default PIN `4321` left unchanged lets anyone reset a destroyed object]** → Organizer can set a per-application PIN on the Object tab, and the staff page states the risk beside the field.
- **[Uncalibrated compass misaligns a rectangle]** → Warn when unreliable and accept it. Points and circles are unaffected.
- **[A loud hit signal reveals a hidden phone]** → Intended gameplay.
- **[A phone that dies leaves no wreck]** → Accepted. The object leaves the map within about 60 seconds.
- **[Search results expose the true object to a modified Mortar APK]** → Existing accepted trust model.
- **[Mortar diagnostics confirm a nearby real object]** → Existing behavior within the damage radius, left unchanged.
- **[Applied event ids grow during one life]** → Bounded by shots received, and cleared by reset.
- **[Unique key migration on a live table]** → The new column is entirely NULL, so the index builds without conflict.

## Migration Plan

1. Deploy `tacnav-maps` DB version 5 and confirm that all rows default to searchable and Mortar search results are unchanged.
2. Deploy `tacnav-devices` and flush rewrites, then confirm that Mortar activation, tab, routes, and release are unchanged.
3. Register `com.tacnav.beacon` in Firebase project `tac-nav` and add its `google-services.json` to the project.
4. Build, sign, and publish the APK on the Object tab, then activate a test phone.

Rollback disables Object applications and restores earlier plugin code. The added columns are harmless to older code, which ignores them, except that not-searchable objects would be searchable again, so delete wrecks and areas before rolling back.

## Open Questions

- Which phone models the setup instructions must name, decided from field results.
- Final sound files, replaceable without code changes.

# Design

## Context

See `proposal.md` for motivation and the specs for behavior. This change builds on `open-geo-hub`:

- search takes `role` and returns whole objects with `role`, `owner_team_id`, `visible_team_ids`, and `updated_at`;
- an application may update any object on its map;
- an application update changes only the fields it sends;
- `ttl_seconds: 1` expires an object now.

The current projects relevant to this change:

- **`tacnav-devices`** is type-aware. `App_Types::all()` defines Mortar and Object. `canonical()` maps every value other than `object` to Mortar. `reference_settings()` dispatches validation by type, and `Admin::page_query()` appends `&app=` only for `object`. `Release` loops over `App_Types::all()` for rewrites and downloads. `App_Pages::all()` lists the tab pages.
- **The theme front page** (`templates/front-page.html`) has Mortar app and Object app buttons.
- **Tac Nav Object** (`apps/tacnav-object-android/`) already implements activation by QR and by manual entry, a PIN policy with delays, encrypted local state, the icon catalog picker, scoped map and team choice, OkHttp API access with bearer context, rectangle geometry from a heading, and alarm-channel signals. It shares no code with Tac Nav Mortar, by design.
- **Tac Nav Mortar** computes heading from the rotation vector, using the more horizontal of the top edge and the through-screen axis, and corrects it to true north with `GeomagneticField`. Object reads the phone top only.

## Goals / Non-Goals

**Goals:**

- Make every Sapper rule a pure, unit-testable decision over location, heading, clock, and server facts.
- Never lose a completed hold, and never duplicate an object, across release, restart, and lost network.
- Keep server load to an indexed read every 5 seconds per detecting phone.

**Non-Goals:**

- A shared Android module with Object or Mortar. The project copies what it needs, as Object did.
- Background or pocket operation, push messages, or a foreground service.
- Compass calibration.
- Server-side checks of a Sapper's position or decisions. The client-trust model applies.
- Explosions or penalties for entering a minefield.

## Decisions

### 1. Project layout copied from Tac Nav Object

`apps/tacnav-sapper-android/` has its own Gradle wrapper, version catalog, and lockfiles at the pinned toolchain.

- **Package and dependencies.** The package and namespace are `com.tacnav.sapper`. Dependencies are those of Object minus Firebase: Compose, CameraX with ML Kit barcode, Play Services Location, OkHttp, Coil, and coroutines. There is no `google-services.json`.
- **Copied from Object, then adapted:**
  - the `activation/` sources;
  - `security/` (the PIN policy and the encrypted state store);
  - the theme;
  - the settings screens, including the icon picker and the map and team pickers.

  Activation sends `package_id = com.tacnav.sapper`.
- **Icon.** The supplied 512 px icon already has padding. It becomes the adaptive-icon foreground on a solid background in its olive colour, plus legacy mipmaps. The source PNG is kept under `apps/tacnav-sapper-android/art/`.

### 2. Pure machines, thin Android shell

The `game/` package holds pure classes that take inputs and return a new state plus effects:

| Class | Responsibility |
| --- | --- |
| `SearchPolicy` | When to search: 5 s after moving more than the fix accuracy, otherwise 30 s. |
| `DetectorMachine` | Detector state, signals, and the Mark minefield and No mines availability. |
| `MarkHold` | A hold that resets on release. |
| `LayingMachine` | Start fix and heading, accumulated time, the moved-away lock, reset, and completion. |
| `ClearingMachine` | Target choice, per-field accumulated time, completion, and discarding vanished fields. |
| `Limits` | Counters for minefields and No mines markers, and the "0 = unlimited" rule. |
| `Outbox` | Pending writes, each with its own retry rule. |

A `SapperController` drives these from a 1 s ticker, location updates, heading updates, button events, and network results. It persists state to the encrypted store before performing any network effect.

### 3. Geometry in a local metre plane

Distances use an equirectangular projection around the current fix, which is accurate at the tens of metres involved:

- **Circle:** distance to the centre minus the radius, floored at zero.
- **Polygon:** zero when the point is inside by ray casting; otherwise the minimum distance to a segment.

The nearest point found this way gives the bearing for the sector test. The minefield rectangle uses Object's `Shapes` logic. Length lies along the heading, width across it, and the rectangle is centred on the start fix.

### 4. Heading as in Mortar

Mortar's rotation-vector heading routine and its declination correction are copied. That routine uses the more horizontal of the top edge (Y) and the back (−Z). There is no calibration. The detector shows a warning while the sensor reports unreliable accuracy.

### 5. Detector states with hysteresis

The states are ordered `NONE < FAR < NEAR < INSIDE`.

- **Signals.** A transition to a higher state plays that state's signal: FAR plays one beep, NEAR and INSIDE play two beeps. A transition down is silent. A state is re-armed once the detector has been below it.
- **Leaving a state** needs a margin, so jitter does not re-arm it:
  - the sector edge plus 10°;
  - the radius or half radius plus max(1 m, 5 % of the radius);
  - outside a field's edge by the fix accuracy, clamped to 1–5 m, before the phone leaves that field. Entering a field still needs the fix inside it. Detect and the Clear mines skull share this per-field check (`Inside` in `Detector.kt`).
- **Mark minefield** shows in *inside* when the containing field has an owner other than the phone's team, or no owner, and its visibility list is non-empty and does not contain the phone's team.
- **No mines** shows outside every kept field.

### 6. Signals generated at runtime

Beeps are synthesised with `AudioTrack` as a short 1 kHz tone. Two beeps are two tones with a short gap. Playback uses `AudioAttributes.USAGE_ALARM`, so media volume does not mute it. Each signal also flashes the detector screen. Generating the tone avoids binary sound assets for a tone this simple, and the generator can be unit-tested.

### 7. Foreground-only sensors

Location requests (fused, high accuracy, 1 s) and the rotation-vector listener run only while the activity is resumed. Fixes pass through an accuracy-weighted Kalman filter (`FixSmoother`) that lets the position move at 2 m/s: it roughly halves the wander of a standing phone and lags a walking player by about 2 m. The raw accuracy is kept on each fix, so the accuracy gate and the clearing tolerance are not loosened. The fused request is already at the highest priority and rate the API offers; real accuracy is bounded by the phone's GNSS hardware and sky view. The window sets `FLAG_KEEP_SCREEN_ON`. There is no foreground service and no background-location permission. Holds pause on `onPause` and persist their accumulated time.

### 8. Writes and idempotency

| Action | Request | Retry and deduplication |
| --- | --- | --- |
| Lay | `POST /objects`: polygon, `role: minefield`, own-team visibility, `ttl_seconds: 0`, minefield icon, title `Minefield`, `idempotency_key` = laying id generated on first press | The server's per-application key deduplicates retries. The counter rises on acceptance. |
| No mines | `POST /objects`: marker, ordinary, own-team visibility, `ttl_seconds: 0`, title `No mines`, `idempotency_key` = tap id | Same as Lay. |
| Mark | `PATCH /objects/{id}`: `visible_team_ids` = stored list plus own team, `updated_at` | On 409, re-search the field and recompute. Done when the list already contains the team or is empty. On 404, done. |
| Clear | `PATCH /objects/{id}`: `ttl_seconds: 1` | Idempotent. On 404, done. |

The `Outbox` persists each entry before sending. It retries every 5 seconds while online and when the network returns.

### 9. Search requests

Detect searches `POST /maps/{map}/search` with `{kind: circle, geometry: {lat, lng, radius: 2R}, role: "minefield"}`. Clear mines uses the radius max(fix accuracy, 1 m). Results replace the kept list. An empty or failed search keeps the previous list until the next success, and a failure marks the screen offline.

### 10. Settings and limits

The local settings use the server reference keys:

- `detect_radius_m`, `detect_sector_deg`;
- `mark_hold_seconds`, `clear_hold_seconds`, `lay_hold_seconds`;
- `minefield_length_m`, `minefield_width_m`;
- `minefield_icon_id`, `no_mines_icon_id`;
- `max_minefields`, `max_no_mines_markers`;
- `max_location_accuracy_m`;
- `settings_pin`.

Activation replaces them and resets the counters and progress. Before activation the PIN is `4321`.

Reset connection clears the encrypted store and returns to the first screen. Another activation is needed to revoke the server credential.

### 11. Server type `sapper`

| Area | Change |
| --- | --- |
| `App_Types` | A `SAPPER` entry: label `Tac Nav Sapper`, package `com.tacnav.sapper`, option `tacnav_devices_release_sapper`, file `tacnav-sapper.apk`, slug `tacnav-sapper`, query prefix `tacnav_sapper`. |
| `canonical()` | Maps `object` and `sapper` to themselves and anything else to Mortar, so the Mortar tab stops listing Sapper records. |
| Validation | `Validation::sapper_reference_settings()` implements the provisioning spec, and `reference_settings()` dispatches to it. |
| `Admin::page_query()` | Appends `&app=` for every type other than Mortar. |
| Sapper page | `Apps/Sapper_Page` and `templates/apps/sapper.php`, modelled on Object, with catalog-title icon selects. |
| `App_Pages::all()` | Lists Sapper after Object. |
| Plugin version | Bumped so the new rewrite rules are flushed. |
| Theme | `front-page.html` gains a Sapper app button after Object app. |

## Risks / Trade-offs

- **[Compass error from nearby metal or a weak calibration]** → The 90° sector absorbs moderate error. A warning shows while the sensor is unreliable. Recalibration is left to Android's figure-eight prompt.
- **[GPS jitter near a field edge]** → Smoothing damps wander, and the accuracy-sized exit margin keeps the phone inside a field while fixes wander past its edge. Mark and clear time is kept per field, so a dropped hold loses no progress. Writes are gated by accuracy, and clearing uses accuracy as its tolerance.
- **[A three-minute hold is tiring]** → Progress persists on release and restart.
- **[Battery drain from screen-on GPS]** → Accepted. The application works only while in use.
- **[Mortar hits on Sapper-created fields create deliveries no one reads]** → They expire after 24 hours. Sapper never registers a push token, so no push is sent.
- **[A modified APK can lay or clear anything]** → Accepted under the platform's client-trust model.

## Migration Plan

1. Deploy `open-geo-hub` first.
2. Deploy `tacnav-devices` with the plugin version bump so rewrites flush. Confirm that the Mortar and Object tabs, activation, and releases are unchanged and that `/tacnav-sapper/` answers.
3. Deploy the theme with the front-page control.
4. Build and sign the APK with the shared keystore, publish it on the Sapper tab, create a Sapper application, and activate a phone.

Rollback: disable Sapper applications and restore the earlier plugin and theme code. Stored Sapper records are ignored by older code, which reads them as Mortar records. Delete them before rolling back.

## Open Questions

- How the connected test phone reaches the local site for end-to-end checks: a LAN address, a Local Live Link, or a staging site. This affects only the test setup.

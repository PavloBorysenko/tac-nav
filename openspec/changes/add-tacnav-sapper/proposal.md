# Proposal

## Why

Organizers want minefields as a field mechanic: players lay minefields, sweep for them, reveal them to their team, and clear them, with every decision made on the phone. Staff can already draw a minefield on the map once `open-geo-hub` adds roles, but no device yet detects, lays, reveals, or clears one.

## What Changes

- Add the native English-language Android application **Tac Nav Sapper**, package `com.tacnav.sapper`, in its own project at `apps/tacnav-sapper-android/`. It supports Android 9 and newer, uses the supplied launcher icon, and has no Firebase dependency because it receives no messages.
- Activation works like the other Tac Nav applications: QR, or a manual server URL and code in PIN-protected settings (PIN `4321` until a reference arrives). Before activation the home screen shows Scan QR and Settings. After activation it shows Detect, Lay mines, Clear mines, and a smaller Settings control. Settings include map and team choice, every gameplay value, and Reset connection, which returns the phone to the first screen.
- **Detect**: a minesweeper screen that searches for minefields every 5 seconds while the phone moves and every 30 seconds while it is still. Between searches it checks locally about once a second.
  - A field within the radius (10 m) and inside the heading sector (90°) gives one short signal.
  - A field within half the radius gives two short signals.
  - Standing inside a field gives one signal on entry.
  - Each signal sounds once when the state is entered. It repeats only after the sector leaves the field and returns, or after the phone leaves the field and comes back.
  - Inside another team's field that the phone's team cannot see, a blinking red Mark minefield button appears. Holding it for 30 seconds adds the team to the field's visibility. There is no limit on marks.
  - Outside every field, a No mines marker can be placed. At most 10 can be placed per activation.
- **Lay mines**: hold for 3 minutes in total. Progress is kept when the button is released. The first press records position and heading. Moving away disables the button until Reset laying. On completion a 10 × 10 m rectangle with role `minefield`, centred on the phone and aligned to its heading, is created and visible to the phone's team. At most 3 can be created per activation.
- **Clear mines**: available only while the phone is within GPS tolerance of a minefield. Hold for 3 minutes in total; progress is kept per field. The nearest field is cleared by expiring it now. The phone then shows "This area is safe" and offers a No mines marker.
- Every duration, size, distance, angle, icon, and limit is a PIN-protected setting whose defaults come from the staff reference. Reactivation restores them, and scanning a QR resets both per-activation limits.
- Add a `sapper` application type to `tacnav-devices`:
  - a Sapper tab with validated reference settings;
  - type-bound activation;
  - its own release at `/tacnav-sapper/` and `/tacnav-sapper.apk`;
  - a Sapper app control on the site front page.

  Mortar and Object behavior and addresses stay unchanged.

## Capabilities

### New Capabilities

- `tacnav-sapper-android`: the Tac Nav Sapper application, covering activation, protected settings, home screens, detection signals, marking, No mines markers, laying, clearing, limits, offline behavior, and screen behavior.
- `tacnav-sapper-provisioning`: staff-defined Sapper applications, reference settings and their validation, type-bound activation, the Sapper release, and the front-page control.

### Modified Capabilities

- `tacnav-device-app-pages`: adds a Sapper tab, and the Mortar tab stops listing Sapper applications. The latter modifies the Object tab requirement introduced by `add-tacnav-object`.

## Impact

- **Dependencies:** requires `open-geo-hub` for roles, role-filtered whole-object search, any-object application writes, and partial updates. This change is archived after `open-geo-hub`, which itself follows `add-tacnav-mortar` and `add-tacnav-object`.
- **Android:** a new `apps/tacnav-sapper-android/` Kotlin project, using the toolchain pinned by the earlier Tac Nav projects. It has no Firebase or `google-services.json`, needs Google Play Services for location, and is signed with the shared release keystore outside Git.
- **WordPress `tacnav-devices`:** `App_Types`, `Validation`, `Admin`, a new `Sapper_Page` and template, release rewrites, tests, and documentation.
- **Theme `tacnav`:** one front-page control.
- **Repository:** no `.gitignore` change; the existing `/apps/**` rules already exclude build output, APKs, and keystores.
- **Server load:** about one indexed, read-only search per detecting phone every 5 seconds. Writes happen only when laying, marking, clearing, or placing a marker.

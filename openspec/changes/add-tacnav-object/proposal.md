# Proposal

## Why

Tac Nav can simulate incoming fire but has nothing worth attacking except map marks. Organizers need a physical field objective, such as an antenna, jammer, or air-defense post, that players deploy and guard while opponents locate and destroy it. The mortar and device platform now provide the identity, map, search, and hit-delivery contracts this objective needs.

## What Changes

- Add the native English-language Android application **Tac Nav Object**, package `com.tacnav.beacon`, in its own project at `apps/tacnav-object-android/`. It shares no code with Tac Nav Mortar at build time and runs on its own phone.
- The application activates by QR or one-time code, keeps PIN-protected local settings, and publishes one map object as a point, circle, or rectangle oriented to the top of the phone, refreshed while it may move.
- It runs a phone-owned life cycle: waiting, deploying with a progress bar, active with a session timer, and destroyed. Activation can be manual or automatic after a still period, and is refused above a GPS accuracy limit.
- It deactivates by button or automatically after moving beyond a configured distance.
- It counts `hit` events on the phone. At a configured hit limit, or when a configured downtime allowance runs out, the phone sounds an alarm, lights the torch, and publishes a permanent public wreck under a new id.
- It can publish a public random hint area around the object, with a stable centre biased away from the true position.
- Keep the screen awake while the application is open.
- Add to `tacnav-maps` objects that map search never returns, and idempotent application creates, so a hint area or a wreck never registers as a target and a retried wreck never duplicates.
- Make `tacnav-devices` serve more than one application type: per-type reference settings, application lists, activation, and releases, with an Object tab, a public `/tacnav-object/` download page, and a front-page control. Mortar settings, activation, addresses, and its published release stay valid without migration or an APK rebuild.

## Capabilities

### New Capabilities

- `tacnav-object-android`: The Tac Nav Object application: activation, protected settings, shape, life cycle, GPS gates, deactivation, hit counting, destruction, downtime allowance, hint area, map publication, signals, and screen behavior.
- `tacnav-object-map-presence`: Server behavior for application objects excluded from map search, idempotent application creates, and map display of area objects.
- `tacnav-object-provisioning`: Staff-defined Object applications, their reference settings and settings PIN, type-bound activation, and a separate public release per application.

### Modified Capabilities

- `tacnav-device-app-pages`: Adds an Object tab beside Mortar. Existing requirements stay unchanged.

## Impact

- Android: new `apps/tacnav-object-android/` Kotlin project with foreground location work, encrypted local state, camera QR scanning, and Firebase Messaging. The package must be registered by the project owner in Firebase project `tac-nav`, and it is signed with the same release keystore as Tac Nav Mortar, kept outside Git.
- WordPress `tacnav-maps`: geo table schema upgrade, object writes, search, canvas rendering, tests, and documentation.
- WordPress `tacnav-devices`: validation, application store, activation, release handling, admin handlers, a new Object page and template, the public download page, tests, and documentation.
- Theme `tacnav`: one front-page control.
- Dependency: builds on the `add-tacnav-mortar` change, whose `tacnav-geo-object-api`, `tacnav-application-provisioning`, and `tacnav-device-messaging` specs are not archived yet. This change therefore adds its server behavior as new capabilities rather than editing those specs.
- Load: each object write rebuilds every map audience's picture, so publication cadence is bounded in design.

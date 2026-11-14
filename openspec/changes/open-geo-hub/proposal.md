# Proposal

## Why

Game logic belongs on the devices, and the site is only a hub. Today the hub still makes game decisions an application should own: an application may change only objects it created, an application update overwrites fields it did not send, search hides "not searchable" objects and returns too little for a device to decide, and nothing tells a device what a map object is for. The planned Tac Nav Sapper must find minefields placed by staff or by other phones, reveal them to its team, and clear them, which is impossible under these rules.

## What Changes

- Add an object **role**: a closed, extensible list of purposes. It starts with ordinary (empty), `minefield`, and `hint`. Staff choose a role in the studio inspector, where `minefield` is offered only for circles and polygons and `hint` is never offered. Players cannot set a role, and a save that does not send one keeps the stored role.
- Let application search filter by one role and return each matching object whole, except the private idempotency key. Without a role filter, search returns every role, so Tac Nav Mortar keeps finding everything in its blast radius.
- **BREAKING**: let an activated application update or delete any object on its allowed map, not only its own. Player self-points stay unchangeable.
- **BREAKING**: an application update changes only the fields it sends. Owner team, origin, and the creating application stay as stored. Mortar and Object always send full objects, so their behavior is unchanged.
- Keep each object's mailbox routed to the application that created it, so another application's edit never redirects hits.
- **BREAKING**: remove the `searchable` column in stages. First add roles, then update Tac Nav Object to publish its hint area with role `hint`, then convert legacy not-searchable areas to `hint` and drop the column. Mortar may then address hint areas and wrecks. Object already counts hits only on its live object, so game results do not change.
- Make the wake-up notification silent in Tac Nav Object and Tac Nav Mortar. Pushes for ignored hits, such as hits on a hint area, no longer make a hidden phone audible. A counted hit still sounds through the application's own alarm signal.
- An application expires an object now by updating it with `ttl_seconds` 1. No new field is needed.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `tacnav-geo-objects`: objects gain a role from a closed registry, preserved by saves that do not send it.
- `tacnav-map-canvas`: the staff inspector offers a Purpose choice.
- `tacnav-geo-object-api` (introduced by `add-tacnav-mortar`, not yet archived): role on create, any-object application writes, partial application updates, whole-object search results, and a role filter.
- `tacnav-device-messaging` (introduced by `add-tacnav-mortar`): another application's write does not move an object's mailbox.
- `tacnav-object-map-presence` (introduced by `add-tacnav-object`): the not-searchable flag is removed, and hint areas are drawn without a centre icon by role.
- `tacnav-object-android` (introduced by `add-tacnav-object`): the hint area uses role `hint`, wrecks are ordinary objects, and the wake-up notification is silent.
- `tacnav-mortar-android` (introduced by `add-tacnav-mortar`): the wake-up notification is silent.

This change must be archived after `add-tacnav-mortar` and `add-tacnav-object`, because five of its deltas modify capabilities those changes introduce.

## Impact

- `tacnav-maps`: DB versions 6 (role) and 7 (drop `searchable`), `Geo_Store`, `Application_Write`, `Access`, `REST` search, update, and binding hook, a new role registry, `canvas.js` inspector and hint drawing, tests, and documentation.
- `tacnav-devices`: no code change. Its binding hook stays the same, but TacNav Maps fires it only for the creating application. Documentation is updated.
- Android: Tac Nav Object 0.2.0 (role `hint`, no `searchable`, silent wake-up channel) and Tac Nav Mortar 0.3.0 (silent wake-up channel).
- Deployment order: role server first, then the Object update on phones, then the column drop. If no older Object build is in use, all three can ship together.
- Mortar diagnostics will list hint areas, wrecks, and minefields among found objects. This is accepted under the hub model.

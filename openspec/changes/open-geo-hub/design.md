# Design

## Context

See `proposal.md` for motivation. The current code relevant to this change:

- `Application_Write::normalize()` builds a complete object from every request and fills missing fields with empty values. It also restamps the owner team from the application context on every update. `Geo_Store::update()` already merges the payload over the stored row, so the overwrite comes only from that normalization.
- `Access::default_editable()` allows an application to edit only `origin = application` rows that carry its own `application_id`. A self-point is checked first, and nobody may edit it.
- `REST::update_object()` fires `tacnav_application_object_saved` after every application update. `Mailbox::bind_object()` then replaces the binding row, which is keyed by object id. Under the current rules this is harmless, because only the creator can update.
- `Geo_Store::search()` selects only `id`, `kind`, `geometry`, and `position_accuracy_m`, and adds `AND searchable = 1`.
- `canvas.js` `isHintArea()` hides the centre icon when `searchable === false`, there is no icon, and the kind is circle or polygon. `objectStamp()` includes `searchable`.
- The update route is registered as `WP_REST_Server::EDITABLE`, so POST, PUT, and PATCH already reach one handler.
- Tac Nav Object counts a hit only for ids in `lifeObjectIds`. Only the live object is added to that list, and hits are ignored while Destroyed. Its `MapPlanner` sends `searchable: false` for the hint area and the wreck.
- Both Android applications post every wake-up push to an `IMPORTANCE_HIGH` channel with the default sound.
- The table schema is managed with `dbDelta`. `dbDelta` never drops a column, so the column drop needs explicit SQL. `Spatial_Schema` already has a `column_exists()` helper.

## Goals / Non-Goals

**Goals:**

- One write rule for applications: any object on the allowed map, and only the fields sent.
- The role is the only semantic marker on an object. The server validates it and filters by it, but never acts on it.
- Mortar and Object keep working at every deployment step, including with older APKs during the transition.

**Non-Goals:**

- The Tac Nav Sapper application and its device type. Those belong to `add-tacnav-sapper`.
- Server-side game behavior per role. For example, the server never refuses a hit on a minefield.
- Changing staff or player write payloads beyond the role field. A staff save currently restamps origin to `staff` unless the body sends `origin` (`REST::payload_from_request()`). That is pre-existing behavior and is left for a separate fix.
- Showing the role on object cards.

## Decisions

### 1. A closed role registry in code

A new `Object_Roles` class returns the registry through filter `tacnav_geo_object_roles`. Each entry has a slug, an English label, the allowed kinds, and whether staff may assign it:

| Slug | Label | Kinds | Staff may assign |
| --- | --- | --- | --- |
| `''` | Object | all | yes |
| `minefield` | Minefield | circle, polygon | yes |
| `hint` | Hint area | circle, polygon | no |

The column is `role VARCHAR(32) NOT NULL DEFAULT ''` and has no index, because the role filter only narrows rows the spatial index has already selected.

Validation runs on the merged row in `Geo_Store`. This covers creates, application updates, and staff updates in one place. If the role is unregistered or not allowed for the kind, the save fails with `400 tacnav_geo_role`.

- **Player writes:** `Player_Write` drops the role, so a create is ordinary and an update keeps the stored role.
- **Staff writes:** they may store any registered role allowed for the kind. The studio offers only assignable roles plus the current one.

Alternatives considered:

- Free-form slugs from applications fit the hub model, but a typo such as `minefeild` would silently create a field that no Sapper can find. The staff select also needs labels anyway.
- A role table in the database is not needed for a list that changes with releases.

### 2. Application updates send only what they change

On update, `Application_Write::normalize()` keeps only the keys present in the body: `kind`, `geometry`, `title`, `description`, `icon_id`, `visible_team_ids`, `position_accuracy_m`, `ttl_seconds`, and `role`. It never emits `owner_team_id`, `origin`, `application_id`, or `created_by_user_id` on update. `Geo_Store::update()` then merges the payload over the stored row as it already does. On create, normalization is unchanged apart from accepting `role`.

No new HTTP method is introduced. The existing route handles every method, and Mortar and Object always send all fields, so one rule serves every client. Making only PATCH partial while PUT stayed a full write was rejected: it would add a second contract that no current client needs.

Concurrency uses the existing `updated_at` body field. A Sapper sends the stamp it read in search results, and a mismatch returns `409 tacnav_geo_conflict`.

### 3. Any object, except self-points

`Access::default_editable()` returns true for an application viewer. The self-point check stays first, so self-points remain locked for everyone, and `tacnav_geo_object_editable` can still deny a write. The map scope is unchanged: `Application_Principal` validates the selected map, and the handler refuses an object from another map.

### 4. Bind only the creator's writes

`REST::update_object()` fires `tacnav_application_object_saved` only when the stored `application_id` equals the writer's `application_id`. A create always fires it, because the writer is the creator. `tacnav-devices` needs no change. It still rebinds on each creator write, which keeps reactivation working, because a new installation of the same application updates its own objects.

Alternatives considered:

- Moving the check into `Mailbox::bind_object()`. This would make `tacnav-devices` read geo rows.
- Binding only on create. This breaks reactivation, because the old installation would keep the binding.

### 5. Search filters by role and returns hydrated rows

The search body gains an optional `role`:

- absent or `null` means every role;
- a string means an exact match, so `""` selects ordinary objects;
- an unregistered value fails with `400 tacnav_geo_role`.

`Geo_Store::search()` selects `*` and returns `hydrate()` for each exact intersection. `hydrate()` never includes `client_key`. The response stays a JSON array of objects. Mortar reads only its four fields, so it is unaffected.

### 6. "Expire now" is `ttl_seconds: 1`

`0` already means permanent, and a client clock must never set expiry. One second after acceptance, search and default lists drop the object, and canvases hide it by time. Staff still see it in the expired list and can restore it by editing its TTL. Adding a dedicated expire flag was rejected as a second way to say the same thing.

### 7. Remove `searchable` in three steps

| Step | Server | Applications |
| --- | --- | --- |
| A, DB v6 | Add `role`, keep honouring `searchable` in search. Canvas hint rule: `role === 'hint'`, or the legacy rule (not searchable, no icon, area kind). | Unchanged |
| B | Unchanged | Object 0.2.0 sends role `hint` for the area, sends no role for the object and the wreck, and stops sending `searchable`. Object and Mortar both get a silent wake-up channel. |
| C, DB v7 | Set role `hint` on rows with `searchable = 0`, no icon, kind circle or polygon, and role `''`, then drop the column. Search no longer filters it, the insert and update SQL and `hydrate()` drop it, `Application_Write` ignores a sent `searchable`, and the canvas uses the role only. | — |

Step C converts legacy hint areas before the drop, so live areas never gain a centre pin. If no older Object build is in use, A, B, and C ship together.

### 8. Studio Purpose control

`Canvas.php` passes `roles` (slug, label, kinds, assignable) to the canvas config. The staff inspector shows a Purpose select for circles and polygons:

- the select lists the assignable roles allowed for the kind;
- the payload carries `role` only from that select;
- if the current role is not assignable, the inspector shows its label as read-only text and omits `role` from the payload, so the merge keeps it.

The player inspector is unchanged. `objectStamp()` includes `role`, so a changed role repaints the object.

### 9. Silent wake-up channels

Android does not allow a channel's sound to change after the channel is created. Each application therefore deletes channel `tacnav-deliveries` and posts wake-ups on a new channel, `tacnav-wake`, with `IMPORTANCE_LOW`: no sound, no vibration, and no heads-up or screen wake. The notification still exists, which is what keeps high-priority FCM delivery from being deprioritized. Alarm-channel hit, warning, destruction, and shot signals are untouched.

`IMPORTANCE_HIGH` with a null sound was rejected: it still shows a heads-up banner and can light a hidden phone's screen.

### 10. Versions

- Tac Nav Object becomes 0.2.0, versionCode 3.
- Tac Nav Mortar becomes 0.3.0, versionCode 3.
- TacNav Maps DB version goes from 5 to 6 in step A and to 7 in step C.
- The plugin version headers are bumped with each step.

## Risks / Trade-offs

- **Any activated application can now delete staff objects.** → Credentials are staff-issued and map-scoped, the edit hook can still deny writes, and this matches the client-trust model already accepted for applications.
- **The owner team no longer follows an application's team change on update.** → Object freezes its context per session. A Mortar position marker keeps the team it was created with until Mortar creates a new one. Both are acceptable.
- **More pushes reach Object phones** (hint areas and wrecks are now found). → The low-importance silent channel keeps them invisible to bystanders. Object already ignores those hits. The cost is FCM traffic and some battery.
- **Mortar diagnostics list hint areas, wrecks, and minefields.** → Accepted under the hub model. The real object is still told apart by id.
- **An older Object build runs after step C.** → Its hint area becomes an ordinary circle with a centre pin, but hit counting is unchanged. Deploy step C only after the Object update is installed.
- **A stale update stamp has one-second precision.** → Two writes to the same object within the same second as its previous update can both pass. This is rare, and the later visibility edit can be repeated.

## Migration Plan

1. Deploy step A. Confirm DB version 6, that every row has role `''`, that Mortar search results are unchanged, and that Object hint areas still have no pin.
2. Build Object 0.2.0 and Mortar 0.3.0, install them on the phones, and confirm that hint areas are written with role `hint` and that wake-ups are silent.
3. Deploy step C. Confirm DB version 7, that the `searchable` column is gone, that legacy hint areas have role `hint`, and that Mortar now finds hint areas while Object counts no hit for them.

Rollback:

- **From step A:** restore the earlier plugin code. Older code ignores the `role` column.
- **From step C:** re-add `searchable TINYINT(1) NOT NULL DEFAULT 1` before restoring older code, because older inserts write that column. Hint areas then become searchable until Object writes them again.

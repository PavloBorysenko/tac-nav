# Hooks and APIs

## Purpose

Name the access hooks other code may attach to without opening the resolver class.

## Responsibilities and boundaries

`tacnav_geo_object_visible` receives the default boolean, the object, the viewer, and the request (`surface`, `map_id`). `tacnav_geo_object_editable` uses the same signature for edit and delete. `tacnav_geo_objects_query` filters listing args (including `include_expired`) with viewer and request.

Default staff viewers see non-expired objects. Empty visibility means all authorized viewers. Team members may edit only `origin=team` objects whose belonging is their team. An activated application may edit or delete any object on its selected map, whatever its origin, belonging, role, or creator; which objects it changes is its own game decision. A player self-point stays locked for everyone. Those defaults are owned here as hook contracts; storage stays on Data model.

`tacnav_geo_object_roles` filters the role registry (slug => `label`, `kinds`, `assignable`). The ordinary role `''` is always kept, and kinds outside the four geometry kinds are dropped. Adding a role changes no stored object.

`tacnav_maps_application_principal` is the credential-owner integration boundary. It receives the current value (`null`) and `WP_REST_Request`, and returns `null` when it does not recognize the request, `WP_Error` for invalid credentials, or an authenticated context containing `application_id`, `installation_id`, selected `map_id`/`team_id`, and `allowed_map_ids`/`allowed_team_ids`. TacNav Maps revalidates the selected context against those scopes and the teams listed on the map. It does not create a WordPress user.

`tacnav_application_object_saved` fires with the committed object id and validated application context after a successful application create, and after an application update only when the writer is the application that created the object (`Application_Write::binds_on_update()`). A messaging integration may use it to bind that object to the active installation, so another application's edit never takes over the object's deliveries. Staff and player writes do not fire it.

Object writes accept `ttl_seconds`; `ttl_minutes` is rejected without compatibility conversion. Positive seconds set `expires_at` from server acceptance, zero clears expiry, and omission on update preserves the stored absolute expiry. `ttl_seconds: 1` is the way to expire an object now.

An application create stamps the application id, origin `application`, and the selected team as belonging. An application update carries only the fields the body sends (`kind`, `geometry`, `title`, `description`, `icon_id`, `visible_team_ids`, `position_accuracy_m`, `ttl_seconds`, `role`), and the store merges them over the stored row. Belonging, origin, creating application, and author never change on update. A body `updated_at` that no longer matches the stored stamp returns `409 tacnav_geo_conflict` and changes nothing. POST, PUT, and PATCH all reach the same handler.

Application and staff writes may send `role`. A `searchable` value from an older client is ignored. A create also accepts `idempotency_key`, 1 to 64 letters, digits, hyphens, or underscores. Repeating a create with the same key from the same application returns the stored object without a second row, a new binding, or a picture publish. `client_object_id` has no server effect because Mortar sends constant values in it.

`POST /tacnav/v1/maps/{map_id}/search` is application-only. The body contains `kind`, normalized `geometry`, and an optional `role`: absent or `null` matches every role, `""` matches ordinary objects, and an unregistered value returns `400 tacnav_geo_role`. It selects unexpired candidates with the map, expiry, and spatial indexes, then performs exact canonical-geometry intersection. Each result is the whole stored object (id, kind, geometry, role, belonging, visibility, origin, creating application, icon, title, description, accuracy, expiry, and `updated_at`) without `client_key`. Distances, probabilities, and game decisions are intentionally absent; each application derives them.

## Implementation references

- `wp-content/plugins/tacnav-maps/app/Access.php` — resolvers and filters
- `wp-content/plugins/tacnav-maps/app/Application_Principal.php` — application boundary and scope validation
- `wp-content/plugins/tacnav-maps/app/REST.php` — application object commit action
- `wp-content/plugins/tacnav-maps/app/Geo_Store.php` — `tacnav_geo_objects_query`
- `wp-content/plugins/tacnav-maps/app/Geometry_Service.php` — projection and exact intersection

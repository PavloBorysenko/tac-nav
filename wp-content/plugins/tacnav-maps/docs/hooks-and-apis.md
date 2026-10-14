# Hooks and APIs

## Purpose

Name the access hooks other code may attach to without opening the resolver class.

## Responsibilities and boundaries

`tacnav_geo_object_visible` receives the default boolean, the object, the viewer, and the request (`surface`, `map_id`). `tacnav_geo_object_editable` uses the same signature for edit and delete. `tacnav_geo_objects_query` filters listing args (including `include_expired`) with viewer and request.

Default staff viewers see non-expired objects. Empty visibility means all authorized viewers. Team members may edit only `origin=team` objects whose belonging is their team. Those defaults are owned here as hook contracts; storage stays on Data model.

`tacnav_maps_application_principal` is the credential-owner integration boundary. It receives the current value (`null`) and `WP_REST_Request`, and returns `null` when it does not recognize the request, `WP_Error` for invalid credentials, or an authenticated context containing `application_id`, `installation_id`, selected `map_id`/`team_id`, and `allowed_map_ids`/`allowed_team_ids`. TacNav Maps revalidates the selected context against those scopes and the teams listed on the map. It does not create a WordPress user.

`tacnav_application_object_saved` fires with the committed object id and validated application context after a successful application create or update. A messaging integration may use it to bind that object to the active installation. Staff and player writes do not fire it.

Object writes accept `ttl_seconds`; `ttl_minutes` is rejected without compatibility conversion. Positive seconds set `expires_at` from server acceptance, zero clears expiry, and omission on update preserves the stored absolute expiry.

`POST /tacnav/v1/maps/{map_id}/search` is application-only. The body contains `kind` and normalized `geometry`. It selects candidates with the map, expiry, and spatial indexes, then performs exact canonical-geometry intersection. Results contain only `id`, `kind`, `geometry`, and optional `position_accuracy_m`; visibility, origin, ownership, distances, probabilities, and game decisions are intentionally absent.

## Implementation references

- `wp-content/plugins/tacnav-maps/app/Access.php` — resolvers and filters
- `wp-content/plugins/tacnav-maps/app/Application_Principal.php` — application boundary and scope validation
- `wp-content/plugins/tacnav-maps/app/REST.php` — application object commit action
- `wp-content/plugins/tacnav-maps/app/Geo_Store.php` — `tacnav_geo_objects_query`
- `wp-content/plugins/tacnav-maps/app/Geometry_Service.php` — projection and exact intersection

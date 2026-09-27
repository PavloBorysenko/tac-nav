# Security

## Purpose

Record who may manage the catalog, write geo objects, open the canvas, or purge expired rows.

## Responsibilities and boundaries

Capabilities: `tacnav_manage_catalog`, `tacnav_manage_geo`, `tacnav_view_canvas`, `tacnav_purge_expired`. Administrator and Organizer (`tacnav_organizer`) receive all four. Organizer also has `read` and `upload_files` only—no plugin or theme management. Organizers land on the Maps list from the dashboard and get an admin-bar Maps shortcut. Player (`tacnav_player`) has `read` only. Role caps are reinstalled when the plugin database version changes.

REST namespace `tacnav/v1` allows staff, a player whose team is listed on a published map, or an anonymous guest token `t`. Cookie authentication plus `X-WP-Nonce` (`wp_rest`) is required for signed-in calls. Guests send the token on the query string. List and mutate callbacks apply visibility and edit resolvers server-side. Player writes are stamped to that team and may use only that team's palette. Self-points are not editable.

Catalog meta boxes verify `tacnav_*_nonce` fields. The player page verifies `tacnav_player`. The canvas returns 403 unless the viewer is staff, a listed player on a published map, or an anonymous guest with a valid token for a listed team. A signed-in user who is not staff and whose team is not listed is refused even with a token. Guest tokens are `{teamId}.{truncated HMAC of mapId|teamId}` using the WordPress auth salt. The guest-link page requires `tacnav_view_canvas`.

## Implementation references

- `wp-content/plugins/tacnav-maps/app/Roles.php` — role and capabilities
- `wp-content/plugins/tacnav-maps/app/REST.php` — REST permission callbacks
- `wp-content/plugins/tacnav-maps/app/Canvas.php` — canvas gate
- `wp-content/plugins/tacnav-maps/app/Guest_Token.php` — guest token
- `wp-content/plugins/tacnav-maps/app/Viewer.php` — staff, player, and guest resolution

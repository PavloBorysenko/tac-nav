# Data model

## Purpose

Record how maps, teams, icons, and geo objects are stored.

## Responsibilities and boundaries

Post types `tacnav_map`, `tacnav_team`, and `tacnav_icon` are private UI types. Geo objects live in `{prefix}tacnav_geo_objects`, not as posts.

Map meta: `tacnav_center_lat`, `tacnav_center_lng`, `tacnav_zoom`, `tacnav_basemap` (`satellite` or `satellite-labels`), `tacnav_team_ids` (teams listed on that map). Title and description use the post title and content. Canvas pan does not write these keys. Staff set center and zoom on a Leaflet picker, not by typing coordinates.

Team meta: `tacnav_color`, `tacnav_palette_icon_ids`, `tacnav_quick_presets` (kind, icon_id, title, description, ttl_minutes defaulting to 3; no coordinates). The team badge is the featured image, not a catalog icon id.

Icon meta: `tacnav_availability` (`staff`, `player`, `both`). The file is the featured image. Player canvas icons are the team's palette, not this availability flag.

Player user meta: `tacnav_team_id` (one team, or empty), `tacnav_avatar_id` (attachment), `tacnav_self_points` (map id to the current self-point object id).

Geo row fields: `kind` (`marker`, `polyline`, `polygon`, `circle`), `geometry` JSON, `owner_team_id` (empty = neutral), `visible_team_ids` JSON (empty = all), `origin` (`staff` or `team`), `icon_id`, `title`, `description`, `expires_at`. Missing or deleted icons fall back to the default marker.

Delete map removes its geo rows. Delete team removes rows whose belonging is that team or whose visibility list includes it, and clears `tacnav_team_id` for players of that team. Removing a team from a map keeps the rows. Delete icon nulls `icon_id` and does not delete rows.

## Implementation references

- `wp-content/plugins/tacnav-maps/app/Post_Types.php` — CPT slugs
- `wp-content/plugins/tacnav-maps/app/Geo_Store.php` — table writes
- `wp-content/plugins/tacnav-maps/app/Cascades.php` — delete cascades
- `wp-content/plugins/tacnav-maps/app/Admin.php` — catalog meta save
- `wp-content/plugins/tacnav-maps/templates/` — map, team, and icon meta-box markup

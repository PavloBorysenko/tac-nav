# Data model

## Purpose

Record how maps, teams, icons, and geo objects are stored.

## Responsibilities and boundaries

Post types `tacnav_map`, `tacnav_team`, and `tacnav_icon` are private UI types. `Catalog::get_maps()`, `get_teams()`, and `get_icons()` list published titles for staff pickers. Geo objects live in `{prefix}tacnav_geo_objects`, not as posts.

Map meta: `tacnav_center_lat`, `tacnav_center_lng`, `tacnav_zoom`, `tacnav_basemap` (`satellite` or `satellite-labels`), `tacnav_team_ids` (teams listed on that map). Title and description use the post title and content. Canvas pan does not write these keys. Staff set center and zoom on a Leaflet picker, not by typing coordinates.

Team meta: `tacnav_color`, `tacnav_palette_icon_ids`, `tacnav_quick_presets` (kind, icon_id, title, description, `ttl_seconds` defaulting to 180; no coordinates). Database version 4 migrates each old `ttl_minutes` preset once by multiplying it by 60 and removes the old key. The team badge is the featured image, not a catalog icon id.

Icon meta: `tacnav_availability` (`staff`, `player`, `both`). The file is the featured image. Player canvas icons are the team's palette, not this availability flag.

Player user meta: `tacnav_team_id` (one team, or empty), `tacnav_avatar_id` (attachment), `tacnav_self_points` (map id to the current self-point object id).

Geo row fields: `kind` (`marker`, `polyline`, `polygon`, `circle`), canonical `geometry` JSON, `owner_team_id` (empty = neutral), `visible_team_ids` JSON (empty = all), `origin` (`staff`, `team`, or `application`), nullable `application_id`, nullable `position_accuracy_m`, `role`, nullable `client_key`, `icon_id`, `title`, `description`, and absolute UTC `expires_at`. Missing or deleted icons fall back to the default marker.

`client_key` is the optional idempotency key of an application create. It is unique per `application_id`, is never returned in object lists or search results, and is not changed by updates. Database version 5 added it.

`role` is `varchar(32)`, defaults to `''` (an ordinary object), and has no index, because the role filter only narrows rows the spatial index already selected. Database version 6 adds it, so every existing row is ordinary. Database version 7 gives role `hint` to every row that had the old `searchable = 0` flag, no icon, and kind circle or polygon, then drops the `searchable` column, so application search now returns every unexpired intersecting object and each application decides which hits count. The closed registry lives in `Object_Roles` and can be extended through filter `tacnav_geo_object_roles`:

| Slug | Label | Kinds | Staff may assign |
| --- | --- | --- | --- |
| `''` | Object | all | yes |
| `minefield` | Minefield | circle, polygon | yes |
| `hint` | Hint area | circle, polygon | no |

`Geo_Store` validates the role on the merged row of every create and update, so an unregistered role or a role the kind does not allow fails with `400 tacnav_geo_role`. A write that omits the role keeps the stored one. Player writes never carry a role. The server stores and filters roles but never acts on them.

`spatial_projection` is a non-null MySQL/MariaDB `GEOMETRY` generated from canonical JSON and protected by a spatial index. Markers project to points, polylines to line strings, polygons to polygons, and circles to a 32-point envelope used only for candidate selection. Versioned migration SQL capability-probes InnoDB geometry/index support, backfills every valid existing row without changing canonical JSON, verifies no null projection remains, and then adds the spatial index. Activation or upgrade fails clearly if that indexed plan is unavailable; there is no JSON-scan fallback. Creates and updates write canonical JSON and projection in one SQL statement.

Delete map removes its geo rows. Delete team removes rows whose belonging is that team or whose visibility list includes it, and clears `tacnav_team_id` for players of that team. Removing a team from a map keeps the rows. Delete icon nulls `icon_id` and does not delete rows.

## Implementation references

- `wp-content/plugins/tacnav-maps/app/Post_Types.php` — CPT slugs
- `wp-content/plugins/tacnav-maps/app/Geo_Store.php` — table writes
- `wp-content/plugins/tacnav-maps/app/Cascades.php` — delete cascades
- `wp-content/plugins/tacnav-maps/app/Admin.php` — catalog meta save
- `wp-content/plugins/tacnav-maps/templates/` — map, team, and icon meta-box markup

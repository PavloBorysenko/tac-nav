## Context

See `proposal.md` for motivation. Constraints that shape the approach:

- Git root is a Local WordPress site. First-party code today is only child theme `tacnav` (Twenty Twenty-Five): textdomain, khaki header/footer, empty `@wordpress/scripts` entry. No first-party plugin. No map CPT, table, or REST.
- Workspace rules: do not edit Core or third-party plugins. A new plugin is first-party once git tracks it and the slug is confirmed (`tacnav-maps`).
- PHPCS prefixes already include `TacNav`, `tacnav`, `tac_`.
- `.gitignore` allowlists `wp-content/themes/tacnav/` and ignores all other themes/plugins unless allowlisted.
- High-frequency GPS is out of this slice, but the object store must accept frequent writes later without becoming `wp_posts`.

## Goals / Non-Goals

**Goals:**

- Plugin owns facts, capabilities, REST, and canvas assets. Theme is not the hub.
- One front-end canvas component with a capability-filtered module registry.
- Object schema and hooks are complete enough that slice 2 does not migrate columns.
- Staff can run a game on a field using only the website.

**Non-Goals:**

- Implementing slice 2 UI or membership (recorded below so it is not lost).
- Live device tokens, native apps, mortar/hit rules, webhooks to phones.
- A second wp-admin Leaflet editor.
- Per-object color picker.
- Scenario/event CPT.

## Decisions

### 1. Plugin `tacnav-maps`, not the child theme

CPT registration, the geo table, roles, resolvers, and REST live in `wp-content/plugins/tacnav-maps/`. The plugin enqueues Leaflet and the canvas on its own front-end route. Theme `tacnav` stays a visual shell unless a later change needs a block wrapper.

Alternative considered: put everything in the child theme. Rejected: catalog and API are product logic, and the theme is already scoped as TT5 overrides.

Alternative considered: two plugins (catalog vs canvas). Rejected for this slice: one team, one prefix, one allowlist.

### 2. CPT for maps, teams, and icons; custom table for geo objects

Maps, teams, and icons are rare editorial records with list/form admin UI — CPT is the fit. Geo objects are geometries with TTL and future live writes — a dedicated table with `map_id`, geometry, belonging, visibility, origin, timestamps.

Suggested columns (names can flex; meaning cannot):

- `id`, `map_id`, `kind` (`marker|polyline|polygon|circle`)
- `geometry` (JSON or MySQL geometry)
- `owner_team_id` (nullable), `visible_team_ids` (JSON array)
- `origin` (`staff|team`), `created_by_user_id`
- `icon_id` (nullable → default marker), `title`, `description`
- `expires_at` (nullable), `created_at`, `updated_at`

Do not use a taxonomy for teams or icons: membership, palettes, and object foreign keys need real IDs.

Alternative considered: geo objects as CPT. Rejected: spatial queries, TTL cleanup, and later GPS pings do not belong in `wp_posts`.

### 3. Teams store palette and quick-add presets

Team meta (or a small child table) stores:

- `palette_icon_ids[]` — subset of the icon CPT
- `quick_presets[]` — `{ kind, icon_id, title, description?, ttl_minutes?, ... }` with no coordinates

Staff canvas in this slice may ignore presets at runtime; the data must still be editable on the team form so slice 2 only reads it.

Icon CPT: title, attachment, availability `staff|player|both`. Colored variants are separate posts, not a color field on the object.

### 4. Delete cascades

- Delete map → delete rows with that `map_id`.
- Delete team → delete objects where `owner_team_id` is that team **or** the team id appears in `visible_team_ids`.
- Delete icon → set affected `icon_id` to null / default; do not delete objects.

### 5. Visibility and edit are resolvers plus hooks

Default view: staff see all non-expired (unless hook denies). Others: empty `visible_team_ids` or viewer team in the list.

Default edit: staff may edit all (unless hook denies). Non-staff: `origin === team` AND belonging is their team.

Hooks receive default boolean, object, viewer, request (`studio|public|live`, `map_id`). Also expose a collection/query filter. JavaScript never decides authorization.

Suggested hook names (prefix can match the plugin): `tacnav_geo_object_visible`, `tacnav_geo_objects_query`, `tacnav_geo_object_editable`.

### 6. Front-end canvas, wp-admin catalog

Lists/forms for maps, teams, and icons use wp-admin (Organizer menus besides TacNav can be hidden later). The editor is a capability-gated front-end page so slice 2 can reuse it on a phone. Canvas does not write center/zoom.

Basemaps: default unlabeled satellite via Esri World Imagery; optional OSM streets. Attribution required. Leaflet is provider-agnostic; if Esri terms become unusable, swap the satellite URL without changing object specs.

### 7. Drawing and tools

Add (`+`) is persist-intent. Measure and coordinates are ephemeral modes, siblings of each other, not of saved kinds.

Drawing does not auto-complete: confirm / cancel on a mode bar. Vertices stay draggable while drawing. After confirm of a catalog type, inspector then save. No per-object color: halo/stroke/fill from belonging team color, else neutral.

TTL UI: none / 3 min / 10 min / 1 hour / custom minutes → `expires_at`. Expired hidden unless staff check “show expired”. Purge deletes expired on the current map only.

### 8. Capabilities, not role names in the UI

Register map capabilities and grant them to Administrator and Organizer. Canvas modules declare the capability they need. Slice 2 adds membership and grants a smaller set.

## Deferred: slice 2 (do not implement)

Keep this context so the next change does not rediscover it. No tasks in this change implement these items.

- Same canvas component and API. Team members get a capability-assembled toolbar, not a second app.
- Membership: user → one global team (not a WordPress role).
- Member create: `origin=team`; belonging and visibility locked to that team. Full type list still available; icon picker uses that team's palette only.
- `+` menu for a member: that team's quick-add presets on top, then marker/circle/line/polygon. A preset applies every stored field except coordinates. Marker preset: tap saves immediately. Circle: center tap; radius from preset or drag. Line/polygon: metadata prefilled, vertices still drawn.
- Members edit/delete any `origin=team` object of their belonging, including marks created by teammates. Staff-origin objects with that belonging are visible when visibility allows and are never editable by members.
- Player filters: one toggle to show/hide team-origin objects of their team (including their own). Expired objects are never returned to members. No staff team-layer list, no expired checkbox, no purge.
- “Mark myself”: a player control that uses browser geolocation to place or update the member on the map. Details (one moving marker vs new points, TTL, icon, update interval) are decided when slice 2 is implemented. This is not an organizer live-token and not a mortar app.
- Coordinate tool may be offered to members without a new entity.
- Live GPS join links, device tokens, native apps, and shot/hit rules stay after slice 2.

## Risks / Trade-offs

- [Esri satellite is not a guaranteed free SLA] → Ship attribution, isolate the tile URL, keep OSM as fallback. Re-check terms before production traffic.
- [wp-admin catalog is poor on mobile] → Acceptable: organizers work at a desk. Canvas is front-end because players will use it later.
- [Team delete is destructive] → Document in the team UI that objects with that belonging or visibility are removed.
- [SVG uploads in the icon CPT] → Sanitize or restrict upload types; prefer a small allowlist.
- [Leaflet.draw is mouse-centric] → Prefer a drawing approach that supports tap-to-vertex, confirm/cancel, and vertex drag (e.g. Geoman or a thin custom layer). Do not rely on double-click to finish.

## Migration Plan

- Greenfield table and CPTs. No data migration.
- On plugin activation: create table, register role and capabilities, grant capabilities to Administrator.
- Rollback: deactivate plugin (CPT content remains in `wp_posts` until deleted; drop table only on uninstall if an uninstall routine is added).
- Allowlist `wp-content/plugins/tacnav-maps/` in `.gitignore` when the plugin is created.

## Open Questions

- Exact REST path prefix and CPT slug strings (`tn_map` vs `tacnav_map`) can be chosen at apply time if they stay namespaced and are not the bare post type `icon`.
- Starter icon files can be placeholders until staff upload the real library.

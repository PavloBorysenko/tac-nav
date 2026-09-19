## Why

TAC Nav needs a staff-facing map studio before phones, team members, or live GPS exist. The child theme is an empty visual shell: there is no catalog of maps, teams, or icons, and no way to draw geo objects on a field. Staff must be able to create that world on the website now so later slices can reuse the same canvas and rules.

## What Changes

- Add first-party plugin `tacnav-maps` (hub: data, capabilities, REST). The child theme `tacnav` stays the presentation host and does not own CPT, tables, or visibility rules.
- Add WordPress role **Organizer**: full control of maps, teams, icons, and geo objects; no site administration (plugins, themes, general settings). Administrators keep site control and the same map capabilities.
- Add CPT **map**: list and form (title, description, center, zoom, default basemap). Deleting a map deletes all of its geo objects.
- Add CPT **team** (global, few records, shared by every map): list and form (title, description, color, icon, per-team icon palette, per-team quick-add presets). Deleting a team deletes geo objects that belong to it or list it in visibility.
- Add CPT **icon**: staff-managed library (title, file, staff/player/both). Teams pick a palette and build quick-add buttons from it. Deleting an icon does not delete objects; they fall back to a default marker.
- Add a custom table of geo objects (marker, open polyline, closed polygon, circle) with belonging, multi-team visibility, origin, TTL, icon, title, and description.
- Add a front-end map canvas (not wp-admin) for administrator and organizer only: draw tools, measure, coordinate readout, popup, inspector, staff filters, satellite/street layers.
- Visibility and edit checks go through resolvers plus hooks that receive full viewer, object, and request context.
- Slice 2 (team-member canvas, player filters, “mark myself”, live GPS) is recorded as deferred context only. It is not implemented in this change.

## Capabilities

### New Capabilities

- `tacnav-map-catalog`: Organizer role, map/team/icon CPTs, team palettes and quick-add presets, staff list/form screens, delete cascades for maps and teams.
- `tacnav-geo-objects`: Geo-object persistence, belonging, visibility, origin, TTL, default resolvers, filter hooks, and staff GeoJSON/REST reads and writes.
- `tacnav-map-canvas`: Front-end Leaflet studio: add/draw/edit UI, measure and coordinate tools, popup and inspector, staff filters, basemap switcher.

### Modified Capabilities

- None. Existing `tacnav-child-theme` requirements (child of Twenty Twenty-Five, khaki header/footer) do not change. Canvas assets are owned by `tacnav-maps`.

## Impact

- New tracked plugin `wp-content/plugins/tacnav-maps/` and a `.gitignore` allowlist for that slug (`build/` still ignored).
- New CPT slugs, a custom table, an organizer role, and REST routes under a `tacnav/v1` (or equivalent) namespace.
- Front-end canvas route/page gated to administrator and organizer.
- Leaflet plus a free satellite basemap (Esri World Imagery, unlabeled) and OSM streets; attribution required.
- Theme `tacnav` is not required to change for this slice if the plugin renders and enqueues the canvas. Site docs that list first-party components will need a catalog hop when the plugin is added (implementation time).
- No member UI, no device tokens, no native apps, no mortar/hit logic.

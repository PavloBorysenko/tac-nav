## 1. Plugin scaffold

- [x] 1.1 Scaffold first-party plugin `tacnav-maps` (prefix `tacnav` / `TacNav`, allowlist `wp-content/plugins/tacnav-maps/` in `.gitignore`, ignore its `build/`) and verify the plugin header loads and `rg tacnav-maps .gitignore` shows the allowlist
- [x] 1.2 Add plugin docs catalog hop from root `docs/catalog.md` when the plugin tree exists and verify the root catalog names `tacnav-maps`

## 2. Roles and catalog

- [x] 2.1 Register Organizer role plus map-staff capabilities, grant them to Administrator and Organizer, and verify an Organizer cannot manage plugins or themes
- [x] 2.2 Register map CPT with title, description, center, zoom, and default basemap form fields and verify creating a map stores those values in the list/edit screens
- [x] 2.3 Register icon CPT (title, media, availability staff/player/both) with a default-marker fallback and verify deleting an icon leaves referencing objects on the default marker
- [x] 2.4 Register team CPT (title, description, color, team icon, icon palette, quick-add presets without coordinates) and verify two teams can store different palettes and presets
- [x] 2.5 Implement map delete → delete that map's geo objects and team delete → delete objects with that belonging or visibility entry, and verify icons are not deleted with a team

## 3. Geo objects and hooks

- [x] 3.1 Create the geo-object table and write path for marker, open polyline, closed polygon, and circle with belonging, visibility list, origin, icon, title, description, and expires_at, and verify a PHPUnit (or equivalent) case stores each kind
- [x] 3.2 Implement default visibility and edit resolvers plus `tacnav_geo_object_visible`, `tacnav_geo_objects_query`, and `tacnav_geo_object_editable` hooks that receive full context, and verify staff-origin + Red belonging is not editable by a Red member in tests
- [x] 3.3 Default listings omit expired objects; staff can include them and purge expired on one map, and verify purge does not delete unexpired rows

## 4. REST / GeoJSON

- [x] 4.1 Add authorized staff REST (or equivalent) to list and write objects for a map, applying resolvers server-side, and verify an unauthorized request is rejected and default list omits expired objects

## 5. Front-end canvas

- [x] 5.1 Add a capability-gated front-end canvas route that loads Leaflet with unlabeled satellite default and OSM streets, and verify a non-staff request is refused
- [x] 5.2 Implement modular add (`+`) drawing: marker tap, polyline/polygon vertices with drag + confirm/cancel, circle center + radius, mode bar, then inspector-before-save, and verify a line stays open and a polygon is closed after confirm
- [x] 5.3 Implement object popup (title/description/belonging, edit/delete only when allowed) and inspector without a color field, using team color for halo/stroke/fill, and verify canvas pan does not persist map center/zoom
- [x] 5.4 Implement ephemeral measure (dynamic segments + total, stop keeps overlay, clear removes it) and coordinate tool (tap shows lat/lng, no object created), and verify neither tool writes a geo row
- [x] 5.5 Add staff filters (belonging teams, show expired, show titles) and purge-expired on the current map, and verify expired objects appear only when the expired filter is on
- [x] 5.6 Use a compact overlay/bottom sheet on a narrow viewport so the map remains the primary surface, and verify the add/filter/inspector modules can be shown independently

## 6. QA gates

- [x] 6.1 Run `phpcs --standard=phpcs.xml.dist` and `php tools/phpstan/vendor/bin/phpstan analyse -c phpstan.neon.dist --memory-limit=1G` on first-party plugin PHP and verify both are green
- [x] 6.2 Lint canvas JS with the repo's first-party JS lint path and verify it is green (report prettier/linebreak debt only; do not rewrite the whole file for style)
- [x] 6.3 Run the focused new tests, then `php tools/phpunit/vendor/bin/phpunit -c phpunit.xml.dist` if that runner exists, and verify they match the spec scenarios

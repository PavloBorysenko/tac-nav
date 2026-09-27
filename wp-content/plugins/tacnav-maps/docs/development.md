# Development

## Purpose

Describe how the staff canvas is enqueued and how first-party JS is linted.

## Responsibilities and boundaries

`TacNav\Maps\Canvas` enqueues Leaflet 1.9.4 from unpkg plus `assets/canvas.js` and `assets/canvas.css`. Localized object `tacnavMapsCanvas` supplies REST URL, nonce, `mode` (`staff`, `player`, or `guest`), map passport, teams listed on the map, icons, presets, and objects. The page root is `#tacnav-canvas-app` with `data-tacnav-canvas` and `data-tacnav-mode`. Players get that team's presets and palette. Guests omit add, filters, layers, live, and mark-myself. Show titles is a toolbar button stored in `sessionStorage` key `tacnav-show-labels` (`1` on, missing or other off). Guest QR is drawn in the browser from `assets/vendor/qrcode.js`.

Map edit screens enqueue `assets/admin-map-picker.js` and `assets/admin.css` so staff pick center and zoom on a satellite preview. Canvas JS is vanilla Leaflet. Lint it with `npm --prefix tools/js-lint run lint -- ../../wp-content/plugins/tacnav-maps/assets`. `tools/js-lint/paths.json` includes that assets directory.

## Constraints and side effects

Rewrite `tacnav-canvas/{id}` must be flushed after activation. Satellite tiles are Esri World Imagery; place names are the Esri World Boundaries and Places overlay. Attribution is required. The canvas map allows zoom 1–22; tiles scale past native level 19. Object icons stay 34px at every zoom. Clicks in draw, measure, and coordinate modes pass through existing polygons and circles. Every side sheet has a close control. Create and update REST rows include `can_edit` so the popup can edit without a reload. The canvas turns off CSS view transitions (`@view-transition { navigation: none }`) and swallows skipped `document.startViewTransition` promises so Chrome does not log `AbortError: Transition was skipped` on this standalone page.

## Implementation references

- `wp-content/plugins/tacnav-maps/app/Canvas.php` — rewrite, enqueue, gate
- `wp-content/plugins/tacnav-maps/templates/canvas.php` — canvas document
- `wp-content/plugins/tacnav-maps/assets/canvas.js` — studio UI
- `wp-content/plugins/tacnav-maps/assets/canvas.css` — toolbar and sheets
- `wp-content/plugins/tacnav-maps/assets/admin-map-picker.js` — catalog map center/zoom picker
- `wp-content/plugins/tacnav-maps/templates/` — catalog meta-box markup

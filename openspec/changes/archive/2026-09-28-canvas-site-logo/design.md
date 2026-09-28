# Design

## Context

See `proposal.md` for motivation. Constraints that shape the approach:

- The canvas is its own document in `templates/canvas.php`. It does not print the theme header, so the header's Site Logo never appears there. Zoom sits at the top-left. The toolbar is a centered pill at the bottom (`rgba(17, 24, 39, 0.6)`). Esri attribution sits at the bottom-right. `data-tacnav-home` is the Center control: it calls `map.setView` and is labeled "Center".
- The custom logo is the WordPress theme mod `custom_logo`. The child theme sizes that image to 80px in the header. The canvas must not depend on theme CSS.
- `Canvas::render()` already passes `$map` and `$mode` into the template. There is no PHPUnit coverage that renders this document.

## Goals / Non-Goals

**Goals:**

- One server-rendered link, same markup for staff, player, and guest, using the site custom logo and the site front URL.
- A ranger-green plate at the same 0.6 opacity as the toolbar, without moving Center or the attribution.

**Non-Goals:**

- A second image file, a site-icon fallback, or a text wordmark when the custom logo is missing.
- Opening the site front in a new tab, or confirming before leaving an in-progress drawing.
- Restyling the toolbar, the theme header logo, or the Center control.

## Decisions

### 1. The mark is markup in the canvas template

`Canvas::render()` reads the custom logo attachment and `home_url( '/' )`. When the attachment has an image URL, the template prints one link, class `tacnav-site-mark`, before the toolbar. The image is the custom logo at 32px height, with the site name as its accessible name. When there is no custom logo, the template prints nothing. Do not add the URL to `tacnavMapsCanvas` and do not build the link in `canvas.js`.

Alternative considered: a control inside the Leaflet map. Rejected: the mark is site chrome, and Leaflet already owns zoom and attribution in the corners.

Alternative considered: fall back to the site icon. Rejected: the mark is the same logo as the header, and a missing logo means no mark.

### 2. The plate matches the toolbar glass, in ranger green

`.tacnav-site-mark` is `position: absolute`, bottom-left (`0.75rem`), `z-index: 500`, padding `0.4rem`, and `background: rgba(54, 68, 40, 0.6)`. The toolbar stays `rgba(17, 24, 39, 0.6)`. Give the toolbar a max-width that leaves the logo corner clear (`calc(100vw - 8.5rem)`). The plate is about 3.6rem wide and sits 0.75rem from the left, and the toolbar is centered, so each side needs that inset. A full staff toolbar on a narrow screen then wraps instead of covering the mark. The link does not use `data-tacnav-home`.

Alternative considered: the same slate as the toolbar. Rejected: the mark should read as the site plate, not as another tool.

### 3. Docs stay on the existing development note

Update `wp-content/plugins/tacnav-maps/docs/development.md` so the canvas chrome description includes the site-logo link, the plate color, and that it is omitted when the site has no custom logo. Do not add a documentation file.

## Risks / Trade-offs

- [Leaving the canvas drops an unfinished drawing] → Accepted. The mark is an ordinary same-tab link, like any other navigation away from the page.
- [A wide toolbar can cover the bottom-left] → The toolbar max-width keeps that corner clear. The mark stays at the bottom-left rather than moving above the toolbar.
- [The header logo is 80px and the canvas mark is 32px] → Same attachment, different display size. The header rule is unchanged.
- [PHPUnit does not render this document] → Do not add a test that pretends to assert the mark. The scenarios are checked on the canvas page.

## Migration Plan

No stored data changes. Rollback removes the template link and the plate rule. Maps, objects, and the custom logo attachment stay as they are.

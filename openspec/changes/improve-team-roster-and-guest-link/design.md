# Design

## Context

See `proposal.md` for motivation. Constraints that shape the approach:

- The team roster is a classic-editor meta box. Players are a checkbox list of the Player role, capped at 500, ordered by display name. Saving posts `tacnav_player_ids[]`. There is no roster script today.
- A player avatar is already an attachment in user meta. `Membership::avatar_url()` returns a thumbnail URL or an empty string. The same URL is used in the cabinet and on the canvas.
- The guest-link route exits in `template_redirect` with its own HTML document, inline body CSS, and `wp_head()` / `wp_footer()`. The child theme `tacnav` has no `header.php` or `footer.php`. Header and footer are Site Editor template parts of the Twenty Twenty-Five child.
- PHPUnit in this plugin boots without WordPress, so these screens are not covered by that runner.

## Goals / Non-Goals

**Goals:**

- Filter, count, green rows, and avatars work on the team edit screen without changing who gets saved.
- The guest-link page picks up the active theme header, footer, and button appearance without editing the child theme.

**Non-Goals:**

- Restyling the player cabinet.
- Raising the 500-player query cap or searching by email.
- Changing the guest token, the canvas URL, or who may open the guest-link page.

## Decisions

### 1. Roster filter stays in the browser

A script on the team edit and new-team screens hides non-matching `li` rows as the organizer types. Match is a case-insensitive substring of the visible name. Hidden rows stay in the form, so their checkboxes still submit. Reset is `type="button"` and only clears the text.

The selected count reads checked boxes, including hidden ones, on load and on every change. A class on the `li` paints the green background for checked rows, including rows that were checked when the screen opened.

Alternative considered: filter on the server with a reload. Rejected: the list has to update as the organizer types.

Alternative considered: keep non-matching selected rows pinned. Rejected: the count is the control for hidden selections.

### 2. Avatars are rendered with the list

Each row gets the existing thumbnail URL at render time, 32 by 32, circular, between the checkbox and the name. An empty URL renders an empty circle in that slot so names stay aligned. The image is decorative; the name remains the text the filter reads.

Alternative considered: load avatars as the organizer types. Rejected: the list is already on the page, and the cap is 500.

### 3. Guest page keeps its route and gains theme chrome

The same staff-only document calls `body_class()` and `wp_body_open()`, and places the handoff in a constrained `<main>`. Header and footer template parts are rendered before `wp_head()`, then printed inside `header.wp-block-template-part` and `footer.wp-block-template-part`. On a block theme the import map is printed in `wp_head` and only includes modules already discovered; rendering the parts later leaves `@wordpress/interactivity` unresolved and skips block styles. The copy control uses the theme button class `.wp-element-button`. A small stylesheet styles only the URL field and the QR card. Body font and body max-width go away so the header is not squeezed.

`block_header_area()` / `block_footer_area()` are used because the child theme stores header and footer as template parts, including database customizations. `get_header()` would not draw those parts: the child has no `header.php`.

Alternative considered: a normal WordPress page in the database. Rejected: the route, capability check, and draft refusal already live on this rewrite.

Alternative considered: editing `tacnav` templates. Rejected: the page should consume the header and footer the site already has.

### 4. Admin assets stay on the team screen

Catalog admin CSS still loads for map, team, and icon screens. The roster script loads only for the team post editor. The map Leaflet picker stays on map screens. Guest-link CSS loads with the existing QR script on that route only.

## Risks / Trade-offs

- [Hiding a checkbox by removing its input drops it from the save] → Hide the row and leave the input in the form.
- [Body max-width would shrink the theme header] → Constrain only the handoff column.
- [A site-editor header override differs from the theme file] → `block_header_area()` renders that override, which is the header staff already see on the site.
- [The isolated PHPUnit bootstrap cannot render these screens] → Do not add a test that pretends to assert the filter or the theme chrome. Check the roster script with the JS lint gate.

## Migration Plan

No stored data changes. Existing team assignments, avatars, and guest URLs stay valid. Rollback is reverting the template, script, and stylesheet.

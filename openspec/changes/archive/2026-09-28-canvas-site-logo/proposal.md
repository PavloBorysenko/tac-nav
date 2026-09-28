# Proposal

## Why

The map canvas is a standalone page with no theme header, so an Organizer, a player, and a guest who can open a map have no way back to the site front. The bottom-left corner of the canvas is empty and can hold a small site mark without covering zoom, the toolbar, or the Esri attribution.

## What Changes

- Show the site custom logo at the bottom-left of the canvas, on a semi-transparent ranger-green plate, as a link to the site front.
- Show that mark to every viewer who can open the canvas: staff, a listed player, and a guest.
- Omit the mark when the site has no custom logo.
- Leave the Center control as the control that recenters the map. The mark does not move the map view and does not write geo objects.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `tacnav-player-canvas`: The canvas shows a site-logo link to the site front for staff, players, and guests, and hides it when the site has no custom logo.

## Impact

- Plugin `wp-content/plugins/tacnav-maps/`: the canvas document, its stylesheet, and the data passed into that page. The Center control, guest write rules, and tile attribution stay as they are.
- The mark uses the same custom logo the theme header already shows. Theme header styling is unchanged.
- `wp-content/plugins/tacnav-maps/docs/development.md` describes canvas chrome and becomes stale when the mark is added.

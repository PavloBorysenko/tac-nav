# Proposal

## Why

Staff assign players from a flat checkbox list on the team form. With many players that list is hard to scan: there is no way to find a name, see how many are selected, or tell selected rows apart, and rows do not show the avatar the player already uploaded. The guest-link page that staff open from a map is a bare document, so the link and QR do not sit in the site header, footer, or content style.

## What Changes

- On the team form, each Player row shows that player's avatar beside the name. A player with no avatar still occupies the same image slot with an empty circle.
- A text filter above the list hides non-matching names immediately in the browser. Reset clears the text and shows every player again. Hidden rows stay checked. The count above the filter is the number of checked players, including those the filter is hiding, and it updates as soon as a box is toggled. A checked row has a green background.
- Choosing a player still assigns that one global team and still moves a player off any previous team. The filter does not change who is saved.
- The staff guest-link page keeps the map name, team name, guest URL, copy control, and QR code, and renders them inside the active theme header and footer, with the link and QR in the theme content column.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `tacnav-player-membership`: The team assignment form adds avatar, live name filter, reset, selected count, and a green background on checked rows. Who may be assigned, and the one-team rule, stay as they are.
- `tacnav-map-teams`: The staff guest-link page adds the active theme header and footer and presents the URL, copy control, and QR in the theme content style. Who may open the page, and what the page reveals, stay as they are.

## Impact

- Plugin `wp-content/plugins/tacnav-maps/`: team meta box markup, a small script and styles on the team edit screen, and the guest-link template. Avatar URLs come from the existing player avatar attachment.
- Guest route `tacnav-guest-link/{map}/{team}` still requires an Organizer or Administrator and still refuses a draft map. The token and canvas URL do not change.
- Child theme `tacnav` is not edited. The guest-link page uses the header and footer template parts the theme already provides.
- The player cabinet is unchanged.

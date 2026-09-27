## Why

Staff can already build maps, teams, and geo objects, but a player still cannot join a team or open a map, and a spectator still cannot watch a team’s picture without an account. This change lets players register, get assigned to one team, and use the same canvas, and lets a guest open one map as that team without writing anything.

## What Changes

- Add a Player role. A person registers on the front end (email, password, nickname) and receives that role with no team. An Organizer or Administrator later assigns the player to one team. Assigning the player on another team moves them; their existing objects stay with the old team, and they can no longer edit those objects.
- Add a front-end player cabinet: avatar, nickname, team name, teammates, and links to published maps that list the player’s team. A player with no team can sign in and edit their profile, and sees no maps.
- Each map stores the teams taking part. On that map, staff belonging, visibility, and team filters offer only those teams. Removing a team from the map keeps existing objects and withdraws player and guest access for that team. Deleting the team record still deletes its objects and also clears its players.
- Players open a published map only when their team is on it. They place markers, circles, lines, and polygons, and they may use the quick-add presets saved on their team. When they pick an icon themselves, the only choices are the icons an Organizer or Administrator added to that team’s palette. Icons that exist in the library but are not on that list are not offered and are rejected if sent. They may save a mark with no expiry. The server stamps origin `team`, belonging, visibility, and color to their team. Teammates may edit or delete those objects. Staff-origin objects stay visible when the visibility rules allow and are not editable by players. Expired objects are not returned to players.
- Add “Mark myself”: one self-point per player per map, shown with the author’s avatar, team color, team visibility, and nickname, expiring after one minute. A new press deletes the previous self-point on that map and creates a new one. No one can edit or delete it from the object card. Expired self-points disappear for players and guests immediately and are removed for staff by the existing purge of expired objects.
- Add a guest link for each team on a published map. Staff open a page with the map name, team name, copyable link, and QR code. The token is a team id plus a short signature for that map, valid only while that team remains on the map. A guest sees the same objects as a player of that team, including teammate marks and self-points, and cannot create, edit, or delete. Guest tools are coordinates, measure, center, and “on me” (pan only). “On me” does not write an object; “Mark myself” does, and only a player has it.
- Move “show titles” out of the staff filter sheet onto its own toolbar control. Organizers, Administrators, players, and guests all get it. It draws object names on the map face and does not change which objects are returned. The choice lasts for the browser tab and starts off in a new tab. The object card still shows the title when the control is off.
- The plain map URL shows nothing to an anonymous visitor without a valid guest token, and nothing to a signed-in player whose team is not on the map, even if a guest token is present. A signed-in Organizer or Administrator opening a guest link gets the staff canvas. Draft maps stay staff-only.

## Capabilities

### New Capabilities

- `tacnav-player-membership`: Player role, self-registration, one global team, assignment by staff, and the player cabinet.
- `tacnav-map-teams`: Teams participating on a map, staff pickers limited to that list, guest link and QR page, and the difference between removing a team from a map and deleting the team.
- `tacnav-player-canvas`: Who may open a published map, player drawing and presets, “Mark myself”, the read-only guest view, and the shared titles control.

### Modified Capabilities

- None. Archived spec `tacnav-child-theme` does not change. In-flight change `add-staff-map-studio` is not archived, so its catalog, geo, and canvas specs are not modified here. This change supersedes two rules from that change: every map’s object form lists every global team (staff on a map choose only the teams listed on that map), and the titles checkbox lives in the staff filter sheet (it becomes a toolbar control for staff, players, and guests).

## Impact

- Plugin `wp-content/plugins/tacnav-maps/`: map meta for participating teams, user membership, Player role, front-end registration and cabinet, canvas and REST authorization for players and guests, guest token check, and a staff page that shows the guest link and QR code.
- Canvas route `tacnav-canvas/{id}` gains a guest token and player access. Staff capabilities stay required for catalog screens, purge, and the staff tool set.
- No new geo-object columns are required for ordinary marks. A self-point must be recognizable as the author’s current self-point on that map so the next press can replace it and so edit and delete stay closed.
- No live GPS stream, device tokens, native apps, or shot/hit rules.
- Child theme `tacnav` does not own membership or canvas rules.

## 1. Player role and membership

- [x] 1.1 Register the Player role on the plugin upgrade routine with `read` only, and verify a Player lacks `tacnav_manage_catalog`, `tacnav_manage_geo`, `tacnav_view_canvas`, `tacnav_purge_expired`, and plugin, theme, and settings management
- [x] 1.2 Store one team in user meta `tacnav_team_id`, edit it from the team form as the only member list, and verify assigning a Red player on Blue leaves them on Blue only and `Access::viewer_from_user()` returns that team
- [x] 1.3 On team delete, clear `tacnav_team_id` for that team’s players while keeping the existing geo-object cascade, and verify those players have no team and objects that belonged to the team or listed it in visibility are gone

## 2. Map teams and guest links

- [x] 2.1 Save participating teams in map meta `tacnav_team_ids` and limit staff belonging, visibility, and team filters on that map to that list, and verify a catalog team that is not listed cannot be selected for a new object
- [x] 2.2 Remove a team from the map list without deleting geo rows, and verify a Blue object remains stored while a Blue player no longer receives that map
- [x] 2.3 Accept a guest token of `{teamId}.{signature}` (truncated HMAC of map id and team id with the WordPress auth salt) only when the signature matches and the team is still listed, and verify a swapped team id or a token for a removed team returns no objects
- [x] 2.4 Add a staff-only front-end page, opened from a published map’s team list, that shows the map name, team name, guest URL, copy control, and a browser-drawn QR code, and verify a Player is refused and a draft map has no guest URL that opens the canvas

## 3. Registration and cabinet

- [x] 3.1 Add a front-end registration form on the player sign-in page (email, password, nickname) that creates a Player with an empty `tacnav_team_id`, and verify the new account can sign in and is not staff
- [x] 3.2 Add a front-end cabinet where a Player can change avatar and nickname, see their team and teammates, and open published maps that list that team via the plain canvas URL, and verify a player with no team sees an empty map list

## 4. Canvas access and object writes

- [x] 4.1 Open `tacnav-canvas/{id}` for staff, for a signed-in Player whose team is on a published map, and for an anonymous visitor with a valid guest token, and verify a draft is staff-only, an anonymous request without a token returns no objects, a signed-in player of another team returns no objects even with a valid token, and a signed-in Organizer opening a guest URL still gets the staff canvas
- [x] 4.2 Let a listed player list and write objects without `tacnav_manage_geo`, stamping origin `team`, belonging and visibility to that team only, and an icon from that team’s palette or the default marker, and verify another team, empty visibility, staff origin, and an icon outside the palette are rejected, `ttl_minutes` of 0 stores no expiry, expired rows are omitted even if requested, a guest receives the same set and cannot create, edit, or delete, and a staff-origin object that belongs to the player’s team is not editable
- [x] 4.3 Add “Mark myself” so each press deletes that player’s previous self-point on that map (including an expired one) and stores one new marker titled with the nickname, expiring in one minute, tracked in user meta `tacnav_self_points`, and verify a second press leaves only the new self-point, ordinary markers remain, no viewer gets edit or delete, a missing device position writes nothing, and purge of expired objects on the map deletes it after the minute

## 5. Canvas modules

- [x] 5.1 Move show-titles out of the staff filter sheet onto one toolbar control for staff, players, and guests, persist it in `sessionStorage` key `tacnav-show-labels` (missing key means off), and verify a reload in the same tab keeps the choice, the object card still shows the title when the control is off, and the control does not call REST
- [x] 5.2 Assemble the player toolbar with add, that team’s presets, mark-myself, coordinates, measure, center, “on me”, and titles, and verify a marker preset saves at the tap, a circle, line, or polygon preset uses only the geometry the player draws, center and “on me” do not write a geo row, and “on me” does not replace the self-point
- [x] 5.3 Assemble the guest toolbar with coordinates, measure, center, “on me”, and titles only, and verify a guest sees a teammate’s mark and a current self-point, and is not offered add, mark-myself, edit, or delete

## 6. QA gates

- [x] 6.1 Update existing plugin docs whose catalog conditions match the new role, map team list, guest route, and canvas controls, and verify those docs name the new behavior
- [x] 6.2 Run `phpcs --standard=phpcs.xml.dist` and `php tools/phpstan/vendor/bin/phpstan analyse -c phpstan.neon.dist --memory-limit=1G` on the changed first-party plugin PHP and verify both are green
- [x] 6.3 Lint the changed canvas JS with `npm --prefix tools/js-lint run lint -- <files>` and verify it is green (report prettier/linebreak debt only; do not rewrite a whole file for style)
- [x] 6.4 Run the focused new tests, then `php tools/phpunit/vendor/bin/phpunit -c phpunit.xml.dist`, and verify they match the membership, token, visibility, and self-point scenarios

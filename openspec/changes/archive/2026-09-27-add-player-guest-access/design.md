## Context

See `proposal.md` for why this slice exists. `add-staff-map-studio` already provides plugin `tacnav-maps`, the geo table, staff resolvers, and the canvas at `tacnav-canvas/{id}`. That change is not archived. Its catalog spec still says every map’s object form lists every global team; this design narrows that form to the map’s own team list when both are in effect.

Current code relevant to the approach:

- `Access::viewer_from_user()` returns `team_ids` as an empty array.
- REST object routes require `tacnav_manage_geo` and stamp `origin` as `staff`.
- The canvas template refuses anyone without `tacnav_view_canvas`.
- Team meta already stores `tacnav_palette_icon_ids` and `tacnav_quick_presets` (kind, icon, title, description, ttl minutes; no geometry).
- Marker geometry keeps only latitude and longitude, so a self-point cannot hide a flag inside the geometry payload.

## Goals / Non-Goals

**Goals:**

- One canvas and one object table for staff, players, and guests.
- Membership and guest access are enforced on the server.
- A self-point can be found and replaced without a new geo column.

**Non-Goals:**

- A live location stream, background updates, or a trail of self-points.
- Email confirmation, public team choice, or a player filter that hides the team’s own marks.
- A basemap switch for players and guests. They use the map’s saved basemap. Titles on the map face are a separate toolbar control, specified below.
- Archiving or rewriting `add-staff-map-studio` in this change.

## Decisions

### 1. Player role plus one user-meta team id

Register role `tacnav_player` with `read` only. Do not grant `tacnav_manage_catalog`, `tacnav_manage_geo`, `tacnav_view_canvas`, or `tacnav_purge_expired`.

The source of truth for membership is user meta `tacnav_team_id` (one post id, or empty). The team form lists users with the Player role and writes that meta: selected players are set to this team, players removed from the field are cleared, and a player taken from another team is overwritten. The team post does not keep a second member list.

`Access::viewer_from_user()` fills `team_ids` from that meta. Staff remain `is_staff` from the existing geo and canvas capabilities.

Alternative considered: a team id stored only on the team post. Rejected because every request would scan team posts to find the current user.

### 2. Map team list is post meta

Store participating team ids in map meta `tacnav_team_ids`. Staff belonging, visibility, and team filters load that list. An empty list leaves those controls empty; staff can still save a neutral object.

Removing an id updates this meta only. Deleting a team keeps the existing geo cascade and also deletes `tacnav_team_id` on users who pointed at that team.

### 3. Same canvas route, three viewer kinds

Keep `tacnav-canvas/{id}`.

- Staff (signed in, staff capabilities): current studio. A guest token in the URL does not reduce this.
- Player (signed in, Player role, `tacnav_team_id` is on the map, map is `publish`): player modules. Ignore any guest token.
- Guest (not signed in, valid token, team still listed, map is `publish`): guest modules.
- Anyone else, including a signed-in player of another team or a player with no team: render no objects. A signed-in player must sign out before a guest token applies.

Draft maps stay on the staff branch only.

Front-end registration and the cabinet are separate plugin routes, not wp-admin. Cabinet map links use the plain canvas URL.

### 4. Guest token is a signed team id

Token form: `{teamId}.{signature}`. The signature is a truncated HMAC of `mapId` and `teamId` using the WordPress auth salt. Verification checks the signature and then checks that the team is in `tacnav_team_ids`. No token table. Removing the team from the map, or changing auth salts, makes existing links fail.

The staff link page is a capability-gated front-end route opened from the map’s team list. It shows the map title, team title, URL, copy control, and a QR code drawn in the browser from that URL.

Alternative considered: a random token stored on the map. Rejected for this slice because the signed team id needs no extra record and still stops a guest from swapping in another team id.

### 5. Player writes are stamped on the server

Player create and update routes do not use `tacnav_manage_geo`. They require a player whose team is on the published map, and they require the object to be editable.

On create, ignore client `origin`, `owner_team_id`, and `visible_team_ids`. Set origin `team`, belonging to the player’s team, and visibility to that team only. Accept an icon only when an Organizer or Administrator added it to that team’s palette, or accept empty for the default marker. Do not offer the rest of the icon library. `ttl_minutes` of 0 stores a null `expires_at`. Positive minutes store an absolute expiry, as staff writes already do.

Preset data is copied into those fields. Coordinates, radius, and path come only from the drawing. A marker preset saves on the tap. Circle, line, and polygon presets wait until the player finishes the geometry.

Listings for players and guests pass `include_expired` false and do not honor a request to include expired rows. Purge stays on `tacnav_purge_expired`.

### 6. Self-point identity lives in user meta

User meta `tacnav_self_points` maps a map id to the current self-point object id. “Mark myself” reads the device position, deletes the stored object for that map when it still exists, inserts a new marker, and stores the new id. The insert uses the player write rules, title set to the nickname, icon empty, and expiry one minute ahead. The canvas draws the author’s avatar for an object id that is that author’s current self-point; with no avatar it uses the default marker.

Edit and delete check the author’s meta for that map. When the object id matches, every viewer is refused, including staff. Ordinary objects are not in this meta, so teammates and staff can still edit them. Purge may delete an expired row and leave a stale meta id; the next press finds no row and inserts a new one. A press with no device position returns before delete or insert.

Alternative considered: a `self_user_id` column. Rejected because the author’s existing `created_by_user_id` plus this meta is enough to find and lock the point.

### 7. Guest and player modules

Guest modules: coordinates, measure, center, “on me”, and the titles toggle. No add, presets, mark-myself, inspector save, purge, expired filter, or team checklist.

Player modules: those, plus add, presets, and mark-myself. “On me” only calls `setView`. Mark-myself is the write. Center returns to the stored passport view and does not save it.

Staff keep the filter sheet for expired objects, team checkboxes, and purge. The titles checkbox leaves that sheet. Staff, players, and guests share one toolbar toggle. It reads and writes `sessionStorage` key `tacnav-show-labels`. Missing key means off. The key is for the browser tab, not for one map, so a reload on any map in that tab keeps the same choice. Closing the tab drops it. The toggle only binds or unbinds the existing Leaflet tooltips. It does not call REST.

Object cards follow the edit resolver. A guest card never shows edit or delete. A self-point card shows them to nobody.

## Risks / Trade-offs

- [Open registration creates players before a team exists] → They can sign in and edit a profile, and they see no maps until staff assign them.
- [A signed-in organizer cannot preview the guest view in the same browser] → The link page is the handoff. Preview requires being signed out.
- [Rotating WordPress salts breaks guest links] → Staff open the link page again and share the new URL. No stored tokens to migrate.
- [A self-point meta id can outlive a purged row] → The next press creates a new point. The edit lock checks the live object id, so a dangling id grants nothing.
- [Player marks with a zero time-to-live remain until someone deletes them] → Accepted. Teammates and staff can delete ordinary marks. Self-points still last one minute.
- [Staff-only icons inside a team palette are offered to that team’s players] → The palette is the allowlist. Icon availability does not filter it again.

## Migration Plan

- On the plugin’s next upgrade routine, add the Player role if it is missing. Do not grant it staff capabilities.
- Existing maps start with an empty `tacnav_team_ids` list. Staff add teams before players or guests can open them.
- No geo-table migration. Rollback is code rollback: player and guest routes stop, staff canvas remains, and the new meta can stay unused.

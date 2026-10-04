# Design

## Context

See proposal.md for why. Today the staff canvas is the only mode with Live. `setLive` in `assets/canvas.js` calls `GET /objects` every 5 seconds and `redrawObjects` destroys every Leaflet layer. Players and guests never start that loop. Guest REST calls do not send the page token `t`, and the first object list is only the payload localized with the page. `Geo_Store::update` writes the merged row and does not check that the row is still the one the editor opened. `updated_at` is a one-second datetime. Objects already have stable ids. Empty visibility means every viewer of the map. Expired rows are omitted for players and guests in `list_for_map`.

## Goals / Non-Goals

**Goals:**

- One refresh mechanism for staff, players, and guests.
- Assemble each audience picture once per map version, then let every viewer of that audience reuse it.
- Keep a quiet poll cheap: matching version returns no object list.
- Patch Leaflet layers by id inside the existing canvas script.
- Keep the stored picture replaceable, so a later slice can serve one file per audience without rewriting the canvas protocol.

**Non-Goals:**

- A prop-beacon app, a player mobile app, WebSocket, SSE, or CRDT.
- Installing Leaflet Realtime.
- A delta (upsert/remove) response. The body is the full audience list.
- Changing "Mark myself" so the self-point keeps one id.
- Serving the picture as a static file in this slice. The store below is the seam for that later change.

## Decisions

### Map version is an integer on the map, used as the ETag

Each successful create, update, delete, mark-myself, or purge increments post meta `tacnav_picture_rev` on that map. The canvas sends it as `If-None-Match`. A match answers `304` with no object list. A miss answers `200` with the full list for that viewer and the new ETag.

`updated_at` is not the picture cursor. Two writes in one second would look identical, and a delete does not leave a later timestamp.

Alternative considered: a per-object client inventory. It makes every response personal, so the team cannot share one picture. Rejected.

Alternative considered: a changelog of edits. The list for one team is small enough that sending it whole is simpler and still correct when an object expires or leaves that team's visibility.

### One stored picture per audience per version

Audiences are staff, and one entry per team id listed on the map. Players and guests of team A read the same stored picture. Staff read the staff picture, which includes every object staff may see.

On increment, rebuild the pictures whose membership changed. A team-only object still bumps the map version, so other teams may download their full list once. That extra download is acceptable for one map and a few teams.

Reads and writes go through one picture store. A picture is identified only by map id, audience, and integer version. The audience is `staff`, `staff-expired`, or a team id. The body is the JSON list the canvas already applies. `put` runs after the version increments. `get` is what the refresh route calls. The route must not query or decorate geo rows itself.

The first store is a transient (options table), because the default WordPress object cache does not survive the request. The canvas does not know that. It sends the version and receives either no list or the full list.

A later slice can replace the store with one file per audience: `staff`, `staff-expired`, and one file per team. For two teams that is the staff file plus two team files, and the expired-staff file only if that screen needs it. Nginx is not required. Whatever already serves site files can serve these. The canvas protocol stays the ETag and the list. The expensive mistakes to avoid now are assembling the list inside the REST callback, keying a picture by user id, and giving the poll a different object shape than the page payload.

The page localizes the current version with the objects it already rendered, so the first poll can match and return `304`.

Guest requests add `t` from the page URL. The server resolves the same team as the page. A missing or wrong token is refused and the canvas keeps its markers.

`can_edit` in the team picture is the value for a player of that team. The guest canvas never offers edit, delete, or add. The write routes still refuse a guest.

Alternative considered: write those files now. The read path would skip PHP, which matters when many devices write coordinates every second. While writers are people saving objects, a transient is enough, and files would tie this slice to how the host serves static assets.

### The canvas patches layers; staff Live only gates the loop

Players and guests start the loop when the canvas opens. Staff start it when Live is pressed and stop it when Live is pressed again. The button does not use a different request.

The loop backs off while the tab is hidden, and stretches toward 5–8 seconds after repeated `304`s. After a `200`, the next wait is shorter, about 3 seconds. A failed response does not clear layers.

On `200`, compare the new list to the markers by id. Add, replace, or remove. Leave an unchanged id in place. Do not install a second realtime library. Skip writing into a form that is still open; keep the existing rule that the open object's layer is not rebuilt under the draft.

Staff "hide team" and titles stay in the browser and are reapplied after each picture. "Show expired" is a second staff representation for that screen only (`include_expired`), with its own ETag. It does not change the picture other viewers use.

The client also removes a layer when `expires_at` passes and that screen is not showing expired objects. That covers a quiet map whose version did not move.

### Overlapping saves use the `updated_at` the form opened with

The save request includes the `updated_at` from when the form opened. The update runs as a conditional write: the row changes only if that timestamp is still current. Zero rows means `409`. The canvas leaves the form open. A successful write is what increments `tacnav_picture_rev`.

Alternative considered: merge fields from both editors, or CRDT. A marker should stay one saved object, not a mix of two forms.

Same-second hole: if the first save does not change `updated_at` because the clock second is unchanged, the second conditional write can still match. That is acceptable for two people pressing Save. A per-row integer can close it later if it shows up.

## Risks / Trade-offs

- [Every viewer poll still boots WordPress] → Matching ETag skips the object query and returns `304`. Hidden tabs slow down. Replacing the picture store with files later removes the boot from the read path. The canvas protocol does not change.
- [A write on one team bumps the version for every audience] → Other teams download one full list of their own objects. Lists are small next to map tiles.
- [Guest token is repeated on each refresh] → Same secret as the page URL. It is not placed in a shared cache key that another team can fetch.
- [Team picture includes player `can_edit`] → Guest UI ignores it, and guest writes stay forbidden on the server.
- [`updated_at` is only precise to one second] → Conditional update still stops the usual case, where the first save moves the timestamp.

## Migration Plan

No table change. The revision meta starts missing and is treated as version `0` until the first write after deploy. Old canvases keep working if the new script is not loaded; the new script replaces the 5-second full redraw. Rollback is reverting the plugin files. Orphan transients expire on their own.

## Open Questions

None. Files stay a later store behind the same map, audience, and version. This slice does not choose a web server for them.

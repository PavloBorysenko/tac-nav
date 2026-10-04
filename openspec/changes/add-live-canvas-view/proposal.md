# Proposal

## Why

Players and guests see a map that is current only at the moment the page opened, so marks placed during a game stay invisible until a reload. Organizers already have a Live button, but it refetches and redraws the whole canvas on a timer. The same game needs one shared picture that stays current for a full team without a request per person per second.

## What Changes

- While a published map canvas stays open, a listed player and a guest of that team follow that team's picture. The guest still cannot create, edit, or delete.
- An Organizer or Administrator follows the staff picture only while Live is on. The button starts and stops updates. It uses the same refresh rules as players and guests.
- Each audience has its own picture: staff, and one per team listed on the map. A quiet picture sends no object list. A changed picture sends the full list for that audience. The canvas adds, replaces, or removes markers by object id and leaves unchanged markers in place.
- A failed refresh keeps the last successful picture on the map.
- A player or guest drops an object when its expiry time passes, even when the picture version did not change. Staff filters, including expired objects and hidden teams, stay on that screen and are not part of the shared picture.
- A guest's refresh request repeats the guest token from the page URL.
- When two people who may edit the same object save at once, the first successful save remains. The second save is refused and that person's open form stays open.
- "Mark myself" still replaces the previous self-point with a new object. A future tracker that must keep one id is out of scope.
- No prop-beacon app, no player mobile app, no push channel, and no added map library. Those clients can use this picture later. This change does not build them.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `tacnav-player-canvas`: Players and guests follow the team picture while the canvas is open; organizers follow the staff picture only while Live is on; simultaneous edits keep the first successful save.

## Impact

- `wp-content/plugins/tacnav-maps` canvas page, canvas script, and the geo-object read and update routes. Guest refreshes must send the existing guest token. Staff Live stays a toolbar button and stops redrawing every marker on each tick.
- No new runtime dependency. Leaflet Realtime is not installed. This slice does not write static files. The picture is stored per map, audience, and version behind one store, so a later change can replace that store with one file per audience (staff and each team) without a new canvas protocol.
- Plugin docs that say guests omit Live, and that staff Live refetches on a fixed timer, become stale when this ships.

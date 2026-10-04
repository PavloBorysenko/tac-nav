# Tasks

## 1. Picture version

- [ ] 1.1 Store `tacnav_picture_rev` on the map and increment it only after a successful create, update, delete, mark-myself, or purge. Verify a PHPUnit test shows the next version is one higher and that a refused write does not advance it.
- [ ] 1.2 Assemble one picture per audience (staff, and one per team on the map) for that version. The body matches the object list already localized on the page. Verify a PHPUnit test shows a player and a guest of the same team resolve the same audience key, and staff resolves a different key.
- [ ] 1.3 Put and get those pictures through one store keyed only by map, audience, and version. The first store is a transient. The refresh route calls the store and does not query geo rows itself. Verify a PHPUnit test that a stand-in store supplies the list, so a later file store can replace the transient without a new canvas protocol.

## 2. Refresh

- [ ] 2.1 On `GET /objects`, treat `If-None-Match` as the picture version. Return `304` and no object list when it matches. Return `200` with the full stored list and the new ETag when it does not. Verify a PHPUnit test covers both the match and the miss.
- [ ] 2.2 Localize the current version with the canvas payload so the first poll can match. Accept a guest refresh only when `t` is the page guest token. Verify a PHPUnit test that a missing or wrong token is refused, and `phpcs` plus PHPStan pass on the touched PHP files.
- [ ] 2.3 Keep staff "show expired" as a separate staff representation for that screen. Reapply hidden-team and title choices in the browser after each picture. Verify the staff live request without that filter does not ask for expired rows, and a hidden-team choice does not change the stored picture.

## 3. Overlapping saves

- [ ] 3.1 Send the form's original `updated_at` on update. Change the row only when that timestamp is still current. Return `409` when it is not, and do not increment the picture version. Verify a PHPUnit test for the matching timestamp and the stale timestamp.
- [ ] 3.2 On `409`, leave the open form in place. Verify the canvas save handler does not close the form or clear the map when the update is refused.

## 4. Canvas loop

- [ ] 4.1 Start the refresh loop for a player and a guest when the canvas opens. Start and stop it for staff only from the Live button. Back off while the tab is hidden, stretch toward 5–8 seconds after repeated `304` responses, and wait about 3 seconds after a `200`. Verify a failed refresh does not remove markers, and `npm --prefix tools/js-lint run lint` passes for `assets/canvas.js`.
- [ ] 4.2 Apply a `200` list by id: add, replace, or remove, and leave an unchanged id in place. Do not write the new list into an open form. Remove a player or guest marker when `expires_at` passes even if the version did not change. Verify the Live path no longer calls the old 5-second full `redrawObjects` poll.

## 5. Docs and gates

- [ ] 5.1 Update `wp-content/plugins/tacnav-maps/docs/development.md` so it no longer says guests omit Live or that staff Live refetches and redraws the whole canvas on a fixed timer. Note that the picture is stored per map, audience, and version so a later file per audience can replace the transient. Verify those sentences match the button, the shared picture, and the guest token on refresh.
- [ ] 5.2 Run `phpcs` and PHPStan on the touched PHP files, `php tools/phpunit/vendor/bin/phpunit -c phpunit.xml.dist`, and the JS lint from task 4.1. Verify each command exits successfully.

# Tasks

## 1. Update payload

- [x] 1.1 On update, an absent `ttl_minutes` keeps the stored `expires_at` for staff and for a player, including when the body also sends a later `expires_at`. `ttl_minutes` of 0 clears the expiry. A positive value, including 1, 20, and 30, sets the expiry to that many minutes after the server accepts the save. A create with a preset duration outside that list, such as 5, still stores that duration. Verify a PHPUnit test covers the kept expiry, the ignored client expiry, the cleared expiry, and a duration counted from the save.

## 2. Canvas form

- [x] 2.1 On edit, the time-to-live select starts on "Don't change" with an empty value, and the save omits `ttl_minutes` while that value stays empty. On create, the select offers 0, 1, 3, 10, 20, 30, and 60 and does not offer "Don't change". The remaining-time line keeps showing the expiry of the object that was opened. Add the "Don't change" string next to "Does not expire". Verify the edit save builder does not send `ttl_minutes` for the empty value, and `npm --prefix tools/js-lint run lint -- ../../wp-content/plugins/tacnav-maps/assets/canvas.js` passes.

## 3. Gates

- [x] 3.1 Run `phpcs` and PHPStan on the touched PHP files, then `php tools/phpunit/vendor/bin/phpunit -c phpunit.xml.dist`. Verify each command exits successfully.

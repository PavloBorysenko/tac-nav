# Tasks

## 1. Team roster

- [x] 1.1 Show each player on the team form with a checkbox, a 32px circular avatar from the existing player thumbnail, and the display name, using an empty circle when the thumbnail URL is empty, and verify a player who saved an avatar shows that image beside the name and a player with no avatar shows the empty circle
- [x] 1.2 Add the selected count and a text filter with reset above the list, a no-match message, and a green background for checked rows in the catalog admin stylesheet, and verify that markup is on the team edit screen
- [x] 1.3 Add a roster script on the team edit and new-team screens only that filters names immediately without regard to letter case, resets the text, updates the count for every checked box including hidden ones, and toggles the green class while leaving hidden inputs in the form, and verify typing hides non-matches, reset shows every row, a hidden checked player stays checked, and the map screen does not load this script

## 2. Guest-link page

- [x] 2.1 Render the guest-link document with the active theme header and footer, the handoff in the theme content column, and the copy control as a theme button, and verify an Organizer sees the site header, map name, team name, guest URL, copy control, QR code, and site footer, with the header at the theme header width
- [x] 2.2 Load a stylesheet for the URL field and QR card only, and remove the document's body font and body max-width, and verify the header is not squeezed to the handoff column and a Player is still refused

## 3. QA gates

- [x] 3.1 Run `phpcs --standard=phpcs.xml.dist` and `php tools/phpstan/vendor/bin/phpstan analyse -c phpstan.neon.dist --memory-limit=1G` on the changed plugin PHP and verify both are green
- [x] 3.2 Lint the roster script with `npm --prefix tools/js-lint run lint -- <file>` and verify it is green, reporting prettier or linebreak debt without rewriting the file for that debt
- [x] 3.3 Run `php tools/phpunit/vendor/bin/phpunit -c phpunit.xml.dist` and verify the suite stays green, without adding a test that claims to render the roster filter or the theme chrome
- [x] 3.4 Update an existing plugin doc only when its catalog reading condition matches this roster or guest-link presentation, and verify no new documentation file was added

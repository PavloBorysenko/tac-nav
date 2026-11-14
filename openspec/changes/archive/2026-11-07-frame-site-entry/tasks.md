# Tasks

## 1. Header and footer sheet

- [x] 1.1 Store the supplied topographic sheet in the child theme and add inline CSS on `wp_enqueue_scripts` that paints it with `cover` and `no-repeat` on `header.wp-block-template-part` at the top and `footer.wp-block-template-part` at the bottom, using the theme file URL, and leave background `#D4CBB3` in `theme.json`, and verify a front-end header shows the top of the sheet on khaki, the footer shows the bottom, the content between them does not use the sheet, and no compiled theme `build/` asset is enqueued
- [x] 1.2 Update `wp-content/themes/tacnav/docs/architecture.md` so the header and footer description includes the topographic sheet as well as `#D4CBB3`, and verify no new documentation file was added

## 2. Front page

- [x] 2.1 Add `templates/front-page.html` that uses the existing header and footer template parts and one centered group with two stacked buttons, Organizer then Player, linking to `wp-login.php` and `/tacnav-player/`, with no other content in that group, and center the group from the same inline stylesheet, and verify `/` shows only those two controls between the header and footer for a signed-out visitor and a signed-in visitor, Organizer opens the WordPress login form, and Player opens the player sign-in and registration page

## 3. Player account page

- [x] 3.1 Render the player account document with the guest-link shell: header and footer template parts before `wp_head()`, content in the constrained theme column, theme button class on submits, and no body max-width, for the signed-out forms, a signed-in non-player, and a signed-in player, and verify a signed-out visitor sees the theme header, both forms, and the theme footer at the theme header width
- [x] 3.2 Present the signed-in cabinet as separate profile, team, and map groups, print each teammate's existing avatar URL or an empty circle, and load `assets/player-account.css` on this route only, and verify a teammate with an avatar shows that image beside the name, a teammate without one shows an empty circle, and a player with no team still has the profile group, the unassigned notice, and an empty map list

## 4. QA gates

- [x] 4.1 Run `phpcs --standard=phpcs.xml.dist` and `php tools/phpstan/vendor/bin/phpstan analyse -c phpstan.neon.dist --memory-limit=1G` on the changed theme and plugin PHP and verify both are green
- [x] 4.2 Run `php tools/phpunit/vendor/bin/phpunit -c phpunit.xml.dist` and verify the suite stays green, without adding a test that claims to render the front page or the theme chrome
- [x] 4.3 Update an existing plugin doc only when its catalog reading condition matches this account presentation, and verify no new documentation file was added

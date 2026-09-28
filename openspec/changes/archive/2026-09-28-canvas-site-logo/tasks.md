# Tasks

## 1. Site mark on the canvas

- [x] 1.1 In `Canvas::render()`, read the custom logo attachment and the site front URL, and print one `tacnav-site-mark` link in `templates/canvas.php` for staff, player, and guest when that image exists, using the site name as the accessible name and not `data-tacnav-home`, and verify a canvas whose site has a custom logo shows that image at the bottom-left linking to the site front, and a canvas whose site has no custom logo shows no mark
- [x] 1.2 Style `.tacnav-site-mark` in `assets/canvas.css` at the bottom-left with padding `0.4rem`, `z-index: 500`, a 32px-tall logo, and background `rgba(54, 68, 40, 0.6)`, and limit the toolbar max-width to `calc(100vw - 8.5rem)`, and verify the plate is that color, the mark does not cover zoom, the toolbar, or the Esri attribution, and Center still recenters the map without storing an object

## 2. Documentation

- [x] 2.1 Update `wp-content/plugins/tacnav-maps/docs/development.md` so the canvas chrome description includes the site-logo link, the plate color, and that the mark is omitted when the site has no custom logo, and verify no new documentation file was added

## 3. QA gates

- [x] 3.1 Run `phpcs --standard=phpcs.xml.dist` and `php tools/phpstan/vendor/bin/phpstan analyse -c phpstan.neon.dist --memory-limit=1G` on the changed plugin PHP and verify both are green
- [x] 3.2 Run `php tools/phpunit/vendor/bin/phpunit -c phpunit.xml.dist` and verify the suite stays green, without adding a test that claims to render the canvas document

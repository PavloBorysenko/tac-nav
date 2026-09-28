# Architecture

## Purpose

Define TacNav as the first-party child of Twenty Twenty-Five and the overrides this child owns.

## Responsibilities and boundaries

`style.css` sets `Template: twentytwentyfive`. This child does not copy the parent template or parts tree. Parent templates, patterns, and `theme.json` merge underneath child `theme.json`.

This child overrides header and footer template-part backgrounds to palette slug `khaki` (`#D4CBB3`) via `theme.json` `styles.css` targeting `header.wp-block-template-part` and `footer.wp-block-template-part`. `tacnav_enqueue_chrome` paints `assets/topographic.png` on that color and does not set background repeat, size, or position. Links in the header and footer are heavier, have a thick black underline, and gain a murky background on hover. The content between header and footer does not use the sheet. Those rules live in `style.css`, which `tacnav_enqueue_chrome` loads. The sheet is a relative `url()` in that file. A `url()` in `theme.json` would not resolve to the theme directory. Block `variations` for those areas do not emit CSS in this WordPress version. `templates/front-page.html` is the site front and is not a copy of a parent template. The child does not register post types, taxonomies, or REST routes.

Switching the active theme away from stylesheet `tacnav` removes these overrides. Unmatched templates fall through to Twenty Twenty-Five. `tacnav_load_textdomain` loads text domain `tacnav`.

## Dependencies

Requires the Twenty Twenty-Five parent theme to be installed. Official parent documentation: https://wordpress.org/themes/twentytwentyfive/

## Constraints and side effects

Site Editor user styles for header or footer color override this default. Do not edit files inside `wp-content/themes/twentytwentyfive/`.

## Implementation references

- `wp-content/themes/tacnav/style.css` — child theme header, `Template: twentytwentyfive`, and header/footer chrome
- `wp-content/themes/tacnav/theme.json` — palette `khaki` and `styles.css` for header/footer template-part backgrounds
- `wp-content/themes/tacnav/functions.php` — `tacnav_load_textdomain`, `tacnav_enqueue_chrome`
- `wp-content/themes/tacnav/assets/topographic.png` — header and footer sheet
- `wp-content/themes/tacnav/templates/front-page.html` — site front with Organizer and Player controls

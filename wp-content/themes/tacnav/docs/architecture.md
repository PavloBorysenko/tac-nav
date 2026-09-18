# Architecture

## Purpose

Define TacNav as the first-party child of Twenty Twenty-Five and the overrides this child owns.

## Responsibilities and boundaries

`style.css` sets `Template: twentytwentyfive`. This child does not copy the parent template or parts tree. Parent templates, patterns, and `theme.json` merge underneath child `theme.json`.

This child overrides header and footer template-part backgrounds to palette slug `khaki` (`#D4CBB3`) via `theme.json` `styles.css` targeting `header.wp-block-template-part` and `footer.wp-block-template-part`. Block `variations` for those areas do not emit CSS in this WordPress version. It does not register post types, taxonomies, or REST routes.

Switching the active theme away from stylesheet `tacnav` removes these overrides. Unmatched templates fall through to Twenty Twenty-Five. `tacnav_load_textdomain` loads text domain `tacnav`.

## Dependencies

Requires the Twenty Twenty-Five parent theme to be installed. Official parent documentation: https://wordpress.org/themes/twentytwentyfive/

## Constraints and side effects

Site Editor user styles for header or footer color override this default. Do not edit files inside `wp-content/themes/twentytwentyfive/`.

## Implementation references

- `wp-content/themes/tacnav/style.css` — child theme header and `Template: twentytwentyfive`
- `wp-content/themes/tacnav/theme.json` — palette `khaki` and `styles.css` for header/footer template-part backgrounds
- `wp-content/themes/tacnav/functions.php` — `tacnav_load_textdomain`

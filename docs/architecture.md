# Architecture

## Purpose

Record which WordPress components this repository owns, and which paths are Core or default themes. Agents use this map instead of treating everything under `wp-content/` as first-party.

## Responsibilities and boundaries

This git root is a Local WordPress site. WordPress Core, `wp-config.php`, and generated content are ignored.

The first-party theme is `tacnav`, a child of Twenty Twenty-Five. The first-party plugin is `tacnav-maps`. Default WordPress themes `twentytwentythree`, `twentytwentyfour`, and `twentytwentyfive` remain on disk and are not project-owned. Do not document or edit them as custom components.

`.gitignore` allowlists `wp-content/themes/tacnav/` and `wp-content/plugins/tacnav-maps/`, plus Composer autoload files under that plugin `vendor/` and the guest QR script under `assets/vendor/`. There are no must-use plugins.

`tacnav` is tiny: phpcs applies to its PHP; do not register PHPStan or PHPUnit paths for the theme. `tacnav-maps` is not-tiny: PHPStan and PHPUnit paths include that plugin. Theme JavaScript uses `@wordpress/scripts` inside the theme folder.

Default `twenty*` themes, Core, and any later disk-installed third-party plugins are out of scope unless git tracks them and the user confirms the slug.

## Constraints and side effects

The Local site folder is `tac-nav`. The first-party theme slug is `tacnav`.

Do not infer first-party ownership from a plugin or theme merely existing under `wp-content/`.

## Implementation references

- `.gitignore` — Core, content, and plugin ignore rules plus the `tacnav` theme and `tacnav-maps` plugin allowlists
- `wp-content/themes/tacnav/` — first-party child theme
- `wp-content/plugins/tacnav-maps/` — first-party map hub plugin
- `.cursor/rules/wp-agent-harness-scope.mdc` — edit limit: Core and unconfirmed plugins are off-limits

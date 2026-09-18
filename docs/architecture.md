# Architecture

## Purpose

Record which WordPress components this repository owns, and which paths are Core or default themes. Agents use this map instead of treating everything under `wp-content/` as first-party.

## Responsibilities and boundaries

This git root is a Local WordPress site. WordPress Core, `wp-config.php`, and generated content are ignored.

The first-party theme is `tacnav`, a child of Twenty Twenty-Five. Default WordPress themes `twentytwentythree`, `twentytwentyfour`, and `twentytwentyfive` remain on disk and are not project-owned. Do not document or edit them as custom components.

`.gitignore` allowlists `wp-content/themes/tacnav/`. There is no custom plugin allowlist: `wp-content/plugins/*` stays ignored. `wp-content/plugins/` is empty. There are no must-use plugins.

`tacnav` is tiny: phpcs applies to its PHP; do not register PHPStan or PHPUnit paths until a non-tiny first-party component exists. Theme JavaScript uses `@wordpress/scripts` inside the theme folder.

Default `twenty*` themes, Core, and any later disk-installed third-party plugins are out of scope unless git tracks them and the user confirms the slug.

## Constraints and side effects

The Local site folder is `tac-nav`. The first-party theme slug is `tacnav`.

Do not infer first-party ownership from a plugin or theme merely existing under `wp-content/`.

## Implementation references

- `.gitignore` — Core, content, and plugin ignore rules plus the `tacnav` theme allowlist
- `wp-content/themes/tacnav/` — first-party child theme
- `.cursor/rules/wp-agent-harness-scope.mdc` — edit limit: Core and unconfirmed plugins are off-limits

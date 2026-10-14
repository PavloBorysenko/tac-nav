# Architecture

## Purpose

Record which WordPress components this repository owns, and which paths are Core or default themes. Agents use this map instead of treating everything under `wp-content/` as first-party.

## Responsibilities and boundaries

This git root is a Local WordPress site. WordPress Core, `wp-config.php`, and generated content are ignored.

The first-party theme is `tacnav`, a child of Twenty Twenty-Five. The first-party plugins are `tacnav-maps` and `tacnav-devices`. `tacnav-devices` depends on `tacnav-maps`; the map plugin owns geo objects and catalog data, while the device plugin owns application provisioning, credentials, mailboxes, push handoff, and Android release metadata. Default WordPress themes `twentytwentythree`, `twentytwentyfour`, and `twentytwentyfive` remain on disk and are not project-owned. Do not document or edit them as custom components.

`.gitignore` allowlists `wp-content/themes/tacnav/`, `wp-content/plugins/tacnav-maps/`, and `wp-content/plugins/tacnav-devices/`, plus Composer autoload files under the map plugin `vendor/` and the guest QR script under `assets/vendor/`. Android source lives under `apps/tacnav-mortar-android/`; generated Gradle output, APKs, app bundles, signing stores, local SDK paths, and Firebase service-account credentials are ignored. There are no must-use plugins.

`tacnav` is tiny: phpcs applies to its PHP; do not register PHPStan or PHPUnit paths for the theme. `tacnav-maps` and `tacnav-devices` are not-tiny: PHPStan and PHPUnit paths include both plugins. Theme JavaScript uses `@wordpress/scripts` inside the theme folder.

Default `twenty*` themes, Core, and any later disk-installed third-party plugins are out of scope unless git tracks them and the user confirms the slug.

## Constraints and side effects

The Local site folder is `tac-nav`. The first-party theme slug is `tacnav`.

Do not infer first-party ownership from a plugin or theme merely existing under `wp-content/`.

## Implementation references

- `.gitignore` — Core, content, Android artifact ignores, and first-party theme/plugin allowlists
- `wp-content/themes/tacnav/` — first-party child theme
- `wp-content/plugins/tacnav-maps/` — first-party map hub plugin
- `wp-content/plugins/tacnav-devices/` — first-party application provisioning and messaging plugin
- `apps/tacnav-mortar-android/` — first-party native Android mortar application
- `.cursor/rules/wp-agent-harness-scope.mdc` — edit limit: Core and unconfirmed plugins are off-limits

# Architecture

## Purpose

Record which WordPress components this repository owns, and which paths are Core or default themes. Agents use this map instead of treating everything under `wp-content/` as first-party.

## Responsibilities and boundaries

This git root is a Local WordPress site. The only tracked project file is `.gitignore`. WordPress Core, `wp-config.php`, and generated content are ignored.

On disk the active theme trees are the default WordPress themes `twentytwentythree`, `twentytwentyfour`, and `twentytwentyfive`. Those are not project-owned. Do not document or edit them as custom components.

`.gitignore` allowlists `wp-content/themes/emg-develop/` as the intended custom theme. That directory is not present. There is no custom plugin allowlist: `wp-content/plugins/*` stays ignored. `wp-content/plugins/` is empty. There are no must-use plugins.

Until `emg-develop` (or another confirmed slug) exists and is tracked, there is no first-party theme or plugin for phpcs, PHPStan, tests, or component `docs/` catalogs.

Default `twenty*` themes, Core, and any later disk-installed third-party plugins are out of scope unless git tracks them and the user confirms the slug.

## Constraints and side effects

The Local site folder is `tac-nav`. The allowlisted theme slug is `emg-develop`. This document does not treat those names as the same product; the relationship is unconfirmed.

Do not infer first-party ownership from a plugin or theme merely existing under `wp-content/`.

## Implementation references

- `.gitignore` — Core, content, and plugin ignore rules plus the `emg-develop` theme allowlist
- `.cursor/rules/wp-agent-harness-scope.mdc` — edit limit: Core and unconfirmed plugins are off-limits

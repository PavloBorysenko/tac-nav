# Architecture

## Purpose

Own the TAC Nav map hub: catalog post types, geo-object storage, staff REST, and the front-end canvas. The child theme does not register these objects.

## Responsibilities and boundaries

This plugin registers map, team, and icon post types, the `tacnav_geo_objects` table, Organizer and Player roles, and REST under `tacnav/v1`. The canvas is a rewrite (`tacnav-canvas/{id}`) rendered as a standalone document, not a wp-admin screen. Player registration and the cabinet use `tacnav-player`. A staff guest-link page uses `tacnav-guest-link/{map}/{team}`. Guests open the same canvas URL with query `t`.

Deactivating the plugin removes menus, routes, and rewrite rules after flush. Catalog posts remain in `wp_posts`. The geo table is not dropped on deactivation. Switching the active theme does not disable this plugin.

`TacNav\Maps\Plugin::boot()` registers hooks. Classes live under `app/` with Composer PSR-4 namespace `TacNav\Maps\`. Activation (`TacNav\Maps\Activator`) creates the table, installs capabilities, and flushes rewrites.

## Constraints and side effects

Staff canvas and catalog screens require the plugin to be active and the user to hold map-staff capabilities. A published map also opens for a player whose team is listed on that map, and for an anonymous guest with a valid team token. Organizers cannot manage plugins or themes. Players have `read` only.

## Implementation references

- `wp-content/plugins/tacnav-maps/tacnav-maps.php` — bootstrap, autoload, and activation hooks
- `wp-content/plugins/tacnav-maps/app/Plugin.php` — hook wiring
- `wp-content/plugins/tacnav-maps/app/Activator.php` — table and rewrite flush
- `wp-content/plugins/tacnav-maps/composer.json` — PSR-4 autoload only

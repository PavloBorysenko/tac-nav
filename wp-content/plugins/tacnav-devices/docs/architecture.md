# Architecture

## Purpose

Own TacNav device provisioning and messaging separately from map and geo-object storage.

## Responsibilities and boundaries

The plugin is a first-party, non-tiny component with PSR-4 namespace `TacNav\Devices\` and an `app/` source tree. Its bootstrap only defines constants, loads the autoloader, registers activation, and calls `Plugin::boot()`.

The plugin registers the `tacnav-devices/v1` REST namespace, a capability-protected staff page under the TacNav Maps menu, and versioned provisioning/mailbox tables. Device rows remain after deactivation; only the hourly retention schedule is removed.

## Dependencies

`tacnav-maps` is required and is declared through the WordPress `Requires Plugins` header. Activation stops with a clear error when TacNav Maps did not load, and normal boot shows an administrator notice instead of wiring device hooks when the dependency disappears.

## Constraints and side effects

Deactivating this plugin must not disable `tacnav-maps`. Device functionality is unavailable without this plugin, while existing maps and geo objects remain owned by TacNav Maps.

The plugin stores no plaintext activation code or bearer credential. Release metadata contains only package id, version, and APK URL. Firebase private credentials are read from an external file and never from a WordPress option.

## Implementation references

- `wp-content/plugins/tacnav-devices/tacnav-devices.php` — bootstrap, plugin metadata, autoload, and activation hook
- `wp-content/plugins/tacnav-devices/app/Plugin.php` — dependency-aware hook wiring
- `wp-content/plugins/tacnav-devices/app/Activator.php` — activation guard and schema-version entry point
- `wp-content/plugins/tacnav-devices/app/Plugin.php` — REST, administration, maintenance, and integration hook wiring
- `wp-content/plugins/tacnav-devices/composer.json` — PSR-4 namespace mapping

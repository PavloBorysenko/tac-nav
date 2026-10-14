# Integrations

## Purpose

Define the narrow dependency direction between device identity/messaging and map-owned catalog/geo data.

## TacNav Maps contract

TacNav Devices requires TacNav Maps and uses its catalog classes to validate maps, teams, complete icons, and recipient geo-object map membership. Maps must not depend on TacNav Devices classes.

This plugin owns two stable hooks:

- `tacnav_maps_application_principal` resolves a bearer-authenticated application principal when called with an empty current context and a `WP_REST_Request`.
- `tacnav_application_object_saved` accepts a committed geo-object id and its resolved application context so the object can receive mailbox deliveries.

The concurrent Maps implementation should use those hooks at its REST/service boundary. If this plugin is inactive, Maps should receive no application context and continue its existing staff/player/guest behavior. Open the TacNav Maps [catalog](../../tacnav-maps/docs/catalog.md) only when this integrations row matched.

## Implementation references

- `wp-content/plugins/tacnav-devices/app/Application_Context.php` — producer of the application-principal context
- `wp-content/plugins/tacnav-devices/app/Plugin.php` — hook registration
- `wp-content/plugins/tacnav-devices/app/Mailbox.php` — object-binding consumer

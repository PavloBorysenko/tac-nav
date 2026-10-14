# Provisioning and messaging

## Purpose

Provide one revocable phone identity per staff-managed application and a durable object-addressed mailbox whose state does not depend on push delivery.

## Provisioning contract

Organizer and Administrator access uses the existing TacNav Maps staff capabilities. An application definition has a type, name, reference-settings JSON, allowed maps and teams, and defaults. Defaults must belong to their allowed scopes, and a selected team must be listed on the selected map.

An activation code is random, stored only as a SHA-256 hash, expires after 15 minutes, and is consumed atomically. QR and manual handoff carry the same server URL and code. Successful activation revokes previous installation credentials and FCM registration, then returns the application identity, allowed scopes, defaults, reference settings, service metadata, and a new opaque bearer. Only its hash persists. Reactivation therefore restores the current server reference while invalidating the former phone immediately.

Devices should store the bearer in Android secure storage. Do not place activation codes or bearers in URLs, logs, screenshots, analytics, WordPress options, or support exports. Staff revocation clears the active phone's FCM registration and makes its bearer fail.

The staff page also publishes the current Android package metadata. Its package is always `com.tacnav.mortar`; staff supply only the version and HTTPS APK URL. Signing keys and Firebase service credentials are not release metadata.

## Application context

Authenticated requests use `Authorization: Bearer <opaque credential>`. Current `map_id` and `team_id` may be request parameters or `X-TacNav-Map` / `X-TacNav-Team` headers, but are checked against activated scopes and current map membership on every request. Client-supplied application or installation identity is never trusted.

`Application_Context::filter()` exposes the resolved principal through `tacnav_maps_application_principal`. With no Authorization header it returns `null`, preserving ordinary staff/player/guest access. TacNav Maps preserves `WP_Error` and revalidates/stamps the returned application, map, and team values. After an application-owned geo object commits, Maps should emit `tacnav_application_object_saved` with the object id and its validated principal so this plugin can create or refresh the mailbox binding; the device plugin resolves the current active installation when Maps omits that internal id.

## Mailbox contract

`POST /events` accepts a client event id, type, opaque payload, and recipient object ids. The server stamps source application, source team, selected map, and acceptance time. Every requested object must exist on that map; only objects with a current binding to an active installation receive deliveries. The unique source-application/event/recipient key makes retries idempotent.

`GET /inbox?cursor=<id>` returns pending rows for the authenticated active installation in ascending delivery-id order. The response cursor is the last returned id. `POST /inbox/ack` accepts delivery ids and is idempotent. Acknowledged deliveries do not reappear.

Pending deliveries remain readable for 24 hours. Acknowledged rows may be removed after one hour. Cleanup runs hourly and opportunistically on mailbox traffic. Geo objects and application definitions are never removed by mailbox cleanup. A push attempt occurs only after the database transaction commits; push failure leaves the delivery pending.

## Implementation references

- `wp-content/plugins/tacnav-devices/app/Application_Store.php` — definitions, hashed activation, bearer replacement, revocation, and FCM registration
- `wp-content/plugins/tacnav-devices/app/Application_Context.php` — authenticated scope resolution and Maps filter
- `wp-content/plugins/tacnav-devices/app/Mailbox.php` — event, delivery, cursor, acknowledgement, binding, and retention behavior
- `wp-content/plugins/tacnav-devices/app/REST.php` — device and staff endpoint registration
- `wp-content/plugins/tacnav-devices/app/Admin.php` — protected application, activation handoff, revocation, and APK metadata controls

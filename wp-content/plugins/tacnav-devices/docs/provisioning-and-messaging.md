# Provisioning and messaging

## Purpose

Provide one revocable phone identity per staff-managed application and a durable object-addressed mailbox whose state does not depend on push delivery.

## Provisioning contract

Organizer and Administrator access uses the existing TacNav Maps staff capabilities. An application definition has a type, name, allowed maps and teams, defaults, and reference settings for that type. `App_Types` lists the known types, `mortar` and `object`. A stored value other than `object` is a Mortar application, because staff once typed the type freely. A new record needs a known type, an update keeps its stored value, and a record cannot move between types. Mortar reference settings are (name, non-empty firing and aiming mode sets, radii, elevations, distances, flight, impact TTL, optional 30-second own-position mark on shot (off by default, default map marker, one object updated per later shot), GPS, acoustic fields, and impact icon). Staff choose published maps, teams, and the impact icon from catalog titles, not typed identifiers. Empty firing or aiming sets cannot be saved. Defaults must belong to their allowed scopes, and a selected team must be listed on the selected map. Activation returns those maps and teams with titles. Default GPS limits are 15 metres and 15 seconds. The activation handoff page shows only the QR (server URL plus code), the manual code, and the current APK link.

Object reference settings are object name (the application name when empty), description, icon, destroyed icon, hits to destroy (0 never), two optional sizes in metres, deployment seconds, visibility (`team` or `all`), random area and radius, automatic deactivation distance, GPS accuracy limit (default 15 metres), automatic activation and still seconds, downtime allowance and reset in minutes, and settings PIN (default `4321`). `Validation::object_reference_settings()` refuses a negative number, a PIN under four digits, a random area smaller than the object, automatic activation without seconds, and an automatic deactivation distance under twice the GPS limit. The PIN is a shared game PIN, not an account secret: it stays in the staff-only reference and reaches the phone only through activation.

An activation code is random, stored only as a SHA-256 hash, expires after 15 minutes, and is consumed atomically. The requesting `package_id` must match the application's type, or the activation is refused before the code is consumed and before any phone is revoked. A request without a package is treated as Mortar. QR and manual handoff carry the same server URL and code. Successful activation revokes previous installation credentials and FCM registration, then returns the application identity, allowed scopes, defaults, reference settings, service metadata, and a new opaque bearer. Only its hash persists. Reactivation therefore restores the current server reference while invalidating the former phone immediately.

Devices should store the bearer in Android secure storage. Do not place activation codes or bearers in URLs, logs, screenshots, analytics, WordPress options, or support exports. Staff revocation clears the active phone's FCM registration and makes its bearer fail.

The Device applications staff page has one tab per application, selected by the `app` request parameter. A missing or unknown value opens Mortar. The Mortar tab lists every non-`object` record and the Object tab lists `object` records. Admin actions from the Object tab post `app=object` and return there; Mortar keeps the address it always had. To add an application: add its entry to `App_Types::all()` with a validator in `App_Types::reference_settings()`, add a class implementing `App_Page` under `app/Apps/`, add its template under `templates/apps/`, and list it after Mortar in `App_Pages::all()`.

The Mortar tab publishes the current Android package. Its package is always `com.tacnav.mortar`. Staff upload a built APK (or paste an HTTPS URL) and a version. That upload replaces the previous latest file under `uploads/tacnav-devices/` (not in Git). Players get it from the public page `/tacnav-mortar/` or the front-page **Mortar app** control, then activate with a staff QR or one-time code. Signing keys and Firebase service credentials are not release metadata.

The Object tab publishes `com.tacnav.beacon` the same way and independently: option `tacnav_devices_release_object`, file `tacnav-object.apk`, public page `/tacnav-object/`, download `/tacnav-object.apk`, and the front-page **Object app** control. Mortar keeps option `tacnav_devices_release` and its addresses. The device `/release` route answers for the caller's own type.

## Application context

Authenticated requests use `Authorization: Bearer <opaque credential>`. Current `map_id` and `team_id` may be request parameters or `X-TacNav-Map` / `X-TacNav-Team` headers, but are checked against activated scopes and current map membership on every request. Client-supplied application or installation identity is never trusted.

`Application_Context::filter()` exposes the resolved principal through `tacnav_maps_application_principal`. With no Authorization header it returns `null`, preserving ordinary staff/player/guest access. TacNav Maps preserves `WP_Error` and revalidates/stamps the returned application, map, and team values. After an application-owned geo object commits, Maps should emit `tacnav_application_object_saved` with the object id and its validated principal so this plugin can create or refresh the mailbox binding; the device plugin resolves the current active installation when Maps omits that internal id.

## Mailbox contract

`POST /events` accepts a client event id, type, opaque payload, and recipient object ids. The server stamps source application, source team, selected map, and acceptance time. Every requested object must exist on that map; only objects with a current binding to an active installation receive deliveries. The unique source-application/event/recipient key makes retries idempotent.

`GET /inbox?cursor=<id>` returns pending rows for the authenticated active installation in ascending delivery-id order. The response cursor is the last returned id. `POST /inbox/ack` accepts delivery ids and is idempotent. Acknowledged deliveries do not reappear.

Pending deliveries remain readable for 24 hours. Acknowledged rows may be removed after one hour. Cleanup runs hourly and opportunistically on mailbox traffic. Geo objects and application definitions are never removed by mailbox cleanup. A push attempt occurs only after the database transaction commits; push failure leaves the delivery pending.

## Implementation references

- `wp-content/plugins/tacnav-devices/app/App_Types.php` — what differs per application type: package, release storage, public addresses, and validator
- `wp-content/plugins/tacnav-devices/app/Application_Store.php` — definitions, hashed activation, bearer replacement, revocation, and FCM registration
- `wp-content/plugins/tacnav-devices/app/Application_Context.php` — authenticated scope resolution and Maps filter
- `wp-content/plugins/tacnav-devices/app/Mailbox.php` — event, delivery, cursor, acknowledgement, binding, and retention behavior
- `wp-content/plugins/tacnav-devices/app/REST.php` — device and staff endpoint registration
- `wp-content/plugins/tacnav-devices/app/Admin.php` — staff page shell plus protected application, activation handoff, revocation, and APK metadata controls
- `wp-content/plugins/tacnav-devices/app/Apps/Mortar_Page.php` and `wp-content/plugins/tacnav-devices/templates/apps/mortar.php` — Mortar tab
- `wp-content/plugins/tacnav-devices/app/Apps/Object_Page.php` and `wp-content/plugins/tacnav-devices/templates/apps/object.php` — Object tab

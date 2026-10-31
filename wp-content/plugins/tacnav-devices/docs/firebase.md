# Firebase configuration

## Purpose

Use Firebase Cloud Messaging only as a best-effort wake transport while the WordPress mailbox remains authoritative.

## Configuration

Set `TACNAV_FIREBASE_SERVICE_ACCOUNT_FILE` as a server environment variable or a constant outside the public WordPress configuration tracked by Git. Its value must be an absolute readable path outside `ABSPATH` to a dedicated Firebase service-account JSON file for project `tac-nav`. Grant only the permissions needed to send Firebase Cloud Messaging messages. Do not upload this file to Media Library or place it in the plugin, web root, Android application, backup shared with developers, or a Git-tracked environment file.

The sender uses PHP OpenSSL to sign an RS256 OAuth JWT and WordPress HTTP APIs to exchange it for the `firebase.messaging` scope. Access tokens are transient-cached before expiry. No PHP OAuth dependency is required.

## Wake behavior

The active installation registers or rotates its FCM token through `PUT /tacnav-devices/v1/fcm`; an empty token unregisters it. Reactivation and staff revocation clear the previous registration. A wake is a data-only, Android high-priority message whose only data field is `delivery_ref` (the delivery cursor id); `Firebase::wake_message()` builds it. It has no `notification` block, because Android would display that itself while the app is backgrounded and skip `onMessageReceived`; the app shows the user-visible notification after starting its mailbox sync. The phone must fetch and acknowledge the mailbox row before applying game logic.

Missing credentials, OAuth errors, token rejection, or transport failures do not acknowledge or delete deliveries. A not-found/gone registration response clears that stale token. The mailbox remains available for application-start, network-restoration, foreground-resume, and periodic reconciliation.

## Incident rotation

If the service-account file may have leaked, disable/delete that key in Google Cloud, create a replacement key for the same least-privilege account (or a replacement account), update the external file atomically, and remove the cached `tacnav_firebase_oauth_*` transients. No application bearer needs rotation unless bearer exposure is also suspected. If a bearer is exposed, staff should revoke or reactivate that application; never disclose the old token while investigating.

## Implementation references

- `wp-content/plugins/tacnav-devices/app/Firebase.php` — credential-path guard, OAuth signing, HTTP v1 send, and stale-token cleanup
- `wp-content/plugins/tacnav-devices/app/Application_Store.php` — active-installation FCM registration and revocation
- `wp-content/plugins/tacnav-devices/app/Mailbox.php` — post-commit wake ordering and durable fallback

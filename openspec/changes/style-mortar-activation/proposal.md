# Proposal

## Why

The staff Tac Nav Mortar activation handoff is a bare admin response: white page, unstyled text, and three links that do not look like buttons. Staff read the one-time code and show the QR from this page, so the layout needs to be readable at a glance.

## What Changes

- Present the existing handoff as a centered card on a filled page background, using the same olive and cream palette as the public Mortar download page.
- Render Download APK, Public download page, and Back as buttons.
- Keep the current information: the 15-minute warning, QR, server URL, manual code, package id, version, and those three links.
- Leave activation, QR payload, APK publishing, and the public download page behavior unchanged.

## Capabilities

### New Capabilities

- `tacnav-activation-handoff`: Presentation of the staff activation handoff as a centered, readable card with button links. Does not redefine what data the page shows.

### Modified Capabilities

- None. Archived specs do not cover this page. The in-flight change `add-tacnav-mortar` already requires the handoff to identify the package, version, and APK; that content requirement stays as it is.

## Impact

- `wp-content/plugins/tacnav-devices/templates/activation.php` — card markup, viewport, and the plugin stylesheet instead of admin `common` and `button` classes.
- `wp-content/plugins/tacnav-devices/assets/activation.css` — new page background, card, and button styles.
- `wp-content/plugins/tacnav-devices/app/Admin.php` — enqueue that stylesheet when the handoff is rendered, the same way the public download page enqueues its CSS.
- No REST, credential, or release-metadata changes.

# Proposal

## Why

The Device applications staff page is written for one application, Tac Nav Mortar. Sapper, object, drone, and other applications are planned, and each will need its own settings page. A place for those pages should exist now so that adding one does not mean reworking a Mortar page that already does its job.

## What Changes

- Add a tab bar to the Device applications staff page, with one tab per application. Mortar is the only tab for now.
- Move the current Mortar page under the `mortar` tab. Its settings, activation QR, revocation, and APK release controls behave as they do today.
- Open the Mortar tab by default, so existing links and post-save redirects keep working.
- Give each application a self-contained page behind a small shared contract, so a new application adds a tab without touching the Mortar page.

No sapper, object, or drone page is built in this change, and no public URL, stored data, REST route, or Android behavior changes.

## Capabilities

### New Capabilities

- `tacnav-device-app-pages`: Tabbed staff page that gives each device application its own independent settings page.

### Modified Capabilities

None. The provisioning requirements for the Mortar page are still in the unarchived `add-tacnav-mortar` change, and this change does not alter them.

## Impact

- `wp-content/plugins/tacnav-devices/app/Admin.php`: page rendering becomes a tab shell.
- `wp-content/plugins/tacnav-devices/app/Apps/`: new application page contract, list, and Mortar page.
- `wp-content/plugins/tacnav-devices/templates/admin-page.php`: moves to `templates/apps/mortar.php`.
- `wp-content/plugins/tacnav-devices/tests/`: new tab selection tests.
- `wp-content/plugins/tacnav-devices/docs/`: architecture and provisioning documents.

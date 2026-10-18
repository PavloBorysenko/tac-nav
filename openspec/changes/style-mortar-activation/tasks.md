# Tasks

## 1. Stylesheet

- [x] 1.1 Add `wp-content/plugins/tacnav-devices/assets/activation.css` with page background `#1f2418`, a centered `#f4efe3` card with dark text, a larger monospace manual code, a light QR pad, a filled full-width primary button, and outline buttons of the same size. Verify the stylesheet defines each of those treatments.

## 2. Handoff template

- [x] 2.1 Update `wp-content/plugins/tacnav-devices/templates/activation.php` with a viewport meta, print the activation stylesheet instead of admin `common`, and place the expiry warning, QR, server URL, manual code, package id, and version in the card. Verify the template no longer prints `common` and still outputs those fields.
- [x] 2.2 Present Download APK as the filled primary button and Public download page and Back as outline buttons. When `download_url` is empty, Download APK links to the public APK address and Public download page stays visible. Verify both links are outside any empty-URL condition in the template.

## 3. Enqueue

- [x] 3.1 Enqueue `activation.css` in `Admin::create_activation()` before the template is included, using the same pattern as the public download page. Verify the handoff response includes that stylesheet handle and that phpcs and PHPStan pass on the changed PHP file.

## 4. Handoff check

- [ ] 4.1 Open a staff handoff at desktop width and at phone width. Verify the olive page, light card, readable selectable code, light QR background, and the three buttons match `specs/tacnav-activation-handoff/spec.md`, with no horizontal page scrolling on the phone width.

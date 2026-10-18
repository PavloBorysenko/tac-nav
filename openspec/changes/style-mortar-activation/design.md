# Design

## Context

See `proposal.md` for motivation and `specs/tacnav-activation-handoff/spec.md` for the visible contract.

The handoff is a standalone HTML response from `create_activation()`. The template prints the admin `common` stylesheet and marks the links with admin `button` classes. Those classes are styled by a stylesheet this response never prints, so the page stays white. The only custom rule centers the QR.

The public download page already uses the olive and cream palette in `assets/download.css`, enqueued in `Release::render_download_page()` and printed from `templates/download.php`.

## Goals / Non-Goals

**Goals:**

- Give the handoff its own stylesheet, enqueued the same way as the public download page.
- Use a light card on the olive page so the one-time code and QR stay easy to read.
- Replace the admin button classes with full-width buttons in that card.

**Non-Goals:**

- Restyling the public download page, the staff application screen, or wp-admin.
- Changing activation, the QR payload, or which fields the page shows.

## Decisions

### 1. A dedicated stylesheet, not a larger style block

Add `assets/activation.css` and enqueue it in `create_activation()` before the template is included. The template prints that handle and drops `wp_print_styles( 'common' )`.

A longer `<style>` block in the template would avoid the enqueue, but the download page already keeps presentation in a CSS file printed from the template. Follow that path so the two Mortar pages stay parallel.

Printing the admin `buttons` stylesheet was the other way to make the existing classes look like buttons. That keeps the page on wp-admin chrome and does not produce the card or the shared palette.

### 2. Light card on the olive page

Page background `#1f2418`, card `#f4efe3`, card text `#1f2418`. The card is a centered column, about 32rem wide, with padding. Download APK is a filled `#1f2418` button with cream text. Public download page and Back are outline buttons of the same width and padding, stacked under the primary button.

Reusing `download.css` unchanged would make the whole page dark, including the code. A light card is the readable surface for a code someone has to type. The QR keeps a white pad so it still scans on the cream card.

### 3. Phone width is a viewport meta plus a fluid card

Add the same viewport meta the download template already has. The card uses border-box width, a max width, and horizontal margin so a phone does not scroll sideways. Buttons are block-level and full width of the card.

## Risks / Trade-offs

- [Stylesheet enqueued after the template starts, or not enqueued] → Enqueue in `create_activation()` before `require`, and print only that handle from the template.
- [QR SVG inherits a dark background and becomes hard to scan] → Keep an explicit light background on the QR element.
- [Outline buttons on cream have weak contrast] → Use the same `#1f2418` for the outline and label as the card text.

## Migration Plan

No data migration. Shipping is the template, the new stylesheet, and the enqueue. Rollback is reverting those three files. Existing activation codes and APK metadata stay as they are.

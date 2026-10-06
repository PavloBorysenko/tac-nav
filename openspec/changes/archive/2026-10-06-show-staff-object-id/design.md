# Design

## Context

See proposal.md for why, and the `tacnav-player-canvas` delta for the required card. `openPopup` in `assets/canvas.js` is the object card. It already has the title, description, belonging, remaining time, and, when `can_edit` is set, Edit and Delete. The hydrated geo row includes `id`, and that value is the primary key of `tacnav_geo_objects`. Staff, players, and guests already receive it. `cfg.mode` is `staff` for an Organizer or Administrator, including when they open a guest URL. A self-point has `can_edit` false, so the card opens without Edit or Delete.

## Goals / Non-Goals

**Goals:**

- Print the existing geo-row `id` on the staff object card.
- Leave player and guest cards, the map face, and the edit form without that line.

**Non-Goals:**

- A new column, a new response field, or removing `id` from the player or guest payload. Players need it to update and delete.
- Showing the id on the map face or in the edit form.

## Decisions

### Gate the line on staff mode, not on edit permission

The card adds the id only when `cfg.mode === 'staff'`. `can_edit` stays the gate for Edit and Delete. A self-point and an expired object staff are showing still open this card, and both still show the id.

Alternative considered: show the id only when the viewer can edit. That hides it on a self-point, which staff cannot edit. Rejected.

### Use the id already on the object

The line is the object's `id`, escaped like the other card text, with a localized "ID" label from the canvas strings. No REST or store change.

Alternative considered: a separate lookup by title. Titles are not unique. Rejected.

## Risks / Trade-offs

- [Id shown only next to Edit] → Check `cfg.mode === 'staff'` in the card, including when Edit and Delete are absent.
- [Player payload still contains `id`] → Accepted. This change only prints the line for staff. Stripping `id` from players would break their edits.

## Migration Plan

No table change and no rewrite of existing rows. Deploy the canvas script and the new string. Rollback is restoring the previous card.

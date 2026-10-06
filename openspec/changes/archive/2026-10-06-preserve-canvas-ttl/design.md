# Design

## Context

See proposal.md for why, and the `tacnav-player-canvas` delta for the required behavior. Geo rows store an absolute `expires_at`, not the duration that was chosen. `Geo_Store::update` merges the new payload onto the existing row, so a missing `expires_at` already leaves the column in place. Both the staff and player update paths clear that column when the body contains `ttl_minutes: 0`. The canvas edit form always sends that zero: the saved object has no `ttl_minutes`, the select matches nothing, and the browser shows the first option, "Does not expire". The staff payload also copies a client `expires_at` when `ttl_minutes` is absent. The canvas duration list is 0, 3, 10, and 60. Team presets store any non-negative integer and apply it only at creation. "Mark myself" inserts a new row with `ttl_minutes` 1 and does not open this form.

## Goals / Non-Goals

**Goals:**

- An edit save that does not choose a duration leaves the stored `expires_at` in place for staff and players.
- A chosen duration is computed on the server from the moment that save is accepted.
- The edit control shows that the current expiry is being left alone, so the first option is no longer "Does not expire".

**Non-Goals:**

- Storing the original duration, or selecting a listed minute value that matches time already remaining.
- A custom-minutes field on the canvas, or turning the team preset number field into this list.
- Changing "Mark myself", guest writes, or the live picture protocol.

## Decisions

### The edit control sends no duration until the editor picks one

On edit, the select's first option is "Don't change" with an empty value, and it starts selected. On create, that option is absent and the choices are 0, 1, 3, 10, 20, 30, and 60. A save includes `ttl_minutes` only when the selected value is non-empty. An empty value must not be coerced to 0.

The remaining-time line keeps calling `formatRemaining` on the object that was opened. Changing the select does not rewrite that line. The form still closes after a successful save.

Alternative considered: preselect 3 or 10 from the remaining time. Saving that choice would start a new clock, which is the bug in another shape. Rejected.

Alternative considered: resend the current `expires_at` from the browser. A player could send a later timestamp and extend the mark. Rejected.

### Expiry is derived only from `ttl_minutes`

Positive `ttl_minutes` sets `expires_at` to that many minutes after the server accepts the save. Zero clears it. On update, an absent `ttl_minutes` leaves the merged row's `expires_at` alone, and a client `expires_at` is ignored. On create, an absent `ttl_minutes` stores no expiry.

The server accepts any non-negative integer. A preset of 5 or 120 minutes must still start at creation. The canvas list is only the choices shown in the form.

Alternative considered: allow only 0, 1, 3, 10, 20, 30, and 60 on the server. That would reject preset durations outside the list. Rejected.

### One string, no schema change

Add a canvas string `Don't change` next to the existing "Does not expire". No new column and no backfill. Rows already have the expiry this change preserves.

## Risks / Trade-offs

- [Empty select value coerced with `Number`] → Treat only a non-empty value as a chosen duration. `Number("")` is 0 and would clear the expiry again.
- [Staff update still copies client `expires_at`] → Stop reading that field. The merge already keeps the stored expiry when the payload omits it.
- [Editor changes the select and still sees the old countdown] → Accepted. The new clock starts at save, and the form closes when the save succeeds.
- [A listed duration cannot restore an off-list preset exactly] → Accepted. Leaving the control unchanged keeps that expiry. Picking a listed value starts that listed duration.

## Migration Plan

No table change and no rewrite of existing rows. Deploy the plugin files. Rollback is restoring the previous form and the previous update payload. Expiries written under the new rule stay absolute timestamps either way.

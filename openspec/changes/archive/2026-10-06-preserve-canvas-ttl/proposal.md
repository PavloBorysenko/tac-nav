# Proposal

## Why

Saving an existing geo object clears a limited time-to-live. The edit form always submits "does not expire", so a mark created for three minutes becomes permanent when someone saves a title or a move two minutes later. The remaining time should keep running until the editor explicitly picks a new duration.

## What Changes

- On edit, the time-to-live control starts on "Don't change". Saving while that choice stays selected keeps the stored expiry, including the time already elapsed. An object created for three minutes and saved two minutes later still has one minute left.
- Choosing a duration on edit sets the expiry to that many minutes after the successful save. Choosing "Does not expire" clears the expiry. Closing the form without saving leaves the stored expiry as it was.
- While the edit form is open, the "Visible for" line keeps counting the stored expiry, even if the control was already changed. An object that has already expired stays expired when the control is left on "Don't change".
- The canvas duration list is 0, 1, 3, 10, 20, 30, and 60 minutes. Labels stay bare numbers, plus "Does not expire" and "Don't change".
- Creating an object, including a quick-add preset, still starts the clock at creation. A preset duration that is not in the canvas list is kept on edit until someone picks a listed value. The team preset field stays a free number. The canvas does not gain a custom-minutes field.
- "Mark myself" still creates a new point that expires one minute after creation. Guests still cannot write.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `tacnav-player-canvas`: Editing an object keeps the remaining time-to-live unless the editor picks a new duration or no expiry. The canvas duration choices include 1, 20, and 30 minutes.

## Impact

- `wp-content/plugins/tacnav-maps` canvas script and the time-to-live string passed to that script. Create and update of geo objects already store an absolute expiry; an update that omits `ttl_minutes` must keep the stored expiry for both staff and players.
- Team preset admin, guest tools, and "Mark myself" stay as they are.

# Proposal

## Why

Scenario setup needs the database id of a mark on the map. Organizers and Administrators can open the object card, but that card does not show the id of the geo-object row, so they cannot look the mark up without another tool.

## What Changes

- When an Organizer or Administrator opens an object card, the card shows that object's geo-row id. The id is the one stored for that mark and is enough to find the row.
- The id appears for every object the staff card can open, including a self-point and an expired object staff are still showing.
- A player card and a guest card do not show the id.
- The id is not drawn on the map face and is not added to the edit form.
- No new request and no new stored field. The id is already on the object the canvas has.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `tacnav-player-canvas`: The staff object card shows the geo-object id. Player and guest cards do not.

## Impact

- `wp-content/plugins/tacnav-maps` canvas object card. Staff, player, and guest already receive the object id with the picture. This change only prints it for the staff card.

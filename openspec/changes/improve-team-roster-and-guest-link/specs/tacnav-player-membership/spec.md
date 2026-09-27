# Spec Delta

## ADDED Requirements

### Requirement: Team form shows who is selected

On the team form, each Player row SHALL show that player's name and the avatar that player saved. When the player has no avatar, the row SHALL show an empty circle in the same avatar slot. A checked row SHALL have a green background. An unchecked row MUST NOT have that green background.

Above the player list, the form SHALL show how many players are checked and a text filter with a reset control. The count SHALL include checked players the filter is hiding, and SHALL change as soon as a checkbox changes.

The filter SHALL hide rows whose names do not contain the typed text, immediately in the browser and without regard to letter case. Reset SHALL clear the text and show every player row. Hiding a row MUST NOT clear its checkbox. When no name matches, the form SHALL say that no players match.

Saving the team SHALL assign every checked player, including players hidden by the filter, under the existing one-team assignment rules.

#### Scenario: Avatar sits beside the name

- **WHEN** an Organizer opens a team and player Anna has saved an avatar
- **THEN** Anna's row shows that avatar beside her name

#### Scenario: Missing avatar keeps the slot

- **WHEN** an Organizer opens a team and player Boris has no avatar
- **THEN** Boris's row shows an empty circle in the avatar slot

#### Scenario: Checked row is green

- **WHEN** an Organizer checks a player on the team form
- **THEN** that row has a green background and the selected count increases by one

#### Scenario: Unchecked row loses the green background

- **WHEN** an Organizer unchecks a player on the team form
- **THEN** that row has no green background and the selected count decreases by one

#### Scenario: Filter hides a checked player without unchecking

- **WHEN** an Organizer has Anna checked, types text that Anna's name does not contain, and saves the team
- **THEN** Anna's row is hidden while the text is present, Anna stays checked, the selected count still includes Anna, and after save Anna belongs to that team

#### Scenario: Filter matches regardless of letter case

- **WHEN** an Organizer types "ann" and a player is named Anna
- **THEN** Anna's row stays visible

#### Scenario: Reset shows every player

- **WHEN** an Organizer has filtered the player list and uses reset
- **THEN** the filter text is empty and every player row is visible again

#### Scenario: No matching name

- **WHEN** an Organizer types text that no player name contains
- **THEN** the form says that no players match and the selected count is unchanged

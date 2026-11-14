# Spec Delta

## ADDED Requirements

### Requirement: Player account page uses the site header and footer

The player account page SHALL render the active theme header above its content and the active theme footer below it, for a signed-out visitor, for a signed-in user who is not a player, and for a signed-in player. Sign-in, registration, and the cabinet SHALL sit in the theme content column between that header and footer. The header and footer SHALL keep the theme's full width.

#### Scenario: Signed-out page has site chrome

- **WHEN** a signed-out visitor opens the player account page
- **THEN** the theme header is above the sign-in and registration forms, the theme footer is below them, and both span the theme header width

#### Scenario: Cabinet has site chrome

- **WHEN** a signed-in player opens the cabinet
- **THEN** the theme header is above the cabinet, the theme footer is below it, and both span the theme header width

### Requirement: Cabinet groups profile, team, and maps

The signed-in cabinet SHALL present the profile editor, the team, and the map list as separate groups. Each other player on the team SHALL be shown with their name and the avatar that player saved. When that player has no avatar, the group SHALL show an empty circle in the avatar slot. A player with no team SHALL still see the profile group, SHALL see that no team is assigned, and SHALL see no maps.

#### Scenario: Teammate avatar sits beside the name

- **WHEN** a player on team Blue opens the cabinet and teammate Anna has saved an avatar
- **THEN** the team group shows Anna's avatar beside her name

#### Scenario: Missing teammate avatar keeps the slot

- **WHEN** a player opens the cabinet and teammate Boris has no avatar
- **THEN** the team group shows an empty circle beside Boris's name

#### Scenario: Unassigned player still has the profile group

- **WHEN** a player with no team opens the cabinet
- **THEN** the profile group is shown, the team group says that no team is assigned, and the map list is empty

## Purpose

Lets a person register as a player, receive one team from staff, and see that team and its published maps in a personal cabinet.

## ADDED Requirements

### Requirement: Player role has no staff access

The system SHALL provide a Player role. A Player MUST NOT receive catalog, geo-staff, canvas-staff, or purge capabilities, and MUST NOT be able to manage plugins, themes, or site settings.

#### Scenario: New player is not staff

- **WHEN** a person completes player registration
- **THEN** the account has the Player role and cannot open catalog screens or the staff tool set

### Requirement: A player can register without a team

The player sign-in page SHALL offer registration. The form SHALL collect email, password, and nickname and SHALL create a Player with no team.

#### Scenario: Registration leaves the team empty

- **WHEN** a visitor submits a valid email, password, and nickname on the registration form
- **THEN** they can sign in as a Player and have no team

### Requirement: Staff assign one global team

An Organizer or Administrator SHALL assign a Player to one team from the team form, choosing only users with the Player role. A player MUST belong to at most one team. Assigning a player who already belongs to another team SHALL move that membership to the team being saved.

#### Scenario: Assignment sets the only team

- **WHEN** an Organizer adds a player with no team to team Blue
- **THEN** that player’s team is Blue

#### Scenario: Assignment replaces the previous team

- **WHEN** an Organizer adds a player who belongs to team Red while saving team Blue
- **THEN** the player’s team is Blue and is no longer Red

### Requirement: Moving a player leaves old objects in place

Objects a player created before a team change SHALL keep their previous belonging and visibility. After the move, that player MUST NOT be allowed to edit or delete those objects.

#### Scenario: Red marks stay red

- **WHEN** a player who created objects for team Red is assigned to team Blue
- **THEN** those objects still belong to Red and the player cannot edit or delete them

### Requirement: Player cabinet shows profile, team, and published maps

A signed-in Player SHALL have a front-end cabinet where they can change avatar and nickname. The cabinet SHALL show the team name and the other players on that team. It SHALL list only published maps that include the player’s team, with links to those maps. A player with no team SHALL still be able to change avatar and nickname, SHALL see that no team is assigned, and SHALL see no maps.

#### Scenario: Assigned player sees team maps

- **WHEN** a player on team Blue opens the cabinet and Blue is listed on a published map
- **THEN** the cabinet shows Blue, Blue’s other players, and a link to that map

#### Scenario: Unassigned player has no maps

- **WHEN** a player with no team opens the cabinet
- **THEN** they can edit avatar and nickname and the map list is empty

### Requirement: Deleting a team clears its players

Deleting a team SHALL remove that team from every player who belonged to it. Those players SHALL become players with no team.

#### Scenario: Players survive as unassigned

- **WHEN** staff delete team Red and a player belonged only to Red
- **THEN** that player remains a Player and has no team

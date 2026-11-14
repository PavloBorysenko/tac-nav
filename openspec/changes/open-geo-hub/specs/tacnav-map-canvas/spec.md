# Spec Delta

## ADDED Requirements

### Requirement: Staff choose an object's purpose

For a circle or polygon, the staff inspector SHALL show a Purpose control. It SHALL offer the ordinary role and each other role that staff may assign to that kind; in this change these are Object and Minefield. A new object SHALL start as ordinary. Saving SHALL store the chosen role.

A role that staff may not assign, such as `hint`, MUST NOT be offered. When the edited object already has such a role, the inspector SHALL show it as read-only, and saving SHALL keep it.

Markers and polylines SHALL show no Purpose control and SHALL stay ordinary. The player inspector MUST NOT show a Purpose control.

#### Scenario: Staff mark a polygon as a minefield

- **WHEN** an Organizer draws a polygon, chooses Minefield, and saves
- **THEN** the polygon is stored with role `minefield`

#### Scenario: Marker has no purpose

- **WHEN** an Administrator opens the inspector for a marker
- **THEN** no Purpose control is shown

#### Scenario: Hint area keeps its role on a staff edit

- **WHEN** an Organizer edits the title of a circle with role `hint`
- **THEN** the inspector shows the purpose as read-only and the saved circle keeps role `hint`

#### Scenario: Players never choose a purpose

- **WHEN** a player draws a polygon
- **THEN** the player inspector shows no Purpose control and the polygon is stored as ordinary

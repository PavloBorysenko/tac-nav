## Purpose

Records which global teams take part in a map and gives staff a guest link for each of those teams on a published map.

## ADDED Requirements

### Requirement: A map lists its teams

A map SHALL store the global teams taking part in it. The list MAY be empty. Teams that are not on the list MUST NOT appear as belonging, visibility, or team-filter choices while staff edit objects on that map.

#### Scenario: Inspector lists only the map’s teams

- **WHEN** a map lists Blue and Red and staff add an object on that map
- **THEN** belonging and visibility choices are Blue and Red only

#### Scenario: A team outside the list is not offered

- **WHEN** team Green exists in the catalog and is not listed on the map
- **THEN** staff cannot select Green for a new object on that map

### Requirement: Removing a team from a map keeps objects

Removing a team from a map’s list SHALL NOT delete geo objects. Players of that team and guests using that team’s link SHALL lose access to the map. Deleting the team record itself SHALL still delete objects whose belonging is that team or whose visibility list includes that team.

#### Scenario: Unlisting a team closes access and keeps marks

- **WHEN** staff remove Blue from a map that already has objects belonging to Blue
- **THEN** those objects remain stored and a Blue player can no longer open the map

#### Scenario: Deleting the team still deletes its objects

- **WHEN** staff delete the Blue team record
- **THEN** objects that belong to Blue or list Blue in visibility are deleted

### Requirement: Published maps have a staff guest-link page

For each team listed on a published map, staff SHALL be able to open a page that shows the map name, the team name, the guest URL, a control that copies that URL, and a QR code of that URL. The page SHALL open from the map’s team list. A user who is not an Organizer or Administrator MUST NOT open that page. A draft map MUST NOT provide a guest URL that opens the map.

#### Scenario: Organizer opens the link page

- **WHEN** an Organizer chooses the guest-link control for Red on a published map
- **THEN** a page shows that map name, Red, the guest URL, copy, and a QR code of the URL

#### Scenario: A player cannot open the link page

- **WHEN** a Player requests the guest-link page for a map
- **THEN** the system refuses access and does not reveal the guest URL

### Requirement: Guest token names one team on one map

A guest URL SHALL carry the team id and a short signature bound to that map. The system SHALL accept it only when the signature matches and that team is still listed on that map. A URL with a changed team id or a signature for a different map MUST be refused.

#### Scenario: Valid token matches the listed team

- **WHEN** a guest opens a published map with a valid token for Blue and Blue is on that map
- **THEN** the system treats the viewer as a guest of Blue

#### Scenario: Removed team invalidates the token

- **WHEN** a guest opens a map with a previously valid Blue token after Blue was removed from that map
- **THEN** the system does not show the map’s objects

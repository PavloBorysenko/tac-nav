# tacnav-player-canvas Specification

## Purpose
Opens a published map to a player of a listed team and to a guest of that team, and lets the player add objects and mark their own position without giving the guest a way to write.

## Requirements

### Requirement: Only staff, listed players, and valid guests can open a map

A published map SHALL open for an Organizer or Administrator, for a signed-in Player whose team is listed on that map, and for an anonymous visitor with a valid guest token for a team listed on that map. A draft map SHALL open for an Organizer or Administrator only. The plain map URL SHALL show no objects to an anonymous visitor who has no valid token. A signed-in Player whose team is not listed, including a player with no team, SHALL see no objects even if the request includes a guest token. A signed-in Organizer or Administrator who opens a guest URL SHALL get the staff canvas.

#### Scenario: Listed player opens the plain URL

- **WHEN** a signed-in player of Blue requests a published map that lists Blue
- **THEN** the canvas loads that map for Blue

#### Scenario: Anonymous visitor without a token sees nothing

- **WHEN** an anonymous visitor requests a map URL with no guest token
- **THEN** the system returns no geo objects

#### Scenario: Signed-in player of another team sees nothing

- **WHEN** a signed-in player of Red requests a map that does not list Red, and the request includes a valid guest token for Blue
- **THEN** the system returns no geo objects

#### Scenario: Staff opening a guest URL keep the staff canvas

- **WHEN** a signed-in Organizer opens a guest URL for a map they may manage
- **THEN** the canvas includes the staff tools

### Requirement: Empty visibility includes every viewer of the map

An object with an empty visibility list SHALL be visible to staff, to every player whose team is listed on that map, and to every guest of a listed team, unless a visibility hook denies it. An object whose visibility list names teams SHALL be visible to staff and to players and guests of those named teams only. Expired objects MUST NOT be returned to players or guests.

#### Scenario: Neutral object is visible to another team’s guest

- **WHEN** a guest of Blue requests a published map that lists Blue and an unexpired object has empty visibility
- **THEN** that object is included

#### Scenario: Red-only object is hidden from Blue

- **WHEN** a player of Blue requests a map and an object’s visibility list is only Red
- **THEN** that object is not included

### Requirement: Titles are a shared toolbar control

Organizers, Administrators, players, and guests SHALL each have a titles control on the toolbar, separate from the staff filter sheet. Turning it on SHALL show the title of each visible object on the map face. Turning it off SHALL remove those map-face titles. The object card SHALL still show the title when the control is off. The control MUST NOT create, update, or delete a geo object and MUST NOT change which objects are returned. The choice SHALL persist for the rest of that browser tab, including a reload, and SHALL start off when the tab has no saved choice.

#### Scenario: A guest can show titles

- **WHEN** a guest turns the titles control on
- **THEN** visible objects that have a title show that title on the map face

#### Scenario: The object card keeps the title

- **WHEN** a player views an object while the titles control is off
- **THEN** the map face does not show that title and the object card does

#### Scenario: Reload keeps the choice

- **WHEN** an Organizer turns titles on and reloads the map in the same browser tab
- **THEN** titles are still shown on the map face

#### Scenario: A new tab starts with titles off

- **WHEN** a player opens the map in a browser tab that has no saved titles choice
- **THEN** titles are not shown on the map face

### Requirement: A guest sees the team picture and cannot write

A guest of a team SHALL receive the same objects a player of that team would receive, including that team’s player-made objects and self-points. A guest MUST NOT create, edit, or delete an object, and MUST NOT have mark-myself. Guest tools SHALL be coordinates, measure, center, “on me”, and the shared titles control. Center SHALL move the view to the map’s stored center and zoom and MUST NOT save that view. “On me” SHALL center the view on the device position when the browser provides it and MUST NOT create or update an object. Coordinates and measure MUST NOT create an object.

#### Scenario: Guest sees a teammate mark

- **WHEN** a guest of Blue requests a map where a Blue player has placed an unexpired object
- **THEN** that object is included and the guest is not offered edit, delete, or add

#### Scenario: On me does not save

- **WHEN** a guest uses “on me” and the browser provides a position
- **THEN** the view moves to that position and no geo object is stored

### Requirement: A player can draw for their team only

A player SHALL be able to add a marker, circle, polyline, or polygon and SHALL be able to use the team’s quick-add presets above those kinds. A marker preset SHALL be stored at the tapped point with the preset’s icon, title, description, and time-to-live. A circle, polyline, or polygon preset SHALL use the geometry the player draws and the preset’s icon, title, description, and time-to-live. A preset MUST NOT supply coordinates, a radius, or vertices. Without a preset, the player SHALL choose kind, title, description, and time-to-live, and an icon only from the icons an Organizer or Administrator added to that team’s palette. The icon library outside that list MUST NOT be offered. The server SHALL store origin team, belonging as the player’s team, visibility as that team only, and color from that team, and SHALL reject an icon that is not on that team’s palette. It SHALL ignore client values that name another team, empty visibility, or staff origin. A time-to-live of zero SHALL store no expiry. Teammates SHALL be able to edit and delete these objects. The player MUST NOT be able to edit or delete a staff-origin object.

#### Scenario: Marker preset saves at the tap

- **WHEN** a Blue player taps the map with a Blue marker preset
- **THEN** a marker is stored at that point with the preset’s icon, title, description, and time-to-live, belonging to Blue and visible only to Blue

#### Scenario: Circle radius comes from the drawing

- **WHEN** a player uses a circle preset and sets a center and radius on the map
- **THEN** the stored circle uses that center and radius and does not take a radius from the preset

#### Scenario: Zero minutes does not expire

- **WHEN** a player saves an object with a time-to-live of zero minutes and the listing is requested later
- **THEN** that object is still included

#### Scenario: An icon outside the team palette is rejected

- **WHEN** a Blue player submits an object whose icon exists in the library but was not added to Blue’s palette
- **THEN** the system refuses to store that icon

#### Scenario: Staff-origin object stays locked

- **WHEN** a Blue player requests edit of a staff-origin object that belongs to Blue
- **THEN** the system refuses the edit

### Requirement: Mark myself replaces one short-lived point

A player SHALL have “Mark myself”, separate from “on me”. Each press SHALL store one marker for that player on that map and SHALL delete that player’s previous self-point on that map, including a self-point that has already expired. Other objects the player created MUST remain. The self-point SHALL show the author’s avatar, or the default marker when the author has no avatar, SHALL use the team color, SHALL be visible only to that team, SHALL use the author’s nickname as its title, and SHALL expire one minute after creation. No viewer, including the author, teammates, and staff, SHALL be offered edit or delete for that object. After one minute it MUST NOT be returned to players or guests. Staff purge of expired objects on that map SHALL delete it. If the browser does not provide a position, the press MUST NOT create or delete a self-point.

#### Scenario: Second press leaves a single self-point

- **WHEN** a player uses “Mark myself” twice on the same map within a minute
- **THEN** only the second self-point remains and the player’s other markers remain

#### Scenario: Nobody can edit the self-point

- **WHEN** an Organizer or a teammate opens the self-point
- **THEN** the card does not offer edit or delete

#### Scenario: The team guest can see the self-point

- **WHEN** a guest of Blue requests the map during the minute after a Blue player marks themselves
- **THEN** that self-point is included and the guest cannot change it

#### Scenario: Missing position writes nothing

- **WHEN** a player uses “Mark myself” and the browser provides no position
- **THEN** no self-point is created and the previous self-point, if any, remains

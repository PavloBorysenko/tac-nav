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

### Requirement: Canvas shows the site logo

When the site has a custom logo, the canvas SHALL show that logo at the bottom-left for an Organizer, an Administrator, a listed player, and a guest. The logo SHALL be a link to the site front in the same tab, and SHALL sit on a plate whose background is `rgba(54, 68, 40, 0.6)`. The mark MUST NOT move the map view, MUST NOT create, update, or delete a geo object, and MUST NOT replace the Center control. The mark MUST NOT cover the zoom control, the bottom toolbar, or the tile attribution. When the site has no custom logo, the canvas MUST NOT show the mark.

#### Scenario: Staff see the logo

- **WHEN** an Organizer opens a map and the site has a custom logo
- **THEN** the bottom-left of the canvas shows that logo on a plate with background `rgba(54, 68, 40, 0.6)`, linking to the site front

#### Scenario: A player sees the logo

- **WHEN** a listed player opens a published map and the site has a custom logo
- **THEN** the bottom-left of the canvas shows that logo linking to the site front

#### Scenario: A guest sees the logo

- **WHEN** a guest opens a map and the site has a custom logo
- **THEN** the bottom-left of the canvas shows that logo linking to the site front

#### Scenario: No custom logo omits the mark

- **WHEN** a listed player opens a published map and the site has no custom logo
- **THEN** the canvas does not show the site-logo mark

#### Scenario: Center still recenters the map

- **WHEN** a guest uses Center while the site-logo mark is visible
- **THEN** the view moves to the map’s stored center and zoom, and the mark does not move the view or store a geo object

### Requirement: Players and guests follow the team picture

While a published map canvas stays open, a listed player and a guest of a listed team SHALL keep the objects for that team current. The player and the guest of the same team SHALL be shown the same objects. The guest MUST NOT be offered a way to create, edit, or delete an object, and a guest save MUST be refused. A guest refresh SHALL be accepted only with the guest token of that page. A refresh that fails, or that reports no new picture, MUST leave the markers already shown in place.

#### Scenario: A player sees a teammate's new object without reloading

- **WHEN** a Blue player has the map open and another Blue player saves a new unexpired object visible to Blue
- **THEN** the first player's map shows that object without a manual reload

#### Scenario: A guest sees the same team picture and still cannot write

- **WHEN** a guest of Blue has the map open and a Blue player saves a new unexpired object visible to Blue
- **THEN** the guest's map shows that object and the guest is not offered edit, delete, or add

#### Scenario: A guest refresh without the page token is refused

- **WHEN** a refresh for the guest canvas is sent without the guest token from that page
- **THEN** the refresh is refused and the markers already shown stay in place

#### Scenario: A failed refresh keeps the current markers

- **WHEN** a player or guest canvas fails to refresh
- **THEN** the markers already shown stay in place

### Requirement: Organizer live uses the same picture update

An Organizer or Administrator SHALL refresh the staff picture only while Live is on. Turning Live off SHALL stop later refreshes and SHALL leave the markers already shown in place. While Live is on, a new picture SHALL add, replace, and remove staff markers by the same rules as a player or guest picture. The staff picture SHALL include the objects that staff may see. Hiding a team, showing expired objects, and the titles control on that screen MUST NOT change which objects are returned to other viewers and MUST NOT by themselves change the picture version.

#### Scenario: Live off does not pick up a new object

- **WHEN** an Organizer has the map open with Live off and a player saves a new object
- **THEN** the organizer's map does not add that object until Live is turned on or the page is loaded again

#### Scenario: Live on shows a new object

- **WHEN** an Organizer turns Live on and a player then saves a new unexpired object
- **THEN** the organizer's map shows that object without a manual reload

#### Scenario: Turning Live off leaves the markers

- **WHEN** an Organizer turns Live off after markers are on the map
- **THEN** those markers stay until the organizer leaves the page or turns Live on again

#### Scenario: A local staff filter does not change another viewer's picture

- **WHEN** an Organizer hides one team on the staff canvas
- **THEN** a player of another listed team still receives the objects that team may see

### Requirement: A new picture updates markers by identity

When a canvas applies a new picture, it SHALL add an object whose id was not shown, replace an object whose id is shown but whose returned contents differ, and remove an object whose id is no longer in that picture. An object whose id and returned contents are unchanged MUST be left as shown. The picture SHALL be the full list for that viewer, not a partial list of edits. Applying a picture MUST NOT replace the fields of a form that is still open for an object.

#### Scenario: A changed object is updated and an unchanged object stays

- **WHEN** a canvas applies a picture in which one shown object has a new title and another shown object is unchanged
- **THEN** the map shows the new title on the first object and the second object remains as it was

#### Scenario: An object removed from the picture disappears

- **WHEN** a canvas applies a picture that no longer includes an object id it was showing
- **THEN** that marker is removed and the other markers remain

#### Scenario: An open form is not overwritten

- **WHEN** a player has an object's form open and a new picture includes newer contents for that object
- **THEN** the open form keeps the values the player has not yet saved

### Requirement: An unchanged picture does not send the object list

A refresh SHALL carry the picture version the canvas last applied. When that version is still current for the viewer, the response MUST NOT include the object list. When the version is no longer current, the response SHALL include the full object list that viewer may see.

#### Scenario: Nothing has changed

- **WHEN** a player refreshes and no object on that map has been created, updated, or deleted since the version the canvas last applied
- **THEN** the response does not include the object list and the markers stay as shown

#### Scenario: Something has changed

- **WHEN** a player refreshes after an object visible to that player was saved
- **THEN** the response includes the full object list that player may see

### Requirement: Expiry can remove a marker without a new picture version

A player or guest canvas SHALL remove an object when its expiry time passes, even if the picture version has not changed. An Organizer with expired objects hidden SHALL hide it the same way. An Organizer with expired objects shown SHALL keep showing it until a picture that staff may see no longer includes it.

#### Scenario: A player drops an expired object while the version is unchanged

- **WHEN** a player is showing an object and its expiry time passes without another save on that map
- **THEN** the player's map removes that object

#### Scenario: Staff can keep showing an expired object

- **WHEN** an Organizer has expired objects shown and an object's expiry time passes without another save
- **THEN** the organizer's map still shows that object

### Requirement: The first successful save of an object wins

When a viewer saves an object that someone else has successfully saved since this form was opened, the system SHALL keep the earlier successful save, refuse the later save, and leave the later editor's form open. A save SHALL succeed when the object has not changed since that form was opened. The refusal MUST NOT discard the earlier save.

#### Scenario: The second overlapping save is refused

- **WHEN** two teammates open the same object, the first saves a new title, and the second then saves a different title
- **THEN** the stored title is the first teammate's title, the second save is refused, and the second teammate's form stays open

#### Scenario: A save of the current object succeeds

- **WHEN** a player opens an object and saves a new title before anyone else saves that object
- **THEN** the stored title is the title that player saved

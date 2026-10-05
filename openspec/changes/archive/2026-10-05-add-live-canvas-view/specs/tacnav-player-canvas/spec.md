# Spec Delta

## ADDED Requirements

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

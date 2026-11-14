# Spec Delta

## RENAMED Requirements

- FROM: `### Requirement: Search returns raw object geometry`
- TO: `### Requirement: Search returns whole stored objects`

## REMOVED Requirements

### Requirement: Applications can change only their own objects

**Reason**: The site is a hub, and which objects a device changes is a game decision that belongs to that device. Tac Nav Sapper must reveal and clear minefields placed by staff and by other phones.

**Migration**: Replaced by "Applications can change any object on an allowed map". Existing applications already change only their own objects, so they keep working unchanged.

## MODIFIED Requirements

### Requirement: An activated application can create map objects

An application with a valid credential SHALL be able to create a marker, circle, polyline, or polygon on its selected allowed map. The server SHALL stamp the authenticated application id, selected team, and map context rather than trusting client-supplied identity fields. The selected team SHALL become the object's belonging at creation. The application MAY choose any existing catalog icon and a visibility list valid for that map, including an empty list meaning visible to every map viewer; it MUST NOT be restricted to the selected team's player palette. The application MAY set a registered role that the object's kind allows; without one the object SHALL be ordinary.

#### Scenario: Mortar creates a public impact marker

- **WHEN** an activated mortar creates a marker on its allowed map with empty visibility and an existing impact icon
- **THEN** the server stores an application-origin object belonging to the selected team and visible to every viewer of that map

#### Scenario: Identity fields cannot be forged

- **WHEN** an application request names another application id, map, or team in identity fields
- **THEN** the server ignores or refuses those values and does not attribute the object to that identity

#### Scenario: Missing icon falls back

- **WHEN** an application creates an object with an icon id that no longer exists
- **THEN** the object is stored with the default-marker fallback

#### Scenario: Application creates a minefield

- **WHEN** an activated application creates a polygon with role `minefield` and visibility limited to its selected team
- **THEN** the server stores a minefield that belongs to that team and is visible only to that team and staff

### Requirement: Search returns whole stored objects

Each search result SHALL be the matching object as stored. It SHALL include:

- id, kind, and full normalized geometry;
- role, belonging, visibility list, origin, and creating application;
- icon, title, and description;
- position accuracy, expiry, and update stamp.

A result MUST NOT include the object's idempotency key. The server MUST NOT calculate or return distance from the search geometry, hit probability, target selection, or hit messages. The calling application SHALL derive any measurements or game decisions it needs from the returned objects.

#### Scenario: Marker coordinates are returned

- **WHEN** a circle search finds an intersecting marker
- **THEN** the result contains that marker's id, marker kind, latitude and longitude without `distance_m`

#### Scenario: Polygon geometry is returned

- **WHEN** a circle search finds an intersecting polygon
- **THEN** the result contains the polygon id and complete path without a server-derived nearest distance

#### Scenario: Minefield carries its team facts

- **WHEN** a search finds a minefield
- **THEN** the result includes its role, belonging, visibility list, and update stamp, and no idempotency key

#### Scenario: Search makes no game decision

- **WHEN** a circle search returns several intersecting objects
- **THEN** the response contains stored object facts and no distance, hit, miss, probability, or target-selection result

## ADDED Requirements

### Requirement: Applications can change any object on an allowed map

An activated application SHALL be able to update or delete any object on its selected allowed map, whatever the object's origin, belonging, visibility, role, or creating application. The only exception is a player self-point, which nobody may change. The edit hook MAY still deny a write. Which objects an application changes SHALL be that application's own decision. Staff and player edit rights SHALL remain unchanged.

#### Scenario: Application reveals a staff minefield

- **WHEN** an activated application adds its team to the visibility list of a staff-created minefield on its map
- **THEN** the server applies the update

#### Scenario: Mortar edits another application's object

- **WHEN** a mortar credential updates an object created by an Object application on the same map
- **THEN** the server applies the valid update

#### Scenario: Self-point stays locked

- **WHEN** an application requests an update or delete of a player's self-point
- **THEN** the server refuses it

#### Scenario: Object on another map

- **WHEN** an application requests an update of an object that is not on its selected map
- **THEN** the server refuses it and the object is unchanged

### Requirement: Application updates change only the fields they send

An application update SHALL change only the fields present in the request. Every omitted field SHALL keep its stored value, including geometry, title, description, icon, visibility list, role, position accuracy, and expiry. Belonging, origin, and the creating application SHALL keep their stored values on every application update. When the request carries the object's last-seen update stamp and the object has changed since then, the server SHALL refuse the update as a conflict and leave the object unchanged.

#### Scenario: Expire now

- **WHEN** an application updates a staff minefield with only `ttl_seconds` equal to 1
- **THEN** the minefield expires one second after the server accepts the update and every other field is unchanged

#### Scenario: Belonging survives another team's update

- **WHEN** an application of team Blue updates the visibility list of an object that belongs to team Red
- **THEN** the object still belongs to team Red

#### Scenario: Full updates behave as before

- **WHEN** Tac Nav Mortar updates its position marker with every object field
- **THEN** the stored object matches the fields it sent, as before this change

#### Scenario: Stale update is refused

- **WHEN** two applications read the same object and the second sends its update after the first was accepted, with the update stamp it read
- **THEN** the second update is refused as a conflict and the first update remains

### Requirement: Search can be limited to one role

A search request MAY name one role. The result SHALL then contain only intersecting objects that have that role. The empty role SHALL select ordinary objects. A search without a role SHALL return intersecting objects of every role. A search that names an unregistered role MUST be refused.

#### Scenario: Sapper sees only minefields

- **WHEN** an application searches a circle with role `minefield` and the circle intersects a minefield and an ordinary marker
- **THEN** the result contains the minefield and not the marker

#### Scenario: Mortar still sees everything

- **WHEN** a mortar searches without a role over a minefield, a hint area, and an ordinary marker
- **THEN** the result contains all three

#### Scenario: Unknown role

- **WHEN** a search names a role that is not in the registry
- **THEN** the search is refused

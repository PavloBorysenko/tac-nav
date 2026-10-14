# Spec Delta

## Purpose

Provides scoped device applications with reusable geo-object writes and geometry intersection search while preserving absolute expiry and keeping game decisions outside the site.

## ADDED Requirements

### Requirement: An activated application can create map objects

An application with a valid credential SHALL be able to create a marker, circle, polyline, or polygon on its selected allowed map. The server SHALL stamp the authenticated application id, selected team, and map context rather than trusting client-supplied identity fields. The application MAY choose any existing catalog icon and a visibility list valid for that map, including an empty list meaning visible to every map viewer; it MUST NOT be restricted to the selected team’s player palette.

#### Scenario: Mortar creates a public impact marker

- **WHEN** an activated mortar creates a marker on its allowed map with empty visibility and an existing impact icon
- **THEN** the server stores an application-origin object belonging to the selected team and visible to every viewer of that map

#### Scenario: Identity fields cannot be forged

- **WHEN** an application request names another application id, map, or team in identity fields
- **THEN** the server ignores or refuses those values and does not attribute the object to that identity

#### Scenario: Missing icon falls back

- **WHEN** an application creates an object with an icon id that no longer exists
- **THEN** the object is stored with the default-marker fallback

### Requirement: Applications can change only their own objects

An application SHALL be able to update or delete an ordinary object created by that application on an allowed map. It MUST NOT update or delete an object created by staff, a player, or another application. Staff edit rights and existing player edit rights SHALL remain unchanged.

#### Scenario: Antenna application updates its object

- **WHEN** an activated application updates an object stamped with its own application id
- **THEN** the server applies the valid update

#### Scenario: Mortar cannot edit another application

- **WHEN** a mortar credential requests an update to an antenna application’s object
- **THEN** the server refuses the update

### Requirement: Object lifetimes use seconds at the write boundary

Object create and update writes SHALL accept duration as `ttl_seconds` and SHALL NOT accept the removed `ttl_minutes` field. A positive duration SHALL set the absolute UTC `expires_at` to that many seconds after server acceptance. Zero SHALL clear expiry. An update that omits the duration SHALL preserve the stored absolute expiry, including elapsed time; a client-supplied absolute expiry MUST NOT replace it.

#### Scenario: Thirty-second impact

- **WHEN** an application creates an impact with `ttl_seconds` equal to 30
- **THEN** its stored expiry is 30 seconds after the server accepts the create

#### Scenario: Omitted duration preserves elapsed time

- **WHEN** an object with an existing expiry is updated without `ttl_seconds`
- **THEN** its absolute expiry remains unchanged

#### Scenario: Zero makes an object permanent

- **WHEN** an authorized update supplies `ttl_seconds` equal to zero
- **THEN** the object has no expiry

#### Scenario: Old duration field is rejected

- **WHEN** a write supplies `ttl_minutes`
- **THEN** the server refuses that duration instead of interpreting it as seconds

### Requirement: Player-visible lifetime behavior survives the seconds migration

Existing staff and player forms, quick presets, self-points, expiry filtering, countdowns, and remaining-expiry edits SHALL preserve their specified user-visible durations and behavior after their internal writes and stored preset values move to seconds.

#### Scenario: One-minute player choice remains one minute

- **WHEN** a player selects the existing one-minute duration while creating an object
- **THEN** the stored expiry is 60 seconds after the server accepts the create

#### Scenario: Self-point remains one minute

- **WHEN** a player successfully marks themselves after the migration
- **THEN** that self-point expires 60 seconds after creation

### Requirement: Geometry search is confined to one allowed map

An authenticated application SHALL be able to submit a valid marker, circle, polyline, or polygon search geometry against its selected allowed map. The result SHALL contain intersecting unexpired objects from that map only, regardless of object origin, belonging, or visibility. Expired objects and objects from every other map MUST NOT be returned. Guests and unauthenticated callers MUST NOT use application search.

#### Scenario: Circle finds hidden opposing object

- **WHEN** a mortar searches a circle that intersects an unexpired opposing-team marker hidden from the mortar’s team
- **THEN** the result includes that marker

#### Scenario: Search cannot cross maps

- **WHEN** the same coordinates contain an object on another map
- **THEN** that other-map object is absent from the result

#### Scenario: Expired object is absent

- **WHEN** a search geometry intersects an expired object
- **THEN** the expired object is absent from the result

### Requirement: Search returns raw object geometry

Each search result SHALL include only the object id, kind, full normalized geometry, and position accuracy when present. The server MUST NOT calculate or return distance from the search geometry, hit probability, target selection, or hit messages. The calling application SHALL derive any measurements or game decisions it needs from the returned geometry.

#### Scenario: Marker coordinates are returned

- **WHEN** a circle search finds an intersecting marker
- **THEN** the result contains that marker’s id, marker kind, latitude and longitude without `distance_m`

#### Scenario: Polygon geometry is returned

- **WHEN** a circle search finds an intersecting polygon
- **THEN** the result contains the polygon id and complete path without a server-derived nearest distance

#### Scenario: Search makes no game decision

- **WHEN** a circle search returns several intersecting objects
- **THEN** the response contains raw geometry facts and no distance, hit, miss, probability, or target-selection result

### Requirement: Position accuracy travels with a positioned object

An application MAY supply the horizontal accuracy in metres of the location used for an object. The server SHALL preserve and return that value as `position_accuracy_m` without treating it as a visibility rule or hit calculation.

#### Scenario: Antenna accuracy is returned

- **WHEN** an antenna stores its point with `position_accuracy_m` equal to 6.5 and a mortar search returns that point
- **THEN** the result includes accuracy 6.5 alongside the point geometry

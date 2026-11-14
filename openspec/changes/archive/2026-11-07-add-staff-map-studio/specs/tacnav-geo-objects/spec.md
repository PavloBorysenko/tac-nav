## Purpose

Stores map geometries as hub facts with belonging, visibility, origin, and time-to-live, and decides who may see or edit them through default resolvers plus hooks that receive full context.

## ADDED Requirements

### Requirement: Geo objects have kinds and geometry

The system SHALL persist geo objects on a map as one of: marker, open polyline, closed polygon, or circle. A polyline MUST remain an open chain. A polygon MUST have a closed contour and a fill. A circle SHALL be stored as a center and a radius.

#### Scenario: Polygon is closed

- **WHEN** staff finish drawing a polygon with at least three vertices
- **THEN** the stored geometry is a closed ring

#### Scenario: Line is open

- **WHEN** staff finish drawing a line with at least two vertices
- **THEN** the stored geometry is an open polyline and is not filled as a polygon

### Requirement: Belonging and visibility are separate fields

A geo object SHALL have a single optional belonging team. Empty belonging SHALL mean the object is neutral. A geo object SHALL have a visibility list of zero or more teams. An empty visibility list SHALL mean every authorized viewer of that map can see the object, subject to expiration and hooks. Objects SHALL NOT store a separate per-object color; stroke, halo, and polygon fill SHALL use the belonging team's color, or a neutral style when belonging is empty.

#### Scenario: Neutral and visible to all

- **WHEN** staff save an object with empty belonging and empty visibility
- **THEN** the object is treated as neutral and visible to all authorized viewers of the map

#### Scenario: Team belonging with limited visibility

- **WHEN** staff save an object belonging to team Red that is visible only to Red and Blue
- **THEN** belonging is Red and the visibility list is Red and Blue

### Requirement: Origin distinguishes staff and team-created objects

Each geo object SHALL store an origin of staff or team, stamped at creation. Objects created by an Administrator or Organizer SHALL have origin staff. Origin MUST NOT change if the author later gains or loses a role.

#### Scenario: Staff-created object is origin staff

- **WHEN** an Organizer creates a geo object
- **THEN** the object is stored with origin staff

### Requirement: Time-to-live is optional

A geo object MAY have an expires-at time. Empty time-to-live SHALL mean the object does not expire. Staff TTL input SHALL be a duration (none, presets, or a custom number of minutes) that is stored as an absolute expires-at. Expired objects SHALL be omitted from default reads. Staff SHALL be able to request expired objects for a map and SHALL be able to permanently delete all expired objects on that map. Title MAY be empty. Icon SHALL be required and SHALL fall back to the default marker when missing or deleted.

#### Scenario: Default read hides expired objects

- **WHEN** the current time is after an object's expires-at and a default object listing is requested
- **THEN** that object is not included

#### Scenario: Staff purge expired on one map

- **WHEN** an Organizer or Administrator runs delete-expired on a map
- **THEN** expired objects on that map are permanently deleted and unexpired objects on that map remain

### Requirement: Default visibility resolver and override hook

An Administrator or Organizer SHALL see every non-expired object on a map, unless a visibility hook denies it. Any other viewer SHALL see an object only when visibility is empty or their team is in the visibility list, unless a hook decides otherwise. The visibility hook MUST receive the default result plus the object, the viewer (user, roles, team memberships, capabilities), and the request (surface and map). A collection-level hook SHALL be able to adjust the query or result set with the same context.

#### Scenario: Organizer sees a team-only object

- **WHEN** an Organizer requests objects for a map that includes an object visible only to team Red
- **THEN** the default resolver includes that object

#### Scenario: Hook can hide an object from staff

- **WHEN** a visibility hook returns not visible for a given object and viewer
- **THEN** that object is not shown to that viewer even if the default resolver allowed it

### Requirement: Default edit resolver and override hook

An Administrator or Organizer SHALL be allowed to edit and delete any geo object on a map, unless an edit hook denies it. A non-staff viewer SHALL be allowed to edit or delete an object only when origin is team and the object's belonging is that viewer's team, unless a hook decides otherwise. Staff-origin objects MUST remain non-editable to team members even when belonging is that team. The edit hook MUST receive the default result plus the same full context as the visibility hook.

#### Scenario: Staff-origin object with team belonging is locked for members

- **WHEN** a team member of Red views a staff-origin object whose belonging is Red
- **THEN** the default edit resolver forbids edit and delete

#### Scenario: Team-origin object is editable by any member of that team

- **WHEN** a team member of Red requests edit of a team-origin object belonging to Red
- **THEN** the default edit resolver allows edit and delete

### Requirement: Staff writes go through the hub

Creating, updating, and deleting geo objects SHALL be authorized server-side. Responses that list objects for a surface SHALL apply the visibility resolver and hooks. Clients MUST NOT be the source of truth for who may see or edit an object.

#### Scenario: Unauthorized write is rejected

- **WHEN** a request without map-staff permission attempts to create a geo object
- **THEN** the system refuses the write

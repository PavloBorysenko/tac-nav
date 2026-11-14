# Spec Delta

## ADDED Requirements

### Requirement: Geo objects have a role

Each geo object SHALL have exactly one role from a closed registry kept on the server. The registry SHALL start with three roles. Each role SHALL declare the geometry kinds it allows:

- ordinary, stored as an empty value, allows every kind;
- `minefield` allows circles and polygons;
- `hint` allows circles and polygons.

A write that names an unregistered role, or a role that the object's kind does not allow, MUST be refused without changing the object. An object SHALL be ordinary unless a role is set. This includes every object that existed before roles. A save that does not send a role SHALL keep the stored role. A player write MUST NOT set or change a role. Adding a role to the registry MUST NOT change any stored object.

#### Scenario: Existing objects become ordinary

- **WHEN** the role upgrade runs on a map with existing objects
- **THEN** each existing object has the ordinary role and its other fields are unchanged

#### Scenario: Staff store a minefield polygon

- **WHEN** an Organizer saves a polygon with role `minefield`
- **THEN** the object is stored with role `minefield`

#### Scenario: Role not allowed for the kind

- **WHEN** a write gives role `minefield` to a marker
- **THEN** the write is refused and no object is created or changed

#### Scenario: Unknown role

- **WHEN** a write names a role that is not in the registry
- **THEN** the write is refused

#### Scenario: Save without a role keeps it

- **WHEN** a player edits the title of a team object whose role staff set to `minefield`
- **THEN** the title changes and the role stays `minefield`

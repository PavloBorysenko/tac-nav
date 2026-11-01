# Spec Delta

## Purpose

Lets an application publish map objects that can be seen but never targeted, and create a permanent object safely across retries, without moving any game decision to the server.

## ADDED Requirements

### Requirement: An application can publish an object excluded from search
An activated application SHALL be able to mark an object it creates or updates as not searchable. Map geometry search MUST NOT return a not-searchable object, regardless of its geometry, visibility, or origin. An object SHALL be searchable by default, including every existing object and every object written by a client that does not use this field. Map viewers SHALL see a not-searchable object according to its normal visibility.

#### Scenario: Hint area is not a target
- **WHEN** a mortar searches a circle that intersects a not-searchable public circle
- **THEN** the result omits that circle and no hit can be addressed to it from this search

#### Scenario: Real object inside the area is found
- **WHEN** the same search intersects a searchable object hidden from the mortar's team
- **THEN** the result includes that object

#### Scenario: Existing objects stay searchable
- **WHEN** a mortar searches after the schema upgrade
- **THEN** objects stored before the upgrade are found as before

#### Scenario: Staff edit keeps the flag
- **WHEN** staff edit the title of a not-searchable object
- **THEN** the object stays not searchable

### Requirement: Application creates can be idempotent
An application MAY supply an idempotency key when creating an object. A repeat create by the same application with the same key SHALL return the existing object without storing another. The same key from another application SHALL NOT match. A create without a key SHALL behave as before, and the existing `client_object_id` field SHALL keep having no server effect.

#### Scenario: Retried wreck does not duplicate
- **WHEN** an application repeats a create with the same key after losing the response
- **THEN** one object exists and both responses identify it

#### Scenario: New key creates a new object
- **WHEN** the application creates with a different key after a local reset
- **THEN** a separate object is stored and the earlier one is untouched

#### Scenario: Keys are per application
- **WHEN** two applications create with the same key
- **THEN** each application has its own object

### Requirement: Area objects do not show a false position
The map canvas SHALL draw a not-searchable circle or polygon without an icon as its shape and title only, with no icon at its centre. Every other object SHALL keep its current drawing, including a not-searchable object that has an icon.

#### Scenario: Hint area has no centre pin
- **WHEN** a viewer's map shows a not-searchable circle with no icon
- **THEN** the circle and its title are shown with no marker at the centre

#### Scenario: Wreck keeps its icon
- **WHEN** a viewer's map shows a not-searchable object with an icon
- **THEN** the icon is drawn as for any other object

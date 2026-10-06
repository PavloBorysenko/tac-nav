# Spec Delta

## ADDED Requirements

### Requirement: Editing keeps the remaining time-to-live

When an Organizer, an Administrator, or a player edits an object they are allowed to change, the time-to-live control SHALL start on leaving the stored expiry unchanged. Saving while that choice remains SHALL keep the stored expiry, including time already elapsed, and SHALL ignore a client-supplied expiry time. Choosing 1, 3, 10, 20, 30, or 60 minutes SHALL set the expiry to that many minutes after the server accepts the save. Choosing no expiry SHALL clear the expiry. Closing the form without saving SHALL leave the stored expiry unchanged. While the form stays open, the remaining-time line SHALL keep showing the stored expiry even if the control was changed. An object that is already expired SHALL stay expired when the control is left unchanged. An expiry that does not match a listed duration SHALL be kept when a later edit leaves the control unchanged.

When that same editor creates an object, the time-to-live control SHALL offer no expiry and 1, 3, 10, 20, 30, and 60 minutes, and SHALL NOT offer leaving an expiry unchanged. A chosen duration SHALL start when the server accepts the create. A quick-add preset SHALL still apply its own time-to-live at creation. This requirement MUST NOT change mark-myself and MUST NOT let a guest write.

#### Scenario: A later save keeps the remaining time

- **WHEN** a player opens an object that expires three minutes after creation, two minutes pass, and the player saves a new title without changing the time-to-live control
- **THEN** the stored expiry is still the original expiry

#### Scenario: Staff save keeps the remaining time

- **WHEN** an Organizer opens an object that still has time left and saves a new title without changing the time-to-live control
- **THEN** the stored expiry is unchanged

#### Scenario: A chosen duration starts at the save

- **WHEN** a player opens an object that still has time left and saves it with a time-to-live of 20 minutes
- **THEN** the stored expiry is 20 minutes after that save

#### Scenario: No expiry clears a limited time-to-live

- **WHEN** a player saves an object that still has time left and chooses no expiry
- **THEN** the object has no expiry

#### Scenario: Closing without saving keeps the original expiry

- **WHEN** a player opens an object that still has time left and closes the form without saving
- **THEN** the stored expiry is unchanged

#### Scenario: The open form still shows the stored remaining time

- **WHEN** an editor has the form open for an object that still has time left and selects 30 minutes without saving
- **THEN** the form still shows the remaining time of the stored expiry

#### Scenario: An expired object stays expired

- **WHEN** an Organizer saves an expired object without changing the time-to-live control
- **THEN** the object remains expired

#### Scenario: A preset duration outside the list is kept

- **WHEN** a player edits an object created with a time-to-live other than 1, 3, 10, 20, 30, or 60 minutes and saves without changing the time-to-live control
- **THEN** the stored expiry is unchanged

#### Scenario: Create offers the added durations

- **WHEN** a player creates an object
- **THEN** the time-to-live choices include no expiry and 1, 3, 10, 20, 30, and 60 minutes, and do not include leaving an expiry unchanged

#### Scenario: One minute starts at creation

- **WHEN** a player creates an object with a time-to-live of 1 minute
- **THEN** the stored expiry is 1 minute after that save

#### Scenario: A client expiry time does not extend the object

- **WHEN** an update does not choose a duration and the request includes a later expiry time
- **THEN** the stored expiry stays the original expiry

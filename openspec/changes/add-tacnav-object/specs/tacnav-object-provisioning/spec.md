# Spec Delta

## Purpose

Lets staff define, activate, and distribute Tac Nav Object applications next to Tac Nav Mortar, so that each application type has its own settings, activation, and public release.

## ADDED Requirements

### Requirement: Staff define an Object application
An Organizer or Administrator SHALL be able to create an Object application with a name, allowed maps, allowed teams, default map, default team, and reference settings. The reference settings SHALL be: object name, description, catalog icon, destroyed catalog icon, hits to destroy, two optional size fields in metres, deployment seconds, visibility of team or all, random area with radius, automatic deactivation distance, GPS accuracy limit, automatic activation with still seconds, downtime allowance in minutes, downtime reset time in minutes, and settings PIN. Staff SHALL choose icons from catalog titles rather than typing identifiers. A non-staff user MUST NOT create or inspect an Object application.

#### Scenario: Organizer creates an antenna
- **WHEN** an Organizer saves "Antenna 1" with two hits to destroy, sizes 10 and 20, and a destroyed icon
- **THEN** the definition is stored as an Object application available for activation

#### Scenario: Player cannot manage Object applications
- **WHEN** a Player requests an Object definition
- **THEN** the system refuses the request

### Requirement: Object reference settings are validated
The server SHALL apply these defaults when a field is omitted: hits to destroy 0, deployment seconds 0, visibility team, random area off, automatic deactivation off, GPS accuracy limit 15 metres, automatic activation off, downtime allowance off, and settings PIN `4321`. It SHALL refuse a save when the object name is empty, a number is negative, the settings PIN is not at least four digits, an enabled random area or automatic activation lacks a positive value, the automatic deactivation distance is below twice the GPS accuracy limit, or the random area radius is smaller than the object's own extent. An empty downtime reset time SHALL mean the downtime allowance value, and 0 SHALL mean immediate reset.

#### Scenario: Defaults are filled
- **WHEN** staff save an Object application with only a name
- **THEN** the stored reference is an indestructible, instantly deploying, team-visible point with the default GPS limit and PIN

#### Scenario: False-trigger distance is refused
- **WHEN** staff set automatic deactivation to 20 metres with a GPS accuracy limit of 15 metres
- **THEN** the save is refused with a message that the distance must be at least 30 metres

#### Scenario: Area smaller than the object is refused
- **WHEN** staff set a 10 metre random area on a circle of 30 metres radius
- **THEN** the save is refused

### Requirement: Mortar reference settings are unaffected
Saving a Mortar application SHALL continue to apply the Mortar reference rules, and saving an Object application SHALL apply only the Object rules. An unknown application type MUST NOT be stored as a new type. Existing application records SHALL remain valid without migration.

#### Scenario: Mortar record keeps working
- **WHEN** staff edit an existing Mortar application after this change
- **THEN** its settings validate and save as before

### Requirement: Activation is bound to the application type
Activation SHALL succeed only when the requesting package matches the application's type. A rejected request MUST NOT consume the code or revoke the current phone. The current Tac Nav Mortar package MUST keep activating Mortar applications without an update, and a request without a package SHALL be treated as Mortar.

#### Scenario: Object QR in the mortar application
- **WHEN** Tac Nav Mortar submits a valid code for an Object application
- **THEN** activation is refused and the code stays usable by Tac Nav Object

#### Scenario: Mortar QR in the object application
- **WHEN** Tac Nav Object submits a valid code for a Mortar application
- **THEN** activation is refused and the Mortar application's current phone stays active

#### Scenario: Object activation returns its reference
- **WHEN** Tac Nav Object submits a valid code for an Object application
- **THEN** it receives its identity, credential, scopes, defaults, and Object reference settings including the settings PIN

### Requirement: Each application has its own release
Staff SHALL be able to publish the Object APK from the Object tab by upload or HTTPS URL with a version, independently of the Mortar release. The public page `/tacnav-object/` and download `/tacnav-object.apk` SHALL offer the latest Object release to any visitor without signing in, identify package `com.tacnav.beacon`, and expose no credential, activation code, or settings PIN. Publishing one application's release MUST NOT change the other's, and the existing `/tacnav-mortar/` and `/tacnav-mortar.apk` addresses and published release SHALL stay valid.

#### Scenario: Object release leaves Mortar untouched
- **WHEN** staff publish Object version 0.1.0
- **THEN** `/tacnav-object/` offers that version and `/tacnav-mortar/` offers the same release as before

#### Scenario: Device reads its own release
- **WHEN** an activated Object phone requests release metadata
- **THEN** it receives the Object release rather than the Mortar release

### Requirement: Players reach the Object download from the site front
The site front page SHALL offer an Object app control beside Mortar app that opens the public Object page.

#### Scenario: Player follows Object app
- **WHEN** a visitor follows Object app on the front page
- **THEN** they reach the public Object download page without a WordPress account

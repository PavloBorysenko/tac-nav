# Spec Delta

## Purpose

Lets staff define, distribute, activate, revoke, and reset trusted Tac Nav device applications without coupling their local gameplay settings to a particular phone installation.

## ADDED Requirements

### Requirement: Staff can define an application instance

An Organizer or Administrator SHALL be able to create a named application instance with an application type, reference settings, allowed maps, allowed teams, a default map, a default team, an optional impact icon, and an optional off-by-default flag that publishes the mortar position for 30 seconds after each shot. Staff SHALL choose maps, teams, and the impact icon from the published catalog titles, not by typing identifiers. Each allowed team MUST exist and the selected current team MUST be listed on the selected current map when the device uses a map API. A non-staff user MUST NOT create, inspect, activate, or revoke an application instance. Successful activation SHALL return those maps and teams with their titles so the phone can display names.

#### Scenario: Organizer creates a mortar reference

- **WHEN** an Organizer creates “Mortar 1” with application type mortar, two allowed maps, two allowed teams, and reference firing settings
- **THEN** the system stores that definition and makes it available for activation

#### Scenario: Player cannot manage device definitions

- **WHEN** a Player requests an application definition or activation control
- **THEN** the system refuses the request

### Requirement: One-time activation provisions one phone

Staff SHALL be able to generate an unexpired one-time activation code for an application instance. A QR activation SHALL carry the server URL and that code, and manual activation SHALL accept the same two values. A successful activation SHALL consume the code and return the stable application id, a new API credential, allowed map and team scopes, default selections, reference settings, and required service configuration. Reusing, changing, or guessing a consumed, expired, or invalid code MUST NOT activate a phone.

#### Scenario: Phone activates from QR

- **WHEN** Tac Nav Mortar scans a valid QR for an inactive code
- **THEN** it receives the application identity, credential, scopes, defaults, and reference settings and the code becomes unusable

#### Scenario: Manual activation uses the same contract

- **WHEN** a user enters the QR’s server URL and one-time code manually
- **THEN** the phone receives the same activation result as a QR activation

#### Scenario: Code cannot be reused

- **WHEN** another phone submits a code after its successful activation
- **THEN** the system refuses activation and issues no credential

### Requirement: One application instance has one active phone

Activating an application instance on a new phone SHALL revoke every earlier phone credential and delivery registration for that instance. Staff SHALL also be able to revoke the current phone without deleting the application definition. A revoked credential MUST NOT read or write application data.

#### Scenario: Reactivation replaces the phone

- **WHEN** staff generate a new code and activate Mortar 1 on a second phone
- **THEN** the second phone becomes active and the first phone’s API credential is refused

#### Scenario: Staff revoke a lost phone

- **WHEN** an Organizer revokes Mortar 1
- **THEN** its current credential and delivery registration stop authorizing requests

### Requirement: Identity and playable context are separate

The application id, API credential, server URL, and allowed scopes SHALL come only from activation. The active phone MAY select its current map and current team from the allowed scopes after local PIN authorization. The server MUST reject an unallowed map, an unallowed team, or a team not listed on the selected map even when the phone accepted the local selection.

#### Scenario: Allowed local selection succeeds

- **WHEN** a phone selects an allowed team on an allowed map and makes an authenticated map request
- **THEN** the server accepts that application context

#### Scenario: Local tampering cannot expand scope

- **WHEN** a phone submits a map or team outside its activation scopes
- **THEN** the server refuses the request

### Requirement: Reactivation restores reference settings

Reference settings SHALL be sent during every successful activation. A phone’s later local gameplay-setting changes MUST NOT update those server reference settings. Activating the same application instance again SHALL return the current server reference and thereby reset the newly activated installation.

#### Scenario: Local customization does not change the reference

- **WHEN** a phone changes its radius and sound threshold locally and staff inspect Mortar 1
- **THEN** the server reference retains the staff-defined values

#### Scenario: Reactivation resets customization

- **WHEN** Mortar 1 is activated again after local customization
- **THEN** the new installation starts from the server reference settings

### Requirement: Applications can browse the complete icon catalog

An authenticated application SHALL be able to retrieve every current icon in the site icon catalog with its id, title, and usable image URL. This catalog MUST NOT be limited by the current team’s player palette. An object write MAY use any existing catalog icon; a missing or deleted selected icon SHALL fall back to the site’s default marker behavior.

#### Scenario: Manual settings show all icons

- **WHEN** Mortar 1 opens its PIN-authorized impact-icon setting while online
- **THEN** it can browse the complete current site icon catalog, including icons outside its team palette

#### Scenario: Deleted icon has a fallback

- **WHEN** the locally selected icon has been deleted before the application creates an object
- **THEN** the resulting object uses the default marker rather than failing because the icon is outside a team palette

### Requirement: Staff can hand off the current Android package

Staff SHALL be able to publish the current Tac Nav Mortar APK by uploading the built file or supplying an HTTPS URL, plus package id and version. The same WordPress site SHALL expose one public latest-version page that any player can open without signing in. That page MUST identify `com.tacnav.mortar`, show the published version, and offer the APK. It MUST NOT expose a signing key, activation code, or private messaging credential. A later staff upload SHALL replace the previous latest file.

#### Scenario: Download page offers the current APK

- **WHEN** staff publish a Tac Nav Mortar release
- **THEN** the public download page and the staff handoff page identify `com.tacnav.mortar`, its version, and its APK download

#### Scenario: Players use the site, not wp-admin

- **WHEN** a player opens the site front and follows Mortar app
- **THEN** they reach the public latest-version page and can download the current APK without a WordPress account

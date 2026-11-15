# tacnav-sapper-provisioning Specification

## Purpose

Lets staff define, activate, and distribute Tac Nav Sapper applications next to Tac Nav Mortar and Tac Nav Object. Each Sapper application has its own reference settings, type-bound activation, and public release.

## Requirements

### Requirement: Staff define a Sapper application
An Organizer or Administrator SHALL be able to create a Sapper application with a name, allowed maps, allowed teams, default map, default team, and reference settings. The reference settings SHALL be:

- detection radius in metres and heading sector in degrees;
- mark hold seconds, clear hold seconds, and lay hold seconds;
- minefield length along the phone's heading and width across it, in metres;
- minefield icon and No mines icon, both from the catalog;
- maximum minefields and maximum No mines markers per activation;
- GPS accuracy limit in metres;
- settings PIN.

Staff SHALL choose icons by catalog title, not by typing an identifier. A non-staff user MUST NOT create or inspect a Sapper application.

#### Scenario: Organizer creates a sapper
- **WHEN** an Organizer saves "Sapper 1" with a 15 metre detection radius and a minefield icon
- **THEN** the definition is stored as a Sapper application available for activation

#### Scenario: Player cannot manage Sapper applications
- **WHEN** a Player requests a Sapper definition
- **THEN** the system refuses the request

### Requirement: Sapper reference settings are validated
The server SHALL apply these defaults when a field is omitted:

| Setting | Default |
| --- | --- |
| Detection radius | 10 metres |
| Heading sector | 90 degrees |
| Mark hold | 30 seconds |
| Clear hold | 180 seconds |
| Lay hold | 180 seconds |
| Minefield length and width | 10 metres each |
| Minefield icon and No mines icon | none |
| Maximum minefields | 3 |
| Maximum No mines markers | 10 |
| GPS accuracy limit | 15 metres |
| Settings PIN | `4321` |

A maximum of 0 SHALL mean unlimited. The server SHALL refuse a save when:

- a number is negative;
- the detection radius, a hold time, a minefield side, or the GPS accuracy limit is not positive;
- the heading sector is outside 1 to 360 degrees;
- the settings PIN has fewer than four digits.

#### Scenario: Defaults are filled
- **WHEN** staff save a Sapper application with only a name
- **THEN** the stored reference holds every default listed above

#### Scenario: Wide sector is refused
- **WHEN** staff set the heading sector to 400 degrees
- **THEN** the save is refused

#### Scenario: Unlimited markers
- **WHEN** staff set the maximum No mines markers to 0
- **THEN** the stored reference allows unlimited markers

### Requirement: Existing application types are unaffected
Saving a Mortar or Object application SHALL apply only that type's rules, as before, and saving a Sapper application SHALL apply only the Sapper rules. Existing application records SHALL remain valid without migration, and an application MUST NOT change its type.

#### Scenario: Object record keeps working
- **WHEN** staff edit an existing Object application after this change
- **THEN** its settings validate and save as before

### Requirement: Activation is bound to the Sapper type
A Sapper application SHALL activate only from package `com.tacnav.sapper`, and that package SHALL NOT activate a Mortar or Object application. A refused request MUST NOT consume the code or revoke the current phone. A successful Sapper activation SHALL return the identity, credential, scopes, defaults, and the Sapper reference settings, including the settings PIN.

#### Scenario: Sapper QR in the object application
- **WHEN** Tac Nav Object submits a valid code for a Sapper application
- **THEN** activation is refused and the code stays usable by Tac Nav Sapper

#### Scenario: Sapper activation returns its reference
- **WHEN** Tac Nav Sapper submits a valid code for a Sapper application
- **THEN** it receives its credential, scopes, and Sapper reference settings including the settings PIN

### Requirement: Sapper has its own release
Staff SHALL be able to publish the Sapper APK from the Sapper tab, by upload or by HTTPS URL, with a version, independently of the other releases. The public page `/tacnav-sapper/` and the download `/tacnav-sapper.apk` SHALL offer the latest Sapper release to any visitor without signing in. They SHALL identify package `com.tacnav.sapper` and MUST NOT expose a credential, an activation code, or a settings PIN. Publishing the Sapper release MUST NOT change the Mortar or Object release or their addresses.

#### Scenario: Sapper release leaves the others untouched
- **WHEN** staff publish Sapper version 0.1.0
- **THEN** `/tacnav-sapper/` offers that version and `/tacnav-mortar/` and `/tacnav-object/` offer the same releases as before

### Requirement: Players reach the Sapper download from the site front
The site front page SHALL offer a Sapper app control beside Mortar app and Object app. The control SHALL open the public Sapper page.

#### Scenario: Visitor follows Sapper app
- **WHEN** a visitor follows Sapper app on the front page
- **THEN** they reach the public Sapper download page without a WordPress account

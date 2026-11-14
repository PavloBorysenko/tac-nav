# Spec Delta

## MODIFIED Requirements

### Requirement: Object is an application tab
The Device applications staff page SHALL offer an Object tab after the Mortar tab. The Object tab SHALL show only Object applications and the Object release. The Mortar tab SHALL show every application that is neither an Object nor a Sapper application, together with the Mortar release. After staff save, activate, revoke, or publish from the Object tab, they SHALL return to the Object tab.

#### Scenario: Staff open the Object tab
- **WHEN** an Organizer opens Device applications with `app` set to `object`
- **THEN** the Object tab is marked as current and the page lists Object applications only

#### Scenario: Mortar tab stays separate
- **WHEN** an Organizer opens the Mortar tab while Object and Sapper applications exist
- **THEN** the Mortar tab lists no Object or Sapper application

#### Scenario: Object action returns to its tab
- **WHEN** staff save an Object application
- **THEN** they return to the Object tab rather than the default Mortar tab

## ADDED Requirements

### Requirement: Sapper is an application tab
The Device applications staff page SHALL offer a Sapper tab after the Object tab. The Sapper tab SHALL show only Sapper applications and the Sapper release. After staff save, activate, revoke, or publish from the Sapper tab, they SHALL return to the Sapper tab.

#### Scenario: Staff open the Sapper tab
- **WHEN** an Organizer opens Device applications with `app` set to `sapper`
- **THEN** the Sapper tab is marked as current and the page lists Sapper applications only

#### Scenario: Sapper action returns to its tab
- **WHEN** staff create an activation code from the Sapper tab
- **THEN** they return to the Sapper tab

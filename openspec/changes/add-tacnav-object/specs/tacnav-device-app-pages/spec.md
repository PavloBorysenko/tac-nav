# Spec Delta

## ADDED Requirements

### Requirement: Object is an application tab
The Device applications staff page SHALL offer an Object tab after the Mortar tab. The Object tab SHALL show only Object applications and the Object release, and the Mortar tab SHALL show every other application and the Mortar release. After staff save, activate, revoke, or publish from the Object tab, they SHALL return to the Object tab.

#### Scenario: Staff open the Object tab
- **WHEN** an Organizer opens Device applications with `app` set to `object`
- **THEN** the Object tab is marked as current and the page lists Object applications only

#### Scenario: Mortar tab stays separate
- **WHEN** an Organizer opens the Mortar tab while Object applications exist
- **THEN** the Mortar tab lists no Object application

#### Scenario: Object action returns to its tab
- **WHEN** staff save an Object application
- **THEN** they return to the Object tab rather than the default Mortar tab

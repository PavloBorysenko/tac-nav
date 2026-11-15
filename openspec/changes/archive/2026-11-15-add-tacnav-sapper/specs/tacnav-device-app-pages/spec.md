# Spec Delta

## ADDED Requirements

### Requirement: Sapper is an application tab
The Device applications staff page SHALL offer a Sapper tab after the Object tab. The Sapper tab SHALL show only Sapper applications and the Sapper release. After staff save, activate, revoke, or publish from the Sapper tab, they SHALL return to the Sapper tab.

#### Scenario: Staff open the Sapper tab
- **WHEN** an Organizer opens Device applications with `app` set to `sapper`
- **THEN** the Sapper tab is marked as current and the page lists Sapper applications only

#### Scenario: Sapper action returns to its tab
- **WHEN** staff create an activation code from the Sapper tab
- **THEN** they return to the Sapper tab

# Spec Delta

## Purpose

Presents the staff Tac Nav Mortar activation handoff as a centered card that staff can read while showing the QR and one-time code.

## ADDED Requirements

### Requirement: Handoff sits in a centered card

The staff activation handoff SHALL show its content in one centered card on a filled page background. The page background SHALL be `#1f2418`. The card background SHALL be `#f4efe3` with dark text. The card SHALL include the expiry warning, the QR code, the server URL, the manual code, the package id, and the version.

#### Scenario: Staff open the handoff

- **WHEN** staff open the activation handoff
- **THEN** the expiry warning, QR code, server URL, manual code, package id, and version appear together inside one centered light card on the olive page background

### Requirement: Handoff links are buttons

Download APK, Public download page, and Back SHALL be presented as buttons inside the card. Download APK SHALL be a filled primary button. Public download page and Back SHALL be outline buttons of the same control size. Download APK SHALL link to the published download URL when one is set, and to the public APK address otherwise. Public download page SHALL be shown whether or not a download URL is set.

#### Scenario: Published release

- **WHEN** the handoff has a download URL
- **THEN** Download APK, Public download page, and Back are buttons, and Download APK is the filled primary button linking to that URL

#### Scenario: No download URL

- **WHEN** the handoff has no download URL
- **THEN** Download APK, Public download page, and Back are still buttons, and Download APK links to the public APK address

### Requirement: Code and QR stay readable

The manual code SHALL be shown larger than the surrounding body text, in a monospace face, and SHALL be selectable. The QR code SHALL be drawn on a light background.

#### Scenario: Staff read the code and show the QR

- **WHEN** staff view the handoff
- **THEN** the manual code is larger than body text, monospace, and selectable, and the QR code sits on a light background

### Requirement: Handoff fits a phone width

The handoff SHALL fit a narrow viewport. The card SHALL stay within the screen width, and the buttons SHALL be fully visible without horizontal scrolling of the page. When the card is taller than the viewport, the page SHALL scroll vertically so the buttons remain reachable.

#### Scenario: Phone-width viewport

- **WHEN** the handoff is opened at a phone width
- **THEN** the card fits the screen width and the buttons are fully visible without horizontal page scrolling

#### Scenario: Card taller than the screen

- **WHEN** the handoff card is taller than the viewport
- **THEN** the page scrolls vertically to Download APK, Public download page, and Back

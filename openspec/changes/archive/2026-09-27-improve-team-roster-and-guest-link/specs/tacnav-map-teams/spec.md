# Spec Delta

## ADDED Requirements

### Requirement: Guest-link page uses the site theme

The staff guest-link page SHALL render the active theme header above the handoff and the active theme footer below it. The map name, team name, guest URL, copy control, and QR code SHALL sit in the theme content column between that header and footer. The copy control SHALL match the theme button appearance. The header and footer SHALL keep the theme's full width.

#### Scenario: Organizer sees header, handoff, and footer

- **WHEN** an Organizer opens the guest-link page for Red on a published map
- **THEN** the page shows the site header, the map name, Red, the guest URL, a copy control, a QR code of that URL, and the site footer

#### Scenario: Header stays full width

- **WHEN** an Organizer opens the guest-link page
- **THEN** the site header spans the theme header width and the guest URL and QR code sit in the narrower content column

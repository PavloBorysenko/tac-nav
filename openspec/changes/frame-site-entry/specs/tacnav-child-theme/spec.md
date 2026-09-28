# Spec Delta

## MODIFIED Requirements

### Requirement: Header and footer use light khaki background

While `tacnav` is the active theme and the site has no overriding header or footer style customizations, the header and footer template parts SHALL use background color `#D4CBB3` and SHALL paint the topographic sheet on that color at the image's own size. Links in the header and footer SHALL be heavier than body text and SHALL have a thick black line under them. Hovering such a link SHALL show a murky background. The content between the header and the footer MUST NOT use the sheet.

#### Scenario: Front-end header background

- **WHEN** a visitor views a template that includes the header template part
- **THEN** the header template part is rendered with background `#D4CBB3` and the topographic sheet, and its links are heavier than body text with a thick black line under them

#### Scenario: Front-end footer background

- **WHEN** a visitor views a template that includes the footer template part
- **THEN** the footer template part is rendered with background `#D4CBB3` and the topographic sheet, and its links are heavier than body text with a thick black line under them

#### Scenario: Hover shows a murky background

- **WHEN** a visitor hovers a link in the header or footer
- **THEN** that link shows a murky background

#### Scenario: Content between header and footer stays plain

- **WHEN** a visitor views a page that includes the header and footer template parts
- **THEN** the content between them does not use the topographic sheet

#### Scenario: Editor default without user override

- **WHEN** an editor opens Site Editor styles for the active child theme and header or footer colors have not been customized in the database
- **THEN** the default header and footer background is `#D4CBB3`

### Requirement: Header and footer color does not require a build

The header and footer background color and topographic sheet SHALL apply on the front end without compiling or enqueueing theme JavaScript or compiled CSS.

#### Scenario: Color without built assets

- **WHEN** the child theme is active and no compiled theme `build/` assets are enqueued
- **THEN** the header and footer still use background `#D4CBB3` and the topographic sheet

## ADDED Requirements

### Requirement: Front page offers organizer and player entry

The site front page SHALL show two controls in the content between the header and the footer, and no other content there. The controls SHALL be centered in that area. One control SHALL be labeled Organizer and SHALL open the WordPress login form. One control SHALL be labeled Player and SHALL open the player sign-in and registration page. The controls SHALL be shown whether or not the visitor is signed in.

#### Scenario: Visitor sees only the two entries

- **WHEN** a visitor opens the site front page
- **THEN** the content between the header and the footer shows an Organizer control and a Player control, centered, and no other content

#### Scenario: Organizer opens the WordPress login form

- **WHEN** a signed-out visitor uses the Organizer control
- **THEN** the WordPress login form opens

#### Scenario: Player opens the player account page

- **WHEN** a signed-out visitor uses the Player control
- **THEN** the player sign-in and registration page opens

## Purpose

Provides TAC Nav with a first-party child of Twenty Twenty-Five that holds site visual customizations, starting with header and footer background color.

## ADDED Requirements

### Requirement: Child theme is the customization layer

The site SHALL provide a child theme with stylesheet slug `tacnav` whose parent is Twenty Twenty-Five. Default WordPress themes MUST remain unedited.

#### Scenario: Child theme is registered

- **WHEN** WordPress loads installed themes
- **THEN** a theme with stylesheet `tacnav` and template `twentytwentyfive` is available

#### Scenario: Parent is not copied into the child

- **WHEN** the child theme is present
- **THEN** Twenty Twenty-Five remains the parent theme and the child does not ship a forked copy of the parent template tree

### Requirement: Header and footer use light khaki background

While `tacnav` is the active theme and the site has no overriding header or footer style customizations, the header and footer template parts SHALL use background color `#D4CBB3`.

#### Scenario: Front-end header background

- **WHEN** a visitor views a template that includes the header template part
- **THEN** the header template part is rendered with background `#D4CBB3`

#### Scenario: Front-end footer background

- **WHEN** a visitor views a template that includes the footer template part
- **THEN** the footer template part is rendered with background `#D4CBB3`

#### Scenario: Editor default without user override

- **WHEN** an editor opens Site Editor styles for the active child theme and header or footer colors have not been customized in the database
- **THEN** the default header and footer background is `#D4CBB3`

### Requirement: Header and footer color does not require a build

The header and footer background SHALL apply on the front end without compiling or enqueueing theme JavaScript or compiled CSS.

#### Scenario: Color without built assets

- **WHEN** the child theme is active and no compiled theme `build/` assets are enqueued
- **THEN** the header and footer still use background `#D4CBB3`

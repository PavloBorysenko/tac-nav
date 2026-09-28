# Spec Delta

## ADDED Requirements

### Requirement: Canvas shows the site logo

When the site has a custom logo, the canvas SHALL show that logo at the bottom-left for an Organizer, an Administrator, a listed player, and a guest. The logo SHALL be a link to the site front in the same tab, and SHALL sit on a plate whose background is `rgba(54, 68, 40, 0.6)`. The mark MUST NOT move the map view, MUST NOT create, update, or delete a geo object, and MUST NOT replace the Center control. The mark MUST NOT cover the zoom control, the bottom toolbar, or the tile attribution. When the site has no custom logo, the canvas MUST NOT show the mark.

#### Scenario: Staff see the logo

- **WHEN** an Organizer opens a map and the site has a custom logo
- **THEN** the bottom-left of the canvas shows that logo on a plate with background `rgba(54, 68, 40, 0.6)`, linking to the site front

#### Scenario: A player sees the logo

- **WHEN** a listed player opens a published map and the site has a custom logo
- **THEN** the bottom-left of the canvas shows that logo linking to the site front

#### Scenario: A guest sees the logo

- **WHEN** a guest opens a map and the site has a custom logo
- **THEN** the bottom-left of the canvas shows that logo linking to the site front

#### Scenario: No custom logo omits the mark

- **WHEN** a listed player opens a published map and the site has no custom logo
- **THEN** the canvas does not show the site-logo mark

#### Scenario: Center still recenters the map

- **WHEN** a guest uses Center while the site-logo mark is visible
- **THEN** the view moves to the map’s stored center and zoom, and the mark does not move the view or store a geo object

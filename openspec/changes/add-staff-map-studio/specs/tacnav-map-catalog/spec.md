## Purpose

Gives administrators and organizers a WordPress catalog of maps, global teams, and reusable icons, including per-team icon palettes and quick-add presets, without granting organizers site administration.

## ADDED Requirements

### Requirement: Organizer role is map staff without site administration

The system SHALL provide an Organizer role that can create, edit, and delete maps, teams, icons, and geo objects on every map. An Organizer MUST NOT be granted capabilities to manage plugins, themes, or general site settings. An Administrator SHALL retain site administration and the same map-staff capabilities as an Organizer.

#### Scenario: Organizer can manage catalog content

- **WHEN** a user with the Organizer role opens staff map, team, or icon management screens
- **THEN** they can create, edit, and delete those catalog items

#### Scenario: Organizer cannot administer the site

- **WHEN** a user with only the Organizer role is authenticated
- **THEN** they cannot install or activate plugins, switch themes, or change general site settings

#### Scenario: Administrator has both site and map control

- **WHEN** an Administrator uses the site
- **THEN** they can administer WordPress and also perform every Organizer map-staff action

### Requirement: Maps are catalog items with a field passport

The system SHALL store each map as a catalog item with title, description, center coordinates, zoom, and a default basemap. The canvas MUST NOT persist center or zoom when staff only pan or zoom the view. There is no separate scenario entity; later games on the same terrain SHALL use additional map items.

#### Scenario: Staff create a map

- **WHEN** an Organizer or Administrator submits a new map with title, description, center, zoom, and default basemap
- **THEN** the map is stored with those values and appears in the map list

#### Scenario: Canvas pan does not overwrite the passport

- **WHEN** staff pan or zoom the canvas of an existing map and leave without using the map form
- **THEN** the stored center and zoom remain the values last saved on the map form

### Requirement: Teams are global and few

The system SHALL store teams as a global catalog shared by every map. A team SHALL have title, description, color, a unique team badge stored as the featured image, an icon palette (subset of the icon library), and a list of quick-add presets. Each preset SHALL define every geo-object field except coordinates, including kind, icon from that team's palette, title, and optional description and time-to-live. A new preset SHALL default time-to-live to 3 minutes.

#### Scenario: Same teams on every map

- **WHEN** staff open the object inspector on any map
- **THEN** belonging and visibility choices list the same global teams

#### Scenario: Team palette is a subset of the icon library

- **WHEN** staff edit team A and select icons soldier, tank, and mines
- **THEN** that team's palette contains only those icons

#### Scenario: Team B can use a different soldier icon

- **WHEN** staff assign team B a different soldier icon plus drone and base
- **THEN** team B's palette is independent of team A's palette

#### Scenario: Quick-add preset omits coordinates

- **WHEN** staff add a team quick-add preset with kind marker, a palette icon, title "Enemy mortar", and a TTL
- **THEN** the preset stores those fields and does not store coordinates

### Requirement: Icons are a staff-managed library

The system SHALL store icons as catalog items with a title, a media file, and an availability of staff, player, or both. Deleting an icon MUST NOT delete geo objects; those objects SHALL use the default marker icon.

#### Scenario: Staff add an icon

- **WHEN** an Organizer or Administrator creates an icon with title, file, and availability
- **THEN** the icon appears in the library and can be chosen for palettes, presets, and objects

#### Scenario: Deleting an icon keeps objects

- **WHEN** staff delete an icon that is referenced by existing geo objects
- **THEN** those objects remain and display the default marker

### Requirement: Deleting a map deletes its objects

When a map is deleted, the system SHALL delete every geo object that belongs to that map.

#### Scenario: Map delete removes objects

- **WHEN** staff delete a map that has geo objects
- **THEN** those geo objects are no longer stored or returned for that map

### Requirement: Deleting a team deletes referencing objects

When a team is deleted, the system SHALL delete every geo object whose belonging is that team or whose visibility list includes that team. Icon library items SHALL remain.

#### Scenario: Team delete removes owned objects

- **WHEN** staff delete a team that is the belonging of one or more geo objects
- **THEN** those geo objects are deleted

#### Scenario: Team delete removes objects that listed it in visibility

- **WHEN** staff delete a team that appears only in an object's visibility list
- **THEN** that object is deleted

#### Scenario: Team delete does not remove icons

- **WHEN** staff delete a team that used icons in its palette
- **THEN** those icon catalog items still exist

### Requirement: Catalog screens are staff-only

Map, team, and icon list and form screens SHALL be available only to Administrators and Organizers.

#### Scenario: Member is denied catalog screens

- **WHEN** a user without Administrator or Organizer roles requests a map, team, or icon management screen
- **THEN** the system refuses access

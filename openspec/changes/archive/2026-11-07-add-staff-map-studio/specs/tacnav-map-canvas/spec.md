## Purpose

Provides a front-end Leaflet studio where administrators and organizers draw and inspect geo objects, measure distance, read coordinates, and filter layers, using a modular panel that later slices can reuse.

## ADDED Requirements

### Requirement: Canvas is a staff-only front-end page

The map editor SHALL be a front-end page, not a wp-admin screen. Only Administrators and Organizers SHALL open it in this slice. Other authenticated or anonymous requests SHALL be refused.

#### Scenario: Organizer opens the canvas

- **WHEN** an Organizer requests the canvas for a map they may manage
- **THEN** the page loads the map view using the stored center, zoom, and default basemap

#### Scenario: Non-staff is refused

- **WHEN** a user without Administrator or Organizer roles requests the canvas URL
- **THEN** the system refuses access and does not return geo objects for that surface

### Requirement: Add menu starts a drawing mode that does not auto-finish

The canvas SHALL provide an add control that lists marker, circle, polyline, and polygon. Choosing a type SHALL enter a drawing mode that continues until the user confirms or cancels. Marker placement SHALL take one tap. Polyline and polygon vertices SHALL remain draggable while drawing. A polygon SHALL close on confirm. A circle SHALL be placed by a center tap and a draggable radius. Confirming a full-type drawing SHALL open the inspector; the object SHALL be stored only after staff save. A visible mode bar SHALL show the active drawing or tool state.

#### Scenario: Line drawing waits for confirm

- **WHEN** staff choose polyline and tap two or more vertices
- **THEN** drawing continues until they confirm or cancel

#### Scenario: Inspector opens after confirm

- **WHEN** staff confirm a completed marker, circle, line, or polygon
- **THEN** the inspector opens for remaining fields and the object is not persisted until they save

### Requirement: Existing objects open a popup

Selecting an existing object SHALL show a popup with icon, title (or a fallback when title is empty), description, and belonging. Edit and delete actions SHALL appear in that popup only when the edit resolver allows them. Edit SHALL open the same inspector used after create. Object titles MAY be omitted from the map face unless a labels filter is on.

#### Scenario: Popup hides edit when the viewer cannot edit

- **WHEN** staff view an object they are not allowed to edit
- **THEN** the popup shows identity fields and does not show edit or delete actions

#### Scenario: Popup shows edit when allowed

- **WHEN** an Organizer selects an object
- **THEN** the popup includes edit and delete actions

### Requirement: Measure tool is ephemeral

The canvas SHALL provide a measure tool separate from add. Measure mode SHALL accept a chain of taps, show each segment and the running total dynamically (including a live last segment), and SHALL NOT persist a geo object. A stop action SHALL stop adding vertices while leaving the chain visible. A clear action SHALL remove the overlay.

#### Scenario: Measure does not create an object

- **WHEN** staff complete a measure chain and stop
- **THEN** no geo object is stored

#### Scenario: Stop leaves the chain visible

- **WHEN** staff press stop after measuring
- **THEN** no further vertices are added and the distance overlay remains until cleared

### Requirement: Coordinate tool is ephemeral

The canvas SHALL provide a coordinate tool separate from add and measure. In that mode a tap SHALL show the tapped latitude and longitude without creating an object. A later tap SHALL replace the readout. Leaving the mode or dismissing the readout SHALL clear it.

#### Scenario: Coordinate tap does not create an object

- **WHEN** staff activate the coordinate tool and tap the map
- **THEN** the interface shows that point's coordinates and stores no geo object

### Requirement: Staff filters and basemaps

The canvas SHALL filter the already-authorized object set by belonging team and SHALL offer a control to include expired objects. A labels control SHALL toggle titles on the map face for every geometry kind. Staff SHALL be able to keep the unlabeled satellite basemap and optionally overlay place-name labels. The map's default SHALL be unlabeled satellite unless staff saved the labels overlay on the map form.

#### Scenario: Expired objects stay hidden until requested

- **WHEN** staff view the canvas with the expired filter off
- **THEN** expired objects are not shown

#### Scenario: Default layer is unlabeled satellite

- **WHEN** staff open a map whose default basemap is unlabeled satellite
- **THEN** the canvas shows satellite imagery without place-name labels

### Requirement: Panel is modular and usable on narrow viewports

The canvas SHALL compose tools, filters, inspector, and staff actions as independent modules so later role-based assembly can hide modules without a second editor. On a narrow viewport the map SHALL remain the primary surface and the panel SHALL use a compact overlay (such as a bottom sheet) rather than a persistent full-height sidebar.

#### Scenario: Narrow viewport keeps the map dominant

- **WHEN** staff open the canvas on a narrow viewport
- **THEN** drawing and inspection chrome does not permanently consume most of the map height

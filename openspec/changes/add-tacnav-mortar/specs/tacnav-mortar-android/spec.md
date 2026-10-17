# Spec Delta

## Purpose

Defines the English-language Tac Nav Mortar Android device that converts a calibrated phone pose into delayed simulated impacts and delegates every target reaction to addressed applications.

## ADDED Requirements

### Requirement: Tac Nav Mortar runs as an activated Android device

The application SHALL be named “Tac Nav Mortar”, use package id `com.tacnav.mortar`, support Android 9 and newer, and present all application text in English. It SHALL require a compatible Google Play Services installation for Firebase wake-up features. It MUST NOT use a server API before successful QR or manual activation.

#### Scenario: Supported phone activates

- **WHEN** a phone on Android 9 or newer with Google Play Services activates using a valid server URL and one-time code
- **THEN** Tac Nav Mortar stores the returned application identity, credential, scopes, defaults, and reference settings

#### Scenario: Unsupported Android is not offered

- **WHEN** a device runs an Android version older than 9
- **THEN** the release is not considered a supported Tac Nav Mortar installation

### Requirement: Gameplay settings live on the activated phone

The activated phone SHALL keep its gameplay settings locally. Changing those settings, including the allowed firing modes, allowed aiming modes, approximate-guidance radius, elevation-calibration reference angle, and whether a shot publishes this mortar’s position, SHALL require a local PIN whose initial value is `4321`; performing calibration or choosing among already allowed modes SHALL NOT require the PIN. The default elevation-calibration reference angle SHALL be 60 degrees, the default approximate-guidance radius SHALL be 30 metres, the default GPS limits SHALL be 15 metres of horizontal error and 15 seconds of fix age, and marking the mortar position on shot SHALL be off. Local changes MUST NOT update the server reference. Successful reactivation SHALL replace local gameplay settings and calibration with the newly returned reference state. The application identity, credential, server URL, and allowed scopes MUST NOT be editable as local gameplay settings.

#### Scenario: PIN protects radius

- **WHEN** a user attempts to change the damage radius
- **THEN** the application requires the local PIN before saving it

#### Scenario: Calibration is quick

- **WHEN** a player opens direction or elevation calibration
- **THEN** the player can complete it without entering the PIN

#### Scenario: Mode availability is protected

- **WHEN** a user attempts to enable or disable a firing or aiming mode
- **THEN** the application requires the local PIN before saving the allowed-mode list

#### Scenario: Player selection is not protected

- **WHEN** more than one already allowed firing or aiming mode is available
- **THEN** the player can switch the active mode without entering the PIN

#### Scenario: Reactivation resets customization

- **WHEN** the application is activated again after local settings were changed
- **THEN** it replaces those settings with the server reference and clears installation-specific calibration

### Requirement: Current map and team are selected from activated scopes

A PIN-authorized user SHALL be able to select the current map and team only from the scopes returned at activation. The application SHALL refuse to start a shot when the selected team is not available on the selected map. After activation the initiation screen SHALL show the map title on the left, the mortar name in the center, and the team title on the right, without Map or Team labels.

#### Scenario: User selects an allowed context

- **WHEN** a PIN-authorized user selects an allowed map and a team listed on it
- **THEN** later impact, search, and message requests use that context

#### Scenario: Initiation shows catalog titles

- **WHEN** an activated phone opens the initiation screen
- **THEN** it shows the map name, mortar name, and team name rather than numeric identifiers

#### Scenario: Unallowed context is unavailable

- **WHEN** a map or team is not in the activated scopes
- **THEN** the application does not offer it as a local selection

### Requirement: Manual icon selection uses the live site catalog

The PIN-authorized impact-icon setting SHALL fetch the complete site icon catalog while online, without limiting choices to the current team palette. The chosen icon id and usable image SHALL be cached for offline firing. If the chosen icon is no longer accepted, the application SHALL refresh the catalog and use the default marker until another icon is chosen.

#### Scenario: User chooses an icon outside the team palette

- **WHEN** the online icon catalog contains an icon outside the current team’s player palette
- **THEN** the mortar user can select and use it for impacts

### Requirement: Active game mode keeps required work foreground

The player SHALL explicitly start and stop an active game session from a separate initiation screen whose primary control is “Prepare for battle”. The session screen SHALL show GPS accuracy at the top left and range at the top right as numbers only, a large aiming readout, the fire control, a stand-down control that returns to initiation and stops the session, unprotected gameplay-settings access, and a message list that records each accepted shot and plays a shot sound. While the session is active and the activity is visible, the display SHALL stay awake without player touch. While active, Tac Nav Mortar SHALL also run a foreground service with a persistent notification and keep pending shots, location acquisition, and an armed acoustic trigger working if the player still turns the screen off, subject to Android permissions and force-stop behavior. Restarting the phone MUST NOT silently re-arm the microphone; the player SHALL start a new active session.

#### Scenario: Battle mode keeps the display awake

- **WHEN** the player starts an active session and leaves the application visible
- **THEN** the display remains on until the player stands down

#### Scenario: Screen turns off during flight

- **WHEN** the player turns off the screen while an active session has an in-flight shot
- **THEN** the shot remains scheduled and is processed when due or after connectivity returns

#### Scenario: Reboot does not arm microphone

- **WHEN** Android restarts
- **THEN** Tac Nav Mortar does not begin listening until the player starts an active session again

### Requirement: A shot requires a fresh valid sensor snapshot

At trigger time the application SHALL capture the current latitude, longitude, horizontal accuracy, location age, true heading, calibrated elevation, and trigger time. It SHALL block the shot and name the reason when any value is unavailable, the location exceeds the configured maximum age or horizontal error, the elevation is outside the configured firing range, or required sensor accuracy is unusable. A blocked acoustic trigger MUST NOT create a shot.

#### Scenario: Poor GPS blocks firing

- **WHEN** the maximum accepted horizontal error is 15 metres and Android reports plus or minus 16 metres
- **THEN** the application blocks the shot and reports insufficient GPS accuracy

#### Scenario: Stale location blocks firing

- **WHEN** the configured maximum age is 15 seconds and the last location is older
- **THEN** the application blocks the shot

#### Scenario: Valid snapshot creates one shot

- **WHEN** every required value is current and within its configured limits
- **THEN** the trigger creates exactly one locally durable in-flight shot from that snapshot

### Requirement: Direction and elevation can be calibrated rapidly

The mounted phone SHALL treat the direction through the screen (the back of the phone) as the firing direction whenever that axis has a horizontal component, including at Save and at trigger, and the top edge only when the phone is lying flat. Tube elevation SHALL be measured from horizontal without clamping before the installation offset: a phone lying flat reads near 0°, lifting the top edge SHALL increase the angle toward 90°, and dipping the top below the horizon SHALL read negative. Direction SHALL be converted to true north after geomagnetic correction and a saved installation offset. Known-point calibration SHALL use the live mortar GPS fix and the uncalibrated true heading at the moment the player confirms Save. It SHALL refuse a point closer than 25 metres to that fix so a self-coordinate cannot save an offset that forces the current aim to north. After a successful save, that same aim SHALL read the true bearing to the known point. Elevation calibration SHALL save the difference between the configured reference angle, defaulting to 60 degrees, and the phone’s current measured elevation, including when the phone is nearly flat. The calibrated display MAY fall below the reference when the tube is lowered. The application SHALL expose sensor-quality warnings because nearby metal can make one calibration inaccurate at other headings.

#### Scenario: Known-point direction calibration

- **WHEN** a player aims the phone at a selected known point at least 25 metres away and confirms calibration
- **THEN** the application saves the difference between the known true bearing and the heading at Save, and that same aim reads the known bearing

#### Scenario: Nearby known point is refused

- **WHEN** the entered point is within 25 metres of the live mortar GPS fix
- **THEN** the application refuses to save a direction offset and leaves the heading unchanged

#### Scenario: Repositioned mortar can be recalibrated

- **WHEN** the player moves the mortar to a new position
- **THEN** direction and elevation calibration remain directly available without the settings PIN

#### Scenario: Default elevation calibration uses 60 degrees

- **WHEN** the reference remains at its default and the player activates elevation calibration
- **THEN** the application saves the offset that makes the current physical position read as 60 degrees

#### Scenario: Lowering the tube can read below the reference

- **WHEN** elevation is calibrated at the reference and the player then lowers the tube
- **THEN** the calibrated elevation decreases below the reference instead of sticking at 60 degrees

### Requirement: Range is a linear function of elevation

For an elevation between configured minimum and maximum angles, the application SHALL calculate range by linearly interpolating from maximum distance at the minimum angle to minimum distance at the maximum angle:

`distance = maxDistance - ((angle - minAngle) / (maxAngle - minAngle)) * (maxDistance - minDistance)`.

It SHALL calculate the impact coordinate geodesically from the captured shot origin, calibrated true heading, and calculated distance.

#### Scenario: Endpoint angles use endpoint ranges

- **WHEN** settings are 60 degrees to 85 degrees and 200 metres to 10 metres
- **THEN** 60 degrees yields 200 metres and 85 degrees yields 10 metres

#### Scenario: Intermediate angle is interpolated

- **WHEN** the elevation lies between the configured endpoints
- **THEN** the range lies proportionally between the configured distance endpoints

### Requirement: Allowed firing modes govern the player trigger

The PIN-authorized firing-mode setting SHALL present checkboxes for button and acoustic firing and MUST require at least one checked mode. When exactly one mode is allowed, the player screen SHALL use it without showing a mode selector. When both are allowed, the player SHALL be able to choose the active mode without the PIN. Both modes SHALL use the same sensor validation and shot pipeline.

#### Scenario: One firing mode needs no selector

- **WHEN** only acoustic firing is checked in PIN-authorized settings
- **THEN** the player screen uses acoustic firing and shows no button-versus-acoustic selector

#### Scenario: Two firing modes can be selected

- **WHEN** both firing modes are checked
- **THEN** the player can switch between button and acoustic firing without entering the PIN

#### Scenario: Empty firing list is refused

- **WHEN** a PIN-authorized user tries to save with neither firing mode checked
- **THEN** the application refuses the settings change

### Requirement: Button and acoustic triggers use the same firing pipeline

The button mode SHALL request a shot immediately when pressed. After that press produces an accepted shot, the application SHALL attempt to turn on the phone torch for five seconds. A later accepted button shot during those five seconds SHALL restart the five-second torch period. Missing or unavailable torch hardware SHALL show a warning but MUST NOT cancel an accepted shot. Acoustic mode SHALL be armed by an explicit “Ready to fire” control, display a live relative microphone level, compare it with a configurable digital threshold and minimum impulse duration, and suppress repeated triggers during a configurable debounce interval. It SHALL remain armed after a successful trigger until the player stops listening. Microphone denial MUST disable acoustic firing without disabling allowed button firing.

#### Scenario: Button shot lights the torch

- **WHEN** a button press passes sensor validation and creates a shot
- **THEN** the application attempts to keep the phone torch on for five seconds

#### Scenario: Torch failure does not cancel the shot

- **WHEN** a valid button shot is created on a phone whose torch is unavailable
- **THEN** the shot remains accepted and the application reports the torch problem

#### Scenario: Firecracker impulse triggers one shot

- **WHEN** armed acoustic input crosses the configured threshold for the required duration
- **THEN** the application attempts exactly one sensor-validated shot and suppresses duplicate triggers during debounce

#### Scenario: Button works without microphone

- **WHEN** microphone permission is denied but shot sensors are valid
- **THEN** the player can still fire with the button

### Requirement: Multiple shots can remain in flight

Each accepted shot SHALL receive a unique `shot_id`, retain its immutable sensor snapshot and calculated impact, and become due after its locally configured flight duration. Firing another valid shot MUST NOT cancel, replace, or delay an earlier in-flight shot.

#### Scenario: Second shot precedes first impact

- **WHEN** the player fires twice within the configured flight duration
- **THEN** both shots remain independently scheduled with their own ids and due times

### Requirement: Due shots use target positions current at resolution

When a shot becomes due and the server is reachable, the application SHALL search its configured damage circle against the selected map before creating the visible impact marker. It SHALL therefore use objects at their server positions at resolution time rather than at trigger time. It SHALL then create a public point marker at the calculated impact with the configured impact icon, title derived from the mortar name, and second-based lifetime, whether the search found hits, found only misses, or failed. Object-create failure or message failure SHALL remain retryable without generating a new random result for an already resolved object.

#### Scenario: Target leaves during flight

- **WHEN** a target was inside the future circle at firing time but is outside it when the shot becomes due
- **THEN** the target is absent from that shot’s search result

#### Scenario: Impact is a point and search is a circle

- **WHEN** a due shot resolves successfully
- **THEN** search uses the configured-radius circle and the displayed impact is a marker at its center

#### Scenario: Own impact is not searched

- **WHEN** a due shot resolves
- **THEN** search completes before that shot’s impact marker is created

#### Scenario: A miss still marks the landing

- **WHEN** search returns no objects or every roll misses
- **THEN** the application still creates the impact marker at the calculated landing

#### Scenario: Search failure still marks the landing

- **WHEN** search fails after a shot becomes due
- **THEN** the application still creates the impact marker and may retry hit delivery later

### Requirement: A shot can mark the mortar position

A PIN-protected gameplay setting SHALL control whether an accepted shot immediately publishes a public point at the captured mortar origin. That setting SHALL default to off. When it is on, the point SHALL use the default map marker, not the impact icon, a 30-second lifetime, and a title derived from the mortar name. A later shot SHALL update that same object instead of creating another. A failure to publish that marker MUST NOT roll back the shot.

#### Scenario: Own-position mark is off by default

- **WHEN** the player has not changed the protected setting and fires
- **THEN** the application does not create a marker at the mortar origin

#### Scenario: Enabled own-position mark lasts 30 seconds

- **WHEN** the protected setting is on and a shot is accepted
- **THEN** the application creates a public default marker at the shot origin with a 30-second lifetime

#### Scenario: Later shots move the same mortar marker

- **WHEN** the protected setting is on and a second shot is accepted
- **THEN** the application updates the existing mortar-position object instead of adding another

### Requirement: Offline due shots resolve after connectivity returns

An accepted shot SHALL be stored durably on the phone. If it becomes due without server connectivity, the application SHALL retain it and resolve it after connectivity returns, using object positions current at that later resolution. App restart or screen-off MUST NOT silently discard it.

#### Scenario: Network returns after impact time

- **WHEN** a shot becomes due offline and the server becomes reachable later
- **THEN** the application performs search, impact creation, hit calculation, and message delivery using the original shot and the then-current object picture

### Requirement: Every found object gets an independent linear hit roll

The mortar SHALL apply no target-id, object-type, ownership, visibility, or team allowlist. It SHALL calculate nearest-geometry distance `d` locally from the impact point and each returned raw geometry: point-to-point for a marker, distance to the nearest edge after accounting for radius for a circle, distance to the nearest segment for a polyline, and zero inside a polygon or distance to its nearest boundary outside. For every search result it SHALL calculate probability independently from that local distance, configured radius `R`, and configured edge probability `Pedge`:

`P(d) = 1 - (1 - Pedge) * d / R`.

Probability SHALL be 100 percent at zero distance and equal `Pedge` at the radius boundary. The application SHALL generate and persist one random roll per shot and object. Only results whose roll is at most their probability SHALL be included in the addressed hit batch; misses SHALL remain local. The payload SHALL include shot id, impact, distance, probability, roll, and source context so recipients can make their own decisions.

#### Scenario: Center is certain

- **WHEN** an object’s nearest geometry distance is zero
- **THEN** its calculated probability is 100 percent

#### Scenario: Raw geometry is measured on the phone

- **WHEN** search returns an object id, kind, and geometry without `distance_m`
- **THEN** Tac Nav Mortar calculates the nearest distance locally before calculating probability

#### Scenario: Boundary uses configured probability

- **WHEN** an object’s distance equals the configured damage radius
- **THEN** its probability equals the configured edge probability

#### Scenario: Three objects are rolled independently

- **WHEN** search returns three objects
- **THEN** the mortar persists one probability and one random roll for each and includes only successful objects in the hit batch

#### Scenario: Friendly object is not filtered

- **WHEN** a same-team object is returned by search and passes its roll
- **THEN** the mortar addresses it and leaves the recipient application to decide whether the hit matters

### Requirement: Entered coordinates are aiming assistance only

The player SHALL be able to enter target coordinates. The application SHALL show target distance, required true bearing, required elevation from the inverse linear range function, live left-right and high-low corrections, predicted impact, and expected miss offset. It SHALL indicate when the target is outside configured range. This mode MUST NOT move the calculated impact to the entered coordinate or prevent firing before alignment; every shot SHALL still use its actual trigger-time sensor snapshot.

#### Scenario: Player fires while misaligned

- **WHEN** entered coordinates are visible but the tube is not aligned and the player fires
- **THEN** the impact is calculated from the actual heading and elevation rather than the entered target

#### Scenario: Out-of-range target remains guidance

- **WHEN** entered coordinates are outside the configured distance range
- **THEN** the application reports that condition without changing ordinary sensor-based firing behavior

### Requirement: Allowed aiming modes govern player guidance

The PIN-authorized aiming-mode setting SHALL present checkboxes for free aiming, precise-coordinate guidance, and approximate-coordinate guidance and MUST require at least one checked mode. When exactly one mode is allowed, the player screen SHALL use it without showing an aiming-mode selector. When multiple modes are allowed, the player SHALL be able to choose among them without the PIN.

#### Scenario: One aiming mode needs no selector

- **WHEN** only free aiming is checked
- **THEN** the player screen uses free aiming and shows no aiming-mode selector

#### Scenario: Several aiming modes can be selected

- **WHEN** precise and approximate coordinate guidance are both checked
- **THEN** the player can switch between those two modes without entering the PIN

#### Scenario: Empty aiming list is refused

- **WHEN** a PIN-authorized user tries to save with no aiming mode checked
- **THEN** the application refuses the settings change

### Requirement: Approximate coordinate guidance withholds exact pre-shot correction

Approximate-coordinate guidance SHALL accept target coordinates but, before a shot, SHALL show only `LEFT`, `RIGHT`, `ADD`, or `DROP` direction and whether the predicted impact lies within the configured guidance radius, defaulting to 30 metres. It MUST NOT show the exact correction distance, exact predicted impact coordinate, or exact miss distance before the shot. After a shot is accepted, it SHALL calculate from that shot’s actual predicted impact and show the exact left-right and add-drop correction in metres. Approximate guidance MUST NOT alter the shot coordinate, block firing outside the guidance radius, or change later hit resolution.

#### Scenario: Before firing only coarse direction is shown

- **WHEN** a player enters target coordinates in approximate mode and has not fired
- **THEN** the application shows coarse correction direction and in-zone status without an exact correction distance

#### Scenario: After firing exact correction is shown

- **WHEN** the player fires in approximate mode
- **THEN** the application shows the exact metres left or right and add or drop needed from that shot’s predicted impact

#### Scenario: Approximate mode does not control the impact

- **WHEN** a player fires while the predicted impact is outside the approximate guidance radius
- **THEN** the application accepts any otherwise valid shot and calculates its impact from actual sensors

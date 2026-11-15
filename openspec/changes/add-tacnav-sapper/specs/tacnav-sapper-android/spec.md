# Spec Delta

## Purpose

Defines Tac Nav Sapper, an Android application that lets a player lay, detect, reveal, and clear minefields on the Tac Nav map, with every game decision made on the phone.

## ADDED Requirements

### Requirement: Application identity
Tac Nav Sapper SHALL be a standalone English-language application named "Tac Nav Sapper", with package `com.tacnav.sapper`, supporting Android 9 and newer with Google Play Services for location. It SHALL use the supplied Tac Nav Sapper launcher icon. It SHALL be built from its own project and MUST NOT depend on another Tac Nav application being installed. It MUST NOT register for push messages.

#### Scenario: Sapper runs alone
- **WHEN** the application is installed on a phone without Tac Nav Mortar or Tac Nav Object
- **THEN** it activates and runs normally

### Requirement: Home screen follows the connection
Before activation, the home screen SHALL offer Scan QR and Settings. After activation, it SHALL offer Detect, Lay mines, and Clear mines, plus a smaller Settings control. Every mode screen SHALL offer a Back control at the bottom that returns to the home screen.

#### Scenario: First start
- **WHEN** the application opens without an activation
- **THEN** only Scan QR and Settings are offered

#### Scenario: Activated phone
- **WHEN** the application opens after a successful activation
- **THEN** Detect, Lay mines, and Clear mines are offered, with a smaller Settings control

### Requirement: Activation resets the phone
The application SHALL activate by scanning a QR code, or by entering a server URL and one-time code in Settings. Every successful activation SHALL:

- replace local settings with the server reference;
- set the settings PIN from the reference;
- reset the minefield and No mines limits;
- discard laying and clearing progress.

A refused activation MUST NOT change the current activation.

#### Scenario: QR activation restores the reference
- **WHEN** a phone with locally changed settings and two laid minefields scans a new staff QR
- **THEN** it uses the server reference settings and can lay the full number of minefields again

### Requirement: Settings are protected by PIN
Every Sapper setting SHALL be editable only after entering the PIN. This includes the map and team, which SHALL be limited to the activated scopes. Before the first activation the PIN SHALL be `4321`. Icon settings SHALL browse the complete catalog while online. Saved changes MUST stay on the phone and MUST NOT change the server reference. Repeated wrong PINs SHALL delay further attempts.

Settings SHALL offer Reset connection. It SHALL remove the credential and every local setting, progress, and counter, and SHALL return to the first home screen. The server activation stays until staff revoke it or a new activation replaces it.

#### Scenario: Wrong PIN
- **WHEN** a user enters a wrong PIN
- **THEN** settings stay closed and repeated failures delay further attempts

#### Scenario: Manual activation before the first QR
- **WHEN** a user opens Settings on a new phone with PIN `4321` and enters a server URL and code
- **THEN** the phone activates as with a QR code

#### Scenario: Reset connection
- **WHEN** a PIN-authorized user chooses Reset connection
- **THEN** the home screen shows only Scan QR and Settings

### Requirement: The application works on screen only
The application SHALL detect, hold, and place markers only while it is open in the foreground. While it is open, the display SHALL stay on without touch. It MUST NOT request background location and MUST NOT run a foreground service. Leaving the application SHALL stop searching and SHALL pause every hold without losing saved progress.

#### Scenario: Phone locked during laying
- **WHEN** the user locks the phone during a laying hold
- **THEN** the hold stops and the saved progress is unchanged when the application is opened again

### Requirement: GPS accuracy gates every write
Laying, marking, clearing, and placing a No mines marker SHALL require a fix that is no older than 15 seconds and no worse than the configured GPS accuracy limit. While that is not met, the control SHALL be unavailable and SHALL say why. Detect SHALL keep running with any fix and SHALL show a warning while the fix is worse than the limit.

#### Scenario: Poor GPS
- **WHEN** the fix accuracy is 25 metres and the limit is 15 metres
- **THEN** Lay mines, Clear mines, Mark minefield, and No mines are unavailable with a GPS message, and Detect still signals

### Requirement: Fixes are smoothed
The application SHALL smooth successive fixes, weighting each by its reported accuracy and allowing the position to move at walking speed, so that the wander of a standing phone's fixes is damped. After a gap longer than 15 seconds, the next fix SHALL start the smoothing again. The GPS accuracy limit SHALL be checked against each fix's reported accuracy, not against the smoothed estimate.

#### Scenario: Standing phone
- **WHEN** a standing phone receives fixes once a second that alternate 4 metres either side of its real position
- **THEN** after about 10 seconds the position the application uses stays within 2 metres of the real position

#### Scenario: Walking phone
- **WHEN** the user walks at 1.4 metres per second for 30 seconds
- **THEN** the position the application uses is within 3 metres of the user

### Requirement: Detect searches on a movement cadence
While Detect is open, the application SHALL search for objects with role `minefield` in a circle around the current fix. The circle's radius SHALL be twice the detection radius. The application SHALL search again after 5 seconds when the phone has moved more than its fix accuracy since the last search, and otherwise after 30 seconds. It SHALL keep the latest results for the local checks between searches.

#### Scenario: Still phone
- **WHEN** the phone stays in place with Detect open
- **THEN** it searches about every 30 seconds

#### Scenario: Walking phone
- **WHEN** the user walks with Detect open
- **THEN** it searches about every 5 seconds

### Requirement: Detect signals a change of state
About once a second, the application SHALL check every kept minefield against the current fix and heading:

- **Heading** is the true bearing of the top of the phone, or of its back when the phone is held upright, whichever is closer to horizontal.
- **Distance** to a field is zero inside it, and otherwise the distance to its nearest edge.
- The phone **enters** a field when the fix is inside it, and **leaves** it only when the fix is farther outside the field's edge than the fix accuracy, at least 1 metre and at most 5 metres.
- The **detector state** is one of:
  - *inside*: the phone is in any field;
  - *near*: a field is within half the detection radius and within the heading sector;
  - *far*: a field is within the detection radius and within the heading sector;
  - *none*.

A field is within the heading sector when the bearing to its nearest point is within half the sector of the heading.

On entering *far*, the phone SHALL play one short signal. On entering *near* or *inside*, it SHALL play two short signals. Moving to a lower state SHALL be silent. A state's signal SHALL repeat only after that state is left and entered again. A field SHALL count as leaving the sector only once its bearing is about 10 degrees beyond the sector edge.

Fields of the phone's own team, and fields its team can see, SHALL signal like any other field. Signals SHALL use the alarm channel so they are audible with media volume off. Each signal SHALL be shown as a short flash. The screen MUST NOT show the distance or direction to a field.

Detect SHALL show an animated mine detector display that cannot be mistaken for a button, with a short hint that says how to sweep and what one and two signals mean.

#### Scenario: Sweeping across a field
- **WHEN** the user sweeps the phone so that the sector crosses a field 8 metres away, then turns away and back
- **THEN** one short signal plays each time the sector reaches the field

#### Scenario: Approaching a field
- **WHEN** the user walks toward a field in the sector from 8 metres to 4 metres with a 10 metre radius
- **THEN** one short signal plays and then two short signals play

#### Scenario: Pointing at a field
- **WHEN** the user holds the phone still with a field in the sector
- **THEN** no further signal plays after the first

#### Scenario: Entering a field
- **WHEN** the user walks into a field and stays inside
- **THEN** two short signals play once

#### Scenario: Compass jitter at the edge
- **WHEN** the heading wobbles by 3 degrees around the sector edge
- **THEN** no repeated signals play

#### Scenario: GPS wander at a field edge
- **WHEN** a phone with 3 metre accuracy is inside a field and its fixes wander up to 2.5 metres outside the edge
- **THEN** the phone stays inside, no repeated signals play, and it leaves only when the fix is more than 3 metres outside

### Requirement: A skull warns inside a minefield
While Detect or Clear mines knows that the phone stands inside a minefield, the screen SHALL show a red skull warning. This SHALL apply to fields of every team, including the phone's own team and fields with no owner. Entering and leaving a field SHALL follow the edge margin defined for Detect. The warning SHALL disappear once the phone has left every known field.

#### Scenario: Own field
- **WHEN** a Blue sapper with Detect open walks into a Blue field
- **THEN** the red skull warning appears and no Mark minefield button appears

#### Scenario: Next to a field while clearing
- **WHEN** Clear mines targets a field whose edge is 2 metres away
- **THEN** the clear button is offered and no skull warning is shown

#### Scenario: GPS wander while clearing
- **WHEN** a phone with 3 metre accuracy is inside a field in Clear mines and one fix lands 2 metres outside the edge
- **THEN** the skull warning stays, and it disappears when the fix is 4 metres outside

### Requirement: Hold controls are large and show percent
Every hold button SHALL be a large control. Its progress bar SHALL be thick and SHALL show the progress as a whole percentage.

#### Scenario: Laying partly done
- **WHEN** the saved laying progress is 42 percent
- **THEN** the Lay mines progress bar reads 42%

### Requirement: A sapper marks another team's minefield
While Detect shows the phone inside a minefield that another team owns or that has no owner, and whose visibility list does not already include the phone's team or everyone, a blinking red Mark minefield button SHALL appear.

Holding the button for the mark time SHALL add the phone's team to the field's visibility list and SHALL keep the teams already listed. Hold time SHALL accumulate per field. Releasing the button, the fix becoming unusable, or leaving the field SHALL stop the hold and SHALL keep the time for that field, and the time SHALL survive a restart of the application. Time for a field the team can already see SHALL be discarded. If another edit changed the field first, the application SHALL read it again and retry. Marking SHALL have no limit. The button SHALL disappear once the team can see the field.

#### Scenario: Hidden enemy field
- **WHEN** a Blue sapper stands inside a Red field visible only to Red and holds Mark minefield for 30 seconds
- **THEN** the field becomes visible to Red and Blue

#### Scenario: Own field
- **WHEN** a Blue sapper stands inside a Blue field
- **THEN** no Mark minefield button appears and the entry signal still plays

#### Scenario: Neutral staff field
- **WHEN** a sapper stands inside a staff field with no owner that its team cannot see
- **THEN** the Mark minefield button appears

#### Scenario: Release keeps the mark
- **WHEN** the user releases Mark minefield after 20 of 30 seconds
- **THEN** the next hold on that field completes after 10 more seconds

#### Scenario: Restart keeps the mark
- **WHEN** the user leaves the application after 20 of 30 seconds of marking and opens Detect again inside the same field
- **THEN** the Mark minefield progress bar reads 66% and the next hold continues from there

### Requirement: No mines markers
Detect SHALL offer No mines while the phone is outside every kept minefield. Clear mines SHALL offer it after a successful clearing. One tap SHALL place an ordinary marker with these properties:

- at the phone's position;
- titled `No mines`;
- with the configured No mines icon;
- visible to the phone's team;
- with no expiry.

Each activation SHALL allow the configured maximum number of markers. 0 SHALL mean unlimited. A marker SHALL count once the server accepts it. When the limit is reached, the control SHALL be unavailable and SHALL say so.

#### Scenario: Place a marker
- **WHEN** a sapper outside every field taps No mines with a usable fix
- **THEN** a No mines marker appears on its team's map at the phone's position

#### Scenario: Limit reached
- **WHEN** a sapper has placed 10 markers with a limit of 10
- **THEN** No mines is unavailable with a message that no markers are left

#### Scenario: Inside a field
- **WHEN** the phone is inside a kept minefield in Detect
- **THEN** No mines is not offered

### Requirement: Lay mines
Lay mines SHALL offer a hold button with a progress bar that accumulates hold time up to the lay time:

- The first press SHALL record the start fix and the heading.
- Releasing the button SHALL keep the progress, and the progress SHALL survive a restart of the application.
- When the phone is farther from the start fix than half the shorter minefield side, after allowing for both fixes' accuracy, the hold SHALL be unavailable until Reset laying is used.
- Reset laying SHALL be offered below the button and SHALL discard the progress and the start fix.

On completion the application SHALL create a polygon with role `minefield` and these properties:

- a rectangle centred on the start fix;
- its length side along the recorded heading and its width side across it;
- the configured minefield icon and the title `Minefield`;
- visible to the phone's team, with no expiry.

On completion the phone SHALL also play three short signals on the alarm channel and SHALL show a full-screen Minefield laid confirmation for 5 seconds. The confirmation SHALL appear only for a laying completed while Lay mines is open, and SHALL NOT repeat when the screen is opened again.

The application SHALL retry the create until the server accepts it, without ever storing two fields for one completion. Each activation SHALL allow the configured maximum number of minefields. 0 SHALL mean unlimited. A field SHALL count once the server accepts it. When the limit is reached, Lay mines SHALL be unavailable and SHALL say so.

#### Scenario: Lay a field
- **WHEN** a Blue sapper holds Lay mines for 3 minutes in total at one spot with the phone top facing 40 degrees true
- **THEN** a 10 by 10 metre Blue minefield centred on that spot and aligned to 40 degrees appears for Blue

#### Scenario: Completion is confirmed
- **WHEN** a laying hold completes
- **THEN** three short signals play and Minefield laid is shown for 5 seconds

#### Scenario: Release and resume
- **WHEN** the user releases after 2 minutes and holds again later at the same spot
- **THEN** the progress continues from 2 minutes

#### Scenario: Walked away
- **WHEN** the phone moves 12 metres from the start fix during laying with 10 metre sides
- **THEN** the hold is unavailable until Reset laying

#### Scenario: Limit reached
- **WHEN** a sapper has laid 3 fields with a limit of 3
- **THEN** Lay mines is unavailable with a message that no minefields are left

### Requirement: Clear mines
When Clear mines opens, and then on the Detect search cadence, the application SHALL search for minefields within the current fix accuracy of the phone. When none is found, the screen SHALL say so and SHALL offer no clear button. Otherwise it SHALL target the nearest field. When the phone is inside several fields, it SHALL target the one whose centre is nearest.

A hold button with a progress bar SHALL accumulate hold time for that field up to the clear time. Releasing the button SHALL keep the progress for that field, and the progress SHALL survive a restart of the application.

On completion the application SHALL expire the field immediately, retrying until the server accepts. It SHALL then show `This area is safe` and offer No mines. Progress for a field that disappears from the map SHALL be discarded. A field of any team MAY be cleared.

#### Scenario: Clear a field
- **WHEN** a sapper inside a field holds Clear mines for 3 minutes in total
- **THEN** the field leaves the map, the screen says This area is safe, and No mines is offered

#### Scenario: Not on a field
- **WHEN** the phone is 30 metres from every field with a 6 metre fix accuracy
- **THEN** no clear button is shown

#### Scenario: Progress kept per field
- **WHEN** the user clears one field halfway, walks into another field, and returns to the first
- **THEN** the first field resumes from half

### Requirement: Work continues without network
Without network, Detect SHALL keep checking the last search results and SHALL show that it is offline. A completed laying, mark, clearing, or No mines marker SHALL be kept and sent when the network returns, without another hold. Sending again MUST NOT create a second object.

#### Scenario: Laying completes offline
- **WHEN** a laying hold completes while the phone is offline
- **THEN** one minefield appears when the network returns and the user does not hold again

# Spec Delta

## Purpose

Defines Tac Nav Object, an Android application that turns a phone into a guarded, destructible field objective on the Tac Nav map, with every game decision made on the phone.

## ADDED Requirements

### Requirement: Application identity
Tac Nav Object SHALL be a standalone English-language application with package `com.tacnav.beacon`, supporting Android 9 and newer with Google Play Services. It SHALL be built from its own project and MUST NOT depend on Tac Nav Mortar being installed.

#### Scenario: Object runs alone
- **WHEN** the application is installed on a phone without Tac Nav Mortar
- **THEN** it activates and runs normally

### Requirement: Activation resets the phone
The application SHALL activate by QR or by manually entered server URL and one-time code. Every successful activation SHALL replace local settings with the server reference, set the settings PIN from the reference, clear the hit count, clear the destroyed state, and clear the downtime state.

#### Scenario: QR activation after destruction
- **WHEN** a destroyed phone activates with a new staff code
- **THEN** it starts in Waiting with zero hits and the server reference settings

### Requirement: Settings are protected by PIN
Every Object setting SHALL be editable only after entering the local PIN, including the map and team, which SHALL be limited to the activated scopes. The icon settings SHALL browse the complete catalog while online. Saved changes MUST stay local and MUST NOT change the server reference. A change saved during a session SHALL apply from the next activation.

#### Scenario: Wrong PIN
- **WHEN** a user enters a wrong PIN
- **THEN** settings stay closed and repeated failures delay further attempts

#### Scenario: Edit during a session
- **WHEN** a PIN-authorized user changes the shape while Active
- **THEN** the current session keeps its shape and the next activation uses the new one

### Requirement: Protected reset
The protected settings SHALL offer a reset that deletes the current map object and hint area, clears the hit count, destroyed state, and downtime state, returns to Waiting, and prepares a new identity for the next wreck. A wreck already on the map SHALL stay.

#### Scenario: Reset a destroyed object
- **WHEN** a PIN-authorized user resets a destroyed phone
- **THEN** the phone returns to Waiting with zero hits and the earlier wreck stays on the map

#### Scenario: Next destruction after reset
- **WHEN** the phone is destroyed again after a reset
- **THEN** a second wreck with its own id appears

### Requirement: Shape comes from two size fields
With both size fields empty the object SHALL be a point. With exactly one filled it SHALL be a circle with that radius in metres. With both filled it SHALL be a rectangle whose first side lies along the top of the phone and whose second side lies across it. The phone's position SHALL be the centre of every shape. A rectangle's orientation SHALL be taken once when deployment starts and kept for the session. The application SHALL warn when the compass is unreliable but MUST NOT block activation for that reason.

#### Scenario: One field gives a circle
- **WHEN** only the second size field holds 25
- **THEN** the object is a 25 metre radius circle centred on the phone

#### Scenario: Rectangle follows the phone top
- **WHEN** sizes are 10 and 20 and deployment starts with the phone top facing 40 degrees true
- **THEN** the rectangle's 10 metre side lies along bearing 40 degrees, centred on the phone

#### Scenario: Turning the phone later
- **WHEN** an Active phone is rotated
- **THEN** the rectangle keeps its orientation

### Requirement: Life cycle states
The application SHALL be in exactly one of Waiting, Deploying, Active, or Destroyed, and SHALL keep that state across process death. In Waiting no object SHALL exist on the map. Pressing Activate SHALL start Deploying. With zero deployment seconds the phone SHALL pass directly to Active. Otherwise a progress bar SHALL run for the configured seconds and then enter Active. Cancelling Deploying SHALL delete the object and return to Waiting. Destroyed SHALL NOT accept activation and SHALL end only by QR activation or protected reset.

#### Scenario: Instant activation
- **WHEN** deployment seconds are 0 and the user presses Activate with a valid GPS fix
- **THEN** the phone is Active immediately

#### Scenario: Timed deployment
- **WHEN** deployment seconds are 30 and the user presses Activate
- **THEN** a progress bar runs for 30 seconds before the phone is Active

#### Scenario: Destroyed phone
- **WHEN** a Destroyed phone is opened
- **THEN** activation is unavailable until QR activation or protected reset

### Requirement: GPS accuracy gates activation
The application SHALL refuse to start Deploying while the location is older than 15 seconds or its accuracy is worse than the configured limit, showing the current accuracy and the limit. If accuracy exceeds the limit during Deploying, deployment SHALL be cancelled. A fix worse than the limit during Active MUST NOT move the object and MUST NOT count as displacement, and the phone SHALL stay Active.

#### Scenario: Poor fix blocks the button
- **WHEN** accuracy is 40 metres with a 15 metre limit
- **THEN** Activate is unavailable and both values are shown

#### Scenario: Fix degrades during deployment
- **WHEN** accuracy exceeds the limit during Deploying
- **THEN** deployment is cancelled and the object is deleted

#### Scenario: Fix degrades while Active
- **WHEN** an Active phone receives a 60 metre fix
- **THEN** the phone stays Active and the object does not move

### Requirement: Automatic activation
When automatic activation is enabled, an armed phone SHALL start Deploying as if Activate was pressed after staying still for the configured seconds. Still means every usable fix in that period lies within the larger of the automatic deactivation distance and twice the GPS accuracy limit. Fixes worse than the limit SHALL pause the still timer without resetting it. Moving beyond that radius SHALL restart it. Moving beyond that radius during an automatic deployment SHALL cancel the deployment and return to waiting for stillness. Opening the application SHALL arm it unless the user disarmed it, and an Arm control SHALL re-arm it. Waiting SHALL continue with the screen off once the application has been opened.

#### Scenario: Phone settles
- **WHEN** automatic activation is 60 seconds and the armed phone stays still for 60 seconds
- **THEN** Deploying starts without a button press

#### Scenario: Carried phone stays off
- **WHEN** the armed phone keeps moving
- **THEN** it does not start Deploying

### Requirement: Deactivation
The main screen SHALL offer a Deactivate button without a PIN while Deploying or Active. It SHALL delete the object and hint area and return to Waiting. When an automatic deactivation distance is set, the phone SHALL deactivate itself after several consecutive usable fixes place it beyond that distance from its deployment position, after subtracting both fixes' accuracy. A manual deactivation SHALL disarm automatic activation. An automatic deactivation SHALL wait for a button press in button mode and SHALL stay armed in automatic mode.

#### Scenario: Button deactivation
- **WHEN** anyone presses Deactivate while Active
- **THEN** the object and hint area leave the map and the phone is in Waiting

#### Scenario: Antenna moved in button mode
- **WHEN** a phone without automatic activation moves beyond the distance
- **THEN** it deactivates and waits for Activate

#### Scenario: Antenna moved in automatic mode
- **WHEN** a phone with automatic activation moves beyond the distance and settles
- **THEN** it deactivates, then deploys at the new position after the still period

#### Scenario: Manual stop in automatic mode
- **WHEN** a user presses Deactivate with automatic activation enabled
- **THEN** the phone stays in Waiting until the user presses Arm or Activate

#### Scenario: GPS jitter does not trigger
- **WHEN** consecutive fixes appear beyond the distance only within their reported accuracy
- **THEN** the phone stays Active

### Requirement: Map publication
During Deploying the object SHALL be visible only to the phone's own team, and to staff, with `Deploying` in its description, and it SHALL be searchable. On entering Active without a random area the object SHALL use the configured visibility. Its description SHALL be the configured description followed by the session time such as `Active 12 min`. The object SHALL carry its position accuracy. Writes SHALL use a lifetime so that an object whose phone stops updating leaves the map within about 60 seconds. A moving object SHALL update every few seconds and a still one SHALL update at least every 30 seconds.

#### Scenario: Opponents see nothing during deployment
- **WHEN** a phone is Deploying
- **THEN** opposing players see nothing on the map and own-team players see the object marked Deploying

#### Scenario: Visible to all when Active
- **WHEN** a phone with visibility all and no random area enters Active
- **THEN** every map viewer sees the object

#### Scenario: Moving object follows
- **WHEN** an Active phone moves by more than its fix accuracy
- **THEN** the map object follows within several seconds

#### Scenario: Dead phone leaves the map
- **WHEN** an Active phone loses power
- **THEN** its object leaves the map within about 60 seconds

### Requirement: Session timer
The main screen SHALL show the time Active in the current session, starting at entry to Active. Deploying time SHALL NOT count. The map description SHALL show the same time at minute precision.

#### Scenario: Timer starts after deployment
- **WHEN** a phone finishes a 30 second deployment
- **THEN** the session timer starts from zero

### Requirement: Random hint area
When the random area is enabled, the phone SHALL publish on entering Active a public not-searchable circle of the configured radius titled `<name> area`, with no icon, showing the session time. The real object SHALL then be visible only to the phone's own team and staff, whatever the visibility setting. The area centre SHALL be chosen at random so the object lies inside the area, with the object more likely to lie near the edge than near the centre. The centre SHALL stay fixed until the object's position leaves the area, when a new centre SHALL be chosen, or until deactivation. Hits on the area MUST NOT exist as a concept: only the real object receives hits.

#### Scenario: Opponents see only the area
- **WHEN** a phone with a 100 metre area enters Active
- **THEN** opposing players see a 100 metre circle containing the object but not the object

#### Scenario: Area is stable
- **WHEN** an Active phone moves within its area
- **THEN** the area does not change

#### Scenario: Area is replaced after leaving it
- **WHEN** the phone's position leaves the area
- **THEN** a new area containing the position replaces it

#### Scenario: Distribution favours the edge
- **WHEN** many area centres are generated for one position
- **THEN** the object lies in the outer half of the radius more often than in the inner half, by more than a uniform distribution over the area would give

### Requirement: Hits are counted on the phone
The application SHALL read its mailbox on wake-up push, application start, foreground resume, network return, and periodically while running. Each `hit` event addressed to an object of the current life SHALL add one hit, including an event that arrives after that object was deactivated. The same event MUST count once across retries, duplicates, and process death, even when delivered twice. Hits for objects from before the last reset SHALL be ignored, as SHALL hits while Destroyed. The count SHALL persist through deactivation and restart, and SHALL clear only by QR activation or protected reset. The server MUST NOT decide whether the object is damaged.

#### Scenario: Hit during deployment
- **WHEN** a hit arrives while Deploying below the limit
- **THEN** the count rises and deployment continues

#### Scenario: Duplicate delivery
- **WHEN** the same event arrives twice because the first acknowledgement was lost
- **THEN** the count rises once

#### Scenario: Deactivation does not heal
- **WHEN** a phone with 2 hits deactivates and activates again
- **THEN** the count is still 2

#### Scenario: Indestructible object
- **WHEN** hits to destroy is 0 and a hit arrives
- **THEN** the count rises and the phone is not destroyed

### Requirement: Hit signal
On every counted hit the phone SHALL play a short loud alarm-channel signal, vibrate, and show the count, such as `2 of 5`. The signal SHALL be audible with media volume off and with the screen off.

#### Scenario: Hit with the screen off
- **WHEN** a hit arrives while the screen is off and media volume is zero
- **THEN** the signal sounds and the count updates

### Requirement: Destruction
When the hit count reaches a positive limit, or the downtime allowance runs out, the phone SHALL enter Destroyed immediately. It SHALL sound an alarm-channel signal for about 5 seconds, light the torch for 5 seconds, and vibrate, without waiting for the network. It SHALL fix the wreck position as the current position when a usable fix exists and otherwise as the last known position. It SHALL then create a new permanent object with a new id, the same shape, the destroyed icon, the title `<name> destroyed`, visibility to every viewer, and not searchable, retrying until accepted. The retry MUST NOT produce a second wreck. After that it SHALL delete the live object and hint area. Torch failure MUST NOT prevent destruction.

#### Scenario: Last hit destroys
- **WHEN** a phone with a limit of 3 counts its third hit
- **THEN** the alarm and torch run for about 5 seconds and a public wreck replaces the object on the map

#### Scenario: Destruction offline
- **WHEN** the last hit is applied and the network then drops
- **THEN** the signals still run and the wreck appears at the fixed position once the network returns

#### Scenario: Interrupted during deployment
- **WHEN** the limit is reached during Deploying
- **THEN** deployment stops and the phone is Destroyed

#### Scenario: Wreck ignores later fire
- **WHEN** a mortar fires on a wreck
- **THEN** the wreck is not returned as a target

### Requirement: Downtime allowance
When the downtime allowance is set, a countdown SHALL begin the first time the phone leaves Active after activation or reset, starting from the full allowance. It SHALL run in every state except Active, including Deploying and waiting for stillness, and SHALL pause in Active. Reaching zero SHALL destroy the phone, even when hits to destroy is 0. The countdown SHALL return to the full allowance only after the phone has stayed Active without interruption for the downtime reset time. Otherwise it SHALL resume from the paused value on leaving Active. An empty allowance SHALL disable the countdown. The screen and the persistent notification SHALL show the time left, and while Active SHALL also show the time until full reset.

#### Scenario: Pause without reset
- **WHEN** allowance and reset time are 20 minutes, 5 minutes remain, and the phone is Active for 10 minutes before deactivating
- **THEN** the countdown resumes from 5 minutes

#### Scenario: Full reset
- **WHEN** the same phone stays Active for 20 uninterrupted minutes
- **THEN** the allowance is back to 20 minutes

#### Scenario: Interrupted activity does not add up
- **WHEN** a phone is Active for 12 minutes, deactivates, then is Active for 12 minutes
- **THEN** no full reset happens

#### Scenario: Time runs out
- **WHEN** the countdown reaches zero
- **THEN** the phone is destroyed as if by the last hit

#### Scenario: No allowance
- **WHEN** the allowance is empty
- **THEN** no countdown is shown and downtime never destroys the phone

### Requirement: Downtime warnings
The phone SHALL play a loud alarm-channel signal and post a notification when 5 minutes remain and when 1 minute remains. A warning SHALL be skipped when the countdown starts below its mark.

#### Scenario: Five minutes left
- **WHEN** the countdown crosses 5 minutes
- **THEN** the signal sounds and a notification appears

### Requirement: Recovery after interruption
After process death or reboot the application SHALL restore its state on the next open. A persisted Active session SHALL continue with the session timer counting the whole period, unless a rule below ends it. When a downtime allowance is set, the gap since the last recorded activity SHALL be deducted from the remaining time, and any progress toward full reset SHALL be lost. If nothing remains, the phone SHALL become Destroyed. If the phone is beyond the automatic deactivation distance, it SHALL deactivate as if it had moved. A persisted Deploying phone SHALL return to Waiting and delete its object. The application MUST NOT claim to detect downtime while the phone is off.

#### Scenario: Short restart
- **WHEN** an Active phone restarts after 2 minutes in place with 10 minutes of allowance left
- **THEN** the session continues and 8 minutes of allowance remain

#### Scenario: Long power-off
- **WHEN** a phone stays off longer than its remaining allowance
- **THEN** on the next open it is Destroyed with signals and a wreck

### Requirement: Screen stays awake while open
The display SHALL stay on while the application is open in the foreground, in every state. Leaving the application SHALL restore normal screen behavior, and running work SHALL continue under a persistent notification.

#### Scenario: Application open
- **WHEN** the application is open on any screen
- **THEN** the display does not time out

#### Scenario: Application left
- **WHEN** the user leaves the application while Active
- **THEN** the display can sleep and the session continues

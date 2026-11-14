# Spec Delta

## MODIFIED Requirements

### Requirement: Map publication
During Deploying the object SHALL be visible only to the phone's own team and to staff, with `Deploying` in its description. The object SHALL have the ordinary role in every state. On entering Active without a random area the object SHALL use the configured visibility. Its description SHALL be the configured description followed by the session time such as `Active 12 min`. The object SHALL carry its position accuracy. Writes SHALL use a lifetime so that an object whose phone stops updating leaves the map within about 60 seconds. A moving object SHALL update every few seconds and a still one SHALL update at least every 30 seconds.

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

### Requirement: Random hint area
When the random area is enabled, the phone SHALL publish on entering Active a public circle with role `hint` and the configured radius. The circle SHALL be titled `<name> area`, SHALL have no icon, and SHALL show the session time. The real object SHALL then be visible only to the phone's own team and staff, whatever the visibility setting. The area centre SHALL be chosen at random so that the object lies inside the area and is more likely to lie near the edge than near the centre. The centre SHALL stay fixed until the object's position leaves the area, when a new centre SHALL be chosen, or until deactivation. A hit addressed to the area SHALL be ignored: only hits on the real object count.

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

#### Scenario: Shell into the area is ignored
- **WHEN** a mortar hit is addressed to the hint area and not to the real object
- **THEN** the hit count does not change and no hit signal sounds

### Requirement: Destruction
When the hit count reaches a positive limit, or the downtime allowance runs out, the phone SHALL enter Destroyed immediately. It SHALL sound an alarm-channel signal for about 5 seconds, light the torch for 5 seconds, and vibrate, without waiting for the network. It SHALL fix the wreck position as the current position when a usable fix exists and otherwise as the last known position. It SHALL then create a new permanent ordinary object with a new id, the same shape, the destroyed icon, the title `<name> destroyed`, and visibility to every viewer, retrying until accepted. The retry MUST NOT produce a second wreck. After that it SHALL delete the live object and hint area. Torch failure MUST NOT prevent destruction.

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
- **WHEN** a mortar hit is addressed to a wreck
- **THEN** the phone counts no hit

## ADDED Requirements

### Requirement: Wake-up notification is silent
The notification shown for a wake-up push SHALL make no sound and SHALL NOT vibrate. It SHALL remain a visible notification so that high-priority pushes keep being delivered. A counted hit, a downtime warning, and destruction SHALL keep their own alarm-channel signals.

#### Scenario: Ignored hit stays quiet
- **WHEN** a wake-up push arrives with the screen off for a hit addressed to the hint area
- **THEN** the phone makes no sound and does not vibrate

#### Scenario: Counted hit still sounds
- **WHEN** a wake-up push arrives for a hit on the real object
- **THEN** the hit signal sounds once the hit is counted

# Spec Delta

## ADDED Requirements

### Requirement: Wake-up notification is silent

The notification shown for a wake-up push SHALL make no sound and SHALL NOT vibrate. It SHALL remain a visible notification so that high-priority pushes keep being delivered. Shot sounds and the other signals of the application SHALL be unchanged.

#### Scenario: Hit on an impact marker stays quiet

- **WHEN** another mortar's hit on this mortar's impact marker wakes the phone while the screen is off
- **THEN** the phone makes no sound and does not vibrate

#### Scenario: Shot sound is unchanged

- **WHEN** the player fires an accepted shot
- **THEN** the shot sound plays as before

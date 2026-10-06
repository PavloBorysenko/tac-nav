# Spec Delta

## ADDED Requirements

### Requirement: Staff object card shows the geo-object id

When an Organizer or Administrator opens an object card, the card SHALL show that geo object's id. The card SHALL show the id for every object that card can open, including a self-point and an expired object that staff are still showing. A player card and a guest card MUST NOT show the id. The id MUST NOT be drawn on the map face and MUST NOT appear in the edit form. Opening the card MUST NOT create, update, or delete the object.

#### Scenario: An Organizer sees the id

- **WHEN** an Organizer opens the card for an object
- **THEN** the card shows that object's id

#### Scenario: An Administrator sees the id

- **WHEN** an Administrator opens the card for an object
- **THEN** the card shows that object's id

#### Scenario: Staff on a guest URL still see the id

- **WHEN** a signed-in Organizer opens a guest URL and then opens an object card
- **THEN** the card shows that object's id

#### Scenario: A self-point still shows the id

- **WHEN** an Organizer opens the card for a self-point
- **THEN** the card shows that self-point's id and does not offer edit or delete

#### Scenario: An expired object still shows the id

- **WHEN** an Organizer is showing expired objects and opens the card for an expired object
- **THEN** the card shows that object's id

#### Scenario: A player does not see the id

- **WHEN** a player opens the card for an object
- **THEN** the card does not show that object's id

#### Scenario: A guest does not see the id

- **WHEN** a guest opens the card for an object
- **THEN** the card does not show that object's id

#### Scenario: The map face and the edit form omit the id

- **WHEN** an Organizer is showing an object on the map and then opens its edit form
- **THEN** the map face does not show the id and the edit form does not show the id

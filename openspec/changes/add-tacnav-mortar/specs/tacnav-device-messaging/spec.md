# Spec Delta

## Purpose

Provides durable object-addressed application messages with wake-up notification and idempotent catch-up so device-owned game logic can run after delay, sleep, or temporary network loss.

## ADDED Requirements

### Requirement: An application can address a batch to objects on its map

An activated application SHALL be able to submit one message with a client-generated event id, message type, and one or more recipient object ids from its selected map. The server SHALL stamp the authenticated source application, source team, map, and acceptance time. It MUST NOT accept a recipient from another map or trust client-supplied source identity. Message payload content SHALL remain opaque to game rules.

#### Scenario: Mortar submits successful hits

- **WHEN** a mortar submits one `hit` event listing three object ids returned by its current-map search
- **THEN** the server accepts one source-stamped event for those same-map recipients without recalculating the hits

#### Scenario: Cross-map recipient is refused

- **WHEN** an application addresses an object belonging to another map
- **THEN** the server refuses that recipient and does not expose its mailbox

### Requirement: Only active bindings receive object messages

A recipient delivery SHALL be created only when the addressed object is currently bound to an active application instance authorized for that object’s map. An ordinary map mark, expired application binding, or object without an application binding SHALL not create a readable delivery. This routing decision MUST NOT interpret the game message.

#### Scenario: Old impact marker has no mailbox

- **WHEN** a mortar addresses a previous impact marker that has no active application binding
- **THEN** the server creates no readable delivery for that marker

#### Scenario: Active antenna receives its address

- **WHEN** a mortar addresses an antenna object bound to an active antenna application
- **THEN** that application’s mailbox contains the delivery

### Requirement: A device reads only its bound-object mailbox

An activated phone SHALL be able to read pending messages only for objects bound to its own active application instance. A cursor SHALL allow the phone to request messages after the last applied delivery. A credential for another application, a revoked phone, or an unbound object MUST NOT read that mailbox.

#### Scenario: Antenna catches up after sleep

- **WHEN** an antenna requests messages after its last cursor following a period offline
- **THEN** it receives its still-retained pending deliveries in stable order

#### Scenario: Another application cannot read the mailbox

- **WHEN** a different valid application credential requests the antenna object’s messages
- **THEN** the system refuses access

### Requirement: Message processing is idempotent

The server SHALL treat the combination of source application, client event id, and recipient object as one logical delivery. Repeating the same submission or acknowledgement MUST NOT create or apply an additional delivery. A recipient SHALL be able to acknowledge a delivery after local processing.

#### Scenario: Retried shot does not duplicate a hit

- **WHEN** a mortar retries the same `shot_id` and recipient after losing the response
- **THEN** the antenna mailbox contains only one logical delivery for that shot

#### Scenario: Repeated acknowledgement is safe

- **WHEN** an antenna acknowledges the same delivery twice
- **THEN** both requests leave that delivery acknowledged exactly once

### Requirement: The mailbox survives wake-up failure

Accepted messages SHALL be committed to the mailbox before any push attempt. Failure, delay, duplication, or absence of a push provider MUST NOT remove or acknowledge the mailbox delivery. Reading after application start, network restoration, foreground resume, or reconciliation SHALL still return a retained pending delivery.

#### Scenario: Push fails but catch-up succeeds

- **WHEN** Firebase rejects or delays the wake-up signal after a hit was accepted
- **THEN** the hit remains available in the antenna mailbox until acknowledgement or expiry

### Requirement: Push carries only a wake-up reference

When an active recipient phone has a current Firebase registration, the system SHALL attempt a high-priority, user-visible wake-up notification that identifies the pending delivery without embedding the authoritative game payload. The recipient SHALL fetch the mailbox record before applying game logic.

#### Scenario: Sleeping phone receives a reference

- **WHEN** a hit enters an active antenna mailbox with a registered phone
- **THEN** Firebase is asked to wake that phone with the delivery reference and the hit payload remains authoritative on the site

#### Scenario: Duplicate push does not duplicate processing

- **WHEN** the same wake-up signal is delivered twice
- **THEN** mailbox idempotency and recipient acknowledgement prevent a second logical hit

### Requirement: Deliveries have bounded retention

An unacknowledged delivery SHALL remain readable for 24 hours after server acceptance. An acknowledged delivery MAY be purged one hour after acknowledgement and MUST NOT reappear as pending. Expired deliveries SHALL be purged without changing geo objects or application definitions.

#### Scenario: Offline target returns during retention

- **WHEN** a target reconnects 12 hours after an unacknowledged hit
- **THEN** the hit is still available

#### Scenario: Stale message disappears

- **WHEN** more than 24 hours pass without acknowledgement
- **THEN** the delivery is no longer returned and may be deleted

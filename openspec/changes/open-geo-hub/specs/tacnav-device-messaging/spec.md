# Spec Delta

## ADDED Requirements

### Requirement: An object's mailbox follows the application that created it

An application-created object SHALL be bound to the active instance of the application that created it. A later write by that same application SHALL bind the object to that application's current instance, including after the application is reactivated on a new phone. A write by staff, a player, or another application MUST NOT bind, rebind, or unbind the object. Objects created by staff or players SHALL stay unbound.

#### Scenario: Another application's edit keeps the route

- **WHEN** a sapper application updates an object created by an Object application, and a mortar then hits that object
- **THEN** the delivery goes to the Object application and not to the sapper

#### Scenario: Reactivated phone takes over its objects

- **WHEN** an Object application is reactivated on a new phone and that phone updates the object it created earlier
- **THEN** later hits on that object are delivered to the new phone

#### Scenario: Staff minefield stays unbound

- **WHEN** a sapper application updates a staff-created minefield and a mortar then hits it
- **THEN** no readable delivery is created for that minefield

# Spec Delta

## REMOVED Requirements

### Requirement: An application can publish an object excluded from search

**Reason**: The site is a hub. Search returns every unexpired intersecting object, and each recipient decides whether a hit matters. Tac Nav Object already counts hits only on its live object, so excluding its hint area and wreck from search no longer changes game results.

**Migration**: The upgrade gives role `hint` to every stored not-searchable circle or polygon without an icon. Every other not-searchable object becomes an ordinary searchable object. The flag is then removed from storage, and a `searchable` value in a write is ignored. Tac Nav Object publishes its hint area with role `hint` instead of the flag.

## MODIFIED Requirements

### Requirement: Area objects do not show a false position

The map canvas SHALL draw a circle or polygon whose role is `hint` as its shape and title only, with no icon at its centre. Every other object SHALL keep its current drawing, including a wreck that has an icon.

#### Scenario: Hint area has no centre pin

- **WHEN** a viewer's map shows a circle with role `hint`
- **THEN** the circle and its title are shown with no marker at the centre

#### Scenario: Wreck keeps its icon

- **WHEN** a viewer's map shows an ordinary object with an icon that an application created as a wreck
- **THEN** the icon is drawn as for any other object

#### Scenario: Upgraded hint area keeps its drawing

- **WHEN** the upgrade converts a not-searchable circle without an icon
- **THEN** the circle is still drawn with no marker at its centre

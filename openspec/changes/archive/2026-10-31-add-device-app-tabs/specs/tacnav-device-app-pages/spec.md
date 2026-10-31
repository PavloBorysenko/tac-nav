# Spec Delta

## Purpose

Gives each TacNav device application its own staff settings page, reached by a tab on the Device applications page, so applications can be added independently.

## ADDED Requirements

### Requirement: Staff page shows one tab per application
The Device applications staff page SHALL show a tab bar with one tab for each available application and SHALL mark the selected tab as current. Mortar SHALL be available as a tab, including while it is the only application.

#### Scenario: Staff open the page
- **WHEN** an Organizer or Administrator opens Device applications
- **THEN** the page shows a tab bar containing a Mortar tab marked as current, followed by the Mortar page

### Requirement: Tab selection falls back to Mortar
The page SHALL show only the page of the application named by the `app` request parameter. A missing or unknown value SHALL show the Mortar page. A request value MUST NOT select anything other than an available application.

#### Scenario: Known application is requested
- **WHEN** staff open the page with `app` set to an available application
- **THEN** that application's page is shown and its tab is marked as current

#### Scenario: Parameter is missing
- **WHEN** staff open the page without `app`, including through an existing link or a redirect after saving
- **THEN** the Mortar page is shown

#### Scenario: Unknown application is requested
- **WHEN** staff open the page with `app` set to a value that is not an available application
- **THEN** the Mortar page is shown

### Requirement: Mortar behavior is preserved
The Mortar tab SHALL keep the existing application settings, activation handoff, phone revocation, and APK release behavior. Existing application records, the published release, and the public `/tacnav-mortar/` and `/tacnav-mortar.apk` addresses MUST remain valid without migration.

#### Scenario: Staff use Mortar controls
- **WHEN** staff save an application, create an activation, revoke a phone, or publish a release from the Mortar tab
- **THEN** the action has the same result as before this change and staff return to the Mortar page

#### Scenario: Players download the application
- **WHEN** a player opens the public Mortar download page after this change
- **THEN** the page offers the same published release as before

### Requirement: Application pages are independent
Each application page SHALL provide its own content. Adding an application SHALL add its tab without changing the content or behavior of another application's page.

#### Scenario: A second application is added
- **WHEN** a second application page becomes available
- **THEN** a second tab appears and the Mortar tab shows the same page as before

### Requirement: Staff page access stays restricted
The tab bar and every application page SHALL be available only to users holding the existing TacNav staff permission.

#### Scenario: A non-staff user requests a tab
- **WHEN** a user without the staff permission opens the page with any `app` value
- **THEN** access is refused and no tab or application page content is shown

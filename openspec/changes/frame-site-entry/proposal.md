# Proposal

## Why

The site front is not an entry to TacNav, and the player account page is a bare document. Visitors need one front door, and the player sign-in, registration, and cabinet need the same site header and footer the guest-link page already uses, with the topographic sheet as that chrome.

## What Changes

- Make `/` a front page whose body is only two centered controls: Organizer opens the WordPress login form, Player opens `/tacnav-player/`.
- Paint the supplied topographic sheet on the active theme header and footer. The header shows the top of the sheet, the footer the bottom. Background color stays `#D4CBB3`. The area between header and footer does not use the sheet.
- Render the active theme header and footer on the player account page for sign-in, registration, and the signed-in cabinet. Keep header and footer at the theme width and place the account content in the theme content column.
- Present the signed-in cabinet as separate profile, team, and map groups. Show each teammate's saved avatar, or an empty circle when they have none.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `tacnav-child-theme`: Header and footer keep `#D4CBB3` and gain the topographic sheet. The site front page offers the Organizer and Player entries.
- `tacnav-player-membership`: The player account page uses the active theme header and footer. The cabinet groups profile, team, and maps, and shows teammate avatars.

## Impact

- Child theme `wp-content/themes/tacnav/`: a front-page template, the topographic image, and header/footer background styling. Twenty Twenty-Five stays unedited and is not copied into the child. Header and footer color still applies without a theme JS or CSS build.
- Plugin `wp-content/plugins/tacnav-maps/`: `templates/player-account.php` and `app/Account.php` follow the guest-link document shell. A small account stylesheet. Sign-in, registration, team assignment, and map links stay as they are.
- Guest-link and any other view that already prints the theme header and footer pick up the sheet with no change to those routes.
- `wp-content/themes/tacnav/docs/architecture.md` describes the header and footer as a flat khaki fill and becomes stale when the sheet is added.

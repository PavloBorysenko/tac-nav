# Design

## Context

See `proposal.md` for motivation. Constraints that shape the approach:

- `tacnav` is a child of Twenty Twenty-Five. It does not copy the parent template tree. Header and footer background `#D4CBB3` is extra CSS in child `theme.json` on `header.wp-block-template-part` and `footer.wp-block-template-part`. A relative `url()` in that CSS resolves against the page, not the theme.
- The player account route exits in `template_redirect` with its own HTML document, inline body CSS, and no `wp_head()` or `wp_footer()`. Teammate avatar URLs are already collected and not printed. The guest-link route already renders `block_header_area()` and `block_footer_area()` before `wp_head()`, inside `header.wp-block-template-part` and `footer.wp-block-template-part`.
- The child `functions.php` already treats `/tacnav-player/` as the player account path in the header navigation. PHPUnit in the plugin boots without WordPress.
- Visible product strings are English gettext (`Player`, `Sign in`). The labels on the front door follow that: `Organizer` and `Player`.

## Goals / Non-Goals

**Goals:**

- One front template at `/`, theme chrome on the player account page, and one topographic background for every view that already prints the theme header and footer.
- Keep the khaki color and the sheet free of a theme JS or CSS build.

**Non-Goals:**

- A second organizer login form, a sign-out control, or a change to who may register or which maps a player sees.
- Painting the topographic sheet on the content column, the canvas, or the WordPress login screen.
- Editing Twenty Twenty-Five, copying its template tree, or changing the header navigation filter.
- Hiding the two front-page controls for a signed-in visitor.

## Decisions

### 1. The sheet is a rule in the child stylesheet

Store the supplied topographic image in the child theme. `style.css` sets `background-image` with a relative `url()` and does not set repeat, size, or position. Links in the header and footer use a heavier weight, a thick black underline, and a murky background on hover. `tacnav_enqueue_chrome` loads that stylesheet because a block theme does not enqueue `style.css` on its own. Leave `background-color` in `theme.json` so `#D4CBB3` still shows under the image and still does not depend on a build.

Alternative considered: a `url()` inside `theme.json`. Rejected: that URL is not themed to the stylesheet directory.

Alternative considered: a compiled theme stylesheet. Rejected: the color requirement forbids a build for this chrome.

### 2. The front door is a child front-page template

Add `templates/front-page.html` only. It pulls the existing header and footer template parts by slug and does not copy parent parts. The content is one centered group with two stacked buttons, Organizer then Player, and no heading or other body copy. Organizer links to `wp-login.php`. Player links to `/tacnav-player/`, the same path the navigation filter already recognizes. Centering is CSS on that group in the same inline stylesheet as the sheet: a flex box with a min-height that fills the viewport under the header.

Alternative considered: a plugin rewrite on `/`. Rejected: the front page is theme presentation, and the player route already owns `/tacnav-player/`.

Alternative considered: a page stored in the database. Rejected: the door has to ship with the theme and stay the front page without an editor step.

### 3. The player account page reuses the guest-link document shell

`Account::template_redirect()` renders the header and footer template parts before `wp_head()`, then the template prints them in `header.wp-block-template-part` and `footer.wp-block-template-part`, with `wp_head()`, `wp_footer()`, and `body_class()`. The account content sits in the same constrained `<main>` the guest-link page uses. Drop the inline body max-width so the header stays full width. Enqueue `assets/player-account.css` on this route only.

The signed-in cabinet is three groups: profile (avatar, nickname, save), team (name, or the existing unassigned notice, plus teammates), and maps (the existing links). Each teammate uses the avatar URL already on that row, at a fixed square size. An empty URL renders an empty circle so names stay aligned. Submit controls use `.wp-element-button`.

Alternative considered: `get_header()` / `get_footer()`. Rejected: the child has no `header.php` or `footer.php`, and the guest-link page already documents why the parts must render before `wp_head()`.

### 4. Docs follow the existing architecture note

Update `wp-content/themes/tacnav/docs/architecture.md` so the header and footer description includes the sheet as well as `#D4CBB3`. Do not add a documentation file. The plugin catalog has no player-account row; do not create one.

## Risks / Trade-offs

- [A Site Editor background on the header or footer can cover the sheet] → Same boundary as the khaki default: user styles win. The theme default stays the color plus the sheet when those styles are absent.
- [`front-page.html` replaces whatever `/` shows today] → Accepted. The front page is this door.
- [Body max-width would shrink the theme header] → Constrain only the account column, as on the guest-link page.
- [Root-relative `/wp-login.php` and `/tacnav-player/` assume the WordPress root is the site root] → This install is that layout, and the navigation filter already matches `/tacnav-player/`.
- [The isolated PHPUnit bootstrap cannot render these screens] → Do not add a test that pretends to assert the front page or the theme chrome.

## Migration Plan

No stored data changes. Player accounts, teams, and avatars stay valid. Rollback removes the front-page template, the image, the inline chrome CSS, and restores the bare player-account document.

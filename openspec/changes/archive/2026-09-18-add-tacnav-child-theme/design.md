## Context

See `proposal.md` for motivation and scope. Constraints that shape the approach:

- Git root is a Local WordPress site. Default themes `twentytwentythree` / `twentytwentyfour` / `twentytwentyfive` are on disk and are not first-party.
- No first-party theme or plugin exists yet. PHPCS already lists prefixes `TacNav`, `tacnav`, `tac_`.
- `.gitignore` ignores all themes except a leftover `emg-develop` allowlist. Site docs still describe that slug.
- Twenty Twenty-Five is a block theme (`theme.json` version 3). `parts/header.html` and `parts/footer.html` only reference parent patterns; inner groups have no background.
- Block-theme `style.css` is metadata, not an auto-enqueued stylesheet.
- Repo-level QA tools stay at the WordPress root. Theme JS that uses `@wordpress/scripts` is linted inside the theme via `npm run lint:js`.

## Goals / Non-Goals

**Goals:**

- Child overrides only what this change needs; parent tree is not copied.
- Header/footer color is a `theme.json` default so it applies on the frontend and in the Site Editor without a build.
- `@wordpress/scripts` is present in the child as the contract for later theme JS, blocks, and compiled CSS.
- `emg-develop` disappears from `.gitignore` and site documentation; `tacnav` is the allowlisted theme.

**Non-Goals:**

- Activating the child with WP-CLI (not on PATH; activate in admin unless asked at apply time).
- Custom blocks, SCSS, or enqueue of `build/` in this change.
- Editing default `twenty*` themes or vendored skill examples that mention `emg-develop`.
- Adding phpcs/PHPStan/PHPUnit/Jest inside the theme.

## Decisions

### 1. Child of Twenty Twenty-Five, not a fork

`style.css` sets `Template: twentytwentyfive`. Identifiers: folder / text domain `tacnav`, Theme Name `TacNav`, Author `SND Team`, PHP `tacnav_`. Tiny bootstrap: `functions.php` loads the text domain and does not enqueue assets yet.

Alternative considered: copy TT5 into `tacnav` as a standalone theme. Rejected so WordPress.org parent updates still flow, and first-party code stays an override layer.

Alternative considered: classic child (`style.css` + enqueue only). Rejected because the parent is a block theme; appearance defaults belong in child `theme.json`.

### 2. Header/footer background in child `theme.json`

Child `theme.json` version 3 merges with the parent. Add a palette color `#D4CBB3` and set `styles.css` to `header.wp-block-template-part, footer.wp-block-template-part { background-color: var(--wp--preset--color--khaki); }`.

`styles.blocks.core/template-part.variations.header|footer` does not emit CSS against the rendered `<header>` / `<footer class="wp-block-template-part">` markup on this WordPress version: the khaki preset is registered, but no template-part background rule is generated.

Alternative considered: copy `parts/header.html` / `footer.html` (or parent patterns) and set block background attributes. Rejected: that freezes parent markup in the child.

Alternative considered: enqueue CSS targeting `header.wp-block-template-part`. Rejected for this color: extra enqueue, weaker editor integration, and it would blur the wp-scripts contract.

### 3. Install `@wordpress/scripts` now; do not use it for this color

Theme `package.json` with `@wordpress/scripts` (`build`, `start`, `lint:js`) and a valid `src/index.js` entry so the CLI is wired. Commit `package-lock.json`. Do not enqueue `build/` until a later change adds real JS, blocks, or compiled CSS. New theme functionality goes through this pipeline, not ad-hoc root CSS.

Alternative considered: document the intent only, install later. Rejected: the user wants the toolchain present so later theme work has an obvious entry.

Alternative considered: compile the khaki through `src/` CSS now. Rejected: one color does not justify a build, and `theme.json` already covers editor + frontend.

### 4. Replace the accidental `emg-develop` allowlist

`.gitignore` drops `emg-develop` and allowlists `wp-content/themes/tacnav/`. Update `docs/architecture.md`, `docs/development.md`, `docs/catalog.md`, and `docs/human-overview.md` so TAC Nav’s first-party theme is `tacnav`. Leave `.agents/skills/` examples unchanged.

## Risks / Trade-offs

- [Site Editor user styles override `theme.json`] → Test on frontend and in Styles; if the color is missing, reset header/footer customizations for the stylesheet.
- [TT5 parent updates change header/footer markup] → Child does not copy parts/patterns, so most parent updates still apply; re-check only if the template-part wrapper no longer receives background.
- [Someone runs `build` and expects the khaki to come from CSS] → Keep color only in `theme.json`; do not enqueue `build/` until there is real `src/` behavior.
- [Child inactive after apply] → Site keeps showing the parent until `tacnav` is activated.

## Migration Plan

1. Add `wp-content/themes/tacnav/` (headers, `theme.json`, wp-scripts stub) and `npm install` in that folder.
2. Swap `.gitignore` allowlist `emg-develop` → `tacnav`.
3. Rewrite site docs that named `emg-develop`.
4. Activate `tacnav` in Appearance → Themes (manual unless requested).
5. Rollback: activate Twenty Twenty-Five, remove the child folder and its allowlist, restore docs.

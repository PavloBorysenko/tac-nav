## Why

This site has no first-party theme. Default block themes cannot hold TAC Nav customizations, and `.gitignore` plus site docs still name `emg-develop` from another project. A child theme is needed now so visual overrides live in tracked code instead of Core defaults or the database.

## What Changes

- Add first-party child theme `tacnav` of Twenty Twenty-Five (Author: SND Team).
- Set header and footer background to `#D4CBB3` in the child `theme.json`.
- Scaffold `@wordpress/scripts` in the child (package.json, `src/` entry) as the toolchain for later theme JS, blocks, and compiled CSS. Do not route this header/footer color through that build.
- Allowlist `wp-content/themes/tacnav/` in `.gitignore`.
- Remove leftover `emg-develop` allowlist and all site-doc mentions of that slug (`docs/architecture.md`, `docs/development.md`, `docs/catalog.md`, `docs/human-overview.md`).

## Capabilities

### New Capabilities

- `tacnav-child-theme`: First-party child of Twenty Twenty-Five that owns TAC Nav visual customizations, including header and footer background `#D4CBB3`.

### Modified Capabilities

- None. There are no existing specs under `openspec/specs/`.

## Impact

- New tracked theme at `wp-content/themes/tacnav/` (tiny: `style.css`, `functions.php`, `theme.json`, `package.json`, `src/index.js`).
- Repo `.gitignore` and Architecture / Development / catalog / human-overview docs.
- Theme-local Node dependency `@wordpress/scripts`; `node_modules/` stays gitignored.
- Default `twenty*` themes stay unedited. The child must be activated in WordPress to take effect; activation is out of scope unless requested at apply time.
- PHPCS prefixes `TacNav` / `tacnav` / `tac_` already match; no phpcs prefix list change.

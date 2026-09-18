## 1. First-party path

- [x] 1.1 Replace the `.gitignore` theme allowlist `emg-develop` with `wp-content/themes/tacnav/` and verify `rg emg-develop .gitignore` is empty and the `tacnav` allowlist line is present

## 2. Child theme bootstrap

- [x] 2.1 Add `wp-content/themes/tacnav/style.css` with Theme Name `TacNav`, Author `SND Team`, `Template: twentytwentyfive`, text domain `tacnav`, Version `0.1.0`, Requires at least / PHP copied from Twenty Twenty-Five, and verify the header fields are present
- [x] 2.2 Add tiny `wp-content/themes/tacnav/functions.php` (`ABSPATH` guard, `tacnav_` text domain load, no asset enqueue) and verify `phpcs --standard=phpcs.xml.dist wp-content/themes/tacnav/functions.php` passes
- [x] 2.3 Confirm Twenty Twenty-Five was not copied or edited and verify the child has no `templates/` or `parts/` tree and parent files are unchanged

## 3. Header and footer styles

- [x] 3.1 Add child `theme.json` version 3 with palette color `#D4CBB3` and `core/template-part` header and footer variation backgrounds pointing at that preset, and verify those keys exist in the JSON (no `build/` enqueue)

## 4. wp-scripts toolchain stub

- [x] 4.1 Add `package.json` with `@wordpress/scripts` scripts `build`, `start`, and `lint:js`, plus a lint-clean `src/index.js` entry, run `npm install` in the theme, commit `package-lock.json` only, and verify `npm run lint:js` in `wp-content/themes/tacnav` passes and `functions.php` still does not enqueue `build/`

## 5. Documentation

- [x] 5.1 Update `docs/architecture.md`, `docs/development.md`, `docs/catalog.md`, and `docs/human-overview.md` so the first-party theme is `tacnav` and `emg-develop` is gone, and verify `rg emg-develop docs .gitignore` is empty
- [x] 5.2 Add the `tacnav` component `docs/catalog.md` (and a root catalog hop) per wordpress-component-creation, and verify the new catalog names the child theme and header/footer color surface
- [x] 5.3 Leave `.agents/skills/` examples that mention `emg-develop` unchanged and verify those skill files still contain the old example slug

## 6. QA gates

- [x] 6.1 Run `phpcs --standard=phpcs.xml.dist wp-content/themes/tacnav` on first-party PHP and `npm run lint:js` in the theme, and verify both are green (do not register phpstan/PHPUnit paths; this component is tiny and `WP_UnitTestCase` is not configured)

# Development

## Purpose

Record how TacNav compiles and lints theme JavaScript, and that header/footer color must not go through that pipeline.

## Responsibilities and boundaries

`package.json` defines `@wordpress/scripts` scripts `build`, `start`, and `lint:js`. The webpack entry is `src/index.js`. Future theme JavaScript, blocks, and compiled CSS MUST use this toolchain.

`.eslintrc.json` uses `eslint:recommended` instead of `plugin:@wordpress/recommended`. The WordPress ESLint plugin currently crashes on Node 24 when it loads `@typescript-eslint`. Keep this shim until that stack runs on the Local Node version; do not drop `wp-scripts lint:js`.

`functions.php` does not enqueue `build/` until `src/index.js` has real behavior. Header and footer background is owned by child `theme.json`, not by compiled CSS.

`node_modules/` and `build/` are gitignored. Commit `package-lock.json`. Lint theme JS with `npm run lint:js` in `wp-content/themes/tacnav/`, not via empty root `tools/js-lint/paths.json`.

## Constraints and side effects

Do not add PHPCS, PHPStan, PHPUnit, Jest, or OpenSpec to this theme's Composer or npm dependencies. Repo-level QA stays at the WordPress git root.

## Implementation references

- `wp-content/themes/tacnav/package.json` — `@wordpress/scripts` scripts
- `wp-content/themes/tacnav/.eslintrc.json` — Node 24 ESLint shim
- `wp-content/themes/tacnav/src/index.js` — webpack entry
- `wp-content/themes/tacnav/functions.php` — no asset enqueue

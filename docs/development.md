# Development

## Purpose

Describe how this WordPress git root is laid out for local work, what Git tracks, and which agent QA tools exist. There is no project CI in the repository yet.

## Responsibilities and boundaries

Local development uses a Local by Flywheel WordPress root at `app/public` (this git root). WordPress Core, `wp-config.php`, uploads, and installed themes and plugins are gitignored except the allowlisted custom theme path `wp-content/themes/tacnav/`.

The first-party theme `tacnav` is a child of Twenty Twenty-Five. Theme JavaScript, blocks, and compiled CSS go through `@wordpress/scripts` in that folder (`src/index.js`, `npm run build` / `start` / `lint:js`). Header and footer color lives in child `theme.json` and must not depend on `build/`. Do not enqueue `build/` until `src/index.js` has real behavior.

Agent harness files under `.agents/`, Cursor rules under `.cursor/`, OpenSpec under `openspec/`, and `skills-lock.json` are present on disk; commit them only when that is requested.

QA gates are provisioned at this WordPress root: `phpcs.xml.dist` (prefixes `TacNav`, `tacnav`, `tac_`), `tools/phpstan/` plus `phpstan.neon.dist` and `phpstan-baseline.neon`, `tools/js-lint/`, `tools/phpunit/` plus `phpunit.xml.dist`, and `tools/js-test/`. Global `phpcs` with WordPress standards is available on PATH. WP-CLI is not on PATH. WordPress integration tests (`WP_UnitTestCase`) are not configured. The Cursor QA-loop rule is `task`: run existing gates on changed first-party files before finishing a coding task. After those gates, UI tasks invoke `wp-browser-sensor`; non-tiny first-party PHP, HTML templates, JS, or CSS invoke `local-code-review`. `tacnav` is tiny: run phpcs on its PHP and `npm run lint:js` in the theme; do not register PHPStan or PHPUnit paths for it.

OpenSpec CLI is installed globally. This repo has `openspec/` initialized with `--tools cursor`. Official `openspec-*` skills and `/opsx:*` commands live in `.cursor/skills` and `.cursor/commands`; they are pinned with `disable-model-invocation` so they do not auto-invoke. Do not create `openspec/changes/<id>/` unless the user invoked `/opsx:*` or asked to propose, apply, or archive a change.

The WordPress skills set, `wordpress-project-documentation`, `wp-browser-sensor`, `local-code-review`, `wordpress-testing`, and `wordpress-component-creation` are vendored under `.agents/skills`. `wordpress-project-creation` is not installed.

## Constraints and side effects

Do not Composer-require PHPCS, PHPStan, PHPUnit, Jest, or OpenSpec inside a theme or plugin. Repo-level tools belong at the WordPress git root (`phpcs.xml.dist`, `phpstan.neon.dist`, `phpunit.xml.dist`, `tools/phpstan/`, `tools/phpunit/`, `tools/js-lint/`, `tools/js-test/`, `openspec/`). Theme-local `@wordpress/scripts` in `wp-content/themes/tacnav/package.json` is the exception for that theme's JS toolchain. `node_modules/` stays gitignored.

PHPStan `paths` and PHPUnit `<directory>` entries stay empty until a non-tiny first-party component exists; `phpstan analyse` will refuse to run until a path is registered. The Local PHP 8.3.23 CLI binary loads no `php.ini`, so it lacks `mbstring` and cannot run PHPUnit; use a PHP CLI that has `mbstring`, or point the Local CLI at the site ini, before relying on `tools/phpunit`.

Do not point any future `WP_UnitTestCase` suite at this Local development database.

## Implementation references

- `.gitignore` — ignore and allowlist rules for Core, themes, plugins, and QA tool caches
- `wp-content/themes/tacnav/package.json` — theme `@wordpress/scripts` scripts
- `phpcs.xml.dist` — first-party PHPCS ruleset (no `<file>` paths yet)
- `phpstan.neon.dist` / `phpstan-baseline.neon` — PHPStan config (empty `paths`)
- `phpunit.xml.dist` — isolated PHPUnit suite (no test directories yet)
- `tools/js-lint/` — ESLint toolbox (`paths.json` empty; theme JS uses `npm run lint:js` in `tacnav`)
- `tools/js-test/` — Jest toolbox (`passWithNoTests`)
- `.cursor/rules/wp-agent-harness-qa.mdc` — QA-loop mode `task` and gate commands
- `.agents/skills/wp-agent-harness/` — vendored harness controller
- `openspec/config.yaml` — OpenSpec project config (`schema: spec-driven`)

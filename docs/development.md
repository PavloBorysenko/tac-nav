# Development

## Purpose

Describe how this WordPress git root is laid out for local work, what Git tracks, and which agent QA tools exist. There is no project build or CI in the repository yet.

## Responsibilities and boundaries

Local development uses a Local by Flywheel WordPress root at `app/public` (this git root). WordPress Core, `wp-config.php`, uploads, and installed themes and plugins are gitignored except the allowlisted custom theme path `wp-content/themes/emg-develop/`.

Git currently tracks only `.gitignore`. Agent harness files under `.agents/`, Cursor rules under `.cursor/`, OpenSpec under `openspec/`, and `skills-lock.json` are present on disk and untracked until someone commits them.

QA gates (PHPCS config, PHPStan, JS lint, PHPUnit, Jest) are not provisioned because there is no first-party component. Global `phpcs` with WordPress standards is available on PATH. WP-CLI is not on PATH. The Cursor QA-loop rule is `task`: run existing gates on changed first-party files before finishing a coding task. After those gates, UI tasks invoke `wp-browser-sensor`; non-tiny first-party PHP, HTML templates, JS, or CSS invoke `local-code-review`.

OpenSpec CLI is installed globally. This repo has `openspec/` initialized with `--tools cursor`. Official `openspec-*` skills and `/opsx:*` commands live in `.cursor/skills` and `.cursor/commands`; they are pinned with `disable-model-invocation` so they do not auto-invoke. Do not create `openspec/changes/<id>/` unless the user invoked `/opsx:*` or asked to propose, apply, or archive a change.

The WordPress skills set, `wordpress-project-documentation`, `wp-browser-sensor`, `local-code-review`, `wordpress-testing`, and `wordpress-component-creation` are vendored under `.agents/skills`. `wordpress-project-creation` is not installed.

## Constraints and side effects

Do not Composer-require PHPCS, PHPStan, PHPUnit, or OpenSpec inside a theme or plugin when those layers are added later. Repo-level tools belong at the WordPress git root (`phpcs.xml.dist`, `tools/phpstan/`, `tools/phpunit/`, `openspec/`).

Do not point any future `WP_UnitTestCase` suite at this Local development database.

## Implementation references

- `.gitignore` — ignore and allowlist rules for Core, themes, and plugins
- `.cursor/rules/wp-agent-harness-qa.mdc` — QA-loop mode `task` and gate commands
- `.agents/skills/wp-agent-harness/` — vendored harness controller
- `openspec/config.yaml` — OpenSpec project config (`schema: spec-driven`)

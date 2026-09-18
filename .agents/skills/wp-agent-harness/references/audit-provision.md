# Audit and provision questions

Read this file only in **audit** mode after `audit.mjs`, or when the user already named one layer (PHPCS, PHPStan, JS lint, PHPUnit, Jest, WP_UnitTestCase, tests, OpenSpec, a Cursor rule, or a skill). Do not read it during a coding task. It is not permission to start `audit.mjs`.

Install nothing until the user confirmed the exact items this turn. If first-party was unconfirmed in an audit report, finish that question and re-run audit before these questions.

Ask as structured choices. Do not skip the WordPress skills set or recommended questions just because required is empty or “yes”.

## WordPress skills set (missing only)

Ask whenever `skills.wordpressSet.missing` is non-empty, including when this repo has no custom plugin, theme, or first-party PHP yet. These are official `WordPress/agent-skills` with `set: wordpress` in `skill-catalog.json`, not the whole upstream repo and not Drupal/Laravel skills. A personal `~/.cursor/skills` copy does not count as installed.

- Prompt: install the WordPress skills set for this project?
- Options: `Install WordPress skills set` / `Choose individually` / `Do not install WordPress skills`

`Install WordPress skills set` means every missing id in `skills.wordpressSet.missing`. Do not add extra filter questions for that package choice.

If `Choose individually`, a follow-up with `allow_multiple` listing each missing wordpressSet id. Then extra questions only for ids the user picked:

- If `wp-playground` is picked: do you need a disposable WP without Local? Yes / No (if No, drop it).
- If `wp-rest-api` is picked: are there custom REST routes to maintain? Yes / No / Not sure (Not sure → keep it optional, do not auto-add).
- If `wp-performance` is picked: should agents profile queries/autoload? Yes / No.

Do not ask wordpressSet ids again under required or recommended this turn if the user already approved or declined them here. If they chose `Do not install WordPress skills`, still ask remaining **required-by-shape** ids that are also in `skills.required.missing`.

## Required skills (missing only)

Skip ids already decided in the WordPress skills set question this turn.

- Prompt: install missing required skills for this project shape?
- Options: `Install all missing required` / `Choose individually` / `Do not install skills`

If `Choose individually`, a follow-up with `allow_multiple` listing each remaining missing required id.

## Recommended skills (fitting this shape, missing only)

Always ask when the audit lists any remaining recommended ids that are not `set: wordpress`. Extra questions to prepare optional install. Do not skip `wordpress-testing` or `wordpress-component-creation` when they are listed.

- Which recommended skills to add? `allow_multiple` of the remaining missing recommended ids, plus `None`.
- If `responsive-design` or frontend is offered: will agents change theme CSS/layout? Yes / No (if No, drop `responsive-design`).
- If `wp-browser-sensor` is offered: after green gates, should agents check a local URL on UI work? Yes / No (if No, drop it).
- If `local-code-review` is offered: after green gates, should agents locally review non-tiny first-party PHP, HTML templates, JS, or CSS? Yes / No (if No, drop it).
- If `wordpress-testing` is offered: should agents derive behavioral PHPUnit/Jest from specs and use an existing WordPress integration suite when required (not install the toolbox/bootstrap)? Yes / No (if No, drop it).
- If `wordpress-component-creation` is offered: should agents scaffold new first-party themes/plugins with this repo's prefix and bootstrap rules? Yes / No (if No, drop it).
- If `wordpress-project-creation` is offered: should agents set up git/GitLab/CI and print SSH wp-content/DB sync commands (not run them)? Yes / No (if No, drop it).

## PHPCS (team-global tool + repo config)

Read `references/phpcs.md` only after the user wants this layer.

- If `qa.phpcs.onPath` is false: ask `Install PHPCS + WPCS globally (required for every teammate)` / `Not now`. Do not offer per-plugin Composer as the default. After Yes, `references/phpcs.md` pins PHPCS 3.x + WPCS 3.x to the PHP that will run `phpcs`; do not install PHPCS 4 or unpinned `squizlabs/php_codesniffer`.
- If `onPath` and not `qa.phpcs.wpcs`: ask `Add WordPress standards globally` / `Not now`.
- If the tool is ready and `qa.missing.phpcsConfig`: ask prefixes, then `Write phpcs.xml.dist for confirmed first-party paths` / `Not now`.
- PHPStan and JS lint stay `Not now` in this PHPCS block unless the user already named those layers.

## Documentation

- Root or component `docs/catalog.md` missing: `Leave as-is` / `Initialize documentation (separate docs skill, I confirm creation)`. Never scaffold catalogs from this skill.

## QA tools (other)

- PHPStan: if the user named this layer, read `references/phpstan.md`. Ask `Write tools/phpstan + phpstan.neon.dist + baseline for first-party paths` / `Not now`. After Yes, pin `phpstan/phpstan` and `szepeviktor/phpstan-wordpress` with `php-tool-constraint.mjs` to the PHP that will run analyse.
- JS lint: if the user named this layer, read `references/js-lint.md`. Ask `Use existing wp-scripts lint:js where it exists, and tools/js-lint (ESLint + WordPress plugin) for other first-party JS` / `Not now`. Skip hashed/minified/vendor files. Do not add ESLint to each plugin `package.json`. Do not use PHPCS on JS.

## Tests (optional)

Read `references/tests.md` only after the user wants this layer (named PHPUnit, Jest, WP_UnitTestCase, or tests, or they chose a test option below). Do not open it only because audit listed a gap. PHPStan and JS lint stay `Not now` unless the user already named those layers.

- If `qa.missing.phpunit` is true: ask `Configure a runnable tools/phpunit + phpunit.xml.dist suite` / `Not now`. If config exists but the runner is absent, install its locked Composer dependencies instead of replacing the config. After Yes, `references/tests.md` pins PHPUnit to the PHP that will run tests; do not install an unpinned `phpunit/phpunit`.
- If `qa.missing.jest` is true: ask `Write tools/js-test (Jest) for first-party JS utilities` / `Not now`.
- If `qa.missing.wpUnitTestCase` is true: separately ask `Configure WordPress integration tests (wp-phpunit/wp-phpunit + polyfills + bootstrap + dedicated disposable test database)` / `Not now`.
- If the user named PHPUnit, Jest, or tests without an audit run, ask only the named runner.

`WP_UnitTestCase` is not a standalone install. Read `references/tests.md` after approval and provision the WordPress integration layer it describes. Never infer database credentials, print passwords, or point the suite at a development/production database. `Not now` is valid; test layers are not required like PHPCS.

## OpenSpec (optional)

Ask how to enable OpenSpec. Enabling is not folders-only: global CLI if missing, `openspec init --tools cursor` (official `openspec-*` skills go to `.cursor/skills` and `/opsx:*` to `.cursor/commands`; some setups use `.agents/skills`), then **pin in the same turn**. Cursor init does not copy those skills into `.agents/skills`. Pin writes `disable-model-invocation: true` so `openspec-apply-change` does not auto-invoke on ordinary PHP. Read `references/openspec.md` only after the user wants this layer (named OpenSpec, or they chose an OpenSpec option below). Do not open it only because audit listed a gap.

- If `openspec.missing.cli` is true: ask `Install OpenSpec CLI globally (npm install -g @fission-ai/openspec@latest)` / `Not now`. Requires Node 20.19.0+. Do not add `@fission-ai/openspec` to a theme or plugin `package.json`.
- If `openspec.missing.init` or `openspec.missing.skills` is true: ask `Enable OpenSpec in this repo (openspec init --tools cursor; official skills in .cursor/skills; then pin so they do not auto-invoke)` / `Not now`. If the CLI is also missing, install it first. Re-running init is safe. **Yes includes the pin**: after init, immediately run `scripts/disable-openspec-auto-invoke.mjs --confirm --root`. Tell the user the skills landed in `.cursor/skills` (or `.agents/skills` if that is where init wrote them), not that “no OpenSpec skill was added” if `.agents/skills` is empty.
- If `openspec.missing.pin` is true and this turn did not already pin as part of Enable OpenSpec: ask `Pin official OpenSpec skills (disable-model-invocation) so they do not auto-invoke` / `Not now`. After Yes, run the pin script. Do not skip this when init already exists.
- If the user named OpenSpec without an audit run, ask only the missing CLI, enable/init, and pin steps.

`Not now` is valid; OpenSpec is not required like PHPCS. Tiny WordPress fixes stay on the harness without a spec.

## Cursor rules

Read `references/cursor-rules.md` only after the user wants this layer.

If `rules.missing.qaLoop` is true, ask how to run checks:

- `task` (default) — when the agent is developing: run gates on changed first-party files before finishing (self-correction)
- `every-change` — after every first-party file write in the same turn
- `manual` — only when the user asks to run phpcs, phpstan, lint, or tests
- `Not now`

Then write only the approved mode:

```bash
node scripts/write-cursor-rule.mjs --confirm --root "/absolute/wp-root" --id qa-loop --mode task
```

If audit lists missing recommended rules, ask separately (`allow_multiple`): `first-party-scope` / `docs-catalog` (skip if already present) / `None`. Do not add extras with the QA-loop rule.

## Custom skills

`wordpress-project-documentation`, `wp-agent-harness`, `wp-browser-sensor`, `local-code-review`, `wordpress-testing`, `wordpress-component-creation`, and `wordpress-project-creation` are `supernova-pack` in `skill-catalog.json`, not `npx skills add` from WordPress/agent-skills. PHPUnit, Jest, and OpenSpec are not skills; do not pass them to `install-skills.mjs`. `wordpress-testing` is writing rules only; install it with `--skills wordpress-testing`, not as part of `copy-harness.mjs`. `wordpress-component-creation` scaffolds a new first-party theme or plugin; install it with `--skills wordpress-component-creation`, not as part of `copy-harness.mjs`. `wordpress-project-creation` writes gitignore/CI and prints git/SSH/WP-CLI commands; install it with `--skills wordpress-project-creation`, not as part of `copy-harness.mjs`.

If any of those pack skills are missing in the **project**, ask to install the approved ids into `.agents/skills`. Do not treat a personal `~/.cursor/skills` copy as installed. After approval, one command (fetches `result/<id>/` from the pack git, or the local authoring folder if present; omits `evals/`):

```bash
node scripts/install-skills.mjs --root "/absolute/wp-root" --confirm --skills wp-agent-harness,wordpress-project-documentation,wp-browser-sensor,local-code-review,wordpress-testing,wordpress-component-creation,wordpress-project-creation
```

Pass only the ids the user approved. From the authoring workspace, `copy-harness.mjs` still vendors the harness plus browser and review siblings without `evals/`. It does not vendor `wordpress-testing`, `wordpress-component-creation`, or `wordpress-project-creation`. Dedicated `copy-local-code-review.mjs` / `copy-browser-sensor.mjs` / `copy-wordpress-project-documentation.mjs` / `copy-wordpress-testing.mjs` / `copy-wordpress-component-creation.mjs` / `copy-wordpress-project-creation.mjs` remain for a single named layer from `result/`. Then delete any user-global `~/.cursor/skills/wp-agent-harness` so Cursor loads the repo copy.

Do not guess a Desktop path. Do not copy `evals/` onto the site.

## Install (only after approval)

Official WordPress/agent-skills plus pack skills the user named (mix allowed). After `Install WordPress skills set`, pass every approved missing `skills.wordpressSet` id (not the whole upstream repo):

```bash
node scripts/install-skills.mjs --root "/absolute/path/to/wp-root" --confirm --skills wordpress-router,wp-phpstan,wp-plugin-development,wp-block-themes,wp-block-development,wp-rest-api,wp-performance,wp-patterns,wp-playground,wpds
```

Pack skills the user named in the same or a later command:

```bash
node scripts/install-skills.mjs --root "/absolute/path/to/wp-root" --confirm --skills wordpress-router,wp-phpstan,wordpress-project-documentation
```

The script refuses to run without `--confirm` and without a non-empty `--skills` list. Pass only ids the user approved. Do not add extras “while you are at it”. Do not install PHPUnit, Jest, or OpenSpec here.

From the authoring folder only, vendor the harness siblings:

```bash
node scripts/copy-harness.mjs --confirm --root "/absolute/wp-root"
```

That writes `.agents/skills/wp-agent-harness/` without `evals/`. Do not install the team copy into `~/.cursor/skills`.

Global PHPCS (only the list the user approved):

```bash
node scripts/install-phpcs.mjs --confirm --global
```

Repo `phpcs.xml.dist` (only confirmed first-party files and prefixes):

```bash
node scripts/write-phpcs-config.mjs --confirm --root "/absolute/wp-root" --prefixes "Supernova,sn_" --files "wp-content/themes/foo"
```

## After install

Re-run `audit.mjs` only if this turn is already in audit mode. In develop mode, run phpcs/phpstan/lint on changed first-party files according to the QA-loop rule. If a PHPUnit or Jest config already exists, run that existing suite for the touched component. The browser sensor is not a provision item and not a harness reference: after green gates, on UI tasks only, Read `.agents/skills/wp-browser-sensor/SKILL.md`. If that skill is missing, skip. Local review is the same pattern: after gates and after the browser pass or skip, on non-tiny first-party PHP, HTML templates, JS, or CSS, launch one isolated readonly subagent whose packet tells it to Read `.agents/skills/local-code-review/SKILL.md`. If that skill is missing, skip; do not inline the review checklist; do not review the diff yourself.

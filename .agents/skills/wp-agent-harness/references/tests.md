# PHPUnit and Jest for the WP agent harness

Read this file only when the user approved a PHPUnit, Jest, or tests step.

This file is install and run policy. It is not a guide for writing tests. Writing rules live in the separate `wordpress-testing` skill.

## Team policy

Installing a runner is optional. `Not now` is valid. Do not treat a missing suite as a broken harness.

- **Do not** `composer require` PHPUnit inside a theme or plugin (deploy noise, forked versions).
- **Do** put PHPUnit in `tools/phpunit/` via Composer (`phpunit/phpunit`).
- **Config** lives in the git repo: `phpunit.xml.dist` at the WordPress root.
- Tests live in first-party `<theme-or-plugin>/tests/`.
- Isolated tests use PHPUnit alone. WordPress integration tests additionally use `wp-phpunit/wp-phpunit`, Yoast PHPUnit Polyfills, an explicit bootstrap, and a dedicated disposable test database.
- Jest lives in `tools/js-test/`. Do not add Jest to each plugin `package.json`.
- `tools/phpunit/vendor/` and `tools/js-test/node_modules/` are gitignored. Commit the lockfiles.
- Production deploys use `composer install --no-dev`. QA packages must not ship on the server.
- Never reuse a development or production database for `WP_UnitTestCase`; the WordPress test suite resets tables.

## PHPUnit (after explicit confirmation)

Detect the PHP binary that will **run** the suite (Local site PHP if this is a Local install). Do not use a newer CLI PHP that happens to be on PATH. Then pin PHPUnit to that runtime (`php-tool-constraint.mjs`; `phpunit-constraint.mjs` prints the PHPUnit subset):

```bash
node .agents/skills/wp-agent-harness/scripts/php-tool-constraint.mjs --php "/path/to/local/php"
```

On Windows, pass the Local `php.exe` with `--php`. `--php-version 8.1.29` is only for checks. If the script exits 2, stop; do not install.

Use the JSON `packages.phpunit` and `platform` values. Never install `phpunit/phpunit` unpinned: that pulls PHPUnit 13 and breaks PHP 8.1–8.3.

```bash
composer init --working-dir=tools/phpunit --name=site/phpunit-tools --no-interaction
composer config --working-dir=tools/phpunit platform.php <platform>
composer require --working-dir=tools/phpunit --dev phpunit/phpunit:<constraint> --no-interaction
```

Write `phpunit.xml.dist` at the WordPress root. Point `<directory>` at confirmed first-party `tests/` paths only. After intent Yes with no custom code yet, the suite and `tools/phpunit` may exist with no `<directory>` entries until the first **non-tiny** component gets `<component>/tests/`. When this turn created a new **non-tiny** first-party theme or plugin and PHPUnit already exists, add that `tests/` path and create the empty folder. Do not invent a behavior test. Tiny scaffolds skip that registration.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<phpunit xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         bootstrap="tools/phpunit/vendor/autoload.php"
         colors="true"
         xsi:noNamespaceSchemaLocation="tools/phpunit/vendor/phpunit/phpunit/phpunit.xsd">
  <testsuites>
    <testsuite name="first-party">
      <directory>wp-content/themes/example/tests</directory>
      <directory>wp-content/plugins/example/tests</directory>
    </testsuite>
  </testsuites>
</phpunit>
```

Replace the example directories with the confirmed first-party paths. Do not point PHPUnit at Core, `twenty*` themes, or third-party plugins.

Run:

```bash
php tools/phpunit/vendor/bin/phpunit -c phpunit.xml.dist
```

If only one first-party component changed, run that component’s suite when it has its own `phpunit.xml.dist`. Do not invent `--filter` heuristics.

## WordPress integration (after separate explicit confirmation)

`WP_UnitTestCase` is provided by the WordPress PHPUnit test library; it is not installed as a standalone class.

1. Reuse the PHP binary from the isolated PHPUnit step. Add `wp-phpunit/wp-phpunit` and let Composer resolve PHPUnit; if the lock conflicts with PHPUnit 11+, keep the PHPUnit version `wp-phpunit` requires rather than the newest isolated constraint. Do not guess `latest`.
2. Add `wp-phpunit/wp-phpunit` and `yoast/phpunit-polyfills` to `tools/phpunit/composer.json` as dev dependencies.
3. Add a repository bootstrap that loads `tools/phpunit/vendor/autoload.php`, the polyfills, the first-party components under test, and `wp-phpunit/wp-phpunit/includes/bootstrap.php`.
4. Point `WP_PHPUNIT__TESTS_CONFIG` at a gitignored local test config. Keep database passwords out of Git and chat output.
5. Require a dedicated disposable test database and an unmistakable test-only table prefix. Stop if its ownership is uncertain.
6. Run the focused test file, then `php tools/phpunit/vendor/bin/phpunit -c phpunit.xml.dist`.

Prefer the Composer-installed WP PHPUnit library for a cross-platform team setup. Do not download or run `install-wp-tests.sh` by default.

## Jest (after explicit confirmation)

Offer Jest when first-party source JS exists, or after intent Yes even with no custom JS yet (`roots` may stay empty until a non-tiny component has utilities). When this turn created a new **non-tiny** first-party theme or plugin with source JS, add those directories to the existing Jest config. Tiny scaffolds skip that edit.

```bash
npm init -y --prefix tools/js-test
npm install --prefix tools/js-test --save-dev jest
```

Write `tools/js-test/jest.config.cjs` with `roots` limited to confirmed first-party JS directories. Skip hashed, minified, vendor, and build files.

Run:

```bash
npm --prefix tools/js-test test
```

If a component already has a `test` script, run that script in the component instead of adding a second runner.

## Develop mode

If a PHPUnit or Jest config exists, run that existing suite for the touched first-party component before finishing. Red output is the fix list.

For new or changed observable behavior, use the separate `wordpress-testing` skill to add tests when a matching suite exists and the expected result is explicit in OpenSpec or acceptance criteria. Do not write new tests for a tiny wiring/presentation fix unless the user asked or a regression test is required.

Run a focused new or failing test before the suite. Investigate a failing verified test against its governing contract; do not change its expected value merely to get green. A missing suite is not a reason to run `audit.mjs` or offer installation during a coding task.

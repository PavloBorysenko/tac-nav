# PHPStan for the WP agent harness

Read this file only when the user approved a PHPStan step.

## Team policy

Installing PHPStan is required for this harness layer. It is not a smell.

- **Do not** `composer require` PHPStan inside a theme or plugin (deploy noise, forked versions).
- **Do** put the analyser in `tools/phpstan/` via Composer (`phpstan/phpstan`, `szepeviktor/phpstan-wordpress`, plus site-specific stubs such as ACF when the theme calls `get_field`).
- **Config** lives in the git repo: `phpstan.neon.dist` at the WordPress root, plus `phpstan-baseline.neon` for legacy findings.
- `tools/phpstan/vendor/` is gitignored. Commit `composer.json` and `composer.lock` in `tools/phpstan/`.
- Global `phpstan` on PATH is optional. Provision still uses `tools/phpstan` so teammates and later CI share one lockfile.
- After a PHP change, run PHPStan on the changed first-party paths. Do not add new errors to the baseline.

## Repo config (after explicit confirmation)

Detect the PHP binary that will **run** PHPStan (Local site PHP). Pin with `scripts/php-tool-constraint.mjs`. Never `composer require phpstan/phpstan` unpinned.

```bash
node .agents/skills/wp-agent-harness/scripts/php-tool-constraint.mjs --php "/path/to/local/php"
composer init --working-dir=tools/phpstan --name=site/phpstan-tools --no-interaction
composer config --working-dir=tools/phpstan platform.php <platform>
composer require --working-dir=tools/phpstan --dev phpstan/phpstan:<phpstan> szepeviktor/phpstan-wordpress:<phpstanWordpress> --no-interaction
php tools/phpstan/vendor/bin/phpstan analyse -c phpstan.neon.dist --memory-limit=1G
```

Use the JSON `packages.phpstan` and `packages.phpstanWordpress` values. Match majors: PHPStan 2 with `szepeviktor/phpstan-wordpress` 2. Do not mix 1.x stubs onto PHPStan 2.

On Windows, if parallel workers run out of memory, keep `parameters.parallel.maximumNumberOfProcesses: 1` in `phpstan.neon.dist`.

Generate a baseline once:

```bash
php tools/phpstan/vendor/bin/phpstan analyse -c phpstan.neon.dist --generate-baseline phpstan-baseline.neon --memory-limit=1G
```

Then `includes: phpstan-baseline.neon` in the dist file. Analyse must be clean. New code must not grow the baseline.

`paths` must be first-party only. Never point PHPStan at `wp-admin`, `wp-includes`, or third-party plugins.

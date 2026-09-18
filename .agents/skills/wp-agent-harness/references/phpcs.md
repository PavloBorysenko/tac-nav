# PHPCS for the WP agent harness

Read this file only when the user approved a PHPCS step.

## Team policy

- **Tool** lives globally on each developer machine: `phpcs` and `phpcbf` on PATH, with WordPress standards (`WordPress-Extra`).
- **Config** lives in the git repo: `phpcs.xml.dist` at the WordPress root (or git root). Same file for the whole team.
- Do **not** `composer require` PHPCS or WPCS inside a theme or plugin. That forks versions per component and misses teammates who never `composer install` that package.
- CI should call the same global (or image-provided) `phpcs`, not a random `vendor/bin` from a third-party plugin.

## Global install (after explicit confirmation)

Detect the PHP binary that will **run** `phpcs` (Local site PHP if this is a Local install). Pin packages with:

```bash
node .agents/skills/wp-agent-harness/scripts/php-tool-constraint.mjs
node .agents/skills/wp-agent-harness/scripts/php-tool-constraint.mjs --php "/path/to/local/php"
```

WPCS 3.x requires PHPCS `^3.13.5`, not PHPCS 4. Never `composer global require squizlabs/php_codesniffer` unpinned. Set `composer global config platform.php` to the JSON `platform` so Composer does not resolve against a newer CLI PHP.

Requires Composer on PATH. Prefer the harness command (still needs `--confirm`):

```bash
node scripts/install-phpcs.mjs --confirm --global --php "/path/to/local/php"
```

Manual equivalent uses the JSON `packages.phpcs`, `packages.wpcs`, and `packages.phpcsInstaller`:

```bash
composer global config platform.php <platform>
composer global config --no-plugins allow-plugins.dealerdirect/phpcodesniffer-composer-installer true
composer global require squizlabs/php_codesniffer:<phpcs> wp-coding-standards/wpcs:<wpcs> dealerdirect/phpcodesniffer-composer-installer:^1.0 --no-interaction
```

Then confirm:

```bash
phpcs --version
phpcs -i
```

`phpcs -i` must list `WordPress-Extra`. If Composer global bin is not on PATH, tell the user to add Composer's global `vendor/bin` (Windows: `%APPDATA%\Composer\vendor\bin`).

## Repo config (after explicit confirmation)

Ask for PHP prefixes / text domain used in first-party code (example: `Supernova`, `sn_`). Then:

```bash
node scripts/write-phpcs-config.mjs --confirm --root "/absolute/wp-root" --prefixes "Supernova,sn_" --files "wp-content/themes/foo,wp-content/plugins/bar"
node scripts/write-phpcs-config.mjs --confirm --root "/absolute/wp-root" --prefixes "Acme,acme_" --allow-empty-files
```

`--files` must be first-party paths the user already confirmed. After intent Yes with no custom code yet, `--allow-empty-files` writes the ruleset with prefixes only and no `<file>` tags. Never point PHPCS at `wp-admin`, `wp-includes`, or third-party plugins. The generated ruleset sets `extensions` to `php` so PHPCS does not scan JS or CSS.

The script refuses to overwrite. When this turn created a new **non-tiny** first-party theme or plugin, edit the existing `phpcs.xml.dist` in place: add that folder and its PHP prefix family. Tiny scaffolds skip that edit.

## Agent loop

After a PHP change in first-party paths:

```bash
phpcs --standard=phpcs.xml.dist path/to/changed/file.php
phpcbf --standard=phpcs.xml.dist path/to/changed/file.php
```

Red phpcs means the task is not done. Prefer `phpcbf` for auto-fixable sniffs, then fix the rest.

Do not run PHPCS on JS, CSS, or hashed `assets/` bundles. Those belong to the JS lint layer.

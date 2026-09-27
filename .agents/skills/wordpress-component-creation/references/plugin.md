# Plugin scaffold

Read this file after the component is a plugin and author, prefix, slug, and tiny vs not-tiny are resolved — including when the user just answered those questions. Read it before writing PHP. Placeholders: `{slug}` folder and text domain, `{Prefix}` human name, `{prefix}_` functions, `{PREFIX}_` constants, `{PhpNamespace}` PSR-4 namespace. Copy `Requires at least` / `Requires PHP` from a sibling header.

Do not put phpcs, PHPStan, PHPUnit, or Jest in this component's `composer.json`.

## Tiny

One file `wp-content/plugins/{slug}/{slug}.php`:

```php
<?php
/**
 * Plugin Name: {Prefix Feature}
 * Description: {one line}
 * Version: 0.1.0
 * Author: {Author}
 * License: GPL-2.0-or-later
 * Requires at least: {sibling}
 * Requires PHP: {sibling}
 * Text Domain: {slug}
 */

defined( 'ABSPATH' ) || exit;

// Register hooks in this file. Do not add CPT, REST, or an admin page.
```

No `composer.json`. No `app/`. No `uninstall.php` unless asked.

## Not-tiny

```text
wp-content/plugins/{slug}/
├── {slug}.php          bootstrap only
├── composer.json       autoload only, if PSR-4 was approved
└── app/                or the sibling autoload directory
```

`{slug}.php` loads constants, requires Composer autoload when present, and calls `{PhpNamespace}\Plugin::boot()`. It does not query, persist, or print HTML.

If siblings already define Composer PSR-4, copy their directory name (`app/`, `src/`, …) and namespace pattern. If they do not, ask before adding `composer.json`. Approved autoload example:

```json
{
  "name": "project/{slug}",
  "autoload": {
    "psr-4": {
      "{PhpNamespace}\\": "app/"
    }
  }
}
```

Do not run `composer require` for QA tools. Do not add Settings API, REST, or CPT from this file; use official `wp-plugin-development` after the scaffold exists.

Vendor third-party JS/CSS under this plugin (`assets/vendor/` or npm). Enqueue the local file. Do not enqueue unpkg, jsDelivr, cdnjs, or another CDN unless the user named that remote URL.

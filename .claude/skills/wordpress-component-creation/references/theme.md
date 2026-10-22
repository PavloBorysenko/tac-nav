# Theme scaffold

Read this file after the component is a theme and classic/child/block plus tiny vs not-tiny are resolved — including when the user just answered those questions. Read it before writing theme files. Placeholders: `{slug}`, `{Prefix}`, `{prefix}_`, `{Author}`.

Do not add `composer.json` or `app/` to a classic or child theme. `functions.php` registers and includes; it is not a dump of query, persist, CPT, or template HTML.

## Classic

```text
wp-content/themes/{slug}/
├── style.css
├── functions.php
├── index.php
└── screenshot.png     optional; do not invent a binary
```

`style.css` header:

```text
/*
Theme Name: {Prefix Feature}
Author: {Author}
Version: 0.1.0
Requires at least: {sibling}
Requires PHP: {sibling}
Text Domain: {slug}
License: GPL-2.0-or-later
*/
```

Keep additional classic templates only when the task needs them. Follow nearby first-party theme files when they exist.

## Child

```text
wp-content/themes/{slug}/
├── style.css
└── functions.php
```

Set `Template: {parent-stylesheet-slug}` in `style.css`. Override only what this task needs. Do not copy the parent tree. Do not write `docs/` inside a third-party parent.

## Block

```text
wp-content/themes/{slug}/
├── style.css
├── functions.php
├── theme.json
├── templates/
└── parts/
```

After this root exists, follow official `wp-block-themes` for `theme.json`, templates, and parts. Do not duplicate that handbook here. `functions.php` stays a thin bootstrap (enqueue, `add_theme_support` that `theme.json` does not cover).

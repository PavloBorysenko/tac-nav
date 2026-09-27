---
name: wordpress-component-creation
description: >-
  Scaffolds a new first-party WordPress theme or plugin inside an existing
  WordPress git repository, including prefix, author, slug, text domain,
  namespace, bootstrap layout, .gitignore allowlist, and component docs when
  the docs skill is present. Use when the user asks to create, scaffold, or
  add a new custom theme or custom plugin. Also use when they answer a prefix,
  author, slug, or PSR-4 question for that new component. Also use when
  /opsx:propose or /opsx:apply (or proposing or applying an OpenSpec change)
  creates a new custom theme or plugin: propose records layout in design.md
  and does not write PHP. Do not use for editing an existing component, adding
  a class or file to one, installing a third-party plugin, ordinary WordPress
  coding, docs-only work, audit, tests, /opsx:archive, /opsx:explore, Drupal,
  or starting a new WordPress project.
---

# WordPress component creation

Open this skill when the user asked to create, scaffold, or add a **new** custom theme or custom plugin, when they answer a prefix, author, slug, or PSR-4 question for that new component, or when `/opsx:propose` or `/opsx:apply` creates one. Do not open it to edit an existing component, add a class, write docs, audit, test, `/opsx:archive`, `/opsx:explore`, or start a new WordPress project.

Create the component inside the existing WordPress git root. Do not init a nested git repo. Official `wp-plugin-development` and `wp-block-themes` cover hooks and `theme.json` **after** this skill resolves placement, identifiers, and autoload. They must not default a new plugin to `includes/class-*.php` while PSR-4 is unanswered.

## Load only what is needed

| File | Read when |
| --- | --- |
| `references/plugin.md` | After the component is a plugin and author, prefix, slug, and tiny/not-tiny are resolved, including when the user just answered those questions |
| `references/theme.md` | After the component is a theme and classic/child/block plus tiny/not-tiny are resolved, including when the user just answered those questions |

Do not read both layout files. Do not open them to decide prefix or author. Do not inline Plugin Handbook or `theme.json` schema here.

If `.agents/skills/wordpress-project-documentation/SKILL.md` or `.cursor/skills/wordpress-project-documentation/SKILL.md` exists, Read it after the code scaffold and follow **documentation creation** for this component only. Do this even when this turn only answered a prefix, author, or slug question. Do not copy that skill's catalog rules into this file.

## Workflow

1. Confirm this is an existing WordPress git repository (`wp-load.php` or `wp-content` + `wp-includes`). If the user wants a new project from scratch, stop; that is `wordpress-project-creation`.
2. Resolve the git root. Treat it as the WordPress project root.
3. Discover **siblings**: git-tracked first-party themes and plugins only. Use git paths, `.gitignore` allowlists, headers, namespaces, project rules, and an existing catalog. Skip Core, `twenty*`, disk-only copies, and third-party plugins even when tracked. Do not classify ownership from a slug prefix alone. Do not rename existing components.
4. If theme vs plugin, classic vs block vs child, or the feature name is unclear, ask. Do not guess. Do not put the component in `mu-plugins` unless asked.
5. Classify **tiny**: one responsibility and no CPT, REST, or admin UI (tiny settings screens are allowed). Otherwise not-tiny.
6. Resolve **author** and **prefix** with the algorithm below. Stop and ask rather than invent. Asking is not the end of this skill.
7. Resolve PSR-4 (not-tiny plugin) and any third-party JS/CSS library host. Ask when those are unresolved. Do not pick `includes/class-*.php` or a CDN silently.
8. If this turn is `/opsx:propose` (or writing OpenSpec `design.md` only): record author, prefix, slug, tiny/not-tiny, PSR-4 vs classic includes, and local vendor JS/CSS in `design.md`. Do not write PHP. Do not allowlist `.gitignore`. Do not create docs. Do not let official `wp-plugin-development` write classic `includes/class-*.php` into the design while PSR-4 is unanswered. Stop until apply.
9. If this turn is a direct scaffold or `/opsx:apply`: when author, prefix, slug, tiny/not-tiny, and PSR-4 are known (from siblings, `design.md`, or answers **this turn**), build one identifier family, allowlist the new folder in `.gitignore`, Read the matching layout file, then write the scaffold. Do not write PHP until that layout file is Read. Do not write `includes/class-*.php` when PSR-4 was chosen or is still unanswered.
10. After the code exists, hand off docs (section below). Then register QA paths if the component is **not-tiny** (section below). If `wp-agent-harness` is present, continue its develop gates. Do not start an OpenSpec change from this skill. Do not activate the plugin with WP-CLI unless asked.

If a previous turn of this conversation already asked for author, prefix, feature slug, theme vs plugin, or PSR-4, and the user answered, **this turn is still this skill**. Resume at step 8 or 9. Do not skip the layout file or docs because the create/scaffold sentence was last turn.

## Author and prefix

Take a value the user named in this turn.

Otherwise inspect siblings (first-party themes **and** plugins):

| Situation | Author | Prefix (folder / PHP / text domain / namespace) |
| --- | --- | --- |
| No siblings | Ask, offer **SND Team** | Ask. Do not derive from the Local folder or domain. Offer a value only when project rules or root `docs/catalog.md` already name it |
| Every sibling shares one value | Use it | Use it |
| Values conflict | Ask | Ask |

Compare author strings and prefix tokens as exact values. `Supernova Dev` and `Supernova Dev Team` conflict. Folder `emg-develop`, text domain `villa-emg`, and PHP `emg_` conflict. If one sibling's tokens disagree, ask. Do not invent Author URI or email.

## Identifier family

Once prefix and feature are known, keep one family:

- Folder: `wp-content/plugins/{prefix}-{feature}/` or `wp-content/themes/{prefix}-{feature}/`. If this repo already names themes differently, ask; do not invent a second slug style.
- Text domain = folder slug.
- Plugin bootstrap file = `{slug}.php`, never `plugin.php`.
- `Plugin Name` / `Theme Name`: human-readable, same brand.
- Functions and hooks: `{prefix}_`.
- Constants: `{PREFIX}_`.
- PHP namespace and `block.json` `namespace`: same prefix family as siblings when they already have one.

Copy sibling **names and autoload style**, not a bloated `functions.php`.

## Tiny vs bootstrap

| | Tiny | Not-tiny |
| --- | --- | --- |
| Plugin | One `{slug}.php` file is allowed | Bootstrap file only: header, `ABSPATH` guard, constants, autoload, `boot()`. No query, persist, or HTML |
| Theme | `functions.php` may hold `add_theme_support`, menus, enqueue, and thin hooks | Same. No query, persist, CPT, or template HTML in `functions.php` |

Thin hooks are not “any logic on `init`”. CPT, meta, checkout, and SQL belong in a plugin when this project already splits theme and plugins that way.

## PSR-4

For a **not-tiny plugin** only: if a sibling already has Composer autoload, copy that tree. If none does, ask — including during `/opsx:propose`, before `design.md` locks an autoload. Do not add `composer.json` or `app/` to a tiny plugin or a classic/child theme. Do not put phpcs, PHPStan, PHPUnit, or Jest in the component `composer.json`.

## Third-party frontend libraries

Prefer a local copy (`assets/vendor/` or npm, then `wp_enqueue_*` that local file). Do not enqueue unpkg, jsDelivr, cdnjs, or another CDN unless the user named that remote URL. During propose, write the local-vendor choice into `design.md`; do not record a CDN URL as the default.

## Headers and defaults

- `Author`: from the algorithm above.
- `Requires at least` / `Requires PHP`: copy a sibling first-party header; do not invent “latest WordPress”.
- `Version`: `0.1.0` unless the user named another.
- License: `GPL-2.0-or-later`.
- Comments: English. User-facing strings: sibling language, else English.
- Load the text domain using the folder slug.
- Do not add `uninstall.php` that deletes data unless asked.
- Do not invent ACF, WooCommerce, or other plugin dependencies. If the task requires one, guard it; do not silently couple a theme to that logic.

Child theme: `Template:` plus overrides only. Do not copy the parent. Do not write `docs/` inside a third-party parent.

## Git

Add an explicit `.gitignore` allowlist for the new folder. Match the existing ignore style. Do not rewrite the whole file. Do not commit `vendor/`, `node_modules/`, `build/`, or secrets.

## Documentation

After the scaffold files exist, if the documentation skill file exists, Read it and create the **component** `docs/catalog.md`. A prefix or author answer this turn does not skip that Read. If root `docs/catalog.md` exists, add a hop to the new component. If root catalog is missing, do not create it. If the documentation skill is missing, still write the code scaffold; do not invent docs and do not switch to a harness audit.

## PHPCS and QA registration

Do not create `phpcs.xml.dist` from scratch. That file is harness provision. The phpcs prefix list (`Acme,acme_`) is not the folder slug. Do not put phpcs, PHPStan, PHPUnit, or Jest in the component `composer.json`.

After a **not-tiny** scaffold, if existing repo configs are present, add this folder (and PHP prefix family) to `phpcs.xml.dist`, `phpstan.neon.dist`, `phpunit.xml.dist`, and JS lint/Jest configs. Create `<component>/tests/` if PHPUnit exists. Do not invent a behavior test. Tiny scaffolds skip that registration. If those configs are missing, ask the same toolbox questions as harness intent Yes; do not run `audit.mjs`. If `phpcs.xml.dist` exists and does not yet list this PHP prefix family, add it for not-tiny or tell the user for tiny. Do not install PHPCS from this skill.

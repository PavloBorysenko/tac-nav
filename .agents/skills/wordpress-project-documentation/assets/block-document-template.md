Read this template only when writing a block module file. Do not preload it for investigation, code change, or tasks that create no block documents.

Use this template for a registered project-owned block. Link the finished file from the component `docs/catalog.md`. Do not create `blocks/catalog.md`. Fold a PHP helper that exists only to serve this block into this file, including `functions.php` wiring of the same query or render contract. Put `ClassName` on this block's catalog row. Do not create `php/<class-slug>.md` for that helper.

# [Block title] (`<namespace>/<block-slug>`)

## Purpose

[Explain why this block exists and which public surface it owns.]

## Registration

- Name: `<namespace>/<block-slug>`
- Directory: `wp-content/themes/<theme>/blocks/<block-slug>/`
- Render: [PHP render callback or `render.php`]
- Inserter: [hidden or available]
- Attributes: [list, or none]

## Where it is used

- `templates/<file>.html` — [why this template embeds it]

Write paths as backticks. Do not markdown-link templates or PHP files.

## Render contract

[When the block returns without markup. Which class, query, or meta it reads. What it prints.]

If this file folds the helper, write `Visibility: ClassName::is_visible()` and the missing-meta default here. If a separate class document owns visibility, write `Visibility: ClassName::is_visible()` and do not explain the default. Do not paraphrase thin `get_post_meta` wrappers.

## Sections

| Section | Template | Show key | Fields |
| --- | --- | --- | --- |
| `<section-id>` | `sections/<file>.php` | `<meta_key>` | [fields read] |

Omit this table when the block has no sections. List show-key names only. Do not explain visibility defaults in this table. Do not copy theme heading strings unless other code or a selector depends on them.

## Frontend contract

[Data attributes, script handles, or CSS hooks that other code depends on. Omit when none exist. Do not list every presentational BEM class. Put selected `data-*` hooks on this block's catalog row unless another row already owns them.]

Name any helper class in the render contract. Do not add a Related classes or Related documents list. Do not open that class file unless its catalog condition independently matches.

## Constraints and side effects

[Assumptions, empty states, copied identifier strings, and failure behavior. Do not link another component's `docs/catalog.md`.]

## Implementation references

- `wp-content/themes/<theme>/blocks/<block-slug>/render.php` — render entry
- `wp-content/themes/<theme>/inc/<file>.php` — folded helper, when this block owns it; use the real path (`inc`, `includes`, `src`, `classes`, `lib`, `app`)

Remove every section that is not meaningful for this block. Do not paste render.php or method bodies. After drafting, if this file is longer than the listed source (except when a section map is still shorter than the `sections/` set), cut it.

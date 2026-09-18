Read this template only when writing a classic PHP template document. Do not preload it for investigation, code change, or a block theme with no PHP template documents.

Use this template for a classic or hybrid PHP template that is a decision surface. Do not create `docs/templates/` when HTML `templates/` block files are the only templates, and do not create `docs/templates/catalog.md`. Fold a helper that exists only to serve this template into this file.

# [Template title] (`<file.php>`)

## Purpose

[Why this template exists and which public surface it owns.]

## Selection

- File: `wp-content/themes/<theme>/<file>.php`
- Loaded when: [hierarchy rule, page-template slug, or `get_template_part` name]

Write paths as backticks. Do not markdown-link PHP files.

## Render contract

[Queries, copied plugin identifiers, empty and failure markup, what it prints.]

If a helper is folded here, state its non-obvious invariant. If a separate class document owns that invariant, name `ClassName::method()` and do not restate the default.

## Template parts

| Part | File | Role |
| --- | --- | --- |
| `<name>` | `template-parts/<file>.php` | [why this part runs] |

Omit this table when the template does not call `get_template_part`.

## Constraints and side effects

[Assumptions and failure behavior. Do not link another component's `docs/catalog.md`.]

## Implementation references

- `wp-content/themes/<theme>/<file>.php` — template entry

Remove every section that is not meaningful. After drafting, if this file is longer than the listed source, cut it.

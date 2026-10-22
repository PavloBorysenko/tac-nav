Read this template only when writing a shortcode document. Do not preload it for investigation, code change, or components with no shortcodes.

Use this template for a project-owned shortcode that is a decision surface. Link the finished file from the component `docs/catalog.md`. Do not create `php/catalog.md` or `shortcodes/catalog.md`. Fold tightly coupled tags that share one renderer into one file.

# `[shortcode]`

## Purpose

[What the shortcode prints and what it deliberately does not own.]

## Registration

- Tag: `[shortcode]`
- Callback: [`function_name`]
- File: `wp-content/themes/<theme>/functions.php` or an include such as `inc/shortcodes.php`

## Attributes

| Attribute | Default | Role |
| --- | --- | --- |
| `id` | `` | [why callers depend on it] |

Omit this table when the shortcode has no attributes.

## Render contract

- Returns or echoes: [HTML string, or that it prints]
- Empty or failure: [verified missing-data behavior only]
- Invariants: [non-obvious default]

Do not paraphrase the whole callback. Do not paste the function body.

## Constraints and side effects

[Enqueue dependencies, capability checks, copied plugin identifiers. Do not link another component's `docs/catalog.md`.]

## Implementation references

- `wp-content/themes/<theme>/functions.php` — `function_name()`; or the include file that actually holds the callback

Write paths as backticks. After drafting, length-gate this file against the callback, not against the entire `functions.php`. If the shortcode is a one-line wrapper with no invariant, delete this file and put `[shortcode]` on another catalog row.

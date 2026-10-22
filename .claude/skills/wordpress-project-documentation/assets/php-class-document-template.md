Read this template only when writing an unfolded PHP class document. Do not read it when every class is folded into a caller or skipped. Do not preload it for investigation or code change.

Use this template only for a project-owned PHP class that is cheaper to route to than to scan and that is not folded into a caller. Do not create this file when the class exists only to serve one block, PHP template, or shortcode, or when `functions.php` only requires it or hooks the same query or render contract: put `ClassName` on that caller's catalog row instead. Link the finished file from the component `docs/catalog.md`. Do not create `php/catalog.md`. Fold tiny variants of one contract into a single file.

# [ClassName]

## Purpose

[Explain what this class owns and what it deliberately does not own.]

## Location

- Namespace: `[Namespace\ClassName]` — omit this line when the file declares no namespace
- File: `wp-content/themes/<theme>/inc/<file>.php` — use the path discovered in the theme (`inc`, `includes`, `src`, `classes`, `lib`, `app`, or `functions.php`)

## Public constants

| Constant | Value | Role |
| --- | --- | --- |
| `ClassName::CONST` | `` `value` `` | [why callers depend on it] |

Omit this table when the class has no public constants.

## Public methods

Document a method only when empty, failure, or invariant behavior is not obvious from the signature. Skip thin wrappers such as `return (string) get_post_meta(...)`. Omit empty or failure rows you did not verify in the opened source.

### `methodName()`

- Signature: `public static function methodName(int $post_id, string $key): string`
- When to call: [task that needs this method]
- Returns or echoes: [return type, or that it prints]
- Empty or failure: [verified missing-data behavior only]
- Invariants: [non-obvious default, such as missing meta treated as visible]

Do not document private methods unless they define a public invariant. Do not paste method bodies.

## Constraints and side effects

[Assumptions, copied plugin identifier strings, and failure behavior. Do not link another component's `docs/catalog.md`. Do not add a Related documents list.]

## Implementation references

- `wp-content/themes/<theme>/inc/<file>.php` — class source; use the real path

Write paths as backticks. Do not markdown-link PHP files. Remove every section that is not meaningful. If this class is a small variant of a shared contract, fold it into that sibling document instead of creating this file. If this draft is longer than the class, or after cutting to invariants is still a constants-and-signatures table, delete the file and put `ClassName` on another catalog row.

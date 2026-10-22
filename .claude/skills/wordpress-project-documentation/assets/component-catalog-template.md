Read this template only when creating or rewriting a theme or plugin `docs/catalog.md`. Do not preload it for investigation or code change.

# [Component Name] Documentation

Read only the links whose condition matches the current task.

## Overview

- [Architecture](architecture.md) — read only when the task changes disable or theme-switch behavior, unmatched-template fallback, or whether this component registers post types, taxonomies, or REST routes
- [Integrations](integrations.md) — read only when changing which other catalog that contract points to, or when the set of tracked consumers changes
- [Security](security.md) — read only when the task changes `<nonce>`, capabilities, or REST write access

Add a link only for overview files that exist and own a distinct decision surface. Other optional names: `workflows.md`, `data-model.md`, `hooks-and-apis.md`, `configuration.md`, `development.md`. Do not add Architecture, Workflows, or Hooks merely to complete a list. Do not put a block name, class name, shortcode tag, PHP template filename, post type, taxonomy, or `namespace/*` / `sn_*` wildcard on an overview row when a module or Data model row already owns that identifier. A prefix must not match another row's identifier. Integrations must not repeat those identifiers.

## Blocks

- [Block title](blocks/<block-slug>.md) — read only when changing `<namespace>/<block-slug>`, `<ClassName>` folded into this block, or `templates/<file>.html`

## PHP templates

- [Single product](templates/single-product.md) — read only when changing `single-product.php` or `single-sn_product.php`

## Shortcodes

- [Hours](php/hours.md) — read only when changing `[acme_hours]`

## PHP classes

- [ClassName](php/<class-slug>.md) — read only when changing `ClassName` or `<identifier>` keys it owns

## Human

- [Human overview](human-overview.md) — read only when the user asks for a human-readable report, stakeholder summary, or onboarding overview about this component; never open for code, investigation, or agent-documentation work

Omit unused sections. Omit Blocks when there is no `block.json`. Omit PHP templates when there are no classic PHP template documents. Omit Shortcodes when tags are folded into a caller or skipped. Omit the PHP classes section when every class is folded into a caller or skipped. Omit the Human section when `human-overview.md` does not exist. Do not create `blocks/catalog.md`, `php/catalog.md`, or `templates/catalog.md`. Each reading condition must include a stable identifier when the target has one. Include a `data-*` hook when JS or another template selects it. Do not add a `data-*` hook another row already owns. Do not list every `data-*` in the markup. Do not write conditions with `needs`, `journey`, or `visitor journey`. Do not use "internals", "behavior", "presentation", or "public APIs" as the whole condition. Do not repeat an identifier on two rows in this catalog. If a class document owns meta keys, write `registered <prefix> post-meta keys` rather than a glob that also matches a taxonomy slug. Fold a one-caller helper onto that caller's row, including `functions.php` wiring of the same query or render contract. Do not put query, store, or "does not register" on the Architecture row as a match for listing or module tasks.

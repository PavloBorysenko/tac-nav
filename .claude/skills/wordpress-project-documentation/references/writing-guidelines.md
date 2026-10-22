# Documentation Writing Guidelines

Read this reference before writing or rewriting document bodies or catalog conditions. Do not read it during investigation or code change, except when a code change must edit a stale catalog reading condition. Catalog Prefer/Avoid examples live only in this file.

## Write for future decisions

Prioritize information that helps a developer or agent understand, change, debug, or operate the project without scanning unrelated source:

- purpose and responsibilities;
- reasons behind architectural choices;
- workflow boundaries and required entry points;
- data ownership and lifecycle;
- dependencies and integration contracts;
- important configuration;
- assumptions and invariants;
- side effects and failure behavior;
- limitations and upgrade risks;
- security and deployment implications;
- public method contracts and block render contracts.

Do not duplicate source code in prose. Names of classes, methods, blocks, and hooks are useful when they identify a stable entry point. Line-by-line implementation narration becomes stale and wastes context.

A module document should replace a class or block scan, not reprint it. Record only methods whose empty, failure, or invariant behavior is not obvious from the signature. Do not paste method bodies. Do not paraphrase thin `get_post_meta` wrappers. If the document is longer than the source it describes, cut it or delete it and put the class name on another catalog row. After cutting to non-obvious invariants, if the draft is still a constants-and-signatures table, delete it. Exception: a block section map may exceed one `render.php` when it is still shorter than the section file set.

Give each invariant one owner. A block that folds `EventSingle` writes `Visibility: EventSingle::is_visible()` and the missing-meta default. If a separate class document exists, the block names `EventSingle::is_visible()` and does not restate the default. Frontend contracts list hooks other code depends on, not every BEM class. Put those `data-*` hooks on the module catalog row unless another row already owns them. Heading copy belongs in docs only when other code or a selector depends on it.

## Use evidence

Support claims with current source code, configuration, project documentation, or an explicitly identified external source.

When evidence is incomplete:

- state what is confirmed;
- state what remains unknown;
- identify the investigation required to resolve it.

Do not turn an inference into a documented fact. Omit empty or failure rows you did not verify in the opened source. Do not reconstruct casts, `preg_split` fallbacks, or other branches in prose.

## Use English

Write all created and updated project documentation in English. When maintaining a non-English document, translate the affected document to English as part of the maintenance instead of producing mixed-language documentation.

Keep terminology consistent with the codebase and WordPress conventions.

## Keep catalogs as routers

A catalog exists to save agent context. It is a routing table, not a summary. Each entry must say when to open the target.

Prefer:

```markdown
- [Architecture](architecture.md) — read only when the task changes disable or theme-switch behavior, unmatched-template fallback, or whether this component registers post types, taxonomies, or REST routes
- [Data model](data-model.md) — read only when the task changes `sn_project`, `sn_project_type`, `sn_project_tag`, or rewrite slugs `projects` / `project-type` / `project-tag`
- [Integrations](integrations.md) — read only when changing which other catalog that contract points to, or when the set of tracked consumers changes
- [Projects Grid](blocks/projects-grid.md) — read only when changing `namespace/projects-grid`, `data-projects-filter`, `data-project-filter`, `data-projects-page-size`, or `data-projects-sentinel`
- [Event Single](blocks/event-single.md) — read only when changing `acme/event-single`, `EventSingle`, or Event section templates
- [Hours](php/hours.md) — read only when changing `[acme_hours]`
- [Workflows](workflows.md) — read only when the task changes `header.php`, `footer.php`, `index.php`, or page slugs `products` / `about`
- [Fields](php/fields.md) — read only when changing `Fields`, Project Card or Project Sections meta boxes, `sn_project_sections_nonce`, or a registered `sn_project_*` post-meta key
```

Avoid:

```markdown
- [Hooks and APIs](hooks-and-apis.md) — registers `acme_booking_export` and the REST route `/acme/v1/bookings`
- [Architecture](architecture.md) — read only when the task needs this plugin's responsibilities or internals
- [Architecture](architecture.md) — read only when the task changes what this component does not query, store, or register
- [Workflows](workflows.md) — read only when the task changes the careers or projects visitor journey
- [Architecture](architecture.md) — read only when the task changes `namespace/*` blocks or `sn_*` keys
- [Projects Grid](blocks/projects-grid.md) — read only when changing `data-modal-callback-button`
- [Data model](data-model.md) — read only when the task changes `sn_project`, `sn_project_type`, `sn_project_tag`, rewrite slugs, or Project meta lifecycle
- [Integrations](integrations.md) — read only when adding, removing, or renaming identifiers this plugin exposes
- [Hooks and APIs](hooks-and-apis.md) — read only when changing copied `sn_*` strings
- [Fields](php/fields.md) — read only when changing `Fields` or `sn_project_*` keys
- [Projects Grid](blocks/projects-grid.md) — queries twelve posts, hides the inserter, and emits `data-project-filter`
```

Do not put plugin cards, field dumps, method tables, or implementation detail in a catalog. Put that knowledge in the linked document. If the target has stable identifiers, the reading condition must include them so the agent can match the task without opening neighboring files. Include a `data-*` hook when JS or another template selects it. Do not add a `data-*` hook another row already owns. Do not list every `data-*` in the markup. Do not write conditions with `needs`, `journey`, or `visitor journey`. Do not use "internals", "behavior", "presentation", or "public APIs" as the whole condition. Process documents name template files, page slugs, webpack entries, or deploy steps, not a feature word a module row already owns. Do not put the same identifier on two rows in one catalog. A prefix must not match another row's identifier unless the glob names the identifier kind and still does not match that other identifier. Overview rows must not use `namespace/*` or `sn_*` wildcards, and must not use query, store, or "does not register" as a match for listing or module tasks. Integrations must not repeat identifiers that Data model or a module row already owns.

A component catalog may use `Overview`, `Blocks`, `PHP templates`, `Shortcodes`, `PHP classes`, and `Human` headings. Omit headings with no files. That is still one routing table. Do not create `blocks/catalog.md`, `php/catalog.md`, or `templates/catalog.md`. Skip the Human heading unless the user asked for a report.

The root catalog is a project index: principle documents, one catalog link per custom component, and one card link per critical third-party plugin. If `human-overview.md` exists, list it under a Human heading with a condition that matches only a human-report request. Do not paste a component's document list into the root catalog.

Follow a catalog link only when its reading condition matches the current task. After this task changes an identifier that `integrations.md` lists, open that file to update it and follow its consumer hop if needed. Do not open `integrations.md` only to orient. Do not follow Related documents or Related classes lists. Do not markdown-link a sibling this catalog already routes.

## Describe dependencies clearly

For an important dependency, document:

- why the project needs it;
- which components use it;
- the APIs, hooks, data models, or configuration relied upon;
- expected behavior when it is missing or disabled;
- version-sensitive assumptions and upgrade risks;
- where project-specific integration code lives.

Do not reproduce generic installation or vendor documentation unless the project uses a non-obvious procedure.

For a business-critical third-party plugin, keep the plugin card in a root document such as `docs/contact-form-7.md`. Keep project-specific integration details with the custom theme or plugin that implements them. Link to official technical or developer documentation rather than copying it. Use an identified official product-page fallback only when technical documentation does not exist.

## Keep the other component out of this file

Name the other component and the identifiers this file owns. Do not copy how the other component queries, sorts, filters, maps images, formats dates, or prints headings.

Prefer in a block or class file:

```markdown
These strings are copies of plugin identifiers. Do not import plugin classes.
```

Prefer in `integrations.md` only:

```markdown
This plugin exposes `sn_project` and `sn_project_*`. The theme copies those strings. Open the theme [catalog](../../../themes/<theme>/docs/catalog.md) only when this integrations row matched.
```

Avoid:

```markdown
`ProjectsArchive::configure_query()` disables pagination and orders by `menu_order`. The grid filters by tag slugs ordered by `term_order`.
```

```markdown
## Related documents
- [Careers catalog](../../../../plugins/supernova-careers/docs/catalog.md)
- [`supernova/career-single`](../blocks/career-single.md)
```

That consumer behavior belongs in the theme's block or class document. Putting it in the plugin forces every plugin task to load the theme. Putting the other catalog in a class file forces every class task to load the other component.

`hooks-and-apis.md` may name Fields in prose as the owner of the section map. It must not put `sn_project` or `sn_project_*` on its catalog row, must not markdown-link `php/fields.md`, and must not link another component's catalog. `data-model.md` records registered objects; it must not restate plugin disable or describe the theme's card label or filter chips. Give each invariant one owner file in this component. Sibling overviews may name that owner; they must not restate it.

Put a link to another component's `docs/catalog.md` only in `integrations.md`. Do not add that component's PHP files to Implementation references unless this document is responsible for changing them. Do not add a Related documents or Related classes list. Do not markdown-link a sibling this catalog already routes.

## Reference implementation

Use repository-relative paths when referencing code or documentation:

```text
wp-content/plugins/project-booking/src/BookingService.php
docs/woocommerce.md
```

Prefer stable file or symbol references written as backtick paths. Do not markdown-link templates, PHP files, or other sources; those links pull large files into context. Avoid fixed line numbers in maintained documentation unless the format automatically keeps them current.

## Update existing documents

Before writing a new file:

1. Check the relevant catalog.
2. Search for an existing document that owns the topic.
3. Extend the existing document when the topic belongs there.
4. Create a new module document when the topic is a distinct block, classic PHP template, shortcode, or a class that is not folded into a caller. A helper that serves only one caller belongs in that caller file.
5. Do not create a nested catalog to organize the new file; link it from the existing component catalog.
6. Update the relevant catalog.

## Self-review

Check that:

- the document answers why and how, not only what;
- a block or class document supplies the contract needed to decide, or the class was folded/skipped because source is cheaper;
- every behavioral claim has evidence in an opened file;
- empty or failure behavior was not inferred;
- current behavior is not based on stale documentation;
- boundaries, dependencies, and side effects are explicit;
- headings make the document scannable;
- no empty template sections remain;
- catalog links use correct relative paths, reading conditions, and stable identifiers when the target has them;
- no catalog condition uses "needs", "journey", or "visitor journey", or uses "internals", "behavior", "presentation", or "public APIs" as the whole rule;
- no Architecture catalog condition uses query, store, or "does not register" to match listing or module tasks;
- module catalog conditions include `data-*` hooks that other files select and do not repeat a hook another row already owns;
- no two rows in the same catalog share an identifier, no overview uses a `namespace/*` or `sn_*` wildcard, and no prefix/glob matches another row's identifier unless it names the identifier kind and still does not match that other identifier;
- the Integrations condition does not repeat identifiers owned by Data model or a module row;
- no nested catalog was added;
- no Related documents or Related classes sibling list was added;
- no overview markdown-links a sibling this catalog already routes;
- each skipped or folded class has its class name or public identifiers on some catalog row;
- no `php/<class-slug>.md` exists for a helper that serves only one caller or for `functions.php` wiring of the same query or render contract;
- a folded helper names `ClassName::method()` for its invariant;
- a missing `composer.json` or a PHP folder other than `app/` was not treated as a gap;
- `docs/blocks/` was not created when the component has no `block.json`;
- `functions.php` was not dumped into an overview;
- a child theme documents only this child's overrides;
- no sibling restates an invariant that already has an owner file;
- implementation references are backtick paths, not markdown links to templates or PHP;
- no block or class document links another component's `docs/catalog.md`;
- `human-overview.md` was not opened except for a human report, and was written only from facts already in context for roots the user asked this task to document;
- a request to document one theme or plugin did not create a missing root `docs/`;
- no document copies another component's queries, render details, or field maps;
- the document is located at the correct project or component level;
- the document is not longer than the source it replaces unless the extra length is a section map shorter than the section file set.

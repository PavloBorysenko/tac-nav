# Documentation Model

Read this reference before creating, moving, or substantially reorganizing WordPress project documentation. Do not create files until you have read it. Do not read it during investigation or code change. Do not read it only to classify ownership.

Read `references/writing-guidelines.md` before writing document bodies or catalog conditions. Catalog Prefer/Avoid examples live only there.

## Check the consumption rule

Do this only in Documentation creation, after catalogs exist or this task created one, and in Documentation audit when a `docs/` tree already exists. Skip it in investigation, code change, maintenance, and human report.

A consumption rule is always-apply project guidance that tells agents:

- for project-specific behavior, open `docs/catalog.md` first, follow only links whose reading condition matches the task, and not load the rest of `docs/`;
- after changing code, update existing documents whose catalog conditions match the change, and not create missing documentation unless the user asked for it.

It may live in `.cursor/rules/*.mdc` with `alwaysApply: true` or as equivalent text in `AGENTS.md`. Match those requirements, not the filename. A rule that only records component ownership is not a consumption rule. A catalog-first rule that omits post-change maintenance is incomplete.

1. Inspect `.cursor/rules/` and `AGENTS.md` only. Do not search the rest of the repository. Read always-apply rules and `AGENTS.md` when that file exists.
2. If any required clause is missing, read `assets/documentation-consumption-rule.md`. Report that catalogs will not be used, and that existing docs will not stay in sync after code changes, in sessions that do not load this skill. Offer that text.
3. Do not create or edit `.cursor/rules/` or `AGENTS.md` unless the user explicitly asked to add that rule.

## Project layout

Use a shallow two-level discovery model:

```text
project/
├── docs/
│   ├── catalog.md
│   ├── architecture.md
│   ├── contact-form-7.md
│   └── ...
└── wp-content/
    ├── themes/<custom-theme>/docs/
    └── plugins/<custom-plugin>/docs/
```

The discovery path should not exceed:

```text
root catalog → project principle document, third-party card, or component catalog
            → document
```

Physical directories may be deeper when required by the codebase, but documentation discovery must remain one hop from a catalog to a document. Opening a component catalog from the root catalog is the library hop, not a nested `blocks/catalog.md`. Follow only links whose reading conditions match the current task. Do not add a second catalog under `docs/blocks/`, `docs/php/`, or `docs/templates/`.

## Root documentation

`docs/catalog.md` is the project index. Keep it short. It routes to project principles, to each custom component's catalog, and to third-party cards. It must not copy a component catalog's Overview / Blocks / PHP list.

Root documents describe how the project is put together and how to work on it. They do not duplicate a plugin's field map or a theme's block contracts.

Create only documents containing meaningful project knowledge:

- `architecture.md` — component map and project-wide conventions
- `development.md` — local, Git, build, and CI conventions
- `deployment.md`, `operations.md`, `security.md`
- `business-workflows.md` — only when a workflow spans components and has no single owner
- `configuration.md` — only when environment assumptions are shared
- `human-overview.md` — people-facing report for this documentation root. Create it at the end of documentation creation for each root this task wrote because the user asked for that root. Do not add a project-root copy when the request was one theme or plugin.
- `<plugin-slug>.md` for each business-critical third-party plugin

`architecture.md` and `development.md` are the usual principle files. Do not create a root `data-model.md` or `integrations.md` that dumps component internals. Create a root capability file such as `docs/projects.md` only when a common task spans two custom components and a single catalog link would be ambiguous. That file is a router: two catalog links with reading conditions, no implementation dump.

From the root catalog, give each custom theme and custom plugin one link to its `docs/catalog.md`. The reading condition must include stable identifiers (post type, block namespace, shortcode tag, PHP template filename, or component slug). Do not list that component's architecture, workflows, or hooks in the root catalog.

Keep the root catalog as:

- **Project** — principle documents that exist; if `human-overview.md` exists, list it under a Human heading that matches only a human-report request;
- **Key functionality** — one link per custom theme or custom plugin, pointing at that component's `docs/catalog.md`, with identifiers in the reading condition;
- **Business-critical third-party plugins** — one link per plugin to a root card such as `docs/contact-form-7.md`.

Create or rewrite that root catalog only when the user asked to initialize or document the project, or when the work is project-wide. A request to document one component does not create a missing project-root `docs/`. If root `docs/catalog.md` already exists, you may add a hop to the component you documented; that edit does not create root `human-overview.md`.

## Custom-component documentation

Place knowledge specific to a custom plugin or theme in that component's `docs/` directory.

```text
wp-content/themes/<custom-theme>/docs/
wp-content/plugins/<custom-plugin>/docs/
```

The `blocks/`, `templates/`, and `php/` directories are document folders, not extra routers. Link each file directly from `docs/catalog.md`. Never create `blocks/catalog.md`, `php/catalog.md`, or `templates/catalog.md`. Put a file under `docs/blocks/` only when `block.json` files exist. Put classic PHP template documents under `docs/templates/` and shortcode or unfolded class documents under `docs/php/`. `human-overview.md` belongs next to that catalog, not under those folders.

Keep overview documents at the component `docs/` root. Create an overview file only when it owns a decision surface that is not already in a module file. Completing the recommended filename list is not a goal.

Component overview documents — create a file only when it owns a decision surface that a catalog plus a targeted source read would not cheaply give:

- `architecture.md` — disable or theme-switch behavior, unmatched-template fallback, and whether this component registers post types, taxonomies, or REST routes. The file body may say this component does not query or store plugin content; do not put query, store, or listing identifiers on its catalog row.
- `workflows.md` — editorial or public flow that is more than saving meta on the edit screen, including classic template hierarchy when that map is the decision surface
- `data-model.md` — object registration and value storage only when that table would make a disable-task reader load a large object catalog. Do not add it if Architecture already holds the skipped tiny classes' slugs and activate/deactivate. Do not restate plugin disable here.
- `integrations.md` — tracked consumers and the other component catalog. Do not make this file the catalog owner of identifiers that Data model or a module row already lists.
- `hooks-and-apis.md` — cross-cutting identifiers that are not already owned by a class, block, shortcode, template, or Data model catalog row
- `configuration.md` — settings or environment assumptions
- `development.md` — this component's build or enqueue map
- `security.md` — nonce, capability, or REST auth callers must not miss. Omit it when that nonce already lives on a class catalog row.

A small plugin may ship `catalog.md`, one module file, and `integrations.md`. Put skipped `PostType` / `Taxonomies` identifiers on Integrations only when Architecture and Data model do not exist; otherwise those slugs live on Data model or Architecture, not on both. A large theme still needs a module file per distinct surface that exists, plus every overview that owns a distinct surface. Do not create `docs/blocks/` for a classic theme with no `block.json`. Do not create Architecture and Data model as a pair that repeat the same slugs and the same activate/deactivate paragraph.

Do not put a component's field maps, block contracts, or class APIs in root documents.

## Cross-component contracts

Each custom component documents only the contract it owns.

- **Producer** (registers types, meta, or hooks): document disable behavior on Architecture. Put the identifier strings on the Data model or module row that owns them. Put the consumer catalog link only in `integrations.md`.
- **Consumer** (theme or plugin that reads those identifiers): document how it queries, renders, and fails in its own module files. Those files list the copied identifier strings. They must not link the producer's catalog.
- `integrations.md` is the only overview that may link the other component's `docs/catalog.md`. It is not a dump of the other component and not a second owner of `sn_project` or `sn_project_*`.
- `hooks-and-apis.md` lists cross-cutting identifiers that no module or Data model row already owns. It must not repeat a field or section map, must not markdown-link Fields, and must not link another component's catalog.
- `data-model.md` records registered objects and how values are stored. It must not restate plugin activate, deactivate, uninstall, or disable behavior. `workflows.md` describes this component's editorial flow. Neither file describes another component's listing UI.

Do not copy the other side into this component: queries, sort order, image variants, date formats, heading strings, block markup, or method-by-method behavior. Implementation references list only files this document is responsible for changing. Do not point a block or class document at another component's catalog or PHP files.

## Third-party components

Do not modify a third-party component directory to store project documentation. Do not write class or block documents for vendor code. Do not list every installed third-party plugin.

From the root catalog, give each business-critical third-party plugin one link to a root document such as `docs/contact-form-7.md`. That document records path, role, official technical or developer documentation, required status, disabling consequences, and known compatibility constraints. If official technical documentation does not exist, use the plugin's official product page on the developer's website and identify it as a fallback. Do not use third-party directories, reviews, mirrors, or tutorials.

Place project-specific integration details in the custom component that implements them. Link that component catalog from the card; do not copy vendor APIs or the consumer's render details into the card.

## Maintain catalogs

The primary purpose of every catalog is to save agent context. Keep it as short as a routing table: optional section headings, links, and one reading condition per link. Do not put summaries, plugin cards, method tables, or implementation detail in a catalog.

A component catalog may group links under `Overview`, `Blocks`, `PHP templates`, `Shortcodes`, `PHP classes`, and `Human`. Omit headings with no files. That is still one catalog, not a second routing level. Skip the Human heading unless `human-overview.md` exists; follow it only when the user asked for a report.

Every catalog entry must tell the agent when to open the target, not what the target contains. If the target has stable identifiers, the condition must include them: post type, `namespace/block-name`, `ClassName`, hook, shortcode, nonce, meta-key prefix, or a `data-*` hook that JS or another template selects. Do not add a `data-*` hook another row already owns. Do not list every `data-*` in the markup. Process documents name template files, page slugs, webpack entries, or deploy steps. They must not use `needs`, `journey`, or `visitor journey`, or a feature word a module row already owns (`careers`, `projects`, a block name). Do not use "internals", "behavior", "presentation", or "public APIs" as the whole condition.

Give each identifier one owner row in that catalog. Test every prefix: if `php/fields.md` uses `sn_project_*` and another row owns `sn_project_type`, the glob is illegal. Qualify it (`registered sn_project_* post-meta keys`) or drop it and keep `Fields` plus the meta-box names. Overview conditions name the decision they own: disable or theme-switch behavior, unmatched-template fallback, whether this component registers post types, taxonomies, or REST routes, which other catalog the contract points to, Contact Form 7 hashes, webpack entries. They do not use query, store, or "does not register" as a match for listing or module tasks. They do not reuse a block name, class name, post type, taxonomy, or `namespace/*` / `sn_*` wildcard that would fire on every module task.

`integrations.md` may list this component's identifier strings in its body. Its catalog condition must not repeat post type, taxonomy, meta, block, class, hook, or nonce identifiers that another row already owns.

When you skip or fold a class document, put that class name and its public identifiers on the catalog row of the document that holds those settings. Enqueue-only helpers that exist to serve one meta box belong on that class row, not on Architecture. Do not put "open `PostType.php` when identifiers change" on a document whose catalog row would not match that task.

After adding, removing, renaming, or materially changing a document, verify its owning catalog. Edit the catalog only when a link, reading condition, placement, or fallback target became stale. During a code change, do not rewrite an existing root catalog that already lists that component's key documents.

Write catalog rows using the Prefer/Avoid examples in `references/writing-guidelines.md`.

## Select module documents

Before writing, inventory the surfaces that exist: every `block.json`, classic PHP templates, `add_shortcode` tags, and project-owned PHP classes or include files wherever they live (`inc`, `includes`, `src`, `classes`, `lib`, `app`, or `functions.php`). Follow `require`/`include` from `functions.php`. Do not assume Composer, PSR-4, or an `app/` directory. Skip `node_modules/`, `vendor/`, `build/`. Do not treat a missing `composer.json` or a PHP folder other than `app/` as a documentation gap.

Then create only the module files those surfaces need:

- one `docs/blocks/<block-slug>.md` per registered project-owned block;
- one `docs/templates/<file-slug>.md` per classic PHP template that is a decision surface;
- one `docs/php/<shortcode-slug>.md` per shortcode that is a decision surface, or one grouped file for tightly coupled tags;
- one `docs/php/<class-slug>.md` only for a public class that owns a different decision than its caller and is cheaper to route to than to scan.

Do not create `docs/blocks/` when there is no `block.json`. A registered block or a distinct shortcode that appears only as a one-line row in `hooks-and-apis.md` is incomplete. Overview documents must name module files in prose instead of dumping every block, template, or class. Do not markdown-link those files; the catalog is the index. `integrations.md` must not dump another component: record this component's identifiers and, only in that file, link the other `docs/catalog.md`. Block, template, shortcode, and class documents must not contain that catalog link.

Fold a helper that exists only to serve one caller into that caller document: a block, a PHP template document, or a shortcode document. Put `ClassName` on that one catalog row. In that file name `ClassName::method()` and the non-obvious invariant: `Visibility: EventSingle::is_visible()`. Missing show meta defaults to visible. Do not create `php/<class-slug>.md` for that helper.

Two call sites are not enough. If the second site is only `functions.php` wiring of the same query or render contract the caller already documents, fold `ClassName` into that caller and put the class name on that catalog row. Create `php/<class-slug>.md` only when the class owns a different decision (save or nonce versus markup, a hook other callers must not miss) or must be changed without opening the caller. After cutting to non-obvious empty, failure, or defaults, if the draft is still a constants-and-signatures table, delete it.

Group tightly coupled variants in one module file, such as menu walkers or shortcodes that share one renderer. Do not create a class or shortcode document that is longer than its source function, or that only paraphrases `get_post_meta` wrappers.

Do not dump `functions.php` into Architecture, Workflows, or Hooks. When it holds several unrelated surfaces, give each identifier a catalog row. After a row matches, read only the named function.

After drafting a module document, compare its character count with the source files listed in Implementation references, excluding `functions.php` when it only wires the class. If the logic lives in `functions.php` or an include file, compare against that function or class, not the whole bag file. If the document is longer, cut it to invariants or delete it and put the identifier on another catalog row. Exception: a block or PHP-template section map may exceed one entry file when it replaces several parts and is still shorter than that set.

Do not create placeholder documents merely to complete the overview list.

## Write from evidence

Document the decision surface an agent needs without scanning unrelated source. Avoid line-by-line narration and copied source code. Do not paste method bodies. Do not paraphrase thin wrappers such as `return (string) get_post_meta(...)`. A module document that is longer than the source it replaces degrades context and accuracy.

Give each invariant one owner document in this component. Sibling files may name that owner in prose; they must not restate disable behavior, nonce rules, field maps, missing-meta defaults, or "the theme copies these strings". If a helper is folded into the block, that block owns the visibility invariant: write `Visibility: ClassName::is_visible()` and the missing-meta default. If a separate class document exists, the block writes `Visibility: ClassName::is_visible()` and does not explain the default. A section table may list show-key names only. Plugin activate, deactivate, uninstall, and disable belong to Architecture, not to Data model or Integrations. Frontend contracts list hooks other code depends on, not every presentational BEM class. Put those `data-*` hooks on the module catalog row unless another row already owns them. Document a theme heading string only when other code or a selector depends on it. Do not add a Related documents or Related classes list. Naming a class in a render contract is not permission to open that class file unless its catalog condition independently matches.

## Human overview during creation

Create `human-overview.md` at the end of documentation creation for each documentation root this task actually wrote because the user asked for that root. Put it next to that catalog and add the Human catalog row. Write from facts already in context. Do not Read the previous file. Use `assets/human-overview-template.md`. Keep the four sections short.

## Templates

Read only the template for the kind of file you are about to write:

- `assets/root-catalog-template.md` — creating or rewriting the project-root catalog
- `assets/component-catalog-template.md` — creating or rewriting a theme or plugin catalog
- `assets/third-party-plugin-template.md` — creating a root third-party card
- `assets/document-template.md` — creating a generic overview such as Architecture or Workflows
- `assets/block-document-template.md` — creating a block module file
- `assets/php-template-document-template.md` — creating a classic PHP template document
- `assets/shortcode-document-template.md` — creating a shortcode document
- `assets/php-class-document-template.md` — creating an unfolded class document. Do not read it when every class is folded or skipped.
- `assets/human-overview-template.md` — writing `human-overview.md`
- `assets/documentation-consumption-rule.md` — reporting a missing consumption rule, or when the user asked to add it

Adapt templates and remove empty or irrelevant sections.

## Missing documentation

When `docs/catalog.md` does not exist:

- for a question or investigation, inspect available README files, component documentation, configuration, and code without creating files;
- for an explicit documentation request, create catalogs and documents only within the requested scope. Documenting one theme or plugin does not create a missing root `docs/`;
- for a code change, update affected existing documentation but do not create missing documentation.

Do not treat the mere absence of documentation as authorization to generate a complete documentation tree. Every custom theme and custom plugin is expected to have a concise `docs/catalog.md`. During non-creation work, report a missing catalog only when the component is relevant to the current task or when performing an explicit project-wide audit.

## Creation and audit final check

Before finishing a creation or audit task:

- all created or updated documentation is in English;
- files are at the correct project or component level;
- catalogs remain short routing maps whose links include reading conditions and, where the target has them, stable identifiers rather than "needs", "journey", "internals", or "behavior" as the whole condition;
- no two rows in the same catalog share an identifier, no overview condition uses a `namespace/*` or `sn_*` wildcard, and no prefix/glob matches another row's identifier unless it names the identifier kind and still does not match that other identifier;
- the Integrations condition does not repeat identifiers owned by Data model or a module row;
- each skipped or folded class has its class name or public identifiers on some catalog row;
- no `php/<class-slug>.md` exists for a helper that serves only one caller or for `functions.php` wiring of the same query or render contract, the caller document names `ClassName::method()` for the folded invariant, and no sibling restates the owner file's missing-meta default;
- no Architecture catalog condition uses query, store, or "does not register" to match listing or module tasks;
- module catalog conditions include `data-*` hooks that other files select and do not repeat a hook another row already owns;
- no created module document is longer than the source it lists, unless it is a section map shorter than the section file set;
- `docs/blocks/` was not created when the component has no `block.json`;
- a missing `composer.json` or a PHP folder other than `app/` was not treated as a documentation gap;
- `functions.php` was not dumped into Architecture, Workflows, or Hooks;
- a child theme documents only this child's overrides and has no `docs/` inside a third-party parent;
- implementation references are backtick paths, not markdown links to templates or PHP;
- no Related documents or Related classes sibling list was added, and no overview markdown-links a sibling this catalog already routes;
- no block or class document links another component's `docs/catalog.md`;
- `human-overview.md` was written only from facts already in context for documentation roots this task documented because the user asked for that root;
- a request to document one theme or plugin did not create a missing root `docs/catalog.md` or `docs/human-overview.md`;
- catalogs link to every added, renamed, or materially changed agent document;
- no nested catalog exists under `docs/blocks/`, `docs/php/`, or `docs/templates/`;
- overview documents were not created only to fill the recommended name list, and do not restate an invariant that already has an owner file;
- no document copies another component's queries, render details, or field maps;
- third-party plugin cards live in root `docs/` files rather than in the catalog;
- third-party plugin documents use official technical or developer documentation, or an identified official product-page fallback;
- a missing or incomplete consumption rule was reported after a catalog existed and always-apply rules plus `AGENTS.md` did not contain catalog-first routing and post-change maintenance, and that rule was not created unless asked;
- unresolved contradictions and required catalogs missing within the current task scope were reported.

# Theme surfaces

Read this reference when creating or substantially reorganizing theme documentation if the theme is a child (`Template:` in `style.css`), registers shortcodes, keeps several unrelated surfaces in `functions.php`, stores PHP outside `app/` and has no `block.json`, or has no `theme.json` and has PHP templates such as `index.php` or `header.php`. Do not read it during investigation or code change. Do not read it for a block theme whose PHP already lives in `app/` and which has `block.json`.

The catalog model does not change. Inventory the surfaces that exist. Do not create `docs/blocks/` when there is no `block.json`. Do not invent HTML `templates/` or `parts/` paths for a classic theme. Do not require Composer, a namespace, or a folder named `app/`.

## Find PHP logic

Themes often keep PHP in `functions.php` plus one or more include directories whose names vary: `inc`, `includes`, `src`, `classes`, `lib`, `app`, `helpers`, `inc/classes`. Discover the layout from evidence:

1. Follow `require`, `require_once`, `include`, and `include_once` from `functions.php` and from already loaded theme PHP.
2. Look for `class` declarations and `add_shortcode` / `add_action` in those files.
3. Use `composer.json` autoload only when that file exists. Treat it as a hint, not as a missing dependency.
4. Ignore `node_modules/`, `vendor/`, `build/`, and `dist/`. A theme `src/` that holds only `js/` and `scss/` is frontend; document enqueue on Development, do not treat it as a PHP class tree.

Implementation references use the path you actually found, for example `wp-content/themes/<theme>/inc/class-hours.php`. Do not rewrite it to `app/Content/` or invent a namespace the file does not declare.

Do not skip `wp-content/themes/<theme>/includes/` because WordPress Core uses `wp-includes/` at the WordPress root. Only the Core path is off-limits.

A class file with no namespace is still a class. A procedural include that owns a shortcode is still a shortcode surface. Fold or document it by identifier, not by folder name.

## Detect the theme

Use theme files, not the folder name:

| Evidence | Treat as |
| --- | --- |
| `theme.json` and HTML files under `templates/` | Block theme. Use block module files as in `SKILL.md`. |
| PHP templates (`index.php`, `header.php`, `single.php`, `page.php`, `archive.php`, `*.php` page templates) and no `theme.json` | Classic theme. |
| Both `theme.json` and PHP templates | Hybrid. Document each surface that exists. |
| `style.css` has a `Template:` header | Child theme of that parent slug. |

A child is its own component. Classify the parent independently (custom vs third-party). Never create or edit documentation inside a third-party parent.

## Classic PHP templates

`workflows.md` owns template selection: which PHP file the hierarchy loads, page-template slugs, and `get_template_part` entry points. Name files (`header.php`, `page-contact.php`), not a visitor journey.

Create `docs/templates/<file-slug>.md` only when that PHP template is a decision surface (branching, queries, empty states, copied plugin identifiers) that a catalog plus a cheap source read would not give. Use `assets/php-template-document-template.md`. Link it from the component catalog under PHP templates.

Fold a helper that exists only to serve one PHP template into that template document. Put the class name on that catalog row.

Group `header.php` / `footer.php` into Workflows when they are chrome without a separate contract. Do not create `docs/templates/catalog.md`.

## Shortcodes

Each `add_shortcode` identifier belongs to one catalog row. Use `assets/shortcode-document-template.md`.

- One shortcode with attributes, empty states, or side effects: `docs/php/<shortcode-slug>.md`.
- Tightly coupled variants that share one renderer: one file, several identifiers on that row.
- A one-line wrapper with no invariant: skip the file; put `[shortcode]` on Hooks and APIs or on the template that is its only caller.

Do not dump every shortcode into `hooks-and-apis.md` as the only record. That forces every shortcode task to load unrelated tags.

## Large `functions.php`

Do not paste `functions.php` into Architecture, Workflows, or Hooks. That file is a bag of surfaces, not one chunk.

When it holds more than one unrelated decision surface (several shortcodes, mixed enqueue, template filters, CPT wiring):

1. Give each identifier a catalog row and a module file, or fold it into the template that is its only caller.
2. Implementation references name the file that holds the callback (`functions.php` or an include such as `inc/shortcodes.php`) and the function. After the catalog matches, read that function, not the whole file.
3. Length-gate the document against that function, not against the entire `functions.php`.
4. Architecture body records disable/theme-switch, unmatched-template fallback, and whether this theme registers post types, taxonomies, or REST routes. Its catalog condition must not use query, store, or "does not register" as a match for listing or module tasks.

Enqueue maps still belong on Development when that file owns them. A nonce that callers must not miss may live on Security or on the shortcode row that owns it.

## Child themes

On Architecture, record the `Template:` parent slug, what this child registers or overrides, and what it inherits without copying the parent implementation.

Document only files this child owns: overridden templates, child `functions.php` surfaces, extra shortcodes, extra enqueue. Do not restate parent template hierarchy, parent shortcodes, or parent field maps.

If the parent is a custom component in scope, put the parent catalog link only in the child's `integrations.md`. If the parent is third-party, do not create `docs/` inside it; name the parent slug and, when needed, its official documentation from a root card or from Architecture prose without a sibling markdown dump.

Root catalog: one link to the child's `docs/catalog.md`. Add a parent catalog link only when the parent is custom and this task documents it.

## Catalog headings

A component catalog may use `Overview`, `Blocks`, `PHP templates`, `Shortcodes`, `PHP classes`, and `Human`. Omit any heading that has no files. That is still one routing table.

Root catalog reading conditions name identifiers that exist: theme slug, `namespace/*` blocks when the theme has blocks, `[shortcode]` tags, or PHP template filenames. Do not require `namespace/*` or `templates/` HTML paths on a classic theme.

# First-party PHP

Read this file only when `review-scope.mjs` put `references/php.md` in `read[]`. Classic `*.php` and `block.json`. Not block theme `templates/*.html` or `parts/*.html`.

## Hybrid architecture

Editing existing behavior: put the change next to this repo's current owner. Do not extract a new layer, service, Composer `app/`, REST route, or CPT unless the task asked. A PHP view under `templates/` is presentation, not a new layer.

New file, class, hook, or growth of `functions.php`:

- Bootstrap / `functions.php`: register and include only.
- Query and data: existing plugin or class owner, not a classic theme PHP template.
- Persist and sanitize: the owner class or include. Not inside the view.
- Presentation: classic PHP template, plugin view, or block — not a new container. More than two HTML lines next to persist/sanitize belong in `templates/` (or this repo's existing views folder). Domain subfolders (`admin/`, `shortcode/`) only when the component has those surfaces. Follow an existing views tree; do not invent a second one. Block HTML lives in `templates/` / `parts/` and is reviewed from `references/html.md` when `read[]` names it.
- Assets: `wp_enqueue_*`, not dumped in a template.

Extracting that view is not overengineering. Extracting a service, interface, or helper for a one-file logic fix still is. One or two HTML lines in a persist function may stay.

Classic theme templates and plugin view files render prepared data. They must not sanitize POST or call `update_option` / `update_post_meta`.

Smell: `$keys = array( 'a', 'b', 'c' )` in one function and `$labels = array( 'a' => 'A', 'b' => 'B', 'c' => 'C' )` in another. Adding a key in only one list drifts. Keep one map; derive keys (`array_keys`). Parallel `$keys` + `$labels` are `[must-fix]`. Collapsing those maps is not a new layer. Do not flag a slug used in two calls, coincidental duplicate strings, or a pasted query — those are not this catalog. Pasting the same query twice is still not a reason to extract a class.

Do not copy query or render into a second component when an owner already exists. Do not move plugin logic into the theme “because markup”, or the reverse, when this repo already splits them. Classic `*.php` templates and block `templates/*.html` are different surfaces.

Do not Read `references/html.md`, `references/js.md`, or `references/css.md` from here unless `read[]` already names them.

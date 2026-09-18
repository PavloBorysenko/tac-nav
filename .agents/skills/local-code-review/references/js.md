# First-party JS

Read this file only when `review-scope.mjs` put `references/js.md` in `read[]`. Skip `*.min.js`, `build/`, `dist/`, and hashed bundles.

## Placement

- Put new behavior next to this repo's current JS owner (same entry, same enqueue).
- Do not add a new file or bundle for a few lines in an existing script.
- Enqueue with `wp_enqueue_script`. Do not dump a script tag in a PHP or HTML template.
- Do not invent a framework, bundler, or store unless the task asked.
- The same keys must not live in two hand-maintained lists. Smell: `KEYS = ['a','b','c']` in one function and `LABELS = { a: 'A', b: 'B', c: 'C' }` in another. Adding a key in only one list drifts. `[must-fix]`: one object, derive keys (`Object.keys`). Do not flag coincidental duplicate strings or a slug used in two calls.

## Cheap checks

- Comments added in this diff are English. Do not rewrite older comments.
- Do not paste the same listener or fetch block twice in this diff.
- Do not hunt unused functions.

Do not Read `references/php.md`, `references/html.md`, or `references/css.md` from here unless `read[]` already names them.

# Block HTML templates

Read this file only when `review-scope.mjs` put `references/html.md` in `read[]`. First-party `templates/*.html` and `parts/*.html` (and other theme `.html`). Not classic `*.php` templates. Not this file for `theme.json` alone.

## Placement

- HTML templates compose blocks and parts. Query, capability, and data stay in a PHP render or a block owner.
- Do not add `templates/*.html` when an existing `parts/` file already owns that chrome.
- Do not paste the same `<!-- wp:` block markup into a second template. Reuse a part or the existing template.
- Do not replace an existing theme pattern or block with a raw HTML dump unless the task asked.
- Follow this theme's block markup and naming. Do not invent a second template hierarchy.

## Cheap checks

- Comments added in this diff (`<!-- -->`) are English. Do not rewrite older comments.
- Do not paste the same new markup block twice in this diff.
- Do not hunt unused markup across the theme.

Do not Read `references/php.md`, `references/js.md`, or `references/css.md` from here unless `read[]` already names them.

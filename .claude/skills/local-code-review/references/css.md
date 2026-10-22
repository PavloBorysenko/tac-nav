# First-party CSS

Read this file only when `review-scope.mjs` put `references/css.md` in `read[]`. Skip `*.min.css`, `build/`, `dist/`, and hashed bundles. Viewports and asset 404s are `wp-browser-sensor`, not this file.

## Placement

- Put new rules next to this theme's current stylesheet and naming (existing BEM or project prefix).
- Do not add a new stylesheet for one rule.
- Do not invent a second naming system in the same component.
- Layout belongs in CSS, not in JS. Do not move spacing into a script unless the file already does that.

## Cheap checks

- Comments added in this diff are English. Do not rewrite older comments.
- Do not paste the same new rule block twice in this diff.
- Do not add `!important` unless this file already uses it that way.

Do not Read `references/php.md`, `references/html.md`, or `references/js.md` from here unless `read[]` already names them.

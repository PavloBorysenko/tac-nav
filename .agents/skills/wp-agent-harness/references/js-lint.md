# JS lint for the WP agent harness

Read this file only when the user approved a JS lint step.

## Team policy

Lint first-party **source** JS. Skip `node_modules`, `vendor`, `build`, `dist`, `*.min.js`, and hashed bundles (`main.[hash].js`).

Do not use PHPCS on JS. Do not `npm init` ESLint inside every plugin.

| Shape | What to run |
| --- | --- |
| Component has `@wordpress/scripts` | `lint:js` in that component `package.json` (`wp-scripts lint-js`) |
| First-party JS, no wp-scripts | `tools/js-lint/` (`eslint` + `@wordpress/eslint-plugin/esnext`), `paths.json` lists those directories |
| No first-party JS | Skip. Audit `missing.lintJs` stays false |

## Repo toolbox (after explicit confirmation)

```bash
npm install --prefix tools/js-lint
npm --prefix tools/js-lint run lint
```

`paths.json` is site-specific. `node_modules` is gitignored; commit `package.json` and `package-lock.json`.

Extend `plugin:@wordpress/eslint-plugin/esnext`, not `recommended`. The recommended preset loads `@typescript-eslint` even for plain JS and can crash (`ts-api-utils` / `Intrinsic`) with eslint 8 and `@wordpress/eslint-plugin` 22.

## Existing style debt (Windows CRLF / Prettier)

First-party JS often fails thousands of `linebreak-style`, `prettier/prettier`, `indent`, and `space-in-parens` findings. That is legacy style, not a broken gate.

On a feature task:

- Still run lint on the changed files.
- Do **not** `eslint --fix` or rewrite the whole file for those style rules unless the user asked for a format pass.
- Report the style-error count. Fix syntax, `no-undef`, and issues in lines the agent added.
- Do not mix a repo-wide LF/Prettier rewrite into the feature diff.

A dedicated format commit (`.gitattributes` `eol=lf` plus `--fix`) is a separate, explicit request.

---
name: local-code-review
description: >-
  Reviews first-party WordPress PHP, block HTML templates, JS, and CSS after QA
  gates: architecture placement, mixed PHP layers, parallel key lists, first-party
  bounds, secrets, empty/failure vs the task, overengineering. Use only when
  wp-agent-harness launches one isolated subagent with this skill after green
  phpcs/PHPStan/lint on non-tiny first-party PHP, templates/*.html,
  parts/*.html, JS, or CSS. Do not use when the user asks for a review,
  analysis, or a broader code check.
  Do not use for tiny phpcs-only work, a one-line color tweak, docs, audit,
  Drupal, or as a substitute for lint.
disable-model-invocation: true
---

# Local code review

Inferential architecture check after green computational gates (and after `wp-browser-sensor` if it ran). Not a fourth linter. Not CI.

`wp-agent-harness` decides **when**. This file is **how**. If this file was opened because the user asked for a review, analysis, or a broader check, stop and do not follow it. If gates have not run on the touched first-party files, stop and run them first.

## Isolated reviewer

If this file was opened by the coding agent that just wrote the diff, stop. Do not review it yourself; the harness launches one isolated readonly subagent instead.

If you are that isolated reviewer, continue. Do not explore beyond `review-scope.mjs`. Do not ask the parent why the diff looks this way.

## Load only what `read[]` names

Do not read `references/` unless `review-scope.mjs` listed that file in `read[]`.

| File | Read when `read[]` contains it |
| --- | --- |
| `references/php.md` | First-party `.php` or `block.json` |
| `references/html.md` | Block theme `templates/*.html`, `parts/*.html`, or other first-party `.html` |
| `references/js.md` | First-party JS (not `*.min.js` or `build/`) |
| `references/css.md` | First-party CSS/SCSS (not `*.min.css` or hashed bundles) |

## Scope the diff

Run, then obey the JSON. Do not choose files yourself:

```bash
node .agents/skills/local-code-review/scripts/review-scope.mjs --root "/absolute/path/to/wp-root"
```

From this authoring pack, the same script lives at `scripts/review-scope.mjs`. Pass `--help` for flags.

Review **only** `files[]`. Do not `git log` the repo. Do not open WordPress Core (`wp-admin/`, `wp-includes/`). Do not open a sibling class until a finding names it as the existing owner. Do not open Architecture or unmatched catalog rows for orientation.

- `mode` `skip` → print `Local review: pass` and stop. Do not open `references/`.
- `outOfScope[]` non-empty → `[must-fix]` first-party boundary; do not review style of those paths.
- Then Read each path in `read[]` and nothing else from `references/`.

Find the existing owner cheaply: one matching `docs/catalog.md` row for identifiers in `files[]`, or one first-party grep. Not a theme-wide scan.

## Cheap checks gates miss

Apply to every path in `files[]`:

1. **First-party** — the diff must not include Core, a third-party plugin, or `wp-config.php`.
2. **Secrets** — no passwords, API keys, or config dumps in the diff.
3. **Empty/failure** — only paths the task or spec named in 1–3 bullets. Do not invent extra empty states.
4. **Overengineering** — no extra files, interfaces, or helpers for a one-file fix.
5. **Comments** — comments added in this diff are English. Do not rewrite older comments.
6. **Repeated new block** — do not paste the same new code block twice in this diff. Do not extract a helper class for that copy. Collapsing a split key catalog is check 8, not this.
7. **Mixed PHP layers** — persist/sanitize and more than two HTML lines must not share a PHP file. Move the view to `templates/` (domain folders like `admin/` or `shortcode/` only when those surfaces exist, or this repo's existing views folder). One or two HTML lines may stay. If `signals` includes `mixed-markup`, this is `[must-fix]` unless the extra markup is that small.
8. **Split source of truth** — the same keys must not live in two hand-maintained lists. Smell: `$keys = ['a','b','c']` in one function and `$labels = ['a'=>'A','b'=>'B','c'=>'C']` in another; adding `'d'` in only one list drifts. Same smell in JS (`KEYS` + `LABELS`). `[must-fix]`: one map, derive the key list. Do not add a class only to hold the map. Do not treat every duplicated string or slug as this smell — only a catalog of keys that two lists must keep in sync.

If `signals` includes `sql` or `echo-html`, new input must not go raw into SQL or HTML. If `signals` includes `non-english-comment`, `[must-fix]` those added comments. If `signals` includes `mixed-markup`, apply check 7. Do not restate phpcs or ESLint. Do not hunt unused functions.

## Report

```text
Local review: pass
```

or

```text
Local review: issues
- [must-fix] path:line — one sentence
- [ask] path:line — one sentence
```

Fix `must-fix` before finishing. Pass is one line; do not praise each file. Do not offer `audit.mjs` or PHPUnit because this review ran.

After a fix: re-run phpcs/PHPStan/lint on the touched files, then `review-scope.mjs` again. Do not reopen the browser unless UI changed.

# Cursor rules for the WP agent harness

Read this file only when the user approved a Cursor-rules step.

## Team policy

Computational gates (phpcs, PHPStan, JS lint) do not run by themselves. A project rule in `.cursor/rules/` is what makes the agent run them while developing.

Do not add this as a git hook or CI job from this skill unless the user asked for that separately.

## QA loop (required for job 2)

File: `.cursor/rules/wp-agent-harness-qa.mdc`

Frontmatter must include `harness: qa-loop` and `qaLoop: <mode>` so audit can detect it.

| Mode | When gates run |
| --- | --- |
| `task` (default) | While the agent is developing: on the changed first-party files, before claiming the task done. Self-correction. |
| `every-change` | After each first-party file write in the same turn. |
| `manual` | Only when the user asks to run phpcs, phpstan, or lint. |

If the rule is missing, ask which mode to write. Do not assume `every-change`. Default offer is `task`.

JS lint on a feature task must not drown in legacy `linebreak-style` / Prettier. See `references/js-lint.md`. Do not `--fix` a whole file for those rules unless the user asked to format.

```bash
node scripts/write-cursor-rule.mjs --confirm --root "/absolute/wp-root" --id qa-loop --mode task
```

## Recommended rules (offer, do not auto-add)

| id | Why |
| --- | --- |
| `first-party-scope` | Agent must not edit `wp-admin`, `wp-includes`, or unconfirmed plugins |
| `docs-catalog` | Already present if a rule mentions `docs/catalog.md` (or the theme catalog fallback). Do not duplicate. |

```bash
node scripts/write-cursor-rule.mjs --confirm --root "/absolute/wp-root" --id first-party-scope
```

Do not write `docs-catalog` if audit already found a catalog consumption rule.

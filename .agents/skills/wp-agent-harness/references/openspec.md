# OpenSpec for the WP agent harness

Read this file only when the user approved an OpenSpec step.

## Team policy

- **CLI** lives globally on each developer machine: `openspec` on PATH. Requires Node 20.19.0 or newer.
- **Project folder** lives in the git repo: `openspec/` at the WordPress root (same root as `phpcs.xml.dist`).
- Do **not** add `@fission-ai/openspec` to a theme or plugin `package.json`.
- Do **not** pass `openspec` to `install-skills.mjs`. Official `openspec-*` workflow skills are written by `openspec init`, not by the pack installer.
- `Not now` is valid. Tiny WordPress fixes do not need a spec.

## Global install (after explicit confirmation)

```bash
npm install -g @fission-ai/openspec@latest
openspec --version
```

If `node --version` is older than `v20.19.0`, stop and tell the user OpenSpec needs a newer Node. Do not install anyway.

## Initialize the repo (after explicit confirmation)

Run from the WordPress git root. `--tools cursor` skips the interactive picker. If the CLI is not on PATH yet, install it first.

```bash
openspec init --tools cursor
```

Init is safe to re-run: existing tools refresh; a missing `openspec/` folder is created. Commit `openspec/` (`config.yaml`, `specs/`, `changes/`). Do not invent capability specs during init.

A Yes to enable or initialize OpenSpec includes the pin **in the same turn**. Cursor `--tools cursor` writes official `openspec-*` to `.cursor/skills` and `/opsx:*` to `.cursor/commands`. Some setups write `.agents/skills`. That is the install. Do not report “no OpenSpec skill was added” only because `.agents/skills` has none. Official skills omit `disable-model-invocation`; `openspec-apply-change` would otherwise match ordinary implementation. Immediately:

```bash
node .agents/skills/wp-agent-harness/scripts/disable-openspec-auto-invoke.mjs --confirm --root "/absolute/wp-root"
```

If audit later lists `openspec.missing.pin` (init already present, skills unpinned), ask to pin and run the same script after Yes. Re-run the pin after a later `openspec init` refresh. Harness develop does not start a change unless the user invoked `/opsx:*` or asked for that workflow. Read whichever of those two skill roots has the matching `SKILL.md`.

## After install

Re-run `audit.mjs` only if this turn is already in audit mode. `openspec.missing.cli`, `openspec.missing.init`, `openspec.missing.skills`, and `openspec.missing.pin` should be false. Report where official skills landed. Do not write a change proposal unless the user asked for one.

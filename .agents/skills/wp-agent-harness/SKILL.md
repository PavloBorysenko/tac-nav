---
name: wp-agent-harness
description: >-
  Adds warranted behavior tests and runs phpcs, PHPStan, JS lint, PHPUnit, or
  Jest so first-party WordPress theme and plugin changes can self-correct
  (wp-content themes/plugins, Local app/public, wp-load.php). After those gates, on UI work invokes
  wp-browser-sensor; on non-tiny first-party PHP, HTML templates, JS, or CSS
  launches one isolated subagent with local-code-review. Skip both on tiny
  phpcs-only work, docs, and audit. Use when changing
  first-party WordPress PHP, JS, or CSS. Also use when the user asks to audit,
  check, or set up a WP agent harness, or to add PHPCS, PHPStan, lint, tests,
  PHPUnit, Jest, WP_UnitTestCase, Cursor QA rules, WP skills, docs maps, or OpenSpec. Do not use for
  Drupal, Laravel, or any non-WordPress repo. Never installs anything until
  the user approves an exact list.
---

# WP agent harness

Controller for a portable WordPress outer harness. Three jobs:

1. **Audit and provision** — check what this repo needs; offer an exact list; install only after confirmation.
2. **Run and steer** — while the agent is developing first-party code, add warranted tests for new or changed behavior when the matching runner exists, then run the computational gates that exist (phpcs, phpstan, lint, PHPUnit, or Jest) and fix from the tool output. After those gates are green, invoke `wp-browser-sensor` only when the change is UI, then launch one isolated readonly subagent with `local-code-review` on non-tiny first-party PHP, HTML templates, JS, or CSS. This is the default during coding tasks, not a separate QA request.
3. **Adapt** — the same failure twice becomes a stronger guide or sensor in **this project's** pack (`.agents/skills/wp-agent-harness/`), not a spoken reminder. Promote a change back to the authoring copy (`result/wp-agent-harness` in the skills workspace) only when the next WordPress site needs the same fix.

Run `audit.mjs` only when the user explicitly asked to audit the harness, check the harness, set up the harness, or report what the harness is missing. A coding task, a missing gate, “check this file”, or a request to add one named tool is not that request. Install nothing until the user approves an exact list in this conversation. Installing PHPStan (or PHPCS, JS lint, PHPUnit, Jest, Cursor rules, OpenSpec) is part of job 1 when that layer is approved. Do not Composer-require them in a theme or plugin. Do not add `@fission-ai/openspec` to a theme or plugin `package.json`.

## Load only what the mode needs

Do not read `references/` or `assets/` unless a step for the selected mode names that file. Develop uses this `SKILL.md` only. When the user asked to write, fix, or investigate PHPUnit or Jest tests, you are adding a first-party `tests/` file, or new/changed behavior meets the test-worthy rule below, Read `.agents/skills/wordpress-testing/SKILL.md` if that file exists and follow it. If that file is missing, skip the test-writing guidance; do not inline those rules; do not switch to audit. Do not open it for tiny PHP, phpcs-only work, docs, audit, or a named test-tooling install. After green gates, if the browser sensor is warranted, Read `.agents/skills/wp-browser-sensor/SKILL.md` and follow it. Do not inline that checklist here. After the browser pass or skip, if local review is warranted, launch one isolated readonly subagent whose packet tells it to Read `.agents/skills/local-code-review/SKILL.md` and follow it. Do not review the diff yourself. Do not inline the review checklist.

| File | Read when |
| --- | --- |
| `references/audit-provision.md` | Audit mode after `audit.mjs`, when the user already named one layer to add, or this turn created a new non-tiny first-party theme/plugin and QA configs are missing. Not during an ordinary coding task. |
| `references/phpcs.md` | After the user wants the PHPCS layer. |
| `references/phpstan.md` | After the user wants the PHPStan layer. |
| `references/js-lint.md` | After the user wants the JS lint layer. |
| `references/tests.md` | After the user wants the PHPUnit, Jest, or WP_UnitTestCase layer. |
| `references/openspec.md` | After the user wants the OpenSpec layer. |
| `references/cursor-rules.md` | After the user wants the Cursor-rules layer. |
| `references/skill-catalog.json` | Consumed by `scripts/audit.mjs` only. Do not Read it. |
| `assets/rules/` | Consumed by `scripts/write-cursor-rule.mjs` only. Do not Read them to decide a mode. |

## Hard rules

- This controller is **project-vendored** under `.agents/skills/wp-agent-harness/`. A copy in `~/.cursor/skills` does not satisfy audit and is not the team install. Do not put this skill in the user-global Cursor skills folder as the source of truth.
- WordPress only. If the repo is Drupal, Laravel, or has no WordPress root (`wp-load.php` / `wp-content` + `wp-includes`), do not run this skill, audit, or its QA gates. Do not apply WordPress phpcs/phpstan paths to that codebase.
- If the user is changing first-party **WordPress** code, this is **develop** mode: run job 2. Do not run `audit.mjs`. Do not summarize skills, docs, or QA gaps. If a gate file is missing, run the gates that exist and continue the coding task; do not switch to audit.
- **Audit** mode only when the user explicitly asked to audit the harness, check the harness, set up the harness, or report what the harness is missing. Then run `audit.mjs`, report only, then ask. Naming one tool (PHPCS, PHPStan, JS lint, PHPUnit, Jest, WP_UnitTestCase, tests, OpenSpec, a Cursor rule, a skill) is provision of that layer, not permission to run the full audit.
- Never run `install-skills.mjs`, `copy-harness.mjs`, `install-phpcs.mjs`, `write-phpcs-config.mjs`, `write-cursor-rule.mjs`, `disable-openspec-auto-invoke.mjs`, `npx skills add`, `composer require`, `composer global require`, `npm install -g @fission-ai/openspec`, or `openspec init`, or write config/rule files unless the user confirmed the **exact items** this turn. Pack skills (`source: supernova-pack` in `skill-catalog.json`) install with `install-skills.mjs` from the pack git, omitting `evals/`. Do not install PHPUnit, Jest, or OpenSpec as skills. A Yes to enable or initialize OpenSpec **includes the pin in the same turn**: after `openspec init --tools cursor`, run `disable-openspec-auto-invoke.mjs --confirm --root` before finishing. If `openspec.missing.pin` is true and init already exists, ask to pin; after Yes run that script. Official Cursor skills land in `.cursor/skills`, not `.agents/skills`.
- PHPCS + WPCS are **global team tools** on PATH. Do not add them to a theme/plugin `composer.json`. Repo shares `phpcs.xml.dist` only.
- PHPStan is a **repo QA tool** in `tools/phpstan/` (Composer lockfile + WordPress stubs). Do not add it to a theme/plugin `composer.json`. Repo shares `phpstan.neon.dist` and `phpstan-baseline.neon`. Global `phpstan` on PATH is optional convenience, not the provision default.
- PHPUnit lives in `tools/phpunit/`; Jest in `tools/js-test/`. Do not add them to a theme or plugin. A `WP_UnitTestCase` layer additionally needs an approved WordPress integration bootstrap and dedicated test database; never point tests at the development or production database.
- OpenSpec CLI is a **global team tool** on PATH. Repo shares the `openspec/` folder at the WordPress git root. Do not add `@fission-ai/openspec` to a theme or plugin. Init only after confirmation. Do not create `openspec/changes/<id>/` unless the user invoked `/opsx:*` or asked to propose, apply, or archive an OpenSpec change.
- Never treat “set up the harness” or “looks good” as permission to install everything.
- WordPress skills set, required, and recommended are separate questions. The WordPress set and recommended stay optional. Offer the WordPress set even when this repo has no custom plugin or theme yet.
- Missing `docs/catalog.md` is `degraded.knowledge`, not a reason to generate docs. Create files only if the user explicitly asks for documentation. Offer the documentation skill only in **audit** mode. In **develop** mode do not report or offer docs gaps.
- Skip Core, default `twenty*` themes, and third-party plugins. **Never treat disk-installed plugins as first-party** when git does not map them. Ask the user which themes and plugins the agent may change.
- Do not open a browser inside the QA loop or on tiny PHP. Do not inline the browser checklist. Invoke `wp-browser-sensor` instead. If that skill stops on `wp-login.php`, do not rewrite first-party code. Do not inline the local-code-review checklist. Do not review a warranted local-code-review diff yourself.

## Workflow

1. Resolve the WordPress git root (Local sites: `app/public`). If this is not WordPress, stop and do not use the rest of this skill.
2. Choose mode from the user request only. Default is **develop**. Use **audit** only when the user explicitly asked to audit the harness, check the harness, set up the harness, or report what the harness is missing. Follow only that mode. Do not run `audit.mjs` because a gate, rule, or catalog is missing.

### Develop mode

Do not run `audit.mjs`. Do not summarize skills, docs, or QA gaps.

Do not create `openspec/changes/<id>/` on an ordinary coding task, tiny PHP, phpcs-only work, docs, provision, or audit. Implementing an already approved OpenSpec scenario is coding plus `wordpress-testing`, not a new change and not `openspec-propose`. If the user invoked `/opsx:propose`, `/opsx:apply`, `/opsx:archive`, `/opsx:explore`, or asked to propose, apply, or archive an OpenSpec change, Read the matching `openspec-*/SKILL.md` under `.agents/skills` or `.cursor/skills` if that file exists and follow it. If it is missing, skip; do not inline OpenSpec; do not switch to audit.

Follow `rules.qaLoop`. The QA loop is required. If the rule is missing, still run gates as mode `task` (changed first-party files, before finishing), then ask only the mode (`task` / `every-change` / `manual`) and write the rule this turn. Do not offer Not now. If `mode` is `manual`, run gates only when the user asked. If `every-change`, run the matching gate after each first-party file write. Red PHP output is the fix list. Do not grow `phpstan-baseline.neon`. For JS, still run lint, but do not `--fix` or rewrite a whole file for `linebreak-style`, `prettier/prettier`, `indent`, or `space-in-parens` unless the user asked to format; report that count and fix real errors in lines you added. If a PHPUnit or Jest config exists, run that existing suite for the touched first-party component. If none exists, skip tests; do not audit or offer to install them on an ordinary coding task. Do not invent tests on a tiny fix.

If this turn created a new **non-tiny** first-party theme or plugin, do not run `audit.mjs`. Register the new folder (and PHP prefix family) in existing `phpcs.xml.dist`, `phpstan.neon.dist`, `phpunit.xml.dist`, and JS lint/Jest configs. Create `<component>/tests/` if PHPUnit exists. Do not invent a behavior test. Tiny scaffolds skip that registration. If those configs are missing, Read `references/audit-provision.md` and ask the same toolbox questions as intent Yes.

Before the final gate run in `task` or `every-change` mode, classify new or changed behavior as test-worthy. Add or update tests when all are true: the change introduces or alters observable behavior, an OpenSpec scenario or explicit acceptance criterion makes the expected result unambiguous, and a matching PHPUnit/Jest suite already exists. Prioritize business rules, regressions, state transitions, integration boundaries, and meaningful failure paths. Skip pure wiring, presentation-only tweaks, duplicated coverage, and behavior that cannot be tested faithfully at the available level. Read `.agents/skills/wordpress-testing/SKILL.md` and derive the tests from the contract; do not invent expected results. In `manual` mode, write or run tests only when the user asks.

Run the focused new or failing test first, then the relevant existing suite. A failing test is not merely a text-edit list: follow `wordpress-testing` root-cause diagnosis and do not change an expected assertion only to get green.

After gates are green, decide whether **wp-browser-sensor** is warranted. It is not part of the QA loop. Skip it for tiny PHP, phpcs/PHPStan-only work, docs, skills, provision, and audit. Invoke it once at the end for CSS, enqueue, first-party JS, templates, `block.json`, admin fields, layout, or when the user asked how it looks. A batch of UI fixes gets one pass, not one per file. If warranted, Read `.agents/skills/wp-browser-sensor/SKILL.md` and follow that skill. If that file is missing, skip the browser pass; do not inline the checklist; do not switch to audit. If skipped, do not report the skip as a harness gap.

After the browser pass or skip, decide whether **local-code-review** is warranted. It is not a fourth linter. Skip it for tiny PHP, a one-line color tweak, docs, skills, provision, and audit. Skip it when the user asked only for a review, analysis, or a broader code check. Invoke it once at the end of a coding task when first-party PHP, `templates/*.html`, `parts/*.html`, JS, or CSS adds files, grows `functions.php`, changes a template, is a batch, or changes behavior. If warranted, launch one isolated readonly subagent (inherit the parent model). Packet: the task's 1–3 bullets, not your justifications; the WordPress git root; Read `.agents/skills/local-code-review/SKILL.md` and follow it. Do not review the diff yourself. Do not pass author justifications. Do not launch a second reviewer. Treat `[must-fix]` as binding. After a fix, re-run gates, then one more isolated review. If that file is missing, skip the review step and finish after the gates; do not inline the review checklist; do not switch to audit. If skipped, do not report the skip as a harness gap.

If the same class of failure appears twice, strengthen the pack (rule, template, skill). Do not only warn in chat.

### Audit mode

Execute, do not rewrite:

```bash
node .agents/skills/wp-agent-harness/scripts/audit.mjs --root "/absolute/path/to/wp-root"
```

If `firstParty.needsConfirmation` is true, **stop**. Do not compute provision lists from empty first-party. Ask which themes and plugins the agent may change (work scope). Then re-run audit with the answer:

```bash
node .agents/skills/wp-agent-harness/scripts/audit.mjs --root "/absolute/path/to/wp-root" --first-party "themes/emg-develop,plugins/at-rest-filter-master"
node .agents/skills/wp-agent-harness/scripts/audit.mjs --root "/absolute/path/to/wp-root" --prefix "at-rest,atrest"
```

Use `firstParty.candidates` as the choice list (`allow_multiple`). Also offer: `By slug prefix` (then ask for prefixes) / `None — audit only, no first-party`. Known third-party slugs are already omitted from candidates; still do not assume the rest are yours.

Summarize in plain language: shape, runtime gaps, first-party paths, missing WordPress skills set, missing required skills, recommended skills, docs gaps, QA gaps (including `qa.emptyFirstParty` and `qa.offerOnIntent`), OpenSpec gaps (CLI, init, skill location, pin), Cursor-rule gaps. Do not dump the full JSON unless asked. Then read `references/audit-provision.md`. **Stop and ask** from that file. Do not install yet. If `skills.wordpressSet.missing` is non-empty, ask that set even with no custom plugin or theme. Then ask remaining required and recommended, including `wordpress-testing` and `wordpress-component-creation` when listed. Do not skip those because required was empty or already installed. If `qa.emptyFirstParty` is true, ask whether this project will get a custom theme or plugin. If Yes, walk PHPCS, PHPStan, JS lint, PHPUnit, and Jest from `qa.offerOnIntent` even with no paths. If No, skip those toolboxes. The QA loop is required: ask mode only, do not offer Not now. Ask how to enable OpenSpec from that file: Yes to enable/init includes the pin in the same turn; if `openspec.missing.pin` and init already exists, ask the pin separately.

Install only the approved list. Re-run `audit.mjs` only in this mode. Report what is still missing.

If the same class of failure appears twice, strengthen the pack (rule, template, skill). Do not only warn in chat.

### Named-layer provision

If the user named one tool or skill to add, do not run `audit.mjs`. Read `references/audit-provision.md` and that layer's reference. Ask only about the named layer.

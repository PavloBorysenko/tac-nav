# Audit-mode budget

Skill files are graded from the execution trace, not from the repository diff. `SKILL.md` is always loaded when the skill applies; do not list it in `must_read` or `must_not_read`.

`evals/evals.json` is the source of truth. Run:

```text
python evals/check_audit_mode.py
python evals/check_audit_mode.py --eval 1 --trace <run>/TRACE.md
python evals/check_tests_layer.py
python evals/check_openspec_layer.py
python evals/check_wordpress_set.py
```

The first command checks that `SKILL.md` keeps the develop/audit fork, that `audit.mjs` is not the default path, and that the bundled traces in `evals/traces/` pass or fail as expected.

## Mode map

| Eval | Mode | Must run | Must not run | Must read | Must not read |
| --- | --- | --- | --- | --- | --- |
| 1 | Develop (coding, “check this PHP”) | — | `scripts/audit.mjs` | — | every `references/` and `assets/` file |
| 2 | Named layer (add PHPStan) | — | `scripts/audit.mjs` | `audit-provision.md`, `phpstan.md` | phpcs, js-lint, tests, cursor-rules, browser, catalogs |
| 3 | Audit (“check the harness”) | `scripts/audit.mjs` | — | `audit-provision.md` | layer refs (phpcs/phpstan/js-lint/tests/openspec/cursor-rules/browser); skip WordPress skills set / wordpress-testing / wordpress-component-creation / Enable OpenSpec (init+pin) questions |
| 4 | Named layer (add PHPUnit) | — | `scripts/audit.mjs` | `audit-provision.md`, `tests.md` | phpcs, phpstan, js-lint, openspec, cursor-rules, browser, catalogs |
| 5 | Develop UI (“check how it looks”) | — | `scripts/audit.mjs` | `wp-browser-sensor/SKILL.md`, then launch one isolated subagent whose packet Reads `local-code-review/SKILL.md` | harness layer refs; parent `review-scope.mjs` |
| 6 | Develop PHP batch (`WP_Query` in `functions.php`) | — | `scripts/audit.mjs` | launch one isolated subagent whose packet Reads `local-code-review/SKILL.md` | harness layer refs; `wp-browser-sensor`; parent `review-scope.mjs` |
| 7 | User-asked review (“thorough analysis”) | — | `scripts/audit.mjs` | — | `local-code-review/SKILL.md`; `wp-browser-sensor`; harness layer refs |
| 8 | Develop PHP batch, skill missing | — | `scripts/audit.mjs` | — | `local-code-review/SKILL.md`; inline checklist; `audit.mjs`; `wordpress-testing` |
| 9 | Develop write tests (PHP helper + PHPUnit) | — | `scripts/audit.mjs` | `wordpress-testing/SKILL.md`, then isolated `local-code-review` | harness layer refs including `tests.md`; `wp-browser-sensor` |
| 10 | Develop approved new behavior with existing WP integration suite | — | `scripts/audit.mjs` | `wordpress-testing/SKILL.md`, test file, focused PHPUnit + suite, then isolated `local-code-review` | harness layer refs; `wp-browser-sensor`; skipping tests because they were not explicitly requested |
| 11 | Named layer (add OpenSpec) | — | `scripts/audit.mjs` | `audit-provision.md`, `openspec.md` | phpcs, phpstan, js-lint, tests, cursor-rules, browser, catalogs; skip pin after Yes to enable |
| 12 | Develop `/opsx:propose` (official skill present) | — | `scripts/audit.mjs` | `openspec-propose/SKILL.md` | harness layer refs including `openspec.md`; `wordpress-testing`; inventing a change workflow |
| 13 | Develop `/opsx:propose`, official skill missing | — | `scripts/audit.mjs` | — | `openspec-propose/SKILL.md`; inline OpenSpec; `references/openspec.md`; `audit.mjs` |

Develop must not open `references/audit-provision.md`. Audit and a named layer must. Tiny PHP must not open `wp-browser-sensor`, `local-code-review`, `wordpress-testing`, or `openspec-propose`. A layout/UI coding task must open the browser skill, then launch one isolated readonly subagent for local-code-review. A non-tiny PHP coding task must launch that isolated subagent when the skill file exists. Explicit test writing (eval 9) and test-worthy approved new behavior with a matching runner (eval 10) must Read `wordpress-testing/SKILL.md`, write a test, and run it. Named PHPUnit install (eval 4) must not read the writing skill. Named OpenSpec (eval 11) must not run `audit.mjs`. `/opsx:propose` (eval 12) must Read the official OpenSpec skill and must not open `wordpress-testing`. If that official skill is missing (eval 13), skip; do not inline OpenSpec. The parent must not review the diff itself (`eval-5-self-review-fail.md`, `eval-6-self-review-fail.md`). A user-asked review or analysis must not open local-code-review. If the skill file is missing, skip the review step.

## Why these negatives

A sequential workflow that listed `audit.mjs` as step 3 made coding traces run the script. A bare “check” trigger made “check this PHP” look like audit. Those traces must fail eval 1.

A live audit trace that listed `references/tests.md` under “not opened” used to count as a read. The grader skips lines that say a file was not opened or not read.

# Running evaluations

`evals.json` is the source of truth. `audit-mode.md` is the mode map.

## Audit-mode budget

Grade whether the agent ran `audit.mjs` and which bundled files it opened. `SKILL.md` is always in context.

From the skill directory:

```text
python evals/check_audit_mode.py
python evals/check_audit_mode.py --eval 1 --trace <run>/TRACE.md
```

The first command checks `SKILL.md` structure and the bundled traces in `evals/traces/`. After an agent run, point `--trace` at that eval's `TRACE.md`. Keep skill-file paths and `audit.mjs` command lines visible in the trace.

Do not commit generated run outputs into the skill.

## Tests layer (audit detection)

Grade PHPUnit/Jest `qa.missing` flags and notes from `audit.mjs`:

```text
python evals/check_tests_layer.py
```

## OpenSpec layer (audit detection)

Grade `openspec.missing.cli` / `openspec.missing.init` / `openspec.missing.skills` / `openspec.missing.pin` and notes. Init follows `openspec/config.yaml`; official skills follow `.cursor/skills` or `.agents/skills`; pin follows `disable-model-invocation` on those skills. CLI follows the machine PATH.

```text
python evals/check_openspec_layer.py
```

Harness eval 12 must Read `openspec-propose/SKILL.md` on `/opsx:propose`. Eval 13 skips when that file is missing and must not inline OpenSpec or open `references/openspec.md`. Tiny PHP (eval 1) and approved-scenario implement (eval 10) must not read `openspec-propose`, `openspec-apply-change`, or `openspec-explore`. After init, `scripts/disable-openspec-auto-invoke.mjs --confirm` pins `disable-model-invocation: true` on official `openspec-*` skills.

Live with-skill traces for develop / named PHPStan / audit / named PHPUnit live under `wordpress-harness-workspace/iteration-3/`. Grade those with `--eval N --trace`.

## Browser sensor

`wp-browser-sensor` does not auto-invoke. Harness evals 1–4 must not read it; eval 5 must. Matrix: `result/wp-browser-sensor/evals/invoke.json`.

```text
python ../wp-browser-sensor/evals/check_invoke.py
```

`local-code-review` does not auto-invoke. Harness evals 1–4, 7, and 8 must not read it; evals 5–6 must launch one isolated readonly subagent whose packet Reads it. Parent self-review fail traces: `eval-5-self-review-fail.md`, `eval-6-self-review-fail.md`. Matrix: `result/local-code-review/evals/invoke.json`.

```text
python ../local-code-review/evals/check_invoke.py
python ../local-code-review/evals/check_how.py
```

Live invoke traces: `wordpress-harness-workspace/iteration-4/`. Grade with `--eval 1`, `--eval 3`, and `--eval 5`.

HOW (checklist quality, not invoke): `python ../wp-browser-sensor/evals/check_how.py`. Live: `wordpress-harness-workspace/iteration-5/`.

Sensor skills in `audit.mjs` (recommended, `when: always`): `python evals/check_sensor_skills.py`. WordPress skills set plus `wordpress-testing` / `wordpress-component-creation` on an empty site: `python evals/check_wordpress_set.py`. Empty-site intent QA (`qa.emptyFirstParty`, `qa.offerOnIntent`, mandatory QA loop, `--allow-empty-files`): `python evals/check_intent_qa.py`. Live harness develop + audit JSON: `wordpress-harness-workspace/iteration-8/`. Live eval 7 (user-asked review) and eval 8 (skill missing): `wordpress-harness-workspace/iteration-9/`.

## Test writing

`wordpress-testing` auto-invokes only on an explicit write/create/fix/investigate-tests request. Harness also Reads it for that request (eval 9) and for approved test-worthy new behavior with an existing runner even without a direct test request (eval 10). Harness evals 1, 3, 4, and 6 must not read it. Matrix: `result/wordpress-testing/evals/invoke.json`.

```text
python ../wordpress-testing/evals/check_invoke.py
python ../wordpress-testing/evals/check_how.py
```

Live: `wordpress-harness-workspace/iteration-11/` (HOW 1, 2, 5, 6, 7; harness `--eval 9` / `--eval 10`; Local pilots A/B).

## Vendor copy

Site copies omit `evals/`. Authoring packs keep them. Pack skills install from git (or the local `result/` folder) via `install-skills.mjs`.

```text
python evals/check_vendor_copy.py
python evals/check_install_pack.py
```

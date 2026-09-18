# OpenSpec scenarios as test sources

Read this file only when `openspec/config.yaml` exists.

## Select the contract

- If the task names an active change, read only its relevant `openspec/changes/<change-id>/specs/<capability>/spec.md` and the matching current `openspec/specs/<capability>/spec.md`.
- If one active change is clearly associated with the task, use it. If several could apply, ask which change governs the test.
- Without a relevant active change, read only the matching current capability spec.
- Do not scan `openspec/changes/archive/`.
- Do not treat `proposal.md`, `design.md`, or `tasks.md` as expected behavior. Requirements and scenarios own the behavioral contract.

For changed requirements, the active delta defines the proposed behavior and the current spec supplies unchanged context. Do not preserve behavior marked as removed.

## Translate scenarios

Map each relevant scenario to an executable test or data-provider row:

- `GIVEN` → arrange state and fixtures.
- `WHEN` → perform one action.
- `THEN` and following `AND` clauses → assert observable outcomes.

Use the scenario wording for the test name when practical. Combine equivalent input variants with a data provider. Do not invent extra outcomes from implementation details. If a scenario is not automatable at this test level, state why instead of replacing it with a different test.

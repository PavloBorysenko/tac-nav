---
name: wordpress-testing
description: >-
  Writes and diagnoses behavior-focused PHPUnit and Jest tests for first-party
  WordPress themes and plugins, including WP_UnitTestCase when an existing
  WordPress test suite is required. Use only when the user explicitly asks to
  write, add, create, fix, or investigate tests. Also use when wp-agent-harness
  Reads this skill for test-worthy new behavior with an existing runner. Do not
  use for ordinary WordPress coding, every PHP or JS change, tiny PHP,
  phpcs-only work, docs, audit, installing test tooling, Drupal, or Playwright.
---

# WordPress testing

Open this skill when the user explicitly asked to write, add, create, fix, or investigate tests, or when `wp-agent-harness` Reads this file for test-worthy new behavior. Do not open it on an ordinary coding task.

Write tests that protect agreed behavior. Reliability, not 100% coverage, is the goal. `wp-agent-harness` installs and runs test tooling; this skill does not provision it.

If the required PHPUnit, WordPress test-suite, or Jest configuration is absent, do not invent it or add tests that cannot run. Report the missing layer and stop.

## Load only what is needed

| File | Read when |
| --- | --- |
| `references/openspec.md` | `openspec/config.yaml` exists |
| `references/phpunit.md` | PHP tests or PHPUnit, after a matching runner already exists |
| `references/jest.md` | JS tests or Jest, after a matching runner already exists |

Do not read PHPUnit and Jest guidance “to be sure”. Do not open harness references from this skill.

## Establish expected behavior

Use the first available authoritative source:

1. Relevant approved scenarios in an active OpenSpec change, combined with the current spec.
2. Current OpenSpec requirements and scenarios.
3. Explicit acceptance criteria from the user.
4. Existing project documentation and previously verified tests.

Use implementation only to learn interfaces and arrange fixtures; do not copy its current result into the expected assertion. If sources conflict or expected behavior is ambiguous, ask before writing the test.

## Choose the smallest faithful test

- Pure PHP behavior with no WordPress runtime: `PHPUnit\Framework\TestCase`.
- Behavior that depends on WordPress hooks, factories, database, queries, metadata, REST, or lifecycle: `WP_UnitTestCase` when the existing suite provides it.
- JavaScript behavior: the existing Jest environment.

Do not replace an integration contract with mocks merely to keep the test isolated. Do not use `WP_UnitTestCase` for a pure formatter.

## Write for reliability

- Put tests in the owning first-party component and follow nearby test naming and bootstrap conventions.
- Test observable behavior, not private methods or incidental calls.
- Use Arrange–Act–Assert or map Given–When–Then directly.
- Give each test one reason to fail; several assertions are fine when they describe one outcome.
- Cover the specified happy path and the highest-risk edge or failure scenario. Do not generate combinations only to raise coverage.
- Keep tests deterministic and independent. Stub external boundaries; do not mock the system under test.
- Do not test WordPress Core itself. Test the component behavior that uses Core.

## Diagnose a failing verified test

Do not change an expected value merely to get green.

1. Reproduce the smallest failing test and read its full failure.
2. Compare the failure with the governing scenario or acceptance criterion and the relevant code change.
3. Classify the cause: production regression, intentional behavior change, defective test, environment failure, or nondeterminism.
4. Fix production code when confirmed behavior regressed. Change the test only when the contract intentionally changed or the test is demonstrably wrong. Fix environment or nondeterminism at its source.
5. Run the focused test, then the relevant existing suite.

## Verify

Run the narrowest existing command first, then the component suite. Do not install dependencies, add Playwright, or chase a coverage percentage from this skill.

# Jest for first-party JavaScript

Read this file only when writing JS tests.

## Follow the existing suite

Inspect the Jest config, command, and one nearby test. Follow their roots, environment, naming, module format, and setup. Put the test in the owning first-party component.

Do not `npm install jest` in a plugin or theme. Do not test `*.min.js`, hashed bundles, `vendor/`, or `build/`.

## What to test

Test observable behavior from the governing scenario: pure helpers, state transitions, DOM behavior, or WordPress-facing adapters that the existing Jest environment supports. Use `toBe` / `toEqual` for values and specific DOM assertions for user-visible outcomes.

Use `test.each` for readable variants of the same behavior. Mock network, timers, or WordPress globals only at external boundaries; do not mock the module under test. Keep fixtures minimal and restore mocks and timers after each test.

Do not mount an entire admin page when a smaller DOM fixture proves the behavior. Do not add Playwright from this skill.

## Red flags — do not ship

- A second Jest in `package.json` of the component
- Expected output copied from implementation instead of a requirement
- Broad snapshots that hide the behavior being protected
- Tests that need a running browser
- Copy-pasted cases that should be `test.each`

# PHPUnit for first-party PHP

Read this file only when writing PHP tests.

## Follow the existing suite

Inspect `phpunit.xml.dist`, its bootstrap, and one nearby test in the same component. Follow their namespace, naming, base class, and test command. Tests normally live under the owning component's `tests/`; do not create another PHPUnit installation in its `composer.json`.

## Choose the base class

- Extend `PHPUnit\Framework\TestCase` for pure functions, value objects, formatting, and branching that do not need WordPress runtime state.
- Extend `WP_UnitTestCase` when the behavior requires WordPress hooks, factories, database state, `WP_Query`, metadata, REST, or lifecycle APIs and the existing bootstrap provides the class.

Do not mock WordPress integration just to force it into an isolated test. Conversely, do not boot WordPress for pure logic.

For `WP_UnitTestCase`, use existing factories such as `self::factory()->post->create()` to arrange the minimum state. Follow nearby lifecycle conventions; when none exist, use `set_up()` / `tear_down()` and call the parent methods. Remove hooks or other global state introduced by the test.

Never include `wp-load.php` directly. Do not create `install-wp-tests.sh`, a WordPress bootstrap, or database credentials from this writing skill. If `WP_UnitTestCase` is required but unavailable, report that the integration runner must be provisioned.

## Assertions and doubles

Prefer strict, specific assertions such as `assertSame`, `assertCount`, and `assertStringContainsString`. Assert the component's observable result, not WordPress internals. Several related assertions are valid when they describe one behavior.

- **Stub** queries and readers (canned return).
- **Mock** outgoing commands only when the call is the contract.
- Never mock the system under test.
- Prefer a real small value object or array over a mock for data.

For isolated tests only, use Brain Monkey when the existing toolbox already provides it. Assert the component result rather than merely asserting that a Core function was called. Call `Brain\Monkey\tearDown()` in `tearDown()` and then `parent::tearDown()`.

Use a data provider when it makes variants of the same behavior easier to read, not to inflate case count.

## Red flags — do not ship

- Expected values copied from the current implementation without a requirement
- Direct `wp-load.php`, invented bootstrap, or invented database credentials
- Empty tests or `assertTrue(true)`
- Shared mutable static state across tests
- Testing Core sanitization or query internals
- Replacing a required integration test with mocks
- Giant tests that assert a whole request

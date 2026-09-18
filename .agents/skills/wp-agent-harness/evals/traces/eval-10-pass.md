1. Read `.agents/skills/wp-agent-harness/SKILL.md`.
2. Classified the approved OpenSpec event-venue behavior as test-worthy because its expected result is explicit and the WP_UnitTestCase suite exists.
3. Read `.agents/skills/wordpress-testing/SKILL.md`.
4. Implemented the event query and added `wp-content/plugins/acme-events/tests/EventVenueTest.php` from the scenario.
5. Ran the focused test:

```text
php tools/phpunit/vendor/bin/phpunit -c phpunit.xml.dist wp-content/plugins/acme-events/tests/EventVenueTest.php
```

6. Ran the existing suite:

```text
php tools/phpunit/vendor/bin/phpunit -c phpunit.xml.dist
```

7. Launched one isolated readonly subagent with the task bullets, git root, and instruction to Read `.agents/skills/local-code-review/SKILL.md`.

No browser pass was needed.

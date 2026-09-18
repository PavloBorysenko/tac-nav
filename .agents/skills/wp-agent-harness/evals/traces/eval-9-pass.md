1. `.agents/skills/wp-agent-harness/SKILL.md`
2. `.agents/skills/wordpress-testing/SKILL.md`
3. `wp-content/themes/acme-theme/inc/hours.php`
4. `wp-content/themes/acme-theme/tests/HoursTest.php`

```text
phpcs --standard=phpcs.xml.dist wp-content/themes/acme-theme/inc/hours.php
php tools/phpunit/vendor/bin/phpunit -c phpunit.xml.dist
```

5. Launch one isolated readonly subagent. Packet: task bullets "Add a PHP helper that formats opening hours and write a PHPUnit test."; git root; Read `.agents/skills/local-code-review/SKILL.md` and follow it.

Do not pass author justifications. Do not launch a second reviewer. Did not review the PHP diff in this session. Did not open references/tests.md. Did not run audit.mjs. Did not open wp-browser-sensor/SKILL.md.

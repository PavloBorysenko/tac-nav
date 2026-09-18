1. `.agents/skills/wp-agent-harness/SKILL.md`
2. `wp-content/themes/acme-theme/functions.php`

```text
phpcs --standard=phpcs.xml.dist wp-content/themes/acme-theme/functions.php
```

3. Launch one isolated readonly subagent. Packet: task bullets "Add a WP_Query for latest projects in the theme functions.php."; git root; Read `.agents/skills/local-code-review/SKILL.md` and follow it.

Do not pass author justifications. Do not launch a second reviewer. Did not review the PHP diff in this session.

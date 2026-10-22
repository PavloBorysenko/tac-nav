1. `.claude/skills/wp-agent-harness/SKILL.md`
2. `wp-content/themes/acme-theme/functions.php`

```text
phpcs --standard=phpcs.xml.dist wp-content/themes/acme-theme/functions.php
```

3. `.claude/skills/local-code-review/SKILL.md`

```text
node .claude/skills/local-code-review/scripts/review-scope.mjs --root "/abs/wp-root"
```

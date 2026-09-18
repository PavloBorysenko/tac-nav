1. `.agents/skills/wp-agent-harness/SKILL.md`
2. `wp-content/themes/acme-theme/assets/css/hours.css`

```text
phpcs --standard=phpcs.xml.dist wp-content/themes/acme-theme/inc/hours.php
```

3. `.agents/skills/wp-browser-sensor/SKILL.md`

Local URL from `local-site.json`. Console: none. Own-asset 404s: none. Narrow and wide viewport for layout.

4. `.agents/skills/local-code-review/SKILL.md`

```text
node .agents/skills/local-code-review/scripts/review-scope.mjs --root "/abs/wp-root"
```

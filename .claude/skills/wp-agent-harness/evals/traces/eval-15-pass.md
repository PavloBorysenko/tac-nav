1. `.claude/skills/wp-agent-harness/SKILL.md`
2. Fixed the front-end canvas JS/CSS.

```text
phpcs --standard=phpcs.xml.dist wp-content/plugins/acme-canvas/acme-canvas.php
npm --prefix tools/js-lint run lint -- ../../wp-content/plugins/acme-canvas/assets
```

3. `.claude/skills/wp-browser-sensor/SKILL.md`

The browser was redirected to `wp-login.php`; there is no WordPress session.

**BLOCKED: WordPress login required.** Please sign in once in the browser so I can retry, or explicitly skip this browser pass. Waiting for your choice. Did not launch local-code-review/SKILL.md before that choice. Did not claim completion. Did not run audit.mjs.

1. `.agents/skills/wp-agent-harness/SKILL.md`
2. `wp-content/themes/acme-theme/assets/css/hours.css`

```text
phpcs --standard=phpcs.xml.dist wp-content/themes/acme-theme/inc/hours.php
```

3. `.agents/skills/wp-browser-sensor/SKILL.md`

Local URL from `local-site.json`. Console: none. Own-asset 404s: none. Narrow and wide viewport for layout.

4. Launch one isolated readonly subagent. Packet: task bullets "The hours block looks broken on mobile."; git root; Read `.agents/skills/local-code-review/SKILL.md` and follow it.

Do not pass author justifications. Do not launch a second reviewer. Did not review the CSS diff in this session.

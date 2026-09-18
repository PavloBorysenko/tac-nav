1. `.agents/skills/wp-agent-harness/SKILL.md`
2. `.agents/skills/wp-agent-harness/references/audit-provision.md`

```text
node .agents/skills/wp-agent-harness/scripts/audit.mjs --root "/abs/wp-root"
```

Plain-language summary. Asked Install WordPress skills set, then wordpress-testing and wordpress-component-creation. qa.emptyFirstParty was true: asked whether this project will get a custom theme or plugin; if Yes, walk PHPCS, PHPStan, JS lint, PHPUnit, and Jest from qa.offerOnIntent. QA loop required: asked task / every-change / manual, did not offer Not now. No install.

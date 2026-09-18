Read this template only in documentation creation or audit when reporting a missing or incomplete consumption rule, or when the user explicitly asked to add one. Do not read it during investigation or code change.

Use this template when reporting a missing or incomplete consumption rule, or when the user explicitly asked to add one. Copy it to `.cursor/rules/project-documentation.mdc`, or merge the body into an existing always-apply rule. Do not create this file during documentation creation or audit unless asked. A rule that only records component ownership is not this rule. A catalog-first rule that omits post-change maintenance is incomplete.

```markdown
---
description: How to use repository documentation without loading the docs tree
alwaysApply: true
---

# Project documentation

- For project-specific behavior, open `docs/catalog.md` first. Follow only links whose reading condition matches the task. Do not load the rest of `docs/`.
- After changing code, update existing documents whose catalog conditions match the change. Do not create missing documentation unless the user asked for it.
```

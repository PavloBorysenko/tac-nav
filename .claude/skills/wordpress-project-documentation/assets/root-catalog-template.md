Read this template only when creating or rewriting the project-root `docs/catalog.md`. Do not preload it for investigation, code change, or a request that documents one theme or plugin without creating a root catalog.

# Project Documentation

Read only the links whose condition matches the current task.

## Project

- [Architecture](architecture.md) — read only when the task changes the project-wide component map or shared conventions across custom themes and plugins
- [Development](development.md) — read only when the task changes local development, Git, build, or CI conventions
- [Deployment](deployment.md) — read only when the task changes or repairs the deploy process
- [Security](security.md) — read only when the task changes project-wide security boundaries or controls

## Human

- [Human overview](human-overview.md) — read only when the user asks for a human-readable report, stakeholder summary, or onboarding overview; never open for code, investigation, or agent-documentation work

Include only principle documents that exist. Omit the Human section when `human-overview.md` does not exist. Do not put component field maps or block contracts here.

## Key functionality

### [Theme name]

`wp-content/themes/<project-theme>/`

- [Catalog](../wp-content/themes/<project-theme>/docs/catalog.md) — read only when the task changes `<theme-slug>`, `<namespace>/*` blocks, `[shortcode]`, `header.php`, or files under `templates/`

### [Plugin name]

`wp-content/plugins/<project-plugin>/`

- [Catalog](../wp-content/plugins/<project-plugin>/docs/catalog.md) — read only when the task changes `<identifier>` or this plugin

Give each custom theme and custom plugin one catalog link. Put identifiers that exist in the reading condition (theme slug, block namespace, shortcode, PHP template file). Do not list that component's architecture, workflows, or hooks here. Do not require `namespace/*` blocks on a classic theme.

## Business-critical third-party plugins

- [Contact Form 7](contact-form-7.md) — read only when the task involves public forms or Contact Form 7

Link each critical third-party plugin to one root card. Do not put plugin cards, vendor API dumps, or consumer render details in this catalog. Do not list non-critical third-party plugins.

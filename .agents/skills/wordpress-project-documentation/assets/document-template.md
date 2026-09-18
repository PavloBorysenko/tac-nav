Read this template only when writing a generic overview such as Architecture, Workflows, or Development. Do not preload it for investigation or code change. Do not use it for block, shortcode, class, or catalog files.

# [Document Title]

## Purpose

[Explain why this behavior, component, integration, or decision exists.]

## Responsibilities and boundaries

[Describe what is owned here and what is deliberately owned elsewhere.]

## Workflow

[Describe the important lifecycle or data flow. Omit this section when no workflow exists.]

## Dependencies

[Describe required components, services, APIs, hooks, data models, or configuration.]

For third-party plugins, link to the developer's official documentation and explain only this custom component's part of the integration. For another custom theme or plugin, name the identifiers this component exposes or copies. Link that component's `docs/catalog.md` only from `integrations.md`. Do not put that catalog link in architecture, workflows, hooks, data-model, block, or class documents. Do not copy its queries, render details, or field maps.

## Constraints and side effects

[Document assumptions, limitations, failure behavior, security implications, and non-obvious side effects.]

## Implementation references

- `wp-content/<component>/<file>.php` — [reason this source is relevant]

Write paths as backticks. Do not markdown-link templates or PHP files. Remove every section that is not meaningful for this document. Do not leave placeholders in finished documentation. Do not add a Related documents list. If a module file owns the field map or render contract, name it once in prose instead of restating it.

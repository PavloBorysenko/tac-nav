# WordPress Component Classification

Read this reference when a plugin or theme cannot be confidently classified or when creating or correcting the component registry in `docs/catalog.md`. Do not read it during investigation or code change. Do not read it when Git tracking, headers, and prefix already agree.

## Discovery sequence

1. Determine the actual Git repository root.
2. Inspect tracked files under `wp-content/plugins/` and `wp-content/themes/`. Do not search WordPress Core directories for project components.
3. Inspect the repository `.gitignore` rules for ignored component trees and explicit theme or plugin allowlists.
4. Identify the stable project prefix from project rules, documentation, existing component names, or explicit user input.
5. Compare component slugs with that prefix.
6. Read the component registry in `docs/catalog.md`.
7. Resolve conflicting evidence before assigning ownership.

## Evidence of a custom component

Combine these signals:

- files authored and maintained in the current repository history;
- explicit component allowlisting in the repository `.gitignore`;
- explicit listing under custom plugins or custom themes in `docs/catalog.md`;
- project-specific plugin or theme headers;
- a project-owned Composer package or namespace, when those files exist (themes often have neither);
- documentation describing the component as project-owned;
- a slug beginning with the stable project prefix.

Tracked files and explicit `.gitignore` allowlisting are required discovery evidence in this project model, but neither proves ownership: a repository may intentionally track or deploy third-party code. A matching project prefix is supporting evidence only. No single heuristic is conclusive.

## Evidence of a third-party component

Evidence includes:

- known upstream package metadata or homepage;
- vendor authorship in plugin or theme headers;
- installation through Composer or another dependency mechanism;
- repository history showing imported release archives or vendor updates;
- documentation identifying the component as external.

Third-party files may still be tracked by Git. Git tracking alone must not convert a vendored dependency into a custom component.

## Conflicts

When evidence conflicts:

1. Recheck tracked-file status and the applicable `.gitignore` rules.
2. Inspect the plugin header or theme `style.css`.
3. Inspect `composer.json` and `composer.lock` when they exist. Do not treat a missing Composer setup as missing ownership or as a documentation gap, especially for themes.
4. Inspect relevant Git history when available.
5. Compare namespaces, text domains, update mechanisms, and upstream references.
6. State the conflicting signals in the result.

Do not silently rewrite the registry based only on a naming heuristic.

## Registry categories

Use these categories in root documentation:

- **Custom themes**
- **Custom plugins**
- **Critical third-party plugins**

Do not list every installed third-party plugin. Include only components that materially affect architecture, custom code, configuration, deployment, or key business workflows.

For every custom plugin, record in the root catalog:

- repository-relative path;
- one link to that plugin's `docs/catalog.md`, with a reading condition that includes stable identifiers.

Use the same pattern for a custom theme. Do not list that component's architecture, workflows, or hooks in the root catalog.

For every critical third-party plugin, put one catalog line with a reading condition that links to a root document such as `docs/<plugin-slug>.md`. Record in that document:

- plugin name and plugin path;
- concise project-specific business role;
- link to the plugin's official technical or developer documentation;
- whether the plugin is required or optional;
- consequences of disabling it;
- known compatibility constraints.

Store detailed integration behavior in each custom component that implements part of the interaction. Never create project documentation inside the third-party plugin.

Use the plugin's official product page on the developer's website only when official technical documentation does not exist, and identify that link as a fallback. Never substitute third-party directories, reviews, mirrors, or tutorials.

## Child themes

A `Template:` header in `style.css` identifies a child theme. The child is its own component. Classify the parent independently: tracked custom parent vs third-party parent.

Document the child under `wp-content/themes/<child>/docs/`. Do not create `docs/` inside a third-party parent. If the parent is custom and this task documents it, it gets its own catalog. The child may link that catalog only from `integrations.md`.

## Unknown ownership

If evidence remains insufficient:

- label ownership as unknown;
- explain which evidence was checked;
- avoid creating component-specific documentation that assumes ownership;
- ask for clarification when ownership materially changes the task.

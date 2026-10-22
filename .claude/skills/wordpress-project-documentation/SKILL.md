---
name: wordpress-project-documentation
description: Maintains and consults repository-owned documentation for WordPress projects. Applies whenever an agent investigates project-specific behavior or changes existing code or configuration in a WordPress project repository, including custom themes, custom plugins, integrations, build, deployment, and business workflows, even when documentation is not mentioned. Also applies when creating, auditing, or updating project documentation. Does not apply to generic WordPress advice, scaffolding new themes or plugins, end-user content editing, third-party plugin support unrelated to project code, or work outside a WordPress project repository.
---

# WordPress Project Documentation

## Load only what the mode needs

Do not read `references/` or `assets/` unless a step for the selected mode names that file. Investigation and code change use this `SKILL.md` only.

| File | Read when |
| --- | --- |
| `references/documentation-model.md` | Before creating, moving, or substantially reorganizing documentation. Do not create files until you have read it. Do not read it to classify ownership or to answer a question. |
| `references/writing-guidelines.md` | Before writing or rewriting document bodies or catalog conditions. |
| `references/theme-surfaces.md` | When creating or reorganizing theme docs if the theme is a child (`Template:` in `style.css`), registers shortcodes, keeps several unrelated surfaces in `functions.php`, stores PHP outside `app/` and has no `block.json`, or has no `theme.json` and has PHP templates such as `index.php` or `header.php`. |
| `references/component-classification.md` | When classification is ambiguous or when correcting the component registry. |
| One `assets/*-template.md` | Only the template for the kind of file you are about to write. Do not preload the template list. |
| `assets/documentation-consumption-rule.md` | Only in documentation creation or audit, when reporting a missing consumption rule, or when the user asked to add that rule. |
| `assets/human-overview-template.md` | Only when writing or overwriting `human-overview.md`. |

## Core rules

- Treat the Git repository root as the WordPress project root.
- Treat project documentation as flat progressive disclosure: the catalog is a cheap index; a chunk loads only when its identifiers match. Success is the correct owner file. Failure is opening Architecture or Workflows for orientation.
- Keep discovery flat: one catalog per documentation root. Follow only matching links. Do not create nested catalogs such as `blocks/catalog.md` or `php/catalog.md`.
- Do not read WordPress Core files. Skip `wp-admin/`, `wp-includes/`, and root bootstrap files such as `wp-load.php`, `wp-settings.php`, `wp-blog-header.php`, `xmlrpc.php`, and `index.php` at the WordPress root only. A theme or plugin folder named `includes`, `inc`, `src`, `classes`, or `lib` is project-owned. Do not skip it because the name matches Core.
- Use `docs/catalog.md` at the repository root, and `<custom-theme>/docs/catalog.md` or `<custom-plugin>/docs/catalog.md`, as routing maps. Follow a link only when its reading condition matches the current task. Prefer identifier overlap over document titles.
- In one catalog, each identifier belongs to one row. Match `integrations.md` only when the tracked consumer set changes or the other-catalog pointer changes, not because a module identifier appears in its body.
- A module-file match is not permission to also open Architecture, a sibling module, or another component's catalog. Do not follow Related documents or Related classes lists. A markdown link to a sibling is not permission to open it.
- Write implementation references as backtick repository-relative paths, not markdown links. The only markdown documentation hop across components is a catalog link from `integrations.md`.
- After a catalog row matches a long `functions.php` or a large include, read only the named function or class.
- Do not read `human-overview.md` except in Human report mode. Skip it in code, investigation, and agent-documentation work even when it sits next to `catalog.md`.
- Write all project documentation in English.
- Create missing documentation files only when the user explicitly requests documentation creation.
- When changing code, always update existing documentation affected by that change. This maintenance is part of the code change and does not require a separate request.
- Never create or edit documentation inside a third-party theme or plugin.
- Base behavioral claims on current code and configuration. Report unresolved uncertainty instead of guessing. Omit empty or failure behavior you did not verify in an opened source file.

The required documentation model does not authorize unsolicited file creation. During an investigation or code task, report a missing catalog only when it belongs to a component encountered and relevant to the current task. Do not inspect unrelated components for documentation gaps unless the user requests a project-wide documentation audit or initialization.

## Select the operating mode

Determine the mode before acting. Then load only the files that mode names.

- **Question or investigation:** this `SKILL.md` only. Read relevant documentation before source code. Do not change files. Do not read `references/` or `assets/`. Do not inspect or report a missing consumption rule.
- **Code change:** this `SKILL.md` only. Read relevant documentation first, change the code, then update every existing agent document and catalog row made stale by the change. Then apply Maintain the human overview. Do not read `references/` or `assets/`. If a catalog reading condition became stale, read `references/writing-guidelines.md` before editing that row, and nothing else from `references/` or `assets/`. Do not inspect or report a missing consumption rule.
- **Documentation creation:** confirm scope and classify components. If ownership is unresolved, read `references/component-classification.md` and ask; do not create files and do not read the other skill files. If you will write files, read `references/documentation-model.md` before creating any file, then `references/writing-guidelines.md` before writing bodies or catalog conditions. Read `references/theme-surfaces.md` only when its row in Load only what the mode needs matches. Read only the `assets/` template for each file kind you write. Then apply the consumption-rule check in `references/documentation-model.md`.
- **Documentation maintenance:** verify the topic against code and update existing documents. Read `references/writing-guidelines.md` before rewriting bodies or catalog conditions. Read `references/documentation-model.md` only if moving or substantially reorganizing files. Then apply Maintain the human overview.
- **Documentation audit:** report missing, stale, conflicting, misplaced, or unsupported documentation. Do not rewrite files unless requested. Read `references/documentation-model.md` for the consumption-rule check. Do not read writing guidelines, theme surfaces, classification, or templates unless you were also asked to rewrite.
- **Human report:** open the `human-overview.md` that belongs to the requested scope when it exists. If it is missing, write it from facts already gathered using `assets/human-overview-template.md`. Do not open it to orient a code or investigation task. Do not load agent module files unless a fact must be verified.

Do not interpret a request to change code as permission to create an absent documentation tree. Do not create a missing `human-overview.md` during a code change. Do not open it to check whether it needs an update. Do not create a missing root catalog to complete a project index when the user asked only for one component. If root `docs/catalog.md` already exists, you may add a hop to the component you documented; that edit does not create root `human-overview.md`.

A request to document one theme or plugin is not permission to create a missing root `docs/catalog.md` or `docs/human-overview.md`. A request to document PHP classes is not permission to create `php/<class-slug>.md` for every class. Do not create `docs/blocks/` when there is no `block.json`.

## Navigate documentation

1. Determine the actual Git repository root.
2. Read project rules that define documentation or component conventions.
3. Open root `docs/catalog.md` when it exists.
4. Follow only the catalog links whose reading conditions match the current task. Do not open the remaining links.
5. When the matching link is a custom component's `docs/catalog.md`, open that catalog next and again follow only matching links.
6. Read only those matching documents. If a block or class file matches, read it, then the source it names. Do not open unmatched overview files for orientation. Do not open a `human-overview.md` link unless this task is a human report.
7. Follow a link inside a document only when that target's catalog condition would independently match the task. After this task changes an identifier that `integrations.md` lists, open that file to update it and, if a consumer copies the string, follow its catalog hop. Do not open `integrations.md` only to orient a task that matched another row. Reach another component through the root catalog, or through that hop, not through a block or class file.
8. Inspect source code and configuration after the relevant documentation. Limit that inspection to tracked project files: custom themes, custom plugins, project configuration, and CI. Do not open WordPress Core to confirm standard APIs. If no catalog link matched and the component is small, the catalog plus a targeted source read is enough.

```text
project rules
    → docs/catalog.md
    → project principle document, third-party card, or component catalog
    → only component links whose reading condition matches the task
    → relevant source code and configuration
```

Do not load the complete documentation tree by default. Root catalog to component catalog to a module file is the intended path.

## Classify components

Identify custom and third-party components from evidence, not names alone:

1. Inspect tracked files under `wp-content/themes/` and `wp-content/plugins/`.
2. Inspect the repository `.gitignore`, including explicit theme and plugin allowlists.
3. Check the stable project prefix, component headers, and, when present, package metadata or namespaces. Do not require Composer or a PSR-4 `app/` tree. Themes often have neither.
4. Compare the result with the root component registry.
5. Inspect repository history when evidence conflicts or authorship remains unclear.
6. Resolve conflicting evidence before assigning ownership.

Git tracking, explicit `.gitignore` allowlisting, and a project prefix are signals, not conclusive proof. If ownership remains uncertain, label it unknown and ask when the distinction affects the task. Read `references/component-classification.md` only then, or when correcting the registry.

## Maintain the human overview

`human-overview.md` is a people-facing report, not an agent router. Never Read it except in Human report mode.

Write or overwrite it from facts already in context. Do not open extra agent documents, source files, or the previous report to refresh it. Create it only during documentation creation, at the end, for each documentation root this task actually wrote because the user asked for that root. Do not create it for a component this task did not document. Do not create `docs/human-overview.md` as a side effect of documenting one theme or plugin.

Rewrite an existing file only when the catalog already has a Human row and this task already updated a people-facing agent document: public or editorial role, ownership of a surface, visitor or editor journey, disable or unregister consequences, required plugins, or environments. Skip field maps, sanitizers, nonces, PHP signatures, enqueue maps, and other module-only changes. Existence is the Human catalog row, not a Read of the file. When you do write it, use `assets/human-overview-template.md`.

## Resolve conflicts with code

Source code and active configuration are authoritative for current implementation behavior.

When documentation and implementation disagree:

1. Verify the relevant code and configuration.
2. State what the documentation claims.
3. State what the implementation does.
4. Identify the inconsistency explicitly.
5. Use the implementation as the basis for current behavior.
6. Update the documentation when the active mode authorizes or requires maintenance.

Never silently rely on stale documentation or invent behavior to reconcile a conflict.

## Check documentation impact

After changing code, inspect the existing documents whose catalog conditions match the change. Update all affected existing agent documents and their catalog entries before finishing. After changing an identifier that `integrations.md` lists, update that file even if its catalog row did not match, then follow its catalog hop if a consumer copies the string. Then apply Maintain the human overview. If no relevant agent document exists, do not create one unless explicitly requested; report the documentation gap when it is material.

## Ask before assuming

Ask focused questions when the answer cannot be established from project evidence and would change documentation scope or file placement, component ownership, required versus optional dependency status, business behavior or consequences of disabling a plugin, compatibility constraints, or which documentation the user wants created.

Do not ask for facts that can be verified safely from the repository.

## Final check

Every mode:

- every claim is supported by code, configuration, existing documentation, or an identified external source;
- unmatched overview files were not opened for context;
- `human-overview.md` was not opened except in Human report mode;
- no missing file was created without an explicit documentation request;
- no `references/` or `assets/` file was read unless this mode's steps named it.

Investigation: the working tree is unchanged; a missing consumption rule was not inspected or reported.

Code change: affected existing agent documents were updated; a missing `human-overview.md` was not created; a missing consumption rule was not inspected or reported.

Documentation creation or audit: follow the final check in `references/documentation-model.md`.

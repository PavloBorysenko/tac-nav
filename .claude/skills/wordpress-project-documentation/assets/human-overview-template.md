Read this template only when writing or overwriting `human-overview.md`. Do not read it during investigation or code change, and do not open the existing `human-overview.md` to decide whether to write.

Use this template when writing or rewriting `human-overview.md`. Write from facts already in context. Do not Read the previous file except in Human report mode.

Put the file in the `docs/` folder it describes:

- whole project: `docs/human-overview.md`
- one theme: `wp-content/themes/<theme>/docs/human-overview.md`
- one plugin: `wp-content/plugins/<plugin>/docs/human-overview.md`

List it from that folder's `catalog.md` under a Human heading. The agent must not open this file for code, investigation, or documentation-maintenance tasks.

# [Project or component name]

## What this is

[One or two paragraphs: product or component role, audience, and what people see or use.]

## Who owns what

[Plain-language map. For a project file, name the custom theme, custom plugins, and critical third-party plugins. For a component file, stay inside this theme or plugin.]

## How work usually happens

[Editorial or visitor journeys at a level a stakeholder can follow. Point to other components by name, not by catalog path.]

## What to know before changing it

[A short list of business constraints, required plugins, and environments. Do not paste enqueue maps or PHP signatures.]

Keep this file narrative and short. Duplication with agent documents is allowed because humans will not follow `catalog.md`. Do not add reading conditions, Related documents, or implementation-reference lists. Link official third-party product pages when a non-engineer needs them.

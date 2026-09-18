# TAC Nav

## What this is

TAC Nav is a Local WordPress site. Visitors see a child theme named TacNav on top of the default Twenty Twenty-Five block theme. The first visible customization is a light khaki (`#D4CBB3`) header and footer. Default WordPress themes still exist on disk but are not this project's design surface.

## Who owns what

The project owns the `tacnav` child theme. WordPress Core and the bundled Twenty Twenty-Three, Twenty Twenty-Four, and Twenty Twenty-Five themes are not project-owned and should not be edited for site look and feel. There is no custom plugin yet.

## How work usually happens

Editors can still use the Site Editor on TacNav. Theme-owned header and footer color is a default; saved Site Editor style customizations can override it. New theme JavaScript or blocks should be added through the theme's WordPress scripts toolchain, not by editing the parent theme.

## What to know before changing it

- Activate the TacNav theme in Appearance → Themes before expecting the khaki header and footer on the live site.
- Switching back to Twenty Twenty-Five drops child overrides.
- Do not edit files inside the default `twenty*` theme folders.

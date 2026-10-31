# Design

## Context

`Admin::render()` checks the staff permission, loads Mortar data, and requires `templates/admin-page.php`, which owns the `.wrap` element and the page heading. Save, activation, revoke, and release handlers redirect to the page URL with no application parameter. The activation handoff's Back link uses the same URL.

Mortar-only assumptions also exist below the page: one release in `Release` and the `tacnav_devices_release` option, Mortar-only `Validation::reference_settings()`, and QR activation with no application type. See Non-Goals.

The plugin autoloads `TacNav\Devices\` from `app/` through PSR-4. Its tests run in isolation without a WordPress runtime.

## Goals / Non-Goals

**Goals:**

- Provide a tab shell and a page contract that a new application can plug into.
- Keep the Mortar page's content and behavior unchanged.

**Non-Goals (for the first new application to add, when its needs are known):**

- Per-application APK storage, version, and public download page.
- Filtering the applications list by type. The Mortar tab keeps listing all rows, which today are all Mortar.
- Validation and settings schemas for other applications.
- Binding activation to an application, so a phone cannot consume another application's QR. A request with no application type can be treated as Mortar, so installed APKs need no immediate update.
- More front-page download controls.
- Changing the free-text Type field on the Mortar page.

## Decisions

**Tabs on one page, not a submenu per application.** This is the switching the page was requested to have. Application pages do not depend on the shell, so changing to submenus later would only touch the shell.

**A small `App_Page` contract: `slug()`, `label()`, `render()`.** Each application supplies its own page and shares no form with the others. A shared schema-driven form was rejected: the fields of future applications are unknown, so a schema now would be a guess, and it would force a rewrite of a working Mortar page.

**The list of applications lives in code, in `App_Pages::all()`.** Every application needs its own class and template anyway, so an admin-managed list would add nothing. A public filter was rejected because no consumer exists outside the plugin.

**Tab selection is a pure function: `App_Pages::resolve( $pages, $requested )`.** It receives the page list as an argument, returns the matching page, and otherwise returns the first page. Mortar stays first in the list, so it is the default. The isolated tests can use page doubles to cover multi-tab selection before a second application exists.

**`Admin::render()` becomes the shell.** It checks the permission before reading the parameter, resolves the page, and requires `templates/admin-shell.php`, which prints the `.wrap`, heading, and tab bar with core `nav-tab` markup, then calls the page's `render()`. Only the `.wrap` element and `<h1>` move out of the template.

**Mortar code moves, it is not rewritten.** The data loading from `Admin::render()` moves to `Apps\Mortar_Page::render()`, and `templates/admin-page.php` moves to `templates/apps/mortar.php`. The `admin-post` handlers and `Admin::release_metadata()` stay in `Admin`, because `Release`, `REST`, and the handoff use them. A new application adds its own handlers.

**Redirects do not change.** With Mortar as the default tab, every existing URL without `app` still lands on the Mortar page.

## Risks / Trade-offs

- [The tab bar has one tab until a second application exists] → Accepted. It was requested, and it proves the shell in production.
- [Moved template could change Mortar markup by accident] → Use `git mv` and limit the template diff to the removed wrapper and heading.
- [The `<style>` block in the template now renders inside `.wrap`] → It only styles inline forms, so its position has no visual effect.
- [The contract may prove too small for a later application] → It has three methods and one implementation, so extending it later is cheap.

## Migration Plan

No data migration. Rollback is reverting the code.

---
name: wp-browser-sensor
description: >-
  Checks a local WordPress site in the browser after QA gates: console errors
  and 404s for first-party CSS/JS/images, two viewports on layout. On wp-admin,
  reuses the user's logged-in Chrome for that host and does not use
  isolatedContext. Use when wp-agent-harness invokes this skill after green
  phpcs/PHPStan/lint on UI, CSS, JS, template, block.json, or admin-field work,
  or when the user asks to check how a local WP page looks. Do not use for tiny
  PHP, phpcs-only work, docs, audit, Drupal, or as a substitute for lint. Do
  not invent logins.
disable-model-invocation: true
---

# WP browser sensor

Inferential UI check for a local WordPress URL. Not a fourth linter. Not part of the QA loop.

`wp-agent-harness` decides **when** this skill runs (after green computational gates, UI tasks only). This file is **how**. If the harness did not already run phpcs/PHPStan/lint on the touched first-party files, stop and do that first; do not start with the browser.

Do not store passwords, cookies, or application passwords in this skill, the repo, or chat.

## URL

Resolve the local site URL from the project, never from this skill:

1. A URL the user already gave this turn.
2. Local site config next to `app/public` (`local-site.json`, nginx/apache `server_name`).
3. `WP_HOME` or `WP_SITEURL` in `wp-config.php` only if those constants are defined.
4. If none of those exist, ask. Do not invent `*.local`.

No connection, DNS failure, or the wrong host → **stop**. Do not rewrite permalinks or rewrite rules.

## Surface

Classify before opening a browser. Do not open a tab first and then decide.

**Admin** when any of these is true:

- The user asked to check wp-admin, a settings screen, plugin fields, or an admin list table.
- The change loads only in admin (`admin_enqueue_scripts`, `admin_menu`, `add_options_page` / `add_menu_page`, admin CSS/JS).
- The target URL is under `/wp-admin/` (including `post.php` and the site editor).

**Public** otherwise (front, `wp_enqueue_scripts`, templates). If only admin assets changed, treat as admin. If only front assets changed, treat as public.

## Which browser

Use Chrome DevTools MCP (`user-chrome-devtools`) attached to the user's already-running Chrome (autoConnect / default shared context).

**Public:** a new tab is fine. `isolatedContext` is allowed so cookies from other Local hosts do not leak. A Cursor browser tab is also enough.

**Admin:**

1. Call `list_pages` first. Match the host from the project URL. Ignore tabs on other `*.local` sites.
2. Prefer an existing tab on that host that is not `wp-login.php`.
3. If none, `new_page` in the default context. Do not pass `isolatedContext`.
4. A Cursor IDE profile or an isolated MCP context has no WordPress session — do not use those for wp-admin.

A new tab in the same Chrome profile shares the login cookie. Reusing the existing tab is preferred so the check stays on this host.

## Checklist

Run once at the end of the task, before local review.

Cheap, required:

- Console errors (first-party JS).
- HTTP 404 for **this project's** CSS/JS/images, not third-party or Core noise.

Only if needed:

- Layout change → narrow and wide viewport. Do not screenshot both “to be sure”.
- Clicks or a screenshot → only when console + 404s cannot show the regression.

Skip: Lighthouse, Gutenberg editor snapshots, full click-through, Playwright.

If the page is red, fix first-party code, re-run phpcs/PHPStan/lint on the touched files, then open the browser again.

## Login wall

Unauthenticated `/wp-admin/` is a **302 to `wp-login.php`**, not a product 404.

If the URL is `wp-login.php` or the page is a WordPress login form:

1. **Stop.** This is not a bug in first-party code.
2. Do not change capabilities, rewrite rules, enqueue, or templates to “fix” it.
3. Say admin was not checked (no session). Do not treat the public front as an admin pass.
4. If the task needs wp-admin, ask the user to log into that Chrome session, then retry the same tab. Do not invent a login.

## After the pass

Report console errors and own-asset 404s. If none and the warranted checks passed, say so in one line. Do not offer to install PHPUnit/Jest or run `audit.mjs` because the browser ran.

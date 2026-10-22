---
name: wp-browser-sensor
description: >-
  Checks a local WordPress page in a real browser after QA gates, with no
  prompts: console errors and 404s for first-party CSS/JS/images, narrow and
  wide viewport on layout. Runs scripts/browser-check.mjs (headless Chrome or
  Edge already on the machine) so every QA iteration can repeat it; a WordPress
  login is made once and reused. Finds the local URL from the project before
  asking. Use when wp-agent-harness invokes this skill after green
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

## Run the check

The check is one command. It needs no approval, no extension, and no open browser window, so it works the same on every iteration of the loop:

```bash
node .claude/skills/wp-browser-sensor/scripts/browser-check.mjs --root "/absolute/path/to/wp-root" [--url "<url>"] [--viewports narrow,wide] [--screenshots tools/browser-check/shots]
```

It starts the machine's own Chrome or Edge headless, opens the URL with a persistent profile in `tools/browser-check/.profile/` (gitignored), and prints one JSON report. Without `--url` it takes the URL from the project (see **URL**), so the loop runs the same command every time. It ignores certificate errors only for `*.local`, `*.test`, `localhost`, and `127.*` hosts, so Local's self-signed certificate does not matter. Read the JSON; do not paste it into the final answer.

| `status` (exit) | Meaning | Do |
| --- | --- | --- |
| `PASS` (0) | Checks completed, no console errors, no 404s on this site's own files | Report one line |
| `FINDINGS` (1) | `consoleErrors`, `pageErrors`, or `ownFailures` in a viewport | Fix first-party code, re-run phpcs/PHPStan/lint on the touched files, run the check again |
| `BLOCKED: WordPress login required` (3) | Redirected to `wp-login.php` | Follow **Login wall** |
| `BLOCKED: authenticated page forbidden` (3) | Document returned HTTP 403 | Follow **Login wall**: ask to sign in once, or skip |
| `NOT CHECKED: ...` (4) | No browser, site unreachable, or certificate not trusted on a non-local host | Follow **Result contract** |
| `WRONG HOST` (5) | The host answers but is not this WordPress site | Return to **URL** |

`coreFailures` (`/wp-includes/`, `/wp-admin/`) and `thirdParty` counts are context, not first-party failures. Mention them only when the task touched them. `adminBar: true` means the profile is logged in; say so for public pages, because the front then differs from what a guest sees.

First use in a repo: if the JSON says `playwright-core is not installed`, ask once to approve this exact install, then run it. It adds only `playwright-core` to `tools/browser-check/` and downloads no browser:

```bash
node .claude/skills/wp-browser-sensor/scripts/install-browser-check.mjs --confirm --root "/absolute/path/to/wp-root"
```

If no Chrome or Edge is found, pass `--chrome "<path>"` or set `CHROME_PATH`.

## URL

Resolve the local site URL from the project, never from this skill. Take the first source that yields a URL, then let the script verify it. Do not guess a `*.local` host.

1. A URL the user already gave this turn.
2. `local-site.json` next to `app/public` or in the repo root (`{"url": "https://name.local"}`), or a `Local URL:` line in `CLAUDE.md`. The URL differs per developer: it is written per machine and never committed.
3. `WP_HOME` or `WP_SITEURL` defined in `wp-config.php`.
4. Read-only lookup of the site's own address: `wp option get home` from the WordPress root when WP-CLI works, or the Local `sites.json` entry whose path equals this `app/public`. Never write to WordPress, the database, nginx, or apache files.
5. A tab already open in a session browser on a `.local` or `localhost` host, when such browser tools are present (list tabs; this changes nothing).
6. Nothing found: return **BLOCKED: local URL required**. Ask the user for the URL once, wait for it or for an explicit skip, and save the answer with `node .claude/skills/wp-browser-sensor/scripts/write-local-site.mjs --confirm --root "<wp-root>" --url "<url>"` (the file stays out of git).

**Verify** is done by the script: it needs the `api.w.org` link on the page or an answer from `/wp-json/`. A `WRONG HOST` answer sends you back to step 4 or 6. A host that does not resolve or refuses connections is `NOT CHECKED: site unreachable`, not a missing URL. Do not rewrite permalinks or rewrite rules.

## Surface

Classify before opening a browser. Do not open a tab first and then decide.

**Admin** when any of these is true:

- The user asked to check wp-admin, a settings screen, plugin fields, or an admin list table.
- The change loads only in admin (`admin_enqueue_scripts`, `admin_menu`, `add_options_page` / `add_menu_page`, admin CSS/JS).
- The target URL is under `/wp-admin/` (including `post.php` and the site editor).

**Public** otherwise (front, `wp_enqueue_scripts`, templates). If only admin assets changed, treat as admin. If only front assets changed, treat as public.

Admin pages use the same command with the `/wp-admin/...` URL. The profile's login does the rest.

## Checklist

Run once at the end of the task, before local review.

Cheap, required:

- Console errors (first-party JS).
- HTTP 404 for **this project's** CSS/JS/images, not third-party or Core noise.

Only if needed:

- Layout change → narrow and wide viewport: `--viewports narrow,wide` (375 px and 1280 px). Add `--screenshots` and Read the PNGs only when console + 404s cannot show the regression. Do not take both "to be sure".
- Clicks or interaction → only when console + 404s cannot show the regression, and then through a session browser (see **Fallback**).

Skip: Lighthouse, Gutenberg editor snapshots, full click-through, writing Playwright tests.

## Login wall

Unauthenticated `/wp-admin/` is a **302 to `wp-login.php`**, not a product 404.

If the report says `BLOCKED: WordPress login required` or the page is a WordPress login form:

1. This is not a bug in first-party code.
2. Do not change capabilities, rewrite rules, enqueue, or templates to "fix" it.
3. Say admin was not checked (no session). Do not treat the public front as an admin pass.
4. Ask the user to sign in once: `node .claude/skills/wp-browser-sensor/scripts/browser-check.mjs --login --url "<site origin>" --root "<wp-root>"` opens a window, waits for the dashboard, and saves the session in the profile. Every later run is silent. Offer that or an explicit skip, and wait for the choice. Do not invent a login.

## Fallback: session browser

Use a session browser (the built-in pane, `Claude_Browser__*`, or Claude in Chrome, `claude-in-chrome__*`) only when the script cannot run at all (no Node) or the task needs clicks. If the session also has a `built-in-browser` or `chrome-browser` skill, Read it first. List tabs first and prefer an existing tab on the matching host. If the tool says the site is not allowed yet, ask for access (`request_access`) once; if it is declined or the load still fails, return **NOT CHECKED: browser access denied** and stop. Never click through a certificate warning; if the same host answers on `http://`, use that scheme, otherwise return **NOT CHECKED: certificate not trusted**. These browsers keep one profile shared with the person: never sign out or change accounts. In Claude in Chrome, call `read_console_messages` and `read_network_requests` once, then reload, because they record only from the first call; to list failures compactly run `performance.getEntriesByType('resource').filter(r => r.responseStatus >= 400)` through `javascript_tool` instead of reading every request, and use top-level `await` rather than an async function that is not awaited.

## Result contract

- **PASS** only after the warranted console, own-asset 404, and viewport checks completed.
- **BLOCKED** only for what one person action fixes: unknown URL after all sources, WordPress login wall, or a 403 that remains. Name the exact action and offer two choices: resolve and retry, or explicitly skip. Wait for the answer.
- **NOT CHECKED: <reason>** when the environment cannot do it: **NOT CHECKED: browser unavailable**, **NOT CHECKED: site unreachable**, **NOT CHECKED: browser access denied**, or **NOT CHECKED: certificate not trusted**. State that UI was not browser-checked and why in one line. The parent continues to local review and reports the same line at the end. Do not ask for a choice.
- **SKIPPED (user-approved)** only after the user explicitly chooses skip. State that UI was not browser-checked.
- **BLOCKED is not yet a skip or pass.** Do not continue to local review before the user's choice.

On **PASS**, report console errors and own-asset 404s. If none, say so in one line. Do not offer to install PHPUnit/Jest or run `audit.mjs` because the browser ran.

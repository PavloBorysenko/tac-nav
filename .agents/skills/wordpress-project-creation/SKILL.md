---
name: wordpress-project-creation
description: >-
  Prepares a WordPress git root (Local app/public), writes deny-all .gitignore
  with named first-party allowlists, writes GitLab CI for one test deploy or
  test+prod, and prints user-run commands for GitLab remote, SSH rsync of
  wp-content, and WP-CLI database dump with URL search-replace. Use when the
  user asks to start a new WordPress project, set up git, GitLab, remote,
  gitignore, CI/CD, test/prod deploy, sync or copy wp-content over SSH, or
  dump/import a database with WP-CLI and search-replace. Also use when they
  answer GitLab URL, one vs two deploys, SSH, site URLs, or inbound first-party
  questions for that setup. Do not use for ordinary WordPress coding, editing
  an existing theme or plugin, scaffolding a new custom theme or plugin,
  docs-only work, audit, tests, OpenSpec, Drupal, or executing git, ssh,
  rsync, or WP-CLI in the agent terminal.
---

# WordPress project creation

Open this skill when the user asked to start a **new WordPress project**, set up **git / GitLab / remote / gitignore / CI/CD**, or **sync wp-content or a database over SSH**, or when they answer GitLab URL, one vs two deploys, SSH, site URLs, or inbound first-party questions for that setup. Do not open it to edit an existing theme or plugin, scaffold a new custom component, write docs-only, audit, test, or run ordinary coding.

Do not run git, ssh, rsync, scp, or WP-CLI. Write files in the WordPress tree. Print commands for the user to run in **Local → Open Site Shell** from the git root (`app/public` on Local). Do not put secrets, SSH keys, or `wp-config.php` values in files or chat.

## Load only what is needed

| File | Read when |
| --- | --- |
| `assets/gitignore.template` | Before writing `.gitignore` |
| `assets/gitlab-ci-one-env.yml` | After the user chose **one** deploy (test only) |
| `assets/gitlab-ci-two-env.yml` | After the user chose **two** deploys (test + prod) |
| `references/sync.md` | Sync, or bootstrap that starts with inbound `wp-content` / DB from a server |

Do not read both CI templates. Do not open `sync.md` for gitignore- or CI-only work with no content/DB copy. Asking is not the end of this skill.

## Workflow

1. Confirm a WordPress tree (`wp-load.php` or `wp-content` + `wp-includes`). Git root = WordPress root. If this is Drupal or not WordPress, stop.
2. Classify **bootstrap** (new project, git, GitLab, gitignore, CI) vs **sync** (copy `wp-content` or DB between instances). Bootstrap may **start with inbound sync** when the source of truth is a remote server and git is empty.
3. If a previous turn asked for GitLab URL, one vs two deploys, SSH, URLs, or inbound first-party, and the user answered, **this turn is still this skill**. Resume. Do not stop after the question.
4. Ask any missing row in the table below. Do not derive GitLab host, SSH, site URLs, or prefix from the Local folder name.
5. For sync or inbound-first bootstrap: Read `references/sync.md`, print commands, do not execute. Then follow inbound first-party rules below.
6. For bootstrap files: Read `assets/gitignore.template` and write `.gitignore`. After one vs two deploys is known, Read the matching CI asset and write `.gitlab-ci.yml`. Print git remote / add / commit / push commands. If `origin` already exists, do not re-init git and do not change the remote unless asked.
7. If this is a **new** project, root `docs/catalog.md` is missing, and `.agents/skills/wordpress-project-documentation/SKILL.md` or `.cursor/skills/wordpress-project-documentation/SKILL.md` exists, Read that skill and create the root catalog. Skip docs for git/CI-only or sync-only. Do not invent docs when that skill is missing.
8. Do not scaffold a theme or plugin (`wordpress-component-creation`). Do not provision phpcs, PHPStan, PHPUnit, Jest, or OpenSpec (`wp-agent-harness`). Do not deploy from chat.

## Ask, do not guess

| Need | When |
| --- | --- |
| Working name and machine prefix | Bootstrap. Do not derive from the Local folder |
| GitLab origin URL | `git remote add`. User creates the empty GitLab project in the UI first |
| One deploy (test) or two (test + prod) | Which CI template to copy |
| SSH user, host, absolute WordPress path per target | rsync and remote `wp` |
| Local URL and target site URL | `wp search-replace` |
| Sync direction | remote→local or local→remote |
| What to do with first-party after inbound rsync | Ask. See inbound table |
| Production | Without prod/production in the request, do not target production |

## Inbound `wp-content`

Print a **full** `rsync -az --delete` of `wp-content/` (see `references/sync.md`), without --exclude for first-party themes or plugins. This is a site-copy sync, not GitLab CI deploy.

After inbound rsync, do **not** `git restore` by default. Ask:

| Situation | After rsync |
| --- | --- |
| New project, git empty, code only on the server | Keep the download. Ask which themes/plugins are first-party. Allowlist them. Print `git add` / commit / push **from Local** |
| Another site copy | Keep the download. Do not restore. Do not rewrite `.gitignore` for sync |
| Git already tracks customs and the user wants **git versions** | Then print `git restore` for those tracked paths. Do not print `git reset --hard` when there are uncommitted first-party edits |

## Git

Print (placeholders only, never a copied host from another project):

```bash
git init
git remote add origin <GITLAB_REPO_URL>
git fetch origin
```

Then print `git add` / commit / `git push -u origin develop` as needed. Two deploys: `develop` → test, `main` → prod. One deploy: `develop` → test only; do not create `deploy:prod`.

Do not commit Core, `wp-config.php`, uploads, `vendor/`, `node_modules/`, `build/`, database dumps, or secrets.

## `.gitignore`

Copy `assets/gitignore.template`. Deny Core, root PHP (including `wp-config.php`), and all of `wp-content`, then allowlist **only** first-party slugs the user named or that git already tracks. Inside each allowlist: ignore `node_modules/`, `vendor/`, `build/`, `dist/`. Keep lockfiles. Do not allowlist every folder on disk. If no customs are named, leave deny-all plus the comment to add each custom explicitly. After pulling a live site into empty git, ask which downloaded themes/plugins are first-party before allowlisting.

## GitLab CI

Write `.gitlab-ci.yml` from the matching asset. Do not run the pipeline.

- Two deploys: `deploy:test` on `develop` (`TEST_SSH_USER`, `TEST_SSH_HOST`, `TEST_SERVER_PATH`, `TEST_SSH_PRIVATE_KEY`); `deploy:prod` on `main` (`PROD_*`).
- One deploy: only `deploy:test` and only `TEST_*`. Do not leave a dead `deploy:prod`.

Jobs are `when: manual`. CI does not install WordPress, does not migrate the database, and does not deploy uploads or third-party plugins — only tracked custom themes and plugins. Keep `NODE_VERSION` and `COMPOSER_BIN` from the template unless the user named another runner path.

List those CI variable **names** for the user to set in GitLab UI. Do not invent values and do not write private keys into the repo.

## Out of scope

- Scaffolding a component (`wordpress-component-creation`)
- phpcs / PHPStan / lint / PHPUnit / Jest / OpenSpec
- Copying Core or `wp-config.php` over SSH
- Executing deploy, git, or SSH from this chat

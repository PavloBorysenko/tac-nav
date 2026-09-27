# SSH content and database sync

Read this file only for **sync** or for bootstrap that starts with inbound copy from a server. Print these commands. Do not run them.

Run them in **Local → Open Site Shell** from the WordPress git root. Replace `USER`, `HOST`, `REMOTE_WP` (absolute WordPress root on the server), `OLD_URL`, and `NEW_URL` with values the user gave. Do not invent URLs from the Local folder name.

Do not rsync Core, the site root, or `wp-config.php` (it lives outside `wp-content`). `--delete` removes files on the destination that are missing at the source. Warn once.

Without prod/production in the request, use the test (or only) server, not production. Production DB import needs an explicit extra confirmation.

If remote `wp` is not on PATH, use the binary the user named. Do not guess. Do not print DB passwords. Do not commit `db.sql`.

## wp-content

Full tree, without --exclude for first-party themes or plugins.

Remote → local:

```bash
rsync -az --delete -e ssh \
  USER@HOST:REMOTE_WP/wp-content/ \
  ./wp-content/
```

Local → remote:

```bash
rsync -az --delete -e ssh \
  ./wp-content/ \
  USER@HOST:REMOTE_WP/wp-content/
```

Then follow inbound first-party rules in `SKILL.md`. Do not `git restore` unless the user asked to keep git versions of tracked customs.

## Database

`wp search-replace` uses `--all-tables`. Use the old and new URLs the user named.

Remote → local:

```bash
ssh USER@HOST "cd REMOTE_WP && wp db export -" > db.sql
wp db import db.sql
wp search-replace 'OLD_URL' 'NEW_URL' --all-tables
```

Local → remote:

```bash
wp db export - > db.sql
scp db.sql USER@HOST:/tmp/db.sql
ssh USER@HOST "cd REMOTE_WP && wp db import /tmp/db.sql && wp search-replace 'OLD_URL' 'NEW_URL' --all-tables"
```

# Tasks

## 1. Application page contract

- [x] 1.1 Add the `App_Page` interface in `wp-content/plugins/tacnav-devices/app/Apps/App_Page.php` with `slug()`, `label()`, and `render()`, and verify phpcs and PHPStan pass on the file.
- [x] 1.2 Add `App_Pages` in `app/Apps/App_Pages.php` with `all()` and `resolve( $pages, $requested )`, and verify phpcs and PHPStan pass on the file.
- [x] 1.3 Add `tests/TacNav_Devices_App_Pages_Test.php` using page doubles for a known slug, a missing value, an unknown value, and the first page as default, and verify the focused test passes.

## 2. Mortar page

- [x] 2.1 Move `templates/admin-page.php` to `templates/apps/mortar.php` with `git mv`, remove only the `.wrap` element and `<h1>`, and verify `git diff -M` shows no other template change.
- [x] 2.2 Add `Apps\Mortar_Page` in `app/Apps/Mortar_Page.php` with slug `mortar`, moving the data loading from `Admin::render()` and requiring the moved template, and add it to `App_Pages::all()`; verify phpcs and PHPStan pass on the file.

## 3. Tab shell

- [x] 3.1 Change `Admin::render()` to check the staff permission first, resolve the page from the sanitized `app` parameter, print the `.wrap`, heading, and `nav-tab` bar with the current tab marked, then render the page; verify phpcs and PHPStan pass on `app/Admin.php`.
- [x] 3.2 Confirm the `admin-post` handlers, `Admin::release_metadata()`, and the Back link in `templates/activation.php` still use the unchanged page URL by verifying they have no diff.
- [x] 3.3 Run the full PHPUnit suite with `php tools/phpunit/vendor/bin/phpunit -c phpunit.xml.dist` and verify it passes.

## 4. Documentation

- [x] 4.1 Update `wp-content/plugins/tacnav-devices/docs/architecture.md` and `docs/provisioning-and-messaging.md` with the tabbed page, the `Apps/` layout, and the steps to add an application, and verify the references point to the new paths.

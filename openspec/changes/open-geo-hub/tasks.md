# Tasks

## 1. Roles in storage (step A)

- [x] 1.1 Add `Object_Roles` in `wp-content/plugins/tacnav-maps/app/` with the three roles from design decision 1 and filter `tacnav_geo_object_roles`. Verify that a new PHPUnit test covers slugs, labels, allowed kinds, the assignable flag, and an unknown slug.
- [x] 1.2 Add `role varchar(32) NOT NULL DEFAULT ''` to `Activator::create_table()` and set `DB_VERSION` to `6`. Verify that a PHPUnit assertion finds the column in the schema SQL, and that after the upgrade on the local site every existing row has role `''`.
- [x] 1.3 Store, merge, and hydrate `role` in `Geo_Store`. Refuse an unregistered role, or a role the kind does not allow, with `400 tacnav_geo_role`. Verify with Geo_Store tests for a create with a role, an update without a role that keeps it, and both refusals.
- [x] 1.4 Accept `role` in the staff payload (`REST::payload_from_request()`) and keep it out of player payloads. Verify with tests that a staff save stores the role and that a player edit keeps the stored role.

## 2. Application writes (step A)

- [x] 2.1 Make `Access::default_editable()` allow an application viewer on every object except self-points. Verify with updated Access tests: a mortar can edit an Object application's object and a staff object, and a self-point is refused.
- [x] 2.2 Make application updates in `Application_Write::normalize()` carry only the fields that were sent, and never the owner team, origin, application id, or author. Accept `role` on create and on update. Verify with tests for a body with only `ttl_seconds: 1`, for belonging kept when another team updates, and for a full Mortar-style body that gives the same stored object as before.
- [x] 2.3 Fire `tacnav_application_object_saved` on update only when the stored `application_id` equals the writer's. Verify with a test that a foreign application's update does not fire the hook and a creator's update does.
- [x] 2.4 Verify with a test that an application update carrying a stale `updated_at` returns `409 tacnav_geo_conflict` and leaves the object unchanged.

## 3. Search (step A)

- [x] 3.1 Add the optional search `role`: absent or `null` returns every role, `""` returns ordinary objects, and an unregistered value returns `400 tacnav_geo_role`. Return hydrated objects without `client_key`. Verify with tests for each filter case and for the returned fields.

## 4. Studio (step A)

- [x] 4.1 Pass the role registry from `Canvas.php` into the canvas config. Verify with a PHPUnit assertion that the config lists the assignable roles with their kinds.
- [x] 4.2 In `canvas.js`:
  - add the Purpose select to the staff inspector for circles and polygons;
  - show a current role that staff may not assign as read-only text and leave it out of the payload;
  - add `role` to `objectStamp()`;
  - draw no centre icon for role `hint` or for the legacy not-searchable iconless area.

  Verify that `npm --prefix tools/js-lint run lint -- wp-content/plugins/tacnav-maps/assets/canvas.js` reports no new findings in changed lines.

## 5. Step A gates and documentation

- [x] 5.1 Run phpcs, PHPStan, and the PHPUnit suite on the changed `tacnav-maps` files. Verify they pass and that `phpstan-baseline.neon` has no new entries.
- [x] 5.2 Update the documentation:
  - `tacnav-maps` `docs/data-model.md`: the role column and registry;
  - `tacnav-maps` `docs/hooks-and-apis.md`: the role filter, any-object writes, partial updates, creator-only binding hook, and `tacnav_geo_object_roles`;
  - `tacnav-maps` `docs/development.md`: the Purpose control and the hint drawing rule;
  - `tacnav-devices` `docs/provisioning-and-messaging.md`: the creator-only binding.

  Verify that each catalog link condition still matches its document.

## 6. Applications (step B)

- [x] 6.1 In Tac Nav Object, add `role` to `ObjectRequest`. Send `hint` for the area, send no role for the live object and the wreck, and stop sending `searchable`. Verify that `MapPlannerTest` and `HttpContractTest` cover this and that `.\gradlew.bat :app:testDebugUnitTest` passes.
- [x] 6.2 In Tac Nav Object, post wake-up notifications on a new channel `tacnav-wake` with `IMPORTANCE_LOW`, and delete channel `tacnav-deliveries`. Verify with a test that asserts the channel's importance, no sound, and no vibration.
- [x] 6.3 Make the same silent-channel change in Tac Nav Mortar. Verify with the same kind of test in the Mortar project.
- [x] 6.4 Bump Tac Nav Object to 0.2.0 (versionCode 3) and Tac Nav Mortar to 0.3.0 (versionCode 3). Verify that `:app:assembleDebug`, `:app:testDebugUnitTest`, and `:app:lintDebug` pass in both projects.
- [x] 6.5 Run `:app:connectedDebugAndroidTest` for both projects on the connected phone. Verify that every instrumented test passes.

  Result (2026-11-08): Tac Nav Object passed 25 of 25 through `connectedDebugAndroidTest`. For Tac Nav Mortar, a locked screen first failed its 7 Compose tests. With the phone unlocked, Gradle could not start its test worker because the machine was out of memory. The same debug and androidTest APKs were therefore installed with adb and run with `am instrument`, and they passed 8 of 8.
- [x] 6.6 Install both debug builds on the connected phone and activate Tac Nav Object against a site the phone can reach. Enter Active with a random area. Verify that the stored area has role `hint` and is drawn without a centre pin, and record the result.

  Result (2026-11-08): both debug builds are installed on the moto g53 (Object 0.2.0, Mortar 0.3.0). The phone could not reach the local site: the app requires HTTPS, and the password-protected Local Live Link takes the `Authorization` header that the app bearer needs. As the user decided, the on-device activation was replaced by replaying Object's requests against the local site.
  - Activation as `com.tacnav.beacon` returned 200 with `random_area_enabled: true`.
  - The area body that `HttpContractTest` proves the phone sends was stored with role `hint` and no `searchable` key.
  - An old-client body with `searchable: false` was stored with the ordinary role.
  - Search returned the area with no role and with role `hint`, but not with `minefield`. An unknown role returned 400 `tacnav_geo_role`.
  - `ttl_seconds: 1` changed only the expiry.

  The canvas draws no centre pin because `isHintArea()` is `role === 'hint'`. This was not checked in a browser. The test rows were deleted.

## 7. Remove `searchable` (step C)

- [x] 7.1 Add the DB version 7 migration: set role `hint` on not-searchable circles and polygons that have no icon and role `''`, then drop column `searchable` if it exists. Verify with a PHPUnit test of the migration statements and, on the local site, that the column is gone and converted rows have role `hint`.
- [x] 7.2 Remove `searchable` from the insert and update SQL, `hydrate()`, search, and `objectStamp()`, and remove the legacy canvas rule. `Application_Write` ignores a sent value. Update the tests that referenced the flag. Verify that phpcs, PHPStan, PHPUnit, and JS lint pass.
- [x] 7.3 Update `data-model.md` and `hooks-and-apis.md` for the dropped column. Verify that no document describes `searchable` as current behavior.

## 8. Final verification

- [x] 8.1 Verify with tests that a search without a role returns hint areas, wrecks, minefields, and ordinary objects, and that Tac Nav Object's `onDelivery` counts no hit for an area or wreck id. Record the result.
- [x] 8.2 Run `openspec validate open-geo-hub --strict`. Verify that it passes.

# Tasks

## 1. Staff card

- [x] 1.1 On the object card, when `cfg.mode` is `staff`, show the object's existing `id` with a localized "ID" label. Keep Edit and Delete gated on `can_edit`, so a self-point and an expired object still show the id. Player and guest cards, the map face, and the edit form do not show it. Verify the staff card markup includes that id and the player and guest card markup does not.

## 2. Gates

- [x] 2.1 Run `phpcs` and PHPStan on the touched PHP files, and `npm --prefix tools/js-lint run lint -- ../../wp-content/plugins/tacnav-maps/assets/canvas.js`. Verify each command exits successfully.

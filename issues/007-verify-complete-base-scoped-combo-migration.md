# Verify complete BASE-scoped combo migration

## What to build

Add the final end-to-end verification for the completed BASE-scoped combo migration. The rendered keymap should demonstrate that every pre-existing combo still works on BASE, no pre-existing combo remains global, no extra non-BASE combo behavior was introduced, obvious pattern groups are represented as Combo-layers, and irregular combos remain explicit raw nodes.

This slice is the migration hardening pass: it validates the combined behavior after the raw fallback, horizontal, vertical, and diagonal slices land.

## Acceptance criteria

- [ ] The final rendered keymap output contains all combos from the migration inventory and no additional combos beyond the intended Combo-layer generated nodes and retained raw fallback nodes.
- [ ] Every rendered combo from the migrated set includes a BASE layer restriction.
- [ ] No migrated combo renders as global.
- [ ] No new Nav-specific, Num-specific, or BT-specific Combo-layers are introduced.
- [ ] Generated Combo-layer combo names are accepted for horizontal, vertical, and diagonal groups and use clear group prefixes.
- [ ] Retained raw irregular combos preserve their semantic node names.
- [ ] Rendered output assertions cover bindings, key positions, and layer restrictions rather than internal implementation details.
- [ ] The rendered keymap remains valid ZMK devicetree.
- [ ] Firmware, board, shield, and build configuration files remain unchanged unless a separate explicit request authorizes those changes.
- [ ] The existing Babashka test task passes.

## Blocked by

- `issues/003-retain-irregular-combos-as-base-scoped-raw-nodes.md`
- `issues/004-migrate-horizontal-base-combos-to-combo-layer.md`
- `issues/005-migrate-vertical-base-combos-to-combo-layer.md`
- `issues/006-migrate-diagonal-down-right-base-combos-to-combo-layer.md`

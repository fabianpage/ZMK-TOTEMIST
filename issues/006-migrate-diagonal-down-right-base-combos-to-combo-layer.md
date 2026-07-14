# Migrate diagonal down-right BASE combos to a Combo-layer

## What to build

Convert the obvious diagonal down-right BASE combos identified in the inventory from repeated raw combo nodes into a BASE-scoped Combo-layer using the existing Binding grid, row-width, Pattern, and Layer mechanisms. The generated combo names may use a clear diagonal prefix rather than preserving the old semantic raw-node names.

This slice should preserve the current BASE output for the diagonal combo group while preventing those combos from firing on Nav, Num, or BT layers.

## Acceptance criteria

- [ ] Every inventory item classified as an obvious diagonal down-right combo is represented by the diagonal Combo-layer.
- [ ] The diagonal Combo-layer uses the existing Binding cell DSL for combo bindings.
- [ ] The diagonal Combo-layer uses a diagonal down-right relative-offset Pattern that matches the previous key-position behavior.
- [ ] Generated diagonal combo nodes render with a clear diagonal-oriented prefix.
- [ ] Generated diagonal combo nodes render with explicit BASE layer restrictions resolved to valid ZMK layer indexes.
- [ ] The old semantic raw-node names are not required for generated diagonal Combo-layer combos.
- [ ] No new Nav, Num, or BT-specific diagonal Combo-layers are introduced.
- [ ] Tests verify rendered bindings, key positions, generated naming prefix, and BASE scoping for the diagonal group.
- [ ] The existing Babashka test task passes.

## Blocked by

- `issues/001-confirm-base-combo-migration-inventory.md`

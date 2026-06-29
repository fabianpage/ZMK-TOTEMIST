# Migrate vertical BASE combos to a Combo-layer

## What to build

Convert the obvious same-column adjacent BASE combos identified in the inventory from repeated raw combo nodes into a BASE-scoped Combo-layer using the existing Binding grid, row-width, Pattern, and Layer mechanisms. The generated combo names may use a clear vertical prefix rather than preserving the old semantic raw-node names.

This slice should preserve the current BASE output for the vertical combo group while preventing those combos from firing on Nav, Num, or BT layers.

## Acceptance criteria

- [ ] Every inventory item classified as an obvious vertical same-column adjacent combo is represented by the vertical Combo-layer.
- [ ] The vertical Combo-layer uses the existing Binding cell DSL for combo bindings.
- [ ] The vertical Combo-layer uses a vertical relative-offset Pattern that matches the previous key-position behavior.
- [ ] Generated vertical combo nodes render with a clear vertical-oriented prefix.
- [ ] Generated vertical combo nodes render with explicit BASE layer restrictions resolved to valid ZMK layer indexes.
- [ ] The old semantic raw-node names are not required for generated vertical Combo-layer combos.
- [ ] No new Nav, Num, or BT-specific vertical Combo-layers are introduced.
- [ ] Tests verify rendered bindings, key positions, generated naming prefix, and BASE scoping for the vertical group.
- [ ] The existing Babashka test task passes.

## Blocked by

- `issues/001-confirm-base-combo-migration-inventory.md`

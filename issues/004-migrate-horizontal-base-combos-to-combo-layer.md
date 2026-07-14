# Migrate horizontal BASE combos to a Combo-layer

## What to build

Convert the obvious same-row adjacent BASE combos identified in the inventory from repeated raw combo nodes into a BASE-scoped Combo-layer using the existing Binding grid, row-width, Pattern, and Layer mechanisms. The generated combo names may use a clear horizontal prefix rather than preserving the old semantic raw-node names.

This slice should preserve the current BASE output for the horizontal combo group while preventing those combos from firing on Nav, Num, or BT layers.

## Acceptance criteria

- [ ] Every inventory item classified as an obvious horizontal same-row adjacent combo is represented by the horizontal Combo-layer.
- [ ] The horizontal Combo-layer uses the existing Binding cell DSL for combo bindings.
- [ ] The horizontal Combo-layer uses a horizontal relative-offset Pattern that matches the previous key-position behavior.
- [ ] Generated horizontal combo nodes render with a clear horizontal-oriented prefix.
- [ ] Generated horizontal combo nodes render with explicit BASE layer restrictions resolved to valid ZMK layer indexes.
- [ ] The old semantic raw-node names are not required for generated horizontal Combo-layer combos.
- [ ] No new Nav, Num, or BT-specific horizontal Combo-layers are introduced.
- [ ] Tests verify rendered bindings, key positions, generated naming prefix, and BASE scoping for the horizontal group.
- [ ] The existing Babashka test task passes.

## Blocked by

- `issues/001-confirm-base-combo-migration-inventory.md`

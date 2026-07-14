# Retain irregular combos as BASE-scoped raw nodes

## What to build

Migrate the irregular combos identified in the inventory so they remain explicit raw combo nodes, keep their semantic node names, keep their current BASE bindings and key positions, and gain explicit BASE-only layer restrictions. These combos should not be forced into sparse or obscure Combo-layer grids.

This slice preserves the unusual combo shapes and behavior that are not obvious horizontal, vertical, or diagonal grid patterns while eliminating their current global firing behavior on non-BASE layers.

## Acceptance criteria

- [ ] Every inventory item classified as a retained irregular combo remains an explicit raw combo node.
- [ ] Retained raw irregular combo node names are preserved.
- [ ] Retained raw irregular combo bindings and key positions match their pre-migration BASE behavior.
- [ ] Each retained raw irregular combo renders with an explicit BASE layer restriction.
- [ ] Retained irregular combos no longer render as global combos.
- [ ] BT switching, umlaut macro, bracket, editing, sticky modifier, and Caps Word irregular combos keep their existing BASE behavior when they are classified as retained raw combos.
- [ ] Tests verify retained raw combo names, bindings, key positions, and BASE scoping from rendered output.
- [ ] The existing Babashka test task passes.

## Blocked by

- `issues/001-confirm-base-combo-migration-inventory.md`
- `issues/002-support-layer-keyword-scoping-for-raw-combo-nodes.md`

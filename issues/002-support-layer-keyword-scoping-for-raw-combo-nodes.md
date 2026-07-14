# Support Layer keyword scoping for raw combo nodes

## What to build

Allow retained raw combo nodes to declare layer restrictions with readable Layer names instead of hard-coded numeric layer indexes, and render those restrictions into valid ZMK `layers` properties alongside the existing raw combo body. This should preserve the current raw-node escape hatch while giving irregular fallback combos the same BASE-only behavior as generated Combo-layer nodes.

This is an enabling vertical slice for the irregular combo migration: a config author can write a raw combo with a BASE layer reference, run the generator, and see a raw combo node with the original body plus the resolved BASE layer index in the rendered keymap.

## Acceptance criteria

- [ ] A retained raw combo node can declare a BASE layer restriction using the same readable Layer reference style used by Combo-layers.
- [ ] The rendered raw combo node includes a valid ZMK `layers` property with the resolved BASE layer index.
- [ ] Existing raw combo bodies continue to render unchanged apart from the optional layer restriction when provided.
- [ ] Existing raw combo nodes without a layer restriction remain supported for backward compatibility.
- [ ] Unknown Layer names still fail clearly rather than rendering an invalid `layers` property.
- [ ] Tests cover raw combo layer-name resolution through rendered keymap output.
- [ ] The existing Babashka test task passes.

## Blocked by

None - can start immediately

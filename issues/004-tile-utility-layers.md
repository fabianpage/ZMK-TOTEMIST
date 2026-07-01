## What to build

Convert the utility layers `Num` and `BT` from inline binding grids to tiled assembly using the same physical Tile and Placement style as the other layers. The number and Bluetooth layers should use the shared layout composition approach while preserving the generated keymap exactly.

## Acceptance criteria

- [ ] `Num` declares Placements over physical-region Tiles instead of an inline binding grid.
- [ ] `BT` declares Placements over physical-region Tiles instead of an inline binding grid.
- [ ] Sparse and transparent cells remain explicit and auditable in the tiled configuration.
- [ ] Tile names and Placement structure follow the physical-location convention established by the base layer refactor.
- [ ] The generated Totem keymap matches the pre-refactor baseline.
- [ ] The existing generator test task continues to pass.
- [ ] No firmware config files or build files are modified.

## Blocked by

- issues/001-establish-generated-keymap-equivalence-guard.md
- issues/002-tile-base-layer-by-physical-regions.md

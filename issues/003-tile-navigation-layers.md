## What to build

Convert the navigation layers `Nav1` and `Nav2` from inline binding grids to tiled assembly using the same physical Tile and Placement style established for the base layer. The left-hand, right-hand, and thumb-cluster responsibilities should be visually separated while preserving all generated keymap behavior.

## Acceptance criteria

- [ ] `Nav1` declares Placements over physical-region Tiles instead of an inline binding grid.
- [ ] `Nav2` declares Placements over physical-region Tiles instead of an inline binding grid.
- [ ] Sparse and transparent cells remain explicit and auditable in the tiled configuration.
- [ ] Tile names and Placement structure follow the physical-location convention established by the base layer refactor.
- [ ] The generated Totem keymap matches the pre-refactor baseline.
- [ ] The existing generator test task continues to pass.
- [ ] No firmware config files or build files are modified.

## Blocked by

- issues/001-establish-generated-keymap-equivalence-guard.md
- issues/002-tile-base-layer-by-physical-regions.md

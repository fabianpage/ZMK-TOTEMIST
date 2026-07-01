## What to build

Convert the practical combo-layers from inline binding grids to tiled assembly using physical-location Tiles and Placements where that improves readability. Sparse combo definitions should remain easy to audit, empty combo cells should keep the no-combo meaning, and mirroring should only be used for exact horizontal mirrors. The generated combo output must remain unchanged.

## Acceptance criteria

- [ ] Practical combo-layers are assembled through Placements over physical-region Tiles instead of inline binding grids.
- [ ] Combo-layer empty cells use the established no-combo meaning and remain visually auditable against row widths and patterns.
- [ ] Asymmetric halves remain explicit rather than hidden behind clever mirroring or overlays.
- [ ] Mirroring is only used when the target Tile is an exact horizontal reverse of the source Tile.
- [ ] The generated Totem keymap matches the pre-refactor baseline.
- [ ] The existing generator test task continues to pass.
- [ ] No firmware config files or build files are modified.

## Blocked by

- issues/001-establish-generated-keymap-equivalence-guard.md
- issues/002-tile-base-layer-by-physical-regions.md

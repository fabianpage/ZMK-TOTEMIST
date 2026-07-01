# PRD: Totem Config Tiled Refactor

## Problem Statement

The current Totem generator configuration defines Layers and Combo-layers with inline Binding grids. This makes the physical shape of the keyboard hard to see, especially because each full grid mixes left hand, right hand, and thumb cluster cells in a single structure. The generator already supports Tiles and Placements, but the main Totem configuration does not use them yet.

The user wants the Totem configuration to use Tiles so that the physical layout is easier to maintain, while preserving the generated keymap behavior exactly.

## Solution

Refactor the Totem generator configuration to define reusable Physical-half Tiles and thumb Tiles, then assemble Layers and Combo-layers through Placements. The generated keymap should remain behaviorally identical to the current output.

The refactor should use Tiles as a readability and maintenance aid, not as a new abstraction layer. Tile names should primarily describe physical location, and mirroring should be used only when the target grid is an exact horizontal mirror of the source Tile.

## User Stories

1. As a Totem keymap maintainer, I want the base Layer to be assembled from physical Tiles, so that I can see the keyboard shape directly in the configuration.
2. As a Totem keymap maintainer, I want navigation Layers to be assembled from physical Tiles, so that left-hand, right-hand, and thumb-cluster responsibilities are visually separated.
3. As a Totem keymap maintainer, I want number and Bluetooth Layers to be assembled from physical Tiles, so that all Layers use the same layout composition style.
4. As a Totem keymap maintainer, I want Combo-layers to be assembled from physical Tiles where practical, so that combo definitions benefit from the same physical structure as Layers.
5. As a Totem keymap maintainer, I want Tile names to encode physical location, so that I can understand Placements without mentally decoding semantic abstractions.
6. As a Totem keymap maintainer, I want left-main, right-main, and thumb Tiles to be separate, so that the hand areas and thumb cluster can evolve independently.
7. As a Totem keymap maintainer, I want the row split to reflect the Totem layout of 5/5, 5/5, 6/6, and 3/3 cells, so that the configuration matches the physical keyboard.
8. As a Totem keymap maintainer, I want Placements to assemble full Layer Binding grids, so that rendering continues to use the existing generator pipeline.
9. As a Totem keymap maintainer, I want Combo-layer empty cells to use the no-combo meaning, so that sparse combo definitions remain semantically clear.
10. As a Totem keymap maintainer, I want conservative mirroring only where the data exactly matches, so that the configuration remains explicit instead of clever.
11. As a Totem keymap maintainer, I want asymmetric halves to remain explicit Tiles, so that the configuration does not hide meaningful differences behind overrides.
12. As a Totem keymap maintainer, I want the generated keymap to remain unchanged, so that this refactor does not alter keyboard behavior.
13. As a Totem keymap maintainer, I want tests to continue passing, so that the existing generator behavior remains intact.
14. As a Totem keymap maintainer, I want this change to be limited to the generator configuration, so that firmware files are not touched as part of a readability refactor.
15. As a future maintainer, I want the Tile registry to be the obvious place to inspect reusable Binding grids, so that I do not have to scan large inline grids inside every node.
16. As a future maintainer, I want each Layer or Combo-layer to declare only Placements once tiled, so that there is one source of truth for its assembled Binding grid.
17. As a future maintainer, I want the refactor to respect the existing Tile domain model, so that it aligns with the project’s documented generator concepts.
18. As a future maintainer, I want no new generator behavior to be introduced, so that this remains a safe configuration-only migration.
19. As a keymap author, I want thumb rows represented as their own Tiles, so that thumb-cluster changes do not require editing a larger hand Tile.
20. As a keymap author, I want sparse Combo-layer grids to remain readable after tiling, so that combo positions can still be audited against row widths and patterns.

## Implementation Decisions

- The change is a configuration refactor only. The generator’s Tile, Placement, alias expansion, assembly, and rendering behavior already exists and should be used as-is.
- The existing Tile domain model is authoritative: a Tile is a reusable named Binding grid that does not render directly.
- Physical-half Tiles will be the primary Tile style. A Physical-half Tile represents one hand-side region of the keyboard layout and does not imply symmetry.
- Tile names will optimize for physical location first and Layer/Combo-layer association second.
- Each normal Layer currently using inline Bindings will be converted to Placements over Tiles.
- Each tiled Layer will use the full Totem row widths: 10, 10, 12, and 6.
- The physical split is 5 left and 5 right cells for rows 0 and 1, 6 left and 6 right cells for row 2, and 3 left and 3 right cells for the thumb row.
- Thumb rows will be represented as separate Tiles rather than folded into main hand Tiles.
- Combo-layers will also be converted to tiled assembly where practical, using the same physical-location naming approach.
- Tiled Combo-layers will use an empty Binding cell that means “no combo here”.
- Mirroring will be used only when a target Tile is exactly the horizontal reverse of a source Tile.
- The refactor will not use a mirrored base plus overlay strategy unless exact mirroring already exists. Explicit Tiles are preferred for asymmetric data.
- A node should use either inline Bindings or Placements, not both.
- The generated output should be behaviorally identical after the refactor.
- Firmware configuration and build files are out of scope.

## Testing Decisions

- The highest-value test seam is generated keymap output: the refactored configuration should generate the same keymap text, modulo formatting already produced by the generator.
- The existing generator test suite remains the regression suite for Tile assembly, Placement handling, alias expansion, rendering, Combo-layer rendering, and missing marker handling.
- A good test for this refactor checks external behavior: the generated keymap remains equivalent. It should not assert internal Tile names or Placements unless a future test specifically covers configuration style.
- The generator’s existing example-based tests are prior art for whitespace-insensitive generated keymap comparison.
- The refactor should be verified by running the existing test task.
- The refactor should also be verified by generating the Totem keymap from the template and comparing it against the current generated output or a saved pre-refactor output.
- No new generator seam is required because this PRD does not introduce new generator behavior.

## Out of Scope

- Changing generator semantics.
- Adding new Tile features.
- Adding new Placement options.
- Changing alias expansion behavior.
- Changing rendering behavior for Layers or Combo-layers.
- Changing firmware configuration files.
- Changing build configuration.
- Changing the physical keyboard layout.
- Renaming key behaviors, macros, or Layer names for semantic cleanup.
- Introducing non-horizontal mirroring.
- Introducing clever mirrored base-plus-overlay composition for asymmetric halves.
- Reworking combo patterns or combo position arithmetic.

## Further Notes

This PRD is intentionally narrower than the existing tiled layer composition PRD. The generator already supports the needed Tile and Placement concepts. This work applies that capability to the main Totem configuration while preserving output.

The most important success criterion is that the user-facing generated keymap remains unchanged. The second most important success criterion is that the resulting configuration is easier to audit by physical keyboard region.

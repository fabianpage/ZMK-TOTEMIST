# PRD: Tiled Layer Composition

## Problem Statement

Defining every layer and combo-layer as an inline binding grid is verbose, repetitive, and error-prone for split keyboards (e.g. Totem) where left and right halves are mirrors of each other. Users currently copy-paste entire binding grids and manually reverse column order to achieve mirrored halves, which is tedious and makes maintenance difficult.

## Solution

Introduce **Tiles**: reusable named binding grids defined in a top-level `:tiles` map. Layers and Combo-layers can reference one or more Tiles via **Placements** (`{:tile :name :pos [col row] :mirror :horizontal :clip? boolean}`). The generator assembles a flat `:bindings` grid from the tiles before rendering, keeping half-layouts DRY and making mirroring explicit and automatic.

## User Stories

1. As a ZMK keymap author, I want to define a reusable half-layout Tile, so that I can compose layers without repeating myself.
2. As a ZMK keymap author, I want to place a Tile at a specific grid position within a Layer, so that I can build full layouts from half-layouts.
3. As a ZMK keymap author, I want to horizontally mirror a placed Tile, so that I can reuse a left-half tile for the right half without manual reversal.
4. As a ZMK keymap author, I want overlapping Placements to resolve with last-wins semantics, so that I can layer a small overlay on top of a base tile.
5. As a ZMK keymap author, I want out-of-bounds tile cells to throw by default, so that I catch misaligned placements early.
6. As a ZMK keymap author, I want an optional `:clip?` flag on Placements to silently drop out-of-bounds cells, so that I can intentionally overhang a tile beyond the target grid.
7. As a ZMK keymap author, I want Tile names and Layer names to live in separate namespaces, so that I can name a Tile `:base` without colliding with a Layer named `BASE`.
8. As a ZMK keymap author, I want alias expansion to run globally before any Tile assembly or rendering, so that aliases work inside Tiles, Combo-layers, and Placements.
9. As a ZMK keymap author, I want recursive Tile composition (Tiles referencing other Tiles), so that I can build complex layouts from simpler building blocks.
10. As a ZMK keymap author, I want the generator to detect placement cycles and throw a clear error, so that I don't accidentally create infinite loops in recursive tile definitions.
11. As a ZMK keymap author, I want empty cells in an assembled grid to fill with a configurable default (`:trans` by default), so that I don't have to explicitly pad every grid.
12. As a ZMK keymap author, I want to specify either inline `:bindings` OR `:placements` on a Layer/Combo-layer, but not both, so that the config is unambiguous.
13. As a ZMK keymap author, I want Combo-layers to support `:placements` just like Layers, so that I can reuse Tiles for combo trigger grids.

## Implementation Decisions

- **Config schema changes**: Add a top-level `:tiles` key to the EDN config. A Tile is a map that may contain either `:bindings` (an inline binding grid) or `:placements` + `:row-widths` for recursive composition.
- **Placement schema**: `{:tile <keyword> :pos [col row] :mirror (:horizontal | nil) :clip? boolean}`. `:pos` is the top-left corner where the tile is placed. `:mirror :horizontal` reverses column order within each row.
- **Node schema changes**: Both Layer nodes and Combo-layer nodes gain optional keys `:placements`, `:row-widths`, and `:empty` (default `:trans`). These are mutually exclusive with `:bindings`.
- **Assembly pipeline (single seam)**: Introduce one pure preprocessing step in `generate-keymap` that resolves all `:placements` into `:bindings` before any rendering occurs. Once preprocessed, `render-layer` and `render-combo-layer` require no changes. This keeps the rendering seam untouched.
- **Alias expansion refactor**: `expand-aliases` currently walks only `:bindings` inside `:keymap` nodes. It will be refactored to walk the entire config recursively, including `:tiles`, `:combos` region nodes, and nested binding grids inside `:placements`.
- **Tile assembly engine**: A pure function taking a container spec (`:row-widths`, `:empty`, `:placements`) and a tile registry, returning a flat `:bindings` grid. It handles mirroring (reverse row), clipping (drop out-of-bounds cells when `:clip? true`), and overlap (last placement wins).
- **Tile resolution**: For nodes/Tiles that specify `:placements`, recursively resolve referenced tiles through the registry. Detect cycles by tracking the visited tile chain; throw `ExceptionInfo` with a clear message if a cycle is found.
- **Dual use of `:row-widths`**: In Combo-layer nodes, `:row-widths` already controls combo position arithmetic. When a Combo-layer uses `:placements`, the same `:row-widths` also defines the assembled grid dimensions. No new key is needed; this dual role is natural but should be kept in mind.

## Testing Decisions

- **Highest seam**: The primary seam is the preprocessing function that converts a config with `:placements` into a config with only `:bindings`. Tests at this seam feed EDN configs in and assert on the preprocessed structure (or equivalently on the final `generate-keymap` string output).
- **Prior art**: The existing test suite uses `tokenize` for whitespace-agnostic token comparison of full `generate-keymap` output against expected `.keymap` files. New examples will follow this pattern (EDN + `_in.keymap` + `_out.keymap`).
- **Unit tests**: Pure helper functions (tile assembly, mirroring, overlap resolution, clipping, cycle detection) will have dedicated unit tests using `clojure.test` assertions on data structures, not on rendered strings. This keeps tests fast and focused.
- **Regression**: `bb test` must continue to pass. All existing examples must remain unchanged because `:placements` is purely additive to the schema.

## Out of Scope

- Vertical or diagonal mirroring of tiles (only `:horizontal` is supported initially).
- A visual / TUI editing experience for placing tiles.
- Runtime validation of grid dimensions against physical keyboard metadata (e.g. checking `row-widths` matches the Totem's actual split row counts).
- Changing the region syntax or marker format in `.keymap` templates.
- Multiple `:clip?` semantics per placement (clip vs error is binary).

## Further Notes

- The existing `binding->str`, `render-layer`, and `render-combo-layer` functions remain unchanged — this validates the "single preprocessing seam" approach.
- The `combo-positions` function's out-of-bounds handling (`when (every? ...)`) will naturally skip combos where the assembled binding cell is out of bounds after clipping, keeping existing combo-layer semantics intact.

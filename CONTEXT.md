# Domain Model — ZMK-TOTEMIST Generator

## Glossary

- **Binding cell** — A single key action or combo action. Can be a keyword (`:P`, `:trans`, `:none`) or a vector (`[:lt 3 :DE_S]`). Resolved through the global alias map before rendering or assembly.
- **Binding grid** — A 2D vector of binding cells. Rows may have varying widths (e.g. a split layout row vs a combo-layer grid).
- **Layer** — A node in the `:keymap` region. Renders as a ZMK `keymap` layer node with `display-name` and `bindings`.
- **Combo-layer** — A node in the `:combos` region. Defines a grid of combo trigger cells plus a `:pattern` (relative offsets) and `:row-widths`. Renders into multiple ZMK combo nodes.
- **Alias** — A keyword mapping in the config (e.g. `{:S [:lt 3 :DE_S]}`). Aliases are resolved globally before any rendering or assembly.
- **Tile** — A reusable named binding grid. Defined in the top-level `:tiles` map. Does not render directly; exists only in the config data structure.
- **Placement** — A tile referenced at a position within a larger grid, with optional mirroring. `{:tile :name :pos [col row] :mirror :horizontal}`.
- **Assembled grid** — The flat binding grid produced by merging placements into a container with given `:row-widths` and an `:empty` fill cell.

## Decisions

- Alias expansion is global and runs **before** tile assembly and rendering.
- Tiles live in a separate namespace from Layers and Combo-layers (top-level `:tiles` key).
- A Tile is **only** a binding grid. Tiles may be used by Layers and Combo-layers, but a Tile cannot reference a Combo-layer.
- Tiles may reference other Tiles recursively (composition), but cycle detection is required.
- `:mirror` supports `:horizontal` only for now (reverse column order within each row).
- Overlapping placements: **last placement wins**.
- Empty cells in an assembled grid are filled with the container's `:empty` cell (default `:trans`).
- A node (Layer or Combo-layer) may specify either inline `:bindings` **or** `:placements` — never both. Specifying both is an error.
- The `:clip` flag on a Placement controls whether out-of-bounds cells are silently dropped. If absent, out-of-bounds cells are an error.

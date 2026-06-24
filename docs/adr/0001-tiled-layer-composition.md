# Tiled Layer Composition

Instead of defining every layer and combo-layer as inline binding grids, we introduced **Tiles**: reusable named binding grids that can be placed, mirrored, and merged to assemble the final `:bindings` of a Layer or Combo-layer. This keeps halves of split keyboards DRY and makes mirroring explicit.

## Considered Options

1. **Inline only** — every layer and combo-layer lists its full binding grid explicitly. This is what the generator did before. Simple, but verbose and error-prone for mirrored halves.
2. **Virtual layers that render as both keymap and combo nodes** — rejected because it leaks the `:combos` / `:keymap` region boundary into the tile concept. A Tile is only a binding grid; it is the Layer or Combo-layer node that decides which ZMK region it belongs to.
3. **Tiled composition** (chosen) — Tiles are reusable grids defined in a top-level `:tiles` map. Layer and Combo-layer nodes can declare `:placements` (a list of `{:tile :name :pos [col row] :mirror :horizontal :clip? true}`) plus `:row-widths` and `:empty`. The generator assembles a flat binding grid before normal rendering. This keeps Tiles decoupled from output regions and allows recursive Tile composition.

## Consequences

- The config schema gains a top-level `:tiles` key.
- Tile names live in a separate namespace from Layer names, so a Tile named `:base` and a Layer named `base` do not collide.
- Cycle detection must run during Tile expansion because Tiles can reference other Tiles.
- `:clip` on a Placement makes the assembly tolerant of overhang (cells outside `:row-widths` are silently dropped instead of throwing).

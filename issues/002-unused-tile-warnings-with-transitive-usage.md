# Unused tile warnings with transitive usage

## What to build

Extend the `find-unused` engine from #001 to detect unused tiles. Tile definitions come from the keys of the `:tiles` map. A tile is used if it appears in any `:placements` vector — either in a layer/combo-layer node or inside another tile's own `:placements`. Because tiles can compose recursively, the reference walk must compute the transitive closure: a tile placed by another tile counts as used if that parent tile is itself placed (directly or transitively) in a layer or combo-layer.

If a tile `:alpha` is placed inside `:beta`, and `:beta` is placed in a layer, then `:alpha` is considered used even though it does not appear directly in the layer. If `:beta` were not placed anywhere, both `:alpha` and `:beta` would be flagged as unused.

The existing `resolve-tile-bindings` / `resolve-all-tiles` logic already walks tiles recursively; the reference collector can reuse a similar traversal or be driven from the placement graph.

Tests verify that nested tile compositions correctly produce (or suppress) unused warnings, including deep nesting and mirrored placements.

## Acceptance criteria

- [ ] Every key in `:tiles` is registered as a tile definition in `find-unused`.
- [ ] A tile referenced in a `:placements` vector inside a layer or combo-layer node is not flagged as unused.
- [ ] A tile referenced only inside another tile's `:placements` is not flagged as unused, provided that parent tile is itself placed transitively.
- [ ] A tile referenced inside a parent tile that is itself unplaced is flagged as unused.
- [ ] Tests cover at least: a simple unused tile, a transitively used tile, and a deeply nested tile chain.

## Blocked by

- #001 — Core unused-check engine and alias warnings

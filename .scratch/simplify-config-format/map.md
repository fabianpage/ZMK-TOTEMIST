# Simplify Totem config: replace tiles with mirroring + global defaults

## Destination

The generator config format is refactored so that:
1. The `:tiles` / `:placements` system is removed entirely.
2. Layers declare only `:left` (a grid of binding cells per row-half) and optionally `:right-override` (a grid where `nil` means "fall back to mirrored left value", non-nil means "use this instead").
3. The new `:keyboard` top-level key holds global settings like `:row-widths` and `:default`, eliminating repetition across every layer and combo-layer definition.
4. All existing tests and the `totem_config.edn` baseline remain green (exact output equivalence) after migration.

## Notes

- **Domain skills**: /grilling, /domain-modeling, /prototype
- **Existing decisions** (CONTEXT.md glossary): alias expansion runs *before* tile assembly; a node may specify inline `:bindings` **or** `:placements` — never both. After the refactor, a layer node will specify `:left` / `:right-override` instead of either.
- **Visual preference**: ASCII diagrams for layout shapes and data-flow sketches.

## Decisions so far

1. **Mirror operation**: Horizontal reversal. Left `[:P :O :I :U :T]` → right `[:T :U :I :O :P]` (finger-correspondent mirror).
2. **`:right-override` shape**: Vector of rows matching `:left` row count. Each row is `nil` (full mirror) or a vector of half-width length containing bindings or the sentinel `:*`.
3. **Sentinel**: `:*` means "use mirrored value".
4. **Geometry**: Top-level mandatory`:keyboard {:row-widths [10 10 12 6]}`. `:left` rows must equal `row-width/2`. Only even splits.
5. **`:right-override` `nil` rows**: Allowed — means "mirror entire row".
6. **`:left` is left half only**.
7. **Combo-layers keep `:bindings`** (full grid), no mirroring. Inherit `:row-widths` from `:keyboard`. Fill is hard-coded `:none`.
8. **`:keyboard` keys**: `:row-widths` and `:empty`. `:aliases` stays top-level.
9. **`:keyboard` required**: Config must have `:keyboard` or error. Old `:bindings` config must be migrated.
10. **`:empty` key name**: `:empty` (matches existing `:placements` convention).
11. **Old `:tiles` / `:placements` removed entirely** — no backward-compat shim.

## Not yet specified

(none — spec written to spec.md)

## Out of scope

- Changes to the ZMK firmware or shield definitions in `config/`.
- Changes to the template `.keymap` format (the `// BEGIN` / `// END` marker system).
- Adding new key behaviors (macros, mod-morphs) not already present in the config.

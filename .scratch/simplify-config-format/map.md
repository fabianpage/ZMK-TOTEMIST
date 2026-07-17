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

<!-- index — one line per closed ticket: enough to judge relevance, then zoom the link for the detail the ticket holds -->

(none yet)

## Not yet specified

- What exact operation does "mirror" perform? Horizontal reversal of `:left` per row to produce the right-half implied bindings? Or a copy? The difference matters for finger-position correspondence on a split keyboard.
- Does the `:right-override` also use the `:keyboard` `:default` fill value (e.g. `:trans`) instead of `nil`? The user example shows `nil`, but `nil` in EDN is not a binding cell — the user likely intended it as a sentinel for "use mirrored value".
- How do combo-layers fit in? Combo-layers also use `:row-widths` and `:bindings` grids today. Do they also gain `:left` / `:right-override`, or do they keep a single `:bindings` grid because combos are inherently full-grid?
- Migration strategy: do we rewrite `totem_config.edn` in-place, keep a dual-syntax compatibility shim during transition, or snapshot a baseline and rewrite tests?
- For layers with `:right-override`, how do we validate that `:right-override` row lengths are ≤ the `:keyboard` `:row-widths` / 2? What happens if they don't match?

## Out of scope

- Changes to the ZMK firmware or shield definitions in `config/`.
- Changes to the template `.keymap` format (the `// BEGIN` / `// END` marker system).
- Adding new key behaviors (macros, mod-morphs) not already present in the config.

## Context

The Totem keyboard generator (`generator.clj`) consumes an EDN/Aero config and a `.keymap` template, producing a compiled ZMK keymap. The config currently uses a `:tiles` + `:placements` assembly model for layers and raw DTS string blobs for behaviors. This proposal replaces both patterns with higher-level abstractions.

The Totem is a split keyboard with row widths `[10 10 12 6]`, so left-hand halves are `[5 5 6 3]`. Most layers are dominated by symmetry or empty (`trans`) right halves; the config pays a high verbosity cost for these cases.

## Goals / Non-Goals

**Goals:**
- Reduce `totem_config.edn` from ~520 to ~120 lines while preserving all emitted semantics.
- Make the config read like the physical layout: write one hand, infer the other.
- Eliminate raw DTS strings from user-facing config; expose a compact behavior table.
- Introduce a safe, composable modifier syntax that replaces fragile raw `LS(...)` strings.
- Keep alias resolution transparent and ordering well-defined.

**Non-Goals:**
- Supporting non-split or non-symmetric keyboards (this generator remains Totem-scoped).
- Patterned-macro generation (e.g. `ue1..ue9`) — deferred to future exploration.
- Removing raw `:body` entirely from the generator internals; it remains an engine escape hatch for edge cases.

## Decisions

### 1. Half-grid layers with automatic mirroring

**Rationale:** For 4 out of 5 current layers, the right half is either empty (`trans`) or would be perfectly usable as a mirror of the left. Writing both halves is pure ceremony.

**Approach:**
```
:left  row 0  →  [A B C D E]     (half-width)
:auto-mirror    →  [A B C D E | E D C B A]   (full-width)
```
Rows are reversed individually (`mirror-row` = `reverse`). This matches the physical symmetry of the Totem.

**Alternative considered:** Allow `:right :mirror` keyword. Rejected because defaulting to mirror is the common case; being explicit about overrides is the exception.

### 2. Right-side override via `nil` sentinel

**Rationale:** Nav and Num need a few right-side keys that differ from the mirror (e.g. arrow cluster on the right). A full `:right` grid would force the user to retype mirrored keys. A sparse map would be hard to read because the visual shape of a grid aids spatial reasoning.

**Approach:** `:right` is an optional grid of the same half-dimensions. `nil` means "trust the mirror." Non-nil overrides. This keeps the visual shape while minimizing deviation noise.

```clojure
:right [[nil nil nil nil nil]       ; row 0: all from mirror
        [nil nil nil nil nil]       ; row 1: all from mirror
        [nil nil :RIGHT nil nil nil] ; row 2: override one key
        [nil nil nil]]              ; row 3: all from mirror
```

**Alternative considered:** Sparse key→value map like `{[2 2] :RIGHT}`. Rejected because it loses the visual grid shape and makes code review harder.

### 3. Modifier DSL: `[:LS [:LC :N6]]`

**Rationale:** Raw strings like `"LS(LC(DE_N6))"` are not structural, cannot be aliased into, and are easy to typo. A vector DSL is structural Lisp all the way down.

**Approach:** Nested vectors where outermost = outermost modifier. Terminal must be a keyword. Compilation is recursive: `[:LS [:LC :N6]]` → `"LS(LC(DE_N6))"`. This works because ZMK modifiers nest left-to-right.

**Alternative considered:** Flat vector `[:LS :LC :N6]`. Rejected because nesting makes composition unambiguous: `[:LS [:LC :N6]]` clearly groups `LC` with `N6` inside `LS`.

### 4. Behavior table over raw DTS

**Rationale:** Every current behavior is a `zmk,behavior-mod-morph` with identical `compatible`, `#binding-cells = <0>`, and `mods = <(MOD_LSFT)>`. Only name, binding-a, and binding-b vary. This is a ~20:1 compression opportunity.

**Approach:**
```clojure
:mod-morph {:default-mods :LSFT
            "square_brackets" [[:LA :N5] [:LA :N6]]
            :LCTL {"hCh" [:H [:LC :H]]
                   "jCj" [:J [:LC :J]]}}
```
Generator expands each entry to the full 6-line DTS node. `:default-mods` applies to top-level entries; nested modifier keys (`:LCTL`) override for their subtree.

**Alternative considered:** YAML/JSON-like table. Rejected because EDN maps are native, and keyword grouping (`:LCTL`) is concise.

### 5. Stagger as per-row column offset

**Rationale:** The user needs to shift row 1 left by one column to match physical key stagger. A global model (e.g. "5 columns per half, staggered") is insufficient: physical keyboards have per-row idiosyncrasies.

**Approach:** `:keyboard {:stagger [0 -1 0 0]}` where each value is added to the effective column of that row's keys when computing absolute key-positions for combos. Zero = no shift. Negative = shift toward thumb-side / start of row.

This affects:
- Combo `key-positions` calculation: `effective-col = col + stagger[row]`
- Mirroring: the mirror still flips within the half-width, but absolute combo coordinates include the stagger.

**Note:** This design does NOT change the `:left` row dimensions; stagger is purely an offset for absolute position math. If stagger pushes a combo out of half-width bounds, the feasibility check catches it.

### 6. Warning on infeasible combo binding

**Rationale:** A user might write a binding at the edge of a half-row not realizing the pattern won't fit. Silent dropping would be a footgun.

**Approach:** During combo layer preprocessing, for each cell, compute if all pattern offsets stay within the left half. If a cell has a non-`trans` binding but is infeasible, print a warning to stderr identifying the combo layer, row, col, and binding.

```
WARNING: Combo layer 'horiz_base' — binding at [2, 4] (value :DE_B) cannot be placed with pattern [[0 0] [0 1]] on row of half-width 5
```

### 7. Preserve `:bindings` escape hatch on layers

**Rationale:** The user might occasionally want a completely asymmetric layer. Rebuilding the old capability under new syntax is possible (`:left` + full `:right`), but being able to drop in a literal full grid is useful for one-offs and migration.

**Decision:** A layer node with explicit `:bindings` bypasses half-grid logic entirely. The generator treats it exactly like the old format. All other layers default to half-grid.

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| **Config breaking change** — every existing `.edn` must be rewritten | Provide a migration script or documented before/after mapping. The `examples/` serve as canonical test vectors; rewrite them first. |
| **Combo edge cases with stagger** — negative stagger + wide pattern might produce invalid positions | Feasibility check runs before position calculation; stagger is added after feasibility passes. |
| **Modifier DSL confusion** — `[:LS :H]` vs `[:LS [:LC :H]]` | Generator error if a modifier vector has >2 elements with a non-vector second element, pointing user to nesting rules. |
| **Performance** — alias expansion + mirror + merge adds preprocessing overhead | Configs are tiny (<1KB); preprocessing is cheap. No runtime impact. |
| **Reduced flexibility** — raw `:body` strings are no longer the default | Escape hatch preserved; behaviors with esoteric fields can still use raw `:body`. Combos can still use raw `:body` for one-offs. |
| **Stagger default value** — what happens if `:stagger` is omitted? | Defaults to a zero vector of length matching row count. No effect if absent. |

## Open Questions

1. **Combo layer half-grid shape with stagger:** If stagger shifts a row's effective columns, does the `:left` written width change? Decision: No — `:left` dimensions are always derived from `row-widths / 2`. Stagger is applied only during absolute `key-positions` calculation. This keeps the `:left` grid visually consistent with the physical row shape.
2. **Should `:right` override support `:_` (the alias for `:trans`) as equivalent to `nil`?** Tentative: No, to keep `nil` as the exclusive "mirror fallback" sentinel. `:_` and `:trans` render as `&trans`, which is a legitimate override value.

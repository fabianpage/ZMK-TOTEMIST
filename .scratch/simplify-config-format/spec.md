## Problem Statement

The current generator config format uses a `:tiles` / `:placements` system to assemble keymap layers. Every layer defines `:placements` that reference named tiles from a global `:tiles` registry, with per-layer `:row-widths` duplicated everywhere. This is verbose, hard to edit directly, and obscures the visual relationship between left- and right-hand sides of a split keyboard layout — where the right hand is almost always a horizontal mirror of the left hand.

## Solution

Replace `:tiles` / `:placements` entirely with a declarative `:left` / `:right-override` model. A keymap layer declares only its left half (`:left`); the generator automatically mirrors each row horizontally to produce the right half. When a layer needs the right side to differ from the mirrored default, it supplies `:right-override` — a sparse grid using a sentinel for "use the mirrored value" — and only the overridden cells deviate.

Global keyboard geometry (`:row-widths`, `:empty`) and aliases move to a top-level `:keyboard` key so they are defined once instead of per-node.

## User Stories

1. As a user editing a Totem config, I want to define a layer by writing only the left-hand keys, so that I don't have to manually type the mirrored right-hand keys.
2. As a user adding a new layer, I want to place a single key on the right-hand side (e.g., a Bluetooth toggle) without retyping the entire right half, so that small deviations are easy.
3. As a user reading a config, I want the right-hand overrides to be visually compact, so that I can scan a layer and immediately see what's different from the default mirror.
4. As a user maintaining the Totem config, I want `:row-widths` declared once at the top of the file, so that every layer doesn't repeat the same numbers.
5. As a user with a split keyboard, I want the mirror operation to map left-pinky to right-pinky, left-index to right-index, etc., so that the physical ergonomics are correct.
6. As a user writing a combo-layer, I want it to inherit the global `:row-widths` automatically, so that I don't have to repeat them for every combo definition.
7. As a user migrating from the old format, I want the old example configs to continue working, so that I don't have to rewrite everything at once.
8. As a user writing a config, I want the generator to reject configs that are missing `:keyboard`, so that I get a clear error instead of silent bad output.
9. As a user writing `:right-override`, I want `nil` as a row to mean "mirror this entire row", so that I don't have to type a row of all-sentinel cells.
10. As a user debugging a layer, I want the generator to validate that `:left` and `:right-override` row lengths match the keyboard geometry exactly, so that typos are caught early.
11. As a user writing a test for the generator, I want a new example config using `:left` to produce the identical rendered keymap as an existing example, so that I know the new system is equivalent.
12. As a user with an odd-column keyboard, I want the generator to reject odd `:row-widths`, so that the half-width invariant is never violated.

## Implementation Decisions

### Module: `generator.clj`

- **Remove `:tiles` / `:placements` entirely**. All functions pertaining to tile resolution, assembly, recursive tile nesting, and `mirrors` on placements are deleted.
- **Remove `:row-widths` from layer and combo-layer nodes**. It now lives only under `:keyboard`.
- **Add `:keyboard` top-level key**. Required keys: `:row-widths` (vector of even integers), `:empty` (binding keyword, defaults to `:trans`). Optional keys: none at first. `:aliases` remains top-level.
- **`render-layer` signature stays the same** (`[node level]`), but `:bindings` is no longer supplied directly by the config. The generator assembles `:bindings` transparently from `:left` + mirrored right + `:right-override` before `render-layer` is called.
- **Mirror operation**: for each row in `:left`, reverse the vector to produce the implied right-half row.
- **`:right-override` structure**: a vector whose length equals the row-count of `:left`. Each element is either `nil` (full mirror for that row) or a vector of half-width length.
- **Sentinel**: `:*` means "use the mirrored (reversed) value from `:left`" at that position. Non-sentinel cells override outright.
- **Validation**:
  - `:keyboard` must be present or `ExceptionInfo` is thrown.
  - Each `:left` row length must equal `(quot row-width 2)` for the corresponding `:keyboard :row-widths` entry. Odd `:row-widths` values are rejected.
  - Each non-nil `:right-override` row must have the same length as the corresponding `:left` row.
- **`resolve-placements-node` and `resolve-placements` are removed**. Render-node handling for keymap layers now resolves `:left` / `:right-override` into `:bindings` as a preprocessing step, similar to how `resolve-placements` worked.
- **Combo-layers keep `:bindings`** (full grid) and do not use mirroring. They derive `:row-widths` from `:keyboard` implicitly. Empty cells in combo-layers are always `:none` (hard-coded), regardless of `:keyboard :empty`, because untriggered combo cells must not generate combos.
- **`generate-keymap` pipeline**:
  1. Load + expand aliases (unchanged).
  2. Validate `:keyboard`.
  3. Preprocess each keymap-layer node: `:left` + mirror + `:right-override` → `:bindings`.
  4. Preprocess each combo-layer: resolve `:row-widths` from `:keyboard`.
  5. Render regions (unchanged logic after step 3/4).

### Schema shapes (post-refactor)

**Config top-level:**
```clojure
{:keyboard {:row-widths [10 10 12 6]
            :empty :trans}      ; default for empty keymap cells
 :aliases  {:_ :trans ...}      ; stays top-level
 :regions  [[:combos {...}] ...]}
```

**Keymap layer node:**
```clojure
{:name "BASE"
 :left [[:P :O :I :U :T]
        [[:lt 3 :DE_S] :A :E :N :R]
        [:DE_G :D :L :C :F :H]
        [[:sl 1] [:sk :LEFT_SHIFT] [:sl 2]]]
 :right-override [nil                         ; row 0 = full mirror
                  [:* :* :* :* [:*]]          ; row 1 = override only col 4 (5th key)
                  nil]}
```

**Combo-layer node (unchanged interface, minus `:row-widths`):**
```clojure
{:name "horiz_base"
 :type :combo-layer
 :pattern [[0 0] [0 1]]
 :empty :none                          ; optional per-node override for edge cases
 :bindings [[:DE_X :DE_W ...]]}
```

## Testing Decisions

### What makes a good test

- Tests exercise **external behavior**: give a config and a template, assert on the rendered keymap string.
- Tests do **not** assert on intermediate data structures (`:_mirror` expansion, internal grid shapes) unless that structure is the seam for unit-level edge-case testing.
- The highest seam is `generate-keymap`; most new tests sit at this level.

### Seams and test plan

1. **Integration seam (highest)**: Add a new example config `examples/2.edn` that uses `:left` + `:right-override` and generates the **identical** `.keymap` output as `examples/1_out.keymap` (which uses legacy `:bindings` full-grid). The auto-discovered `deftest-examples` macro picks this up. This proves equivalence between old and new syntax.
2. **Regression seam**: The existing `totem-config-generates-captured-baseline` test continues to pass after `totem_config.edn` is migrated to `:left` syntax. This is the strongest end-to-end test.
3. **Unit seam**: Add deftests for the new `assemble-layer` (or equivalent) function directly:
   - Missing `:keyboard` throws `ExceptionInfo`.
   - Odd `:row-widths` throws `ExceptionInfo`.
   - `:left` row too long / too short throws `ExceptionInfo`.
   - `:right-override` row wrong length throws `ExceptionInfo`.
   - `:*` sentinel correctly picks up the mirrored value.
   - `nil` row in `:right-override` produces full mirror for that row.
   - Absent `:right-override` produces full mirror for all rows.
4. **Combo-layer seam**: A test that a combo-layer without explicit `:row-widths` derives its grid from `:keyboard :row-widths` and still generates the same combos.

### Prior art

- `examples/1.edn` vs `examples/1_in.keymap` / `examples/1_out.keymap` already proves the example-integration pattern.
- `totem-config-generates-captured-baseline` proves the "frozen baseline" regression test.
- Existing `deftest-examples` macro auto-discovers new example files.
- Existing tests for `placements-mirror-horizontal`, `placements-clip`, `combo-layer-generates-combos` all sit at the `generate-keymap` seam or function-level seam.

## Out of Scope

- Changes to the ZMK firmware or shield definitions in `config/`.
- Changes to the template `.keymap` format (the `// BEGIN` / `// END` marker system).
- Adding new key behaviors (macros, mod-morphs) not already present in the config.
- Partial-row `:left` lengths — every row must exactly equal half the declared `:row-widths`.
- Center-column support for odd-width keyboards.
- Mirroring for combo-layers.
- Preserving `:tiles` as a backward-compatibility shim.

## Further Notes

- The example `examples/1.edn` uses `:bindings` directly on layers. After this refactor, `:bindings` on a keymap layer is an error (strictly `:left` or nothing). `examples/1.edn` should be converted to `:left` syntax or replaced by `examples/2.edn`.
- The migration of `totem_config.edn` requires carefully mapping every `:left-main-*` tile + its mirrored `:right-main-*` tile into a single `:left` row plus a `:right-override` row. Because many right halves in the current config are mostly `:trans` (e.g., Nav1, Nav2, Num, BT), the migrated config will be significantly shorter.
- `:empty` in combo-layers: today some combo-layers explicitly set `:empty :none`. After the refactor, combo-layers default to `:none` regardless of `:keyboard :empty`. If a combo-layer explicitly specifies `:empty`, it overrides this default (similar to today's behavior).

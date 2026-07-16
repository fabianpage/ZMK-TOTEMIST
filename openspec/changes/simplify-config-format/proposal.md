## Why

The Totem keyboard EDN config (totem_config.edn) has grown to ~520 lines of repetitive, fragment-oriented declarations. Every layer is split into 4–6 tile fragments (`left-main-nav1-rows-0-1`, `right-main-nav1-rows-0-1`, etc.), and behaviors are written as raw DTS string blobs repeated 30+ times. This makes iteration slow and the config hard to read, edit, and review.

## What Changes

- **BREAKING** Replace tile/placement assembly with a "half-grid" layer definition: write only the left-hand side of each layer; the right half auto-mirrors by reversing each row. Add optional `:right` override grid where `nil` cells fall back to the mirrored value. Remove the entire `:tiles` registry.
- **BREAKING** Replace the raw `:body` string behavior definition with a compact table: `:mod-morph` and `:smart-toggle` blocks that declare name → binding pairs. Generator emits the full DTS boilerplate. Support per-group modifiers (e.g. `:LCTL` group within `:mod-morph`).
- Introduce a rich modifier DSL: nested modifiers like `[:LS [:LC :N6]]` compile to `LS(LC(DE_N6))` instead of raw strings.
- Add global `:keyboard` metadata block for shared constants: `row-widths`, `stagger` (per-row column offset), and `default` empty cell (e.g. `:trans`).
- Simplify combo-layer definition to use the same half-grid `:left` shape as normal layers. Generator computes valid combo start positions from pattern and row widths, skipping infeasible cells. Emit a runtime warning if a non-nil binding cannot be placed.
- Keep cross-half combos as explicit escape-hatch definitions beside automated combo layers.
- Preserve `:aliases` support; encourage its use for ergonomic shortcuts.

## Capabilities

### New Capabilities
- `layer-definition`: Half-grid layer model with auto-mirror, right-side override (`nil` = fallback), and global keyboard defaults.
- `combo-layer-definition`: Combo layers sharing the same half-grid shape as normal layers; pattern-driven feasibility checks and stagger-aware row offsets.
- `behavior-manifest`: Compact `:mod-morph`/`:smart-toggle` tables that expand to full behavior nodes.
- `binding-dsl`: Nested modifier syntax (e.g. `[:LS [:LC :N6]]`) replacing raw `LS(...)` strings.

### Modified Capabilities
- *(none — this is a ground-up replacement of the config schema; the generator's core marker-replacement and output pipeline remain stable)*

## Impact

- `totem_config.edn` shrinks from ~520 → ~120 lines.
- `generator.clj` gains new preprocessing functions (mirror, merge-right, modifier formatter, behavior table expansion).
- All `examples/*.edn` files must be updated to the new schema.
- `test/generator_test.clj` assertions must be rewritten for new input/output pairs.
- The old `:tiles`, `:placements`, raw `:body` behavior nodes, and explicit full-grid `:bindings` in layers are no longer supported.

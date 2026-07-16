## 1. Binding DSL and Modifier Formatter

- [ ] 1.1 Implement `binding->str` enhancement for nested modifier vector syntax `[:MOD1 [:MOD2 :KEY]]`
- [ ] 1.2 Validate modifier vector: error if unresolved keyword appears where modifier expected
- [ ] 1.3 Ensure `binding->str` handles all existing cases: bare keywords, `:trans`/`:none`, `[:lt 3 :DE_S]`, param wrappers, `:to`, `:bt`, `:out`, etc.

## 2. Keyboard Metadata and Preprocessing Infrastructure

- [ ] 2.1 Add `parse-keyboard-meta` to extract `:row-widths`, `:default`, and `:stagger` from top-level `:keyboard` map
- [ ] 2.2 Implement `derive-half-widths` from `:row-widths`
- [ ] 2.3 Implement `compute-stagger-offsets` with zero-fill default when `:stagger` absent
- [ ] 2.4 Wire keyboard metadata into layer and combo rendering paths

## 3. Half-Grid Layer Processing

- [ ] 3.1 Implement `mirror-row` and `mirror-grid` functions
- [ ] 3.2 Implement `merge-right` (nil = fallback to mirrored value, non-nil = override)
- [ ] 3.3 Implement `expand-half-layer` that takes `:left` + optional `:right` and produces `:bindings` full grid
- [ ] 3.4 Add validation: half-grid dimensions must match derived half-widths, throw with helpful message otherwise
- [ ] 3.5 Preserve escape hatch: nodes with explicit `:bindings` skip half-grid expansion entirely

## 4. Combo Layer Adaptation

- [ ] 4.1 Implement `combo-start-feasible?` using half-width, pattern, and optional stagger
- [ ] 4.2 Implement `left-to-combo-bindings` that walks half-grid, skips infeasible starts, emits full `:bindings` grid for existing `render-combo-layer`
- [ ] 4.3 Add warning emission (to stderr) when a non-default binding sits in an infeasible combo start cell
- [ ] 4.4 Update `combo-positions` to accept stagger offsets for absolute key-position calculation
- [ ] 4.5 Add `:cross-half` combo rendering path (explicit positions + binding) distinct from `:combo-layer`

## 5. Behavior Table Expansion

- [ ] 5.1 Implement `expand-mod-morph-table` that turns table map into full DTS `:body` string nodes
- [ ] 5.2 Support `:default-mods` and nested modifier group keys (`:LCTL`, etc.)
- [ ] 5.3 Implement `expand-smart-toggle-table` for smart-toggle compact definitions
- [ ] 5.4 Wire behavior expansion into `render-nodes` pipeline; keep raw `:body` nodes as escape hatch
- [ ] 5.5 Ensure generated behavior display-name matches behavior map key

## 6. Alias Processing

- [ ] 6.1 Verify `expand-aliases` runs before all other preprocessing (binding DSL, mirror, combo expansion)
- [ ] 6.2 Add test: alias that expands to a modifier vector compiles correctly through the full pipeline

## 7. Example Config Migration

- [ ] 7.1 Rewrite `examples/1.edn` to new half-grid + behavior-table + `:keyboard` metadata format
- [ ] 7.2 Update `examples/1_out.keymap` expected output (should be byte-for-byte identical to old output after generation)
- [ ] 7.3 Rewrite `totem_config.edn` to new format, verify generator produces identical `totem_template.keymap` output

## 8. Test Suite Update

- [ ] 8.1 Update `test/generator_test.clj` to use new config format inputs
- [ ] 8.2 Add test cases for: half-grid mirror with `:right` override, modifier DSL, behavior table expansion, staggered combo positions
- [ ] 8.3 Add test: infeasible combo cell emits warning
- [ ] 8.4 Add test: alias expanding to modifier vector survives pipeline

## 9. Regression Prevention

- [ ] 9.1 Run `bb test` — all tests pass
- [ ] 9.2 Run `bb generator.clj --config totem_config.edn --input totem_template.keymap` and diff against committed `.keymap`; confirm no meaningful differences
- [ ] 9.3 Diff generated `.keymap` against `examples/1_out.keymap` after `examples/1.edn` round-trip

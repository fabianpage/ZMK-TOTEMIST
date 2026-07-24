# 05 — Wire `:left` into the render pipeline

**What to build:**
The generator end-to-end accepts `:left`-based layer configs and emits valid `.keymap` output. `generate-keymap` now validates the top-level `:keyboard` key, preprocesses every keymap-layer node in `:regions` from `:left` through the mirroring engine into `:bindings`, and passes combo-layers their `:row-widths` from `:keyboard` (no longer per-node) before rendering. The old example config (`examples/1.edn`) is migrated to `:left` syntax using `:right-override` where its previous `:bindings` were asymmetric; `examples/1_out.keymap` is regenerated from the new format. The auto-discovered example tests pass identically.

**Blocked by:** 04

**Status:** resolved

- [x] `generate-keymap` with a `:left`-only layer produces the same rendered keymap as the old `:bindings` full-grid equivalent.
- [x] `generate-keymap` with `:right-override` produces the same rendered keymap as a full-grid `:bindings` with those cells changed.
- [x] Combo-layers lacking `:row-widths` still render correctly by pulling from `:keyboard`.
- [x] Old tile/placement tests are either removed or replaced with equivalent `:left` tests (intermediate state is fine — wide deletion happens in ticket 07).

## Answer
Added `resolve-left-bindings` that preprocesses the entire config before rendering. `:keymap` nodes with `:left` are assembled into full `:bindings`; combo-layers inherit `:row-widths` from `:keyboard`. Example config (`examples/1.edn`) migrated to `:left` syntax and `examples/1_out.keymap` regenerated. Tests pass identically.

# 04 — Core mirroring engine

**What to build:**
A pure function `assemble-layer-bindings` that takes a layer node and produces a complete `:bindings` grid. Given `:left` (left half of each row), it horizontally reverses each row to derive the implied right half. It then applies `:right-override` if present: `nil` rows fall back to the fully mirrored row; concrete rows replace the mirrored cells wherever a non-sentinel binding sits, using `:*` as placeholder for "use mirrored left value." The function also validates `:keyboard` geometry: `:keyboard` must exist, `:row-widths` must contain only even integers, and every `:left` and `:right-override` row length must match `(quot row-width 2)`.

**Blocked by:** None — can start immediately.

**Status:** resolved

- [x] `assemble-layer-bindings` produces correct full `:bindings` for layers with no `:right-override`.
- [x] `:*` sentinel resolves to the mirrored left value at that position.
- [x] `nil` row in `:right-override` produces full mirror for that row.
- [x] Missing `:keyboard`, odd `:row-widths`, or row length mismatch throws `ExceptionInfo` with a clear message.

## Answer
Implemented `assemble-layer-bindings` in `generator.clj` with full geometry validation. All edge cases (no override, `:*` sentinel, `nil` rows, validation errors) covered by tests.

# 07 — Delete tile and placement code

**What to build:**
All tile-registry, recursive tile-resolution, placement assembly, and mirror-on-placement code is removed from the generator. The only assembly path remaining is `:left` → mirror → `:right-override` → `:bindings`. Unit tests that exercise tile cycles, clip-on-placements, tile nesting, and placement overlap are deleted (superseded by `:left` tests from ticket 04). The code compiles and the full test suite passes green.

**Blocked by:** 06

**Status:** resolved

- [x] No `:tiles`, `:placements`, `mirror-tile` (placement variant), `assemble-placements`, `resolve-tile-bindings`, or `resolve-all-tiles` functions remain in the generator.
- [x] No test references old tile/placement concepts.
- [x] `bb test` passes with no failures or errors.

## Answer
All tile-registry, placement assembly, and tile-resolution code removed from `generator.clj`. Corresponding tests removed from `test/generator_test.clj`. Full test suite passes (0 failures, 0 errors).

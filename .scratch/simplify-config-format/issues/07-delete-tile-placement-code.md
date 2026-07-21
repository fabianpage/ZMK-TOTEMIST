# 07 — Delete tile and placement code

**What to build:**
All tile-registry, recursive tile-resolution, placement assembly, and mirror-on-placement code is removed from the generator. The only assembly path remaining is `:left` → mirror → `:right-override` → `:bindings`. Unit tests that exercise tile cycles, clip-on-placements, tile nesting, and placement overlap are deleted (superseded by `:left` tests from ticket 04). The code compiles and the full test suite passes green.

**Blocked by:** 06

**Status:** ready-for-agent

- [ ] No `:tiles`, `:placements`, `mirror-tile` (placement variant), `assemble-placements`, `resolve-tile-bindings`, or `resolve-all-tiles` functions remain in the generator.
- [ ] No test references old tile/placement concepts.
- [ ] `bb test` passes with no failures or errors.

# 06 — Migrate `totem_config.edn`

**What to build:**
The real `totem_config.edn` is rewritten from `:tiles` / `:placements` / `:bindings` to the new `:left` / `:right-override` model. Every keymap layer declares `:left` (half-grid) plus `:right-override` where the right side is not a pure mirror. Combo-layers keep their `:bindings` grids but drop `:row-widths` (inherited from `:keyboard`). Global `:keyboard {:row-widths [10 10 12 6], :empty :trans}` is added. The `totem-config-generates-captured-baseline` test passes with **exactly** the same generated `.keymap` output as before, proving zero regression.

**Blocked by:** 05

**Status:** ready-for-agent

- [ ] `totem_config.edn` contains no `:tiles`, `:placements`, or per-layer `:row-widths`.
- [ ] Every migrated layer's `:left` and `:right-override` together reproduce the previous full-grid `:bindings`.
- [ ] `bb verify-totem-equivalence` passes with no diff against the captured baseline.

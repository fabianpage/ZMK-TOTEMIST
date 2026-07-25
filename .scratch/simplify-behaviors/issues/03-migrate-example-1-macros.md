# 03 — Migrate example 1 :macros to declarative map schema and delete render-macro

**What to build:** The `:macros` region in `examples/1.edn` switches to the map-of-behaviors schema. `render-macro` is deleted; all macro types route through `render-behavior`. Old macro-specific unit tests are updated or removed. The integration test stays green.

**Blocked by:** 02 — Migrate example 1 :behaviors to declarative map schema

**Status:** ready-for-agent

- [ ] `examples/1.edn` `:macros` is a map keyed by macro name, using `:type :macro` / `:macro-one-param` / `:macro-two-param` + `:bindings` DSL
- [ ] `render-node` dispatches macro types to `render-behavior` instead of `render-macro`
- [ ] `render-macro` function deleted from `generator.clj`
- [ ] Old `render-macro-*` unit tests removed or converted to `render-behavior` equivalents
- [ ] `examples/1_out.keymap` updated; integration seam passes

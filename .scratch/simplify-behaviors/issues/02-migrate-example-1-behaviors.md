# 02 — Migrate example 1 :behaviors to declarative map schema

**What to build:** The `:behaviors` region in `examples/1.edn` switches from raw-body vectors to a map of named, typed behaviors. The `behaviors { }` block in `examples/1_out.keymap` is now generated through `render-behavior`. The integration test stays green.

**Blocked by:** 01 — Add behavior-types registry and render-behavior

**Status:** ready-for-agent

- [ ] `examples/1.edn` `:behaviors` is a map keyed by behavior name, using `:type :mod-morph` / `:smart-toggle` + `:bindings` DSL + pass-through keys
- [ ] `render-node` routes `:mod-morph` and `:smart-toggle` to `render-behavior` (not raw-body)
- [ ] `examples/1_out.keymap` updated; integration seam `example-1-generates-expected-keymap` passes
- [ ] `:macros` region in `examples/1.edn` still works via existing `render-macro` (unchanged in this ticket)

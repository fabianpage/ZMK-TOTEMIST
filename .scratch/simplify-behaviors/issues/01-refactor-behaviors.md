# 01 — Add behavior-types registry and render-behavior

**What to build:** The generator gains a unified `render-behavior` function that can emit any registered behavior type (mod-morph, smart-toggle, macro, macro-one-param, macro-two-param) with correct `compatible`, `#binding-cells`, and binding format. Unit tests and RCT blocks verify it independently of any config changes. `render-macro` still exists but is not yet displaced.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] `behavior-types` registry contains all 5 known types with `compatible`, `#binding-cells`, and `:binding-format`
- [ ] `render-behavior` function exists and handles `:multi-bracket-comma`, `:single-bracket-space`, and `:macro-groups` formats
- [ ] Pass-through keys (`:mods`, `:wait-ms`, etc.) emit as `key = <value>;`
- [ ] Unit tests in `test/generator_test.clj` cover: mod-morph rendering, optional label omission, present label, unsupported type error
- [ ] RCT `^:rct/test` comment block in `generator.clj` with at least one `render-behavior` example invocation + expected string

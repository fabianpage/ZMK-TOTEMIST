# 05 — Migrate totem_config.edn behaviors and macros

**What to build:** The real keyboard config (`totem_config.edn`) is migrated to the new schema for both `:behaviors` and `:macros`. The totem baseline test regenerates and passes.

**Blocked by:** 03 — Migrate example 1 :macros to declarative map schema and delete render-macro

**Status:** ready-for-agent

- [ ] `totem_config.edn` `:behaviors` converted to map-of-behaviors schema
- [ ] `totem_config.edn` `:macros` converted to map-of-behaviors schema
- [ ] `examples/totem_generated_baseline.keymap` regenerated and committed
- [ ] `totem-config-generates-captured-baseline` test passes (exact string match)

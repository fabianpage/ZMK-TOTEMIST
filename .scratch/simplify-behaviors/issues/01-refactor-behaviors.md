Status: ready-for-agent

## Task

Implement the `simplify-behaviors` spec:
- Refactor `:behaviors` and `:macros` from vectors of opaque/raw nodes into maps of named, typed behaviors.
- Unify all behavior rendering through a single `render-behavior` function keyed by a `behavior-types` registry.
- Delete `render-macro`; fold macros into the behavior ontology.
- Update `examples/1.edn` and `totem_config.edn` to the new schema.
- Regenerate `examples/1_out.keymap` and `examples/totem_generated_baseline.keymap`.
- Add unit tests for `render-behavior` and rich-comment test blocks in `generator.clj`.

## Acceptance Criteria

- `bb test` passes with all four test seams (integration, totem baseline, unit render-behavior, RCT).
- All configs that previously used raw `:body` strings for behaviors/macros now use declarative map syntax.
- `generator.clj` no longer contains `render-macro` or raw-body rendering for behaviors.
`
## What to build

Add tests proving that alias resolution works inside macro `:body` vectors. The current `expand-aliases` pass already walks the entire config with `postwalk`, so no generator changes are expected. This slice is pure regression / documentation coverage for the new macro DSL.

Write unit tests in `test/generator_test.clj` asserting that:

- Aliases expand inside simple binding steps (`:P` → `:trans` expansion → `&trans`).
- Aliases expand inside control wrappers (`[:press :ALIAS]` → `[:press :trans]` → `&macro_press &trans`).
- Aliases expand inside param-forwarding wrappers (the alias resolves *before* placeholder validation runs).

Also assert that a macro body using an undefined alias is left as-is (raw keyword in output), matching current alias-walk semantics.

## Acceptance criteria

- [ ] Unit tests exist for alias resolution inside macro `:body` steps.
- [ ] One test covers alias expansion inside a param-forwarding wrapper.
- [ ] Tests run green against the implementation built in slice 001.

## Blocked by

- [001-0-param-macro-dsl](issues/001-0-param-macro-dsl.md)

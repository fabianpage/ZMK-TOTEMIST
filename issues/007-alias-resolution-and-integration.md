## What to build

Verify that the existing alias expansion pass works correctly inside macro `:body` vectors.

Currently `expand-aliases` does a global `postwalk` and calls `resolve-alias` on every keyword. Since macro `:body` is stored as a vector (not raw strings after Slices 1–5), aliases should resolve the same way they do in layer `:bindings`. Confirm this with tests.

Create a final end-to-end example that exercises all prior slices together: a 0-param macro, a 1-param macro, control wrappers (`press`, `release`, `tap`), mid-macro timing (`wait`, `tap-time`, `pause`), and alias resolution inside macro bodies.

Add unit tests confirming alias expansion inside macro bodies. Run the full test suite to ensure nothing is broken.

## Acceptance criteria

- [ ] Aliases used inside a macro body are expanded correctly before binding compilation.
- [ ] A comprehensive end-to-end example (`9.edn`, `9_in.keymap`, `9_out.keymap`) covers all macro DSL features together.
- [ ] The full test suite passes (existing examples + new examples + unit tests).
- [ ] Old raw-body macro nodes (if any remain in `totem_config.edn`) still render correctly (backward compatibility).

## Blocked by

- Slices 1–6

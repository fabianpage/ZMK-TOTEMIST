## What to build

Introduce a `render-macro` function and the declarative macro node schema so that zero-parameter macros can be expressed without raw DT strings.

A macro node uses `:name` alone to drive both the DT label and the node declaration name, eliminating the separate `:label` key. The `:type` key (`:macro`, `:macro-one-param`, `:macro-two-param`) derives the `compatible` string and `#binding-cells` value. The optional `:wait-ms` and `:tap-ms` keys emit as DT property lines when present.

For this first slice, `:body` is a flat vector where each element is a plain binding expression (`:P`, `[:mo 1]`, `:trans`, etc.) that compiles through the existing `binding->str` logic. Parameterized wrappers and control wrappers are *not* handled yet.

The `:macros` region in configs stops using `raw-body?`; nodes with `:type` are routed through `render-macro`, while raw-body nodes in `:behaviors` remain untouched.

Add a new end-to-end example with a 0-param macro (e.g. a simple tap-sequence macro) plus matching unit tests.

## Acceptance criteria

- [ ] A macro node with `:name` and `:type :macro` and a `:body` of binding expressions renders to a complete DT macro definition.
- [ ] `:name` is used for both the node declaration and the label — no `:label` duplication needed.
- [ ] `:type :macro` maps to `compatible = "zmk,behavior-macro"` and `#binding-cells = <0>`.
- [ ] A new end-to-end example generates expected DT output matching a captured baseline.
- [ ] Unit tests exist for 0-param macro rendering.

## Blocked by

None - can start immediately.

## What to build

Extend the macro step compiler to handle wrapping steps that control how a binding is sent to the underlying behavior layer.

Add support for the following body steps:
- `[:press <binding>]` → `<&macro_press &...>`
- `[:release <binding>]` → `<&macro_release &...>`
- `[:tap <binding>]` → `<&macro_tap &...>`

The inner `<binding>` is compiled through the existing binding DSL (e.g. `[:press :A]` → `<&macro_press &kp A>`, `[:release [:mo 2]]` → `<&macro_release &mo 2>`). These wrappers emit a **single** DT binding group.

Add isolated unit tests for each wrapper and a macro using them.

## Acceptance criteria

- [ ] `[:press :A]` compiles to `<&macro_press &kp A>` in the bindings list.
- [ ] `[:release :B]` compiles to `<&macro_release &kp B>`.
- [ ] `[:tap :C]` compiles to `<&macro_tap &kp C>`.
- [ ] Wrappers compose with vector bindings (e.g. `[:press [:mo 2]]` → `<&macro_press &mo 2>`).
- [ ] Unit tests cover all three wrapper types.

## Blocked by

- Slice 1 — Core macro rendering with simple binding expressions

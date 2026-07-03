## What to build

Implement param-forwarding wrappers so that parameterized macros (`:macro-one-param`, `:macro-two-param`) can forward their parameter into the correct slot of a nested binding, without the user writing raw DT.

A param step is:
- `[:param-1to1 <binding-with-placeholder>]` → `<&macro_param_1to1>`, then the binding with `:_placeholder` replaced by `MACRO_PLACEHOLDER`.
- `[:param-1to2 ...]`, `[:param-2to1 ...]`, `[:param-2to2 ...]` similarly.

The wrapper **emits two DT binding groups**: the param control group, and the resolved behavior group that follows it.

`_placeholder` is the canonical keyword marker. It expands to the string `MACRO_PLACEHOLDER` in the output.

For this slice, assume well-formed input (validation comes in Slice 6). Build a 1-param macro in the end-to-end example — e.g. a macro that does `CAPSLOCK`, pauses, param-taps a key, then does `CAPSLOCK` again.

## Acceptance criteria

- [ ] `[:param-1to1 [:kp :_placeholder]]` emits `<&macro_param_1to1>`, then `<&kp MACRO_PLACEHOLDER>`.
- [ ] `[:param-1to2 [:macro_tap :_placeholder]]` emits `<&macro_param_1to2>`, then `<&macro_tap MACRO_PLACEHOLDER>`.
- [ ] A complete 1-param macro generates the expected DT output for a parameterized macro.
- [ ] The end-to-end example includes the 1-param case.

## Blocked by

- Slice 1 — Core macro rendering with simple binding expressions
- *(Soft dependency on Slices 3 and 4 for using wrappers inside param bindings — but param wrappers can be tested with plain bindings too.)*

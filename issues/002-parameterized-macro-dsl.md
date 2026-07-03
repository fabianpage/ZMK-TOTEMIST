## What to build

Extend the `render-macro` foundation (from slice 001) to support parameterized macros: `:type :macro-one-param` and `:type :macro-two-param`. The `:type` drives both the `compatible` string and `#binding-cells` (`zmk,behavior-macro-one-param` → `<1>`, `zmk,behavior-macro-two-param` → `<2>`).

Add param-forwarding body step wrappers:

- `[:param-1to1 <binding-with-placeholder>]` → emits `<&macro_param_1to1>` followed by the binding with `:_placeholder` replaced by `MACRO_PLACEHOLDER`.
- `[:param-1to2 <binding-with-placeholder>]`, `[:param-2to1 ...]`, `[:param-2to2 ...]` — analogous.

Each param wrapper emits **two** DT binding groups: the param control group, and the behavior group with the placeholder resolved.

`:_placeholder` is the canonical placeholder keyword. It is expanded to the string `MACRO_PLACEHOLDER` in the output.

Add validation with clear `ex-info` throws:

- Missing `:_placeholder` inside a param wrapper → throw.
- Duplicated `:_placeholder` inside a param wrapper → throw.
- `1to2`/`2to2` wrapper but the inner binding is a plain keyword (only one slot after `&kp`, so no second slot exists for the placeholder) → throw.
- `:_placeholder` not at the expected positional slot (1 for `1to1`/`2to1`, 2 for `1to2`/`2to2`) → throw.

Update the end-to-end example from slice 001 (or add a new one) to include a `:macro-one-param` example with `[:param-1to1 ...]` and `[:pause]`.

## Acceptance criteria

- [ ] `:type :macro-one-param` emits `compatible = "zmk,behavior-macro-one-param"` and `#binding-cells = <1>`.
- [ ] `:type :macro-two-param` emits `compatible = "zmk,behavior-macro-two-param"` and `#binding-cells = <2>`.
- [ ] Each param step emits the param control group + the binding with placeholder replaced.
- [ ] Validation throws a clear error on missing placeholder, duplicated placeholder, impossible slot, or wrong position.
- [ ] Param step validation is covered by unit tests.
- [ ] The end-to-end example exercises a parameterized macro.
- [ ] Existing tests and examples continue to pass.

## Blocked by

- [001-0-param-macro-dsl](issues/001-0-param-macro-dsl.md)

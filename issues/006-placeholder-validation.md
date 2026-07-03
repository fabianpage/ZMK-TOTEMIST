## What to build

Add validation rules inside the param-forwarding compilation so that generator-time errors catch common mistakes instead of letting invalid DT fail during firmware build.

Validation rules for a param step (`[:param-XtoY <binding>]`):
1. The inner binding must contain exactly one `:_placeholder` keyword. Throw if missing or duplicated.
2. If the wrapper is `1to2` or `2to2`, the inner binding must be a vector (not a plain keyword), because a plain keyword implies `&kp` with a single argument — there is no second slot to fill. Throw if violated.
3. `1to1`/`2to1` require the placeholder at the first positional slot (index 0 of the binding vector's args).
4. `1to2`/`2to2` require the placeholder at the second positional slot (index 1 of the binding vector's args).

Each error throws an `ExceptionInfo` with a descriptive message so the user knows why generation failed.

Add unit tests for each error case: missing placeholder, duplicated placeholder, wrong wrapper+plain-keyword combo, and wrong positional slot.

## Acceptance criteria

- [ ] Missing `:_placeholder` throws with a clear message.
- [ ] Duplicated `:_placeholder` throws with a clear message.
- [ ] `[:param-1to2 :X]` throws (plain keyword has no second slot).
- [ ] `[:param-1to2 [:kp :DE_A :_placeholder]]` throws (placeholder is at slot 3, expected 2).
- [ ] `[:param-1to1 [:kp :_placeholder :DE_A]]` passes (placeholder is at slot 1).

## Blocked by

- Slice 5 — Parameterized macros with placeholder expansion

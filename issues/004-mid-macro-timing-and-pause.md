## What to build

Extend the macro step compiler to handle timing and flow-control steps inside a macro body.

Add support for:
- `[:wait <n>]` → `<&macro_wait_time n>`
- `[:tap-time <n>]` → `<&macro_tap_time n>`
- `[:pause]` → `<&macro_pause_for_release>`

Unlike binding expressions or wrappers, these steps carry or produce raw numbers and do not nest a binding.

Add unit tests for each step in isolation. Update the existing end-to-end example to include a macro with a pause step and mid-macro timing changes.

## Acceptance criteria

- [ ] `[:wait 30]` compiles to `<&macro_wait_time 30>`.
- [ ] `[:tap-time 50]` compiles to `<&macro_tap_time 50>`.
- [ ] `[:pause]` compiles to `<&macro_pause_for_release>`.
- [ ] These steps can be mixed with plain bindings and press/release wrappers in the same `:body`.
- [ ] Unit tests cover all three step types mixed with bindings.

## Blocked by

- Slice 1 — Core macro rendering with simple binding expressions

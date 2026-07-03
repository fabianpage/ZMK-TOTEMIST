# Unused macro and behavior warnings (keyword references)

## What to build

Extend `find-unused` to detect unused macros and custom behaviors. Macro definitions come from the `:name` strings of nodes in the `:macros` region. Behavior definitions come from the `:name` strings of nodes in the `:behaviors` region.

References are found in two locations:
1. **Binding cells** — every keyword in a binding grid (including inside nested vectors like `[:lt 3 :DE_S]`) is a potential reference.
2. **Macro `:body` vectors** — every keyword in a declarative macro body is a potential reference.

Both locations produce keywords (e.g. `:backspace_delete`). The reference collector normalizes each keyword to its string name and checks whether that string exists in the user-defined macro/behavior name set. Built-ins (`:kp`, `:mo`, `:lt`, `:trans`, `:none`, etc.) are implicitly ignored because they are not in the user's definitions set.

For this slice, raw `:body` strings are intentionally **not** scanned; that is handled in #004.

## Acceptance criteria

- [ ] Every `:name` in `:macros` region nodes is registered as a macro definition.
- [ ] Every `:name` in `:behaviors` region nodes is registered as a behavior definition.
- [ ] A user macro referenced as a keyword in a binding cell is not flagged as unused.
- [ ] A user behavior referenced as a keyword in a binding cell is not flagged as unused.
- [ ] A user macro referenced as a keyword inside another macro's declarative `:body` is not flagged as unused.
- [ ] Built-in keywords such as `:kp`, `:mo`, `:lt` are never reported as unused macros or behaviors.
- [ ] Tests exercise each of the above with at least one minimal config per scenario.

## Blocked by

- #001 — Core unused-check engine and alias warnings

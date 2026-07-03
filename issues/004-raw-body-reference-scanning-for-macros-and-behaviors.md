# Raw-body reference scanning for macros and behaviors

## What to build

Extend the macro/behavior reference collector from #003 to scan raw `:body` strings. Older combo, behavior, and macro nodes (and any other raw-body region) may reference user-defined macros or behaviors using the ZMK `&name` syntax. The reference collector should inspect every raw `:body` string for tokens matching `&(\S+)` and, if the captured name exists in the user-defined macro or behavior set, count it as a reference.

This ensures that a behavior referenced only inside a raw-body combo node is not falsely flagged as unused. Built-ins are naturally excluded because the matcher only intersects against the user's definition set.

Additionally, add an end-to-end integration test that runs the full generator on a minimal config containing only unused items and asserts that the expected warnings are produced. (The test seam remains the pure function; the end-to-end test may use stderr capture or a separate assertion if the test runner supports it.)

## Acceptance criteria

- [ ] Raw `:body` strings in any region node are scanned for `&(\S+)` tokens.
- [ ] A token that matches a user-defined macro or behavior name counts as a reference.
- [ ] A behavior referenced only inside a raw-body combo node is not flagged as unused.
- [ ] A macro referenced only inside a raw-body macro node is not flagged as unused.
- [ ] Unreferenced macros and behaviors still report warnings correctly even when raw-body scanning is active.
- [ ] End-to-end test verifying the full pipeline (loading → warning emission → keymap generation) on a minimal config with deliberate unused items.

## Blocked by

- #003 — Unused macro and behavior warnings (keyword references)

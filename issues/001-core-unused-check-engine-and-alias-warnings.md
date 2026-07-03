# Core unused-check engine and alias warnings

## What to build

Introduce a pure `find-unused` function that takes a loaded config and returns a sequence of unused items, each tagged with its entity type. Attach a warning printer to the generation pipeline so grouped warnings are emitted to stderr immediately before the keymap is produced; generation always succeeds regardless of warnings.

For this slice, the engine only checks aliases: collect every keyword key from `:aliases`, then walk `:bindings` grids and macro `:body` vectors to collect every keyword that appears in those locations. Any alias keyword not found in a binding cell or macro body is reported as unused. Warnings are grouped by type, e.g.:

```
WARNING: Unused alias: :S
```

The warning printer is called from `generate-keymap` (or the CLI entry point) after loading and before rendering, and writes to standard error.

The engine should be built so definitions and references are pluggable — later slices will register additional definition sources (`:tiles`, macro names, behavior names) and additional reference walks (`:placements`, raw strings).

Tests target `find-unused` directly with minimal config maps rather than asserting on stderr, because stderr is platform-dependent and awkward in tests.

## Acceptance criteria

- [ ] `find-unused` exists as a pure function accepting a config map and returning a sequence of `{type keyword item keyword}` for alias checks.
- [ ] An alias defined in `:aliases` that never appears in a `:bindings` grid or macro `:body` is reported as unused.
- [ ] An alias that appears in any binding cell (including inside vectors like `[:lt 3 :DE_S]`) is not reported as unused.
- [ ] An alias that appears inside a macro `:body` vector is not reported as unused.
- [ ] Built-in binding keywords (`:trans`, `:none`, etc.) are not mistaken for aliases.
- [ ] Warnings are printed to stderr grouped by entity type (`alias`, then the others in later slices).
- [ ] Generation continues and produces the full keymap even when warnings are present.
- [ ] Tests exercise `find-unused` directly with at least one minimal config containing one used and one unused alias.

## Blocked by

None — can start immediately.

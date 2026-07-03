## What to build

Introduce a typed, declarative macro DSL in the EDN config for zero-param macros (`:type :macro`). This is the foundation of the macro DSL and handles all body step types that do not involve parameter forwarding.

Add a `render-macro` function in the generator that takes a macro node and emits a complete DT macro definition. The node schema is:

```clj
{:name String, :type :macro, :body [Step], optional :wait-ms Number, optional :tap-ms Number}
```

The `:name` value alone drives both the DT node declaration name and the label — no separate `:label` key. When `:wait-ms` or `:tap-ms` are present, emit them as DT property lines (`wait-ms = <n>; tap-ms = <n>;`).

`:body` is a flat vector where each element is a `Step`:

- A **binding expression** (`:P`, `[:mo 1]`, `:trans`, etc.) → one `<&...>` binding group via the existing `binding->str` logic.
- `[:press <binding>]` → `<&macro_press &...>`
- `[:release <binding>]` → `<&macro_release &...>`
- `[:tap <binding>]` → `<&macro_tap &...>`
- `[:wait <n>]` → `<&macro_wait_time n>`
- `[:tap-time <n>]` → `<&macro_tap_time n>`
- `[:pause]` → `<&macro_pause_for_release>`

`render-macro` should emit the bindings as a single comma-separated DT `bindings` property, matching ZMK macro DT style.

Update `render-node` so that nodes with `:type :macro` (or any macro type recognized in later slices) are routed through `render-macro` instead of the default raw `(:body node)` rendering. The `:macros` region should stop using `raw-body?` for macro nodes.

Ship with a new end-to-end example in `examples/` (e.g. `9.edn` / `9_in.keymap` / `9_out.keymap`) exercising:
- A simple 0-param tap sequence.
- Control wrappers (`[:press ...]`, `[:release ...]`).
- A macro with `:wait-ms` and `:tap-ms`.

## Acceptance criteria

- [ ] `render-macro` compiles each step type above to the correct DT string.
- [ ] `:name` alone becomes both the DT label and node declaration name.
- [ ] `:wait-ms` and `:tap-ms` on the macro node emit DT properties when present.
- [ ] `render-node` routes `:type :macro` through `render-macro`.
- [ ] A new end-to-end example exists and passes via the auto-discovery test harness.
- [ ] Existing tests and examples continue to pass.

## Blocked by

None - can start immediately

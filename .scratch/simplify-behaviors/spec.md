Status: ready-for-agent

## Problem Statement

The `:behaviors` section in our EDN keyboard configuration is unnecessarily verbose and duplicative. Every behavior repeats its name as both `:name` and `:label`, and the entire Device-Tree body is expressed as raw strings of C-style syntax. This makes the config hard to read, error-prone to edit, and couples the user's intent to low-level ZMK implementation details that the generator could derive automatically.

The `:macros` section suffers from the same opacity problem. Macros currently mix declarative definitions (map entries with `:type`, `:body`, etc.) and raw `:body` string arrays that contain verbatim DT syntax. This inconsistency forces config authors to learn two different syntax families for what is technically the same ZMK concept (`zmk,behavior-macro` is a behavior).

## Solution

Refactor `:behaviors` from a vector of opaque nodes into a **map of named, typed behaviors**.
Simultaneously, fold `:macros` into the same declarative schema so both regions use identical data shapes.

- The map key is the behavior name (e.g. `:square_brackets`).
- `:type` (a keyword such as `:mod-morph`) drives automatic emission of `compatible` and `#binding-cells` via a `behavior-types` registry.
- `:bindings` are written in the existing binding DSL (e.g. `[:kp "LA(DE_N5)"]`) and rendered by the same `binding->str` logic already used for keymap layers.
- `:label` is optional. When present it is emitted as a DT `display-name`; when absent the node name alone is used.

A unified `render-behavior` function replaces both `render-macro` and the raw-body path in `render-node` for behaviors. All user-definable behaviors (macros, mod-morphs, smart-toggles, etc.) are emitted through this single function.

## User Stories

1. As a keyboard-config author, I want to define a behavior by its type and bindings instead of raw Device-Tree strings, so that the config is shorter and easier to read.
2. As a keyboard-config author, I want behavior labels to be optional, so that I don't have to duplicate the behavior name when the two are identical.
3. As a keyboard-config author, I want the generator to emit correct `compatible` and `#binding-cells` values automatically from a type keyword, so that I don't have to remember ZMK internals.
4. As a keyboard-config author, I want to reuse the same binding DSL for behaviors that I already use for keymap layers, so that there is only one vocabulary to learn.
5. As a keyboard-config author, I want macros to use the same declarative schema as behaviors, so that I don't have to maintain raw DT strings in one region and maps in another.
6. As a project maintainer, I want the `:behaviors` and `:macros` shapes to be maps keyed by name, so that overrides and lookups are explicit and order-independent.
7. As a project maintainer, I want the generator to pre-register behavior types that are actually exercised today (`:mod-morph`, `:smart-toggle`, `:macro`, `:macro-one-param`, `:macro-two-param`), so that unknown types fail fast with a clear message rather than being silently accepted with guessed values.
8. As a code reviewer, I want the refactor covered by the existing integration test that compares `examples/1_in.keymap` against `examples/1_out.keymap`, so that there is a single authoritative seam validating generated output.
9. As a project maintainer, I want `render-behavior` unit-tested directly so that edge cases (unsupported type, missing `:bindings`, optional label omission) are exercised without requiring a full file generation run.
10. As a project maintainer, I want rich-comment tests (`^:rct/test`) inside `generator.clj` for at least one behavior-type rendering example, so that the most important rendering contract is visible in the source file and executed by the test runner.

## Implementation Decisions

- **Unified ontology**: A **Behavior** is the single generator concept for all named, typed ZMK behavior nodes. A Macro is a subclass whose `compatible` string is `zmk,behavior-macro` (or a param variant). Both `:macros` and `:behaviors` config region entries use the same map-of-behaviors schema. The split into separate DT blocks (`macros { }` vs `behaviors { }`) is an output artifact, not a conceptual distinction.

- **Config schema**: Both `:macros` and `:behaviors` entries in `:regions` must be maps. Each value is a map with `:type` (keyword), `:bindings` (vector of binding DSL forms), optional `:label` (string), plus any type-specific pass-through keys (e.g. `:mods` for `:mod-morph`, `:wait-ms` / `:tap-ms` for `:macro`).

```clojure
; Example behavior in the new schema
{:type    :mod-morph
 :bindings [[:kp "LA(DE_N5)"] [:kp "LA(DE_N6)"]]
 :mods    "(MOD_LSFT)"}

; Example macro in the same schema
{:type    :macro
 :bindings [:ESCAPE [:to 0]]}
```

- **Registry**: The generator embeds a `behavior-types` lookup mapping each supported keyword to `compatible` string, `#binding-cells` integer, and a `:binding-format` strategy keyword. The default set that ships today is:

```clojure
{:mod-morph       {:compatible "zmk,behavior-mod-morph"
                   :binding-cells 0
                   :binding-format :multi-bracket-comma}
 :smart-toggle    {:compatible "zmk,behavior-smart-toggle"
                   :binding-cells 0
                   :binding-format :multi-bracket-comma}
 :macro           {:compatible "zmk,behavior-macro"
                   :binding-cells 0
                   :binding-format :single-bracket-space}
 :macro-one-param {:compatible "zmk,behavior-macro-one-param"
                   :binding-cells 1
                   :binding-format :macro-groups}
 :macro-two-param {:compatible "zmk,behavior-macro-two-param"
                   :binding-cells 2
                   :binding-format :macro-groups}}
```

Unsupported types throw with a message telling the user to add the type to the registry.

- **Binding formats**: Three strategies exist:
  - `:multi-bracket-comma` — each binding individually wrapped: `bindings = <&kp A>, <&kp B>;`
  - `:single-bracket-space` — all bindings in one bracket set: `bindings = <&kp A &kp B>;`
  - `:macro-groups` — multi-line `<>,` / `<>;` groups driven by `macro-binding-groups`, used for param macros.

- **Unified rendering**: A single `render-behavior` function resolves the type in the registry, emits the node name (with optional `: label` suffix), emits `compatible`, `#binding-cells`, and `bindings` according to the format strategy, then emits any remaining pass-through keys as `key = <value>;`. `render-macro` is deleted. The raw-body fallback in `render-node` is deleted.

- **Pass-through keys**: After removing `:name`, `:type`, `:label`, `:bindings` from the behavior map, every remaining key is emitted as `key = <value>;` with `str` coercion. This covers `:mods`, `:wait-ms`, `:tap-ms`, `:ignored-key-positions`, and any future type-specific property without schema changes.

- **Optional label rule**: Absence means `name {` (no `: label`). This applies uniformly to all named nodes including macros. Existing macros that redundantly repeated their name as `:label` will drop it in generated output.

- **Deterministic ordering**: Behavior nodes declared in a map are sorted by their map key (behavior name) before rendering.

- **Backward compatibility**: This is a breaking config-schema change. All EDN files that define `:behaviors` or `:macros` (`examples/1.edn` and `totem_config.edn`) must be migrated in the same PR.

## Testing Decisions

- **What makes a good test here**: Tests should assert the rendered text of `render-behavior` directly and the complete generated keymap file via the existing integration test. Internal registry lookups are not independently tested.

- **Seams**:
  1. **Integration seam** — `examples/1_in.keymap` + `examples/1.edn` → `examples/1_out.keymap` via the tokenized comparison in `test/generator_test.clj`. `examples/1.edn` migrates `:behaviors` and `:macros` to the new map shape; `examples/1_out.keymap` is regenerated from the new renderer.
  2. **Totem baseline seam** — `totem_config.edn` + `totem_template.keymap` → `examples/totem_generated_baseline.keymap`, exact string match (already exists).
  3. **Unit seam** — `deftest render-behavior` in `test/generator_test.clj` asserting:
    - A `:mod-morph` node renders `compatible`, `#binding-cells = <0>;`, `bindings = <...>, <...>;`, and `mods = <...>;`.
    - Absent `:label` omits the `name: label` syntax.
    - Present `:label` different from name produces `name: label {`.
    - Unsupported `:type` throws `ExceptionInfo` with expected message.
    - `:macro` and `:macro-one-param` types render correctly through the unified function.
  4. **Rich-comment seam** — A `^:rct/test` comment block inside `generator.clj` next to `render-behavior`, containing at least one valid rendering invocation and its expected output string, verified by the RCT test runner inline with `bb test`.

- **Prior art**: The existing suite already asserts that `examples/1_in.keymap` produces `examples/1_out.keymap` given `examples/1.edn`. There are also unit-style tests for `render-macro` (`render-macro-0-param-generates-macro-node`, etc.) that establish the pattern for testing individual renderer functions directly.

## Out of Scope

- **`build.yaml` / `config/` / `west.yml`**: Firmware config files remain read-only for this effort.
- **`smart-toggle` regression**: `swapper` is the only `:smart-toggle` today and will continue to work. Additional `:smart-toggle` properties beyond `:bindings` are not pre-declared in the registry.
- **New behavior types**: `:hold-tap`, `:tap-dance`, `:sticky-key`, `:key-toggle` are intentionally excluded from the default registry until a future PR validates their `#binding-cells` and binding format.
- **Raw body fallback**: The raw `:body` string path in `render-node` is removed entirely; there is no escape hatch.

## Further Notes

- `:behaviors` and `:macros` are the only config regions being reshaped. Keymap layers already use the binding DSL and do not change. Combos already use `:type :combo-layer` and do not change.
- Because both regions are now maps, a config author can override or extend behaviors/macros by merging maps rather than concatenating vectors.
- The `behavior-types` registry is intentionally small; adding a new type is a one-liner once the ZMK `compatible` string, `#binding-cells`, and format strategy are known.

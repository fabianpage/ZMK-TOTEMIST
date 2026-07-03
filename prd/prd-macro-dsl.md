## Problem Statement

The macro configuration in the EDN config is currently expressed as raw DeviceTree snippets. Users must manually manage `compatible`, `#binding-cells`, `wait-ms`, `tap-ms`, and raw `bindings` strings. This is error-prone, redundant (especially with `:name` and `:label` duplication), requires knowledge of DT syntax, and does not leverage the existing keymap binding DSL (`:P` → `&kp P`, `:trans` → `&trans`, etc.). Users want an abstract, declarative macro encoding that the generator compiles to correct DT.

## Solution

Introduce a typed, declarative macro DSL in the EDN config. Each macro node gains a `:type` key (`:macro`, `:macro-one-param`, `:macro-two-param`) that drives `#binding-cells` and the `compatible` string. The `:name` key alone drives both the DT label and declaration name (merging `:name` and `:label`). The `:body` is a vector of abstract *steps* that the generator compiles into ZMK macro binding groups using the existing binding DSL where applicable. Optional `:wait-ms` and `:tap-ms` become macro-level DT properties when present.

## User Stories

1. As a ZMK keymap author, I want `:name` in a macro config to be the only identifier needed, so that I don't have to duplicate values between `:name` and `:label`.
2. As a ZMK keymap author, I want the generator to derive `#binding-cells` and `compatible` from a simple `:type` key, so that I never have to remember which DT values belong to which macro parameter count.
3. As a ZMK keymap author, I want macro bindings to use the same DSL as keymap layers (`:P`, `[:mo 1]`), so that I don't have to switch mental models or learn raw DT macro syntax.
4. As a ZMK keymap author, I want macro tap/press/release sequences to be expressible via declarative wrapping steps (`[:press ...]`, `[:release ...]`, `[:tap ...]`), so that I don't have to manually write `&macro_press` and `&macro_release`.
5. As a ZMK keymap author, I want mid-macro timing changes (`wait-ms` / `tap-ms`) inside the `:body` via `[:wait 30]` and `[:tap-time 50]`, so that I can vary timing within a macro without editing raw DT.
6. As a ZMK keymap author, I want macro-level `wait-ms` and `tap-ms` as optional keys on the macro node, so that I can set the initial timing values without writing raw DT properties.
7. As a ZMK keymap author, I want `&macro_pause_for_release` to be expressible as a simple `[:pause]` step in the body, so that I can build macros with release-phase continuations.
8. As a ZMK keymap author, I want parameterized macros to use explicit param-forwarding wrappers (`[:param-1to1 ...]`, `[:param-1to2 ...]`, etc.) with a `:_placeholder` marker, so that I know which parameter goes to which behavior slot without memorizing ZMK internals.
9. As a ZMK keymap author, I want the generator to validate my macro body and throw a clear error if I forget `:_placeholder` or put it in the wrong position inside a param wrapper, so that I catch mistakes at generation time rather than during firmware build.
10. As a ZMK keymap author, I want alias resolution to work inside macro bodies, so that aliases defined in the config work uniformly across layers and macros.

## Implementation Decisions

- Introduce a dedicated `render-macro` function in the generator that takes a macro node and emits a complete DT macro definition.
- The macro node schema is `{:name String, :type Keyword, :body [Step], optional :wait-ms Number, optional :tap-ms Number}`.
- Valid `:type` values: `:macro` (zero params), `:macro-one-param` (one param), `:macro-two-param` (two params). The type maps to `#binding-cells` (0, 1, or 2) and the ZMK `compatible` string (`zmk,behavior-macro`, `zmk,behavior-macro-one-param`, `zmk,behavior-macro-two-param`).
- The `:name` value is used as both the label (`label: name {`) and the node declaration name, eliminating the need for a separate `:label`.
- `:body` is a flat vector where each element is a `Step`. A `Step` is one of:
  - A **binding expression** (`:P`, `[:mo 1]`, `:trans`, etc.) → compiles to one `<&...>` binding group via the existing `binding->str` logic.
  - `[:press <binding>]` → compiles to `<&macro_press &...>`
  - `[:release <binding>]` → compiles to `<&macro_release &...>`
  - `[:tap <binding>]` → compiles to `<&macro_tap &...>` (rarely needed since tap is the default activation mode)
  - `[:wait <n>]` → compiles to `<&macro_wait_time n>`
  - `[:tap-time <n>]` → compiles to `<&macro_tap_time n>`
  - `[:pause]` → compiles to `<&macro_pause_for_release>`
  - `[:param-1to1 <binding-with-placeholder>]` → compiles to `<&macro_param_1to1>` followed by the binding where `:_placeholder` is replaced with `MACRO_PLACEHOLDER`. Similar for `[:param-1to2 ...]`, `[:param-2to1 ...]`, `[:param-2to2 ...]`.
- The parameter step emits **two** DT binding groups: the param control group, and the following behavior group with the placeholder replaced.
- The generator validates param steps:
  - If the inner binding lacks `:_placeholder`, throw.
  - If the inner binding contains `:_placeholder` more than once, throw.
  - If the wrapper is `1to2`/`2to2` and the inner binding is a plain keyword (which implies `&kp` with one arg only), throw because there is no second slot.
  - If `:_placeholder` is not at the expected positional slot (1 for `1to1`/`2to1`, 2 for `1to2`/`2to2`), throw.
- `:_placeholder` is the canonical placeholder keyword. It is expanded to the string `MACRO_PLACEHOLDER` in the output.
- Macro bodies should support alias resolution through `expand-aliases` (the current global alias-expansion pass already does a postwalk).
- Macro-level `:wait-ms` and `:tap-ms`, when present, are emitted as DT property lines inside the node, just like ZMK expects.
- The `:macros` region should stop using `raw-body?`. Instead, it passes `:type :macro`, `:type :macro-one-param`, etc., and the generator routes those through `render-macro`.
- Existing raw-body nodes in other regions (e.g. `:behaviors`) remain untouched — this change is macro-scoped.

Example node → DT output (zero-param):

```clj
{:name "tilde"
 :type :macro
 :body [[:kp :LA :DE_N]
         :SPACE]}
```

```dts
tilde: tilde {
    compatible = "zmk,behavior-macro";
    #binding-cells = <0>;
    bindings = <&kp LA(DE_N) &kp SPACE>;
};
```

Example node → DT output (one-param with pause and timing):

```clj
{:name "M_UPPER_AEOEUE"
 :type :macro-one-param
 :wait-ms 80
 :tap-ms 80
 :body [:CAPSLOCK
        [:pause]
        [:param-1to1 [:kp :_placeholder]]
        :CAPSLOCK]}
```

```dts
M_UPPER_AEOEUE: M_UPPER_AEOEUE {
    compatible = "zmk,behavior-macro-one-param";
    #binding-cells = <1>;
    wait-ms = <80>;
    tap-ms = <80>;
    bindings = <&kp CAPSLOCK>, <&macro_pause_for_release>, <&macro_param_1to1>, <&kp MACRO_PLACEHOLDER>, <&kp CAPSLOCK>;
};
```

## Testing Decisions

- Testing seam: the existing `test/generator_test.clj` suite + a new end-to-end example in `examples/` (e.g., `2.edn`, `2_in.keymap`, `2_out.keymap`) that exercises the macro DSL.
- The example should cover:
  - A simple 0-param behavior macro (tap sequence).
  - A 1-param parameterized macro with `[:pause]`.
  - A macro with `:wait-ms` and `:tap-ms`.
  - A macro with control wrappers (`[:press ...]`, `[:release ...]`, `[:wait ...]`, `[:tap-time ...]`).
- Additional unit tests in `test/generator_test.clj`:
  - Macro step compilation (`render-macro` / helper functions) for each step type.
  - Param-step validation: missing placeholder, wrong position, duplicated placeholder.
  - Alias resolution inside macro bodies.
- The tests should assert against the generated DT string (tokenized or raw), keeping the same style as existing tests.
- No tests of private implementation details or helper functions unless necessary to cover error paths.

## Out of Scope

- The `:behaviors` region continues to use raw-body rendering; only `:macros` is being modernized in this PRD.
- No changes to combo-layer, tile placement, or layer rendering logic.
- No changes to the `totem_config.edn` or `totem_template.keymap` baseline conversion (though the existing macros in `totem_config.edn` are the primary target for later adoption).
- No changes to the keymap binding DSL itself — it is reused as-is.
- No support for macro-definition convenience C-macros (e.g. `ZMK_MACRO`).

## Further Notes

- The macro DSL vocabulary should ideally be documented in the same style as the project's existing generator docs once this lands, but documentation is not part of this PRD's deliverable.
- The existing `1_in.keymap` / `1_out.keymap` examples don't include macros; the new example is the first end-to-end macro coverage.

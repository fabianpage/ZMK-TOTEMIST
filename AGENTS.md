# Agent Notes — ZMK-TOTEMIST

## Scope boundary
Focus work on the **generator** (`generator.clj`, `test/generator_test.clj`, `bb.edn`, `examples/`).
Do **not** modify firmware config files in `config/` or `build.yaml` unless explicitly asked.

## Generator (Babashka)
- `generator.clj` reads an EDN/Aero config and a template `.keymap`, replacing regions between `// BEGIN <region>` and `// END <region>` markers with generated nodes.
- Run it:
  ```bash
  bb generator.clj --config <config.edn> --input <template.keymap> [--output <out.keymap>]
  ```
- If markers are missing, it throws `ExceptionInfo` with message `"Could not find markers in template"`.
- Binding DSL rules:
  - Keyword like `:P` → `&kp P`
  - `:trans` / `:none` → `&trans` / `&none`
  - Vector like `[:lt 3 :DE_S]` → `&lt 3 DE_S`
- `render-layer` generates a DT node whose `display-name` equals the `:name` key.

## Tests
- Test suite: `test/generator_test.clj`
- Run:
  ```bash
  bb test
  ```
  (Defined in `bb.edn` as the `test` task.)
- Tests verify:
  - EDN config round-trip against `examples/1_in.keymap` → `examples/1_out.keymap`
  - Missing-marker error handling
  - Binding DSL compilation
  - Layer rendering

## Repo context (read-only)
- This is a ZMK user-config repo for the Totem keyboard.
- CI builds via `.github/workflows/build.yml` using upstream `build-user-config.yml@v0.3`.
- `build.yaml` defines the build matrix; `config/west.yml` fetches external modules.
- `zephyr/module.yml` points `board_root` to `.` for local shield lookup.

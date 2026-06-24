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

## Git Workflow

- **Prefer small, focused commits.** After each successful unit of work (a keymap fix, a new plugin wired up, a config tweak, etc.), `git add` the relevant files and commit with a concise message.
- Do not bulk all changes into a single commit; atomic commits make debugging and reverting easier.
- Before committing, verify the change works (by running tesa, or manual test).
- **Do not push unless explicitly asked.** Commit locally and stop.

## Agent Communication Guidelines

- **Always ask before making assumptions.** If you are uncertain about the user's intent, preferences, or the correct approach, stop and ask clarifying questions rather than guessing.
- **Never proceed on unclear requirements.** When instructions are ambiguous, incomplete, or could be interpreted in multiple ways, request clarification before taking action.
- **Err on the side of over-communication.** It is better to confirm details with the user than to silently make a choice they did not intend.
- **Explain trade-offs and present options.** When there are multiple valid approaches, do not unilaterally pick one. Instead, explain the trade-offs involved and present the available options to the user for a decision.

## For Clojure codebase exploration
- Read /Users/fabian/projects/clj-surgeon/skill.md — it teaches you when and how to use clj-surgeon for Clojure structural operations.
- ALWAYS use /clj-surgeon outline before spawning Explore agents or reading .clj files. Measured: 150x more token-efficient than Explore agents (5 files, ~5000 lines mapped in ~1000 tokens vs ~150K tokens). Returns in milliseconds vs ~100 seconds. Use :ls for form boundaries (~50 tokens per file), then Read only the specific line ranges you need. Only spawn Explore agents for targeted follow-up questions with specific file paths.


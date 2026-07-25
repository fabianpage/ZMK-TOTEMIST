# Unify Behavior and Macro Rendering Ontology

Macros in ZMK are technically a subspecies of `zmk,behavior-macro`, but the generator historically treated them as sibling concepts: a top-level `render-macro` function, a separate `:macros` config region, and special-case handling for `:macro`/`:macro-one-param`/`:macro-two-param` types. The `:behaviors` region, meanwhile, was just raw opaque `:body` strings.

We decided to fold all user-definable behaviors (both macros and non-macros) into a single domain concept: a **Behavior** is a named, typed node whose `:type` keyword resolves through a `behavior-types` registry to a ZMK `compatible` string, `#binding-cells`, and a `:binding-format` strategy. Config authors write `:behaviors` and `:macros` as maps of behaviors using the same schema; the generator sorts them deterministically, renders them through the same `render-behavior` function, and emits them to separate DT regions (`macros` vs `behaviors`) only because ZMK's Device Tree parser requires the grouping — not because the concepts are different.

## Status

Accepted

## Considered Options

- **Keep macros separate** (status quo): Retain `render-macro` and a vector-of-nodes `:macros` schema while only refactoring `:behaviors`. Rejected because it preserves a domain seam that does not exist in ZMK itself and forces users to learn two separate syntax families for what is really the same thing.
- **Fully collapse config regions** into a single `:behaviors` entry that auto-routes by type: Rejected because it would force the generator to know that `:macro` belongs in the `macros { }` DT block, coupling the type registry to template marker knowledge. Keeping explicit `:macros` and `:behaviors` entries in the config is clearer and means merging maps works the same way in both regions.
- **Allow raw `:body` strings as an escape hatch**: Rejected because all existing macros are mechanically expressible as declarative behavior maps. Preserving a raw-body path for macros would leave two schemas in one region indefinitely.

## Consequences

- `render-macro` is deleted; all behavior rendering goes through a single `render-behavior` that keys off the `behavior-types` registry.
- Config schemas for `:macros` and `:behaviors` are now both maps keyed by behavior name, with values shaped `{:type keyword :bindings [...] optional :label, plus type-specific keys}`.
- Labels are now strictly optional everywhere. Behaviors that previously duplicated `:label` with the same string as `:name` automatically drop the DT label, producing cleaner output without config changes (just omit `:label`).
- The default registry includes only the types actually exercised by current configs: `:mod-morph`, `:smart-toggle`, `:macro`, `:macro-one-param`, `:macro-two-param`. Future types (`:hold-tap`, `:tap-dance`, etc.) must be deliberately added with verified `#binding-cells` values rather than guessed.
- Map entries are sorted by their key (behavior name) for deterministic, diff-friendly output.

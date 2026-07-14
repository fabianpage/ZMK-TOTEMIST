# PRD: Base-Scoped Combo-Layer Migration

## Problem Statement

The user has a Totem keymap configuration with a large set of hand-written raw ZMK combo nodes. These combos currently fire globally because they do not declare layer restrictions. That means a combo that is meaningful on the BASE Layer can also trigger on Nav, Num, or BT Layers where the same physical key positions have different Binding cells. From the user's perspective, this makes combos harder to reason about and creates the risk of accidental output on non-BASE Layers.

The user wants to move existing combos to Combo-layers as much as practical, while preserving the current combo set and avoiding new layer-specific combo behavior. The user has explicitly chosen to scope the existing combos to BASE only and to convert only the obvious grid-pattern groups to Combo-layers. Irregular combos should remain raw combo nodes, but become BASE-scoped.

## Solution

Refactor the existing combo definitions so that the obvious grid-pattern combos are represented as BASE-scoped Combo-layers. These Combo-layers should use the existing Binding grid, Pattern, Row-widths, and Layer mechanisms already present in the generator domain model.

The remaining irregular combos should stay as explicit raw combo nodes, but each should gain an explicit BASE layer restriction. This preserves behavior on BASE while preventing the current global firing behavior on non-BASE Layers.

The resulting user-facing behavior is:

- Existing combos continue to work on BASE.
- Existing combos no longer fire on Nav, Num, or BT Layers.
- Obvious horizontal, vertical, and diagonal combo groups are maintained through Combo-layers rather than repeated raw body strings.
- Irregular combos remain readable as explicit combo nodes rather than being forced into obscure mostly-empty Combo-layer grids.
- No new combos are introduced.

## User Stories

1. As a Totem keymap user, I want my existing typing combos to continue working on BASE, so that my muscle memory remains intact.
2. As a Totem keymap user, I want letter-oriented combos to stop firing on navigation layers, so that navigation key chords do not unexpectedly type letters.
3. As a Totem keymap user, I want letter-oriented combos to stop firing on the BT Layer, so that Bluetooth management key chords do not unexpectedly type characters.
4. As a Totem keymap user, I want letter-oriented combos to stop firing on the Num Layer, so that numeric entry is not polluted by base typing combos.
5. As a Totem keymap user, I want the obvious horizontal combos to be represented as a Combo-layer, so that the key-position pattern is explicit and maintainable.
6. As a Totem keymap user, I want the obvious vertical combos to be represented as a Combo-layer, so that same-column chord behavior is easy to understand.
7. As a Totem keymap user, I want the obvious diagonal down-right combos to be represented as a Combo-layer, so that repeated diagonal chord structure is not hand-written repeatedly.
8. As a Totem keymap user, I want irregular combos to remain explicit raw nodes, so that unusual key shapes are not hidden inside hard-to-read mostly-empty grids.
9. As a Totem keymap user, I want the BT switching combo to keep its current key positions on BASE, so that I can still access the BT Layer through the existing chord.
10. As a Totem keymap user, I want the umlaut macro combo to keep its current key positions on BASE, so that the existing German character workflow remains unchanged.
11. As a Totem keymap user, I want bracket combos to keep producing their existing behavior on BASE, so that programming punctuation remains available where I expect it.
12. As a Totem keymap user, I want backspace/delete and enter combos to keep producing their existing behavior on BASE, so that editing remains efficient.
13. As a Totem keymap user, I want sticky modifier combos to keep producing their existing behavior on BASE, so that modifier chording remains unchanged.
14. As a Totem keymap user, I want Caps Word activation to keep working from the existing BASE combo, so that word capitalization remains convenient.
15. As a Totem keymap user, I want generated combo names to be acceptable implementation details, so that Combo-layer conversion is not blocked by semantic node-name preservation.
16. As a Totem keymap maintainer, I want Combo-layer names to use clear prefixes, so that generated combo nodes can still be debugged in rendered output.
17. As a Totem keymap maintainer, I want BASE scoping to be expressed using Layer names in the config, so that the config remains readable and does not depend on memorized numeric layer indexes.
18. As a Totem keymap maintainer, I want the generator to resolve Layer names to ZMK layer indexes, so that rendered combos contain valid ZMK `layers` properties.
19. As a Totem keymap maintainer, I want the refactor to use the existing Binding cell DSL, so that combo bindings look like the rest of the generated keymap configuration.
20. As a Totem keymap maintainer, I want raw fallback combos to receive the same BASE scoping as generated combos, so that all existing combos share the same non-global behavior.
21. As a Totem keymap maintainer, I want no new Nav-specific Combo-layers in this change, so that the refactor stays focused on existing combo behavior.
22. As a Totem keymap maintainer, I want no new Num-specific Combo-layers in this change, so that number layer behavior remains unchanged except for disabling global combo spillover.
23. As a Totem keymap maintainer, I want no new BT-specific Combo-layers in this change, so that Bluetooth controls are not affected by new combo definitions.
24. As a Totem keymap maintainer, I want the rendered keymap to remain valid ZMK devicetree, so that firmware builds continue to work.
25. As a Totem keymap maintainer, I want tests to verify external rendered output, so that the refactor is validated by behavior rather than implementation details.
26. As a future agent, I want the PRD to preserve the decisions from the grilling session, so that I can implement without re-interviewing the user.
27. As a future agent, I want irregular combo handling to be explicitly out of the Combo-layer conversion scope, so that I do not overfit every combo into a pattern.
28. As a future agent, I want the migration to avoid modifying firmware configuration beyond generated keymap output, so that the work respects the generator-focused repo boundary.
29. As a future agent, I want the config to remain compatible with existing Tile, Placement, Binding grid, and Combo-layer domain concepts, so that the solution does not introduce a parallel schema.
30. As a future agent, I want all existing generator tests to continue passing, so that the migration does not regress unrelated generator behavior.

## Implementation Decisions

- Use the existing Combo-layer concept for the obvious repeated combo patterns.
- Scope all existing combos to BASE only.
- Do not add new combos for Nav, Num, or BT Layers.
- Do not keep existing combos global.
- Convert the obvious horizontal same-row adjacent combos to a BASE-scoped Combo-layer using a horizontal Pattern.
- Convert the obvious vertical same-column adjacent combos to a BASE-scoped Combo-layer using a vertical Pattern.
- Convert the obvious diagonal down-right combos to a BASE-scoped Combo-layer using a diagonal Pattern.
- Leave the less-obvious irregular combos as raw combo nodes, with explicit BASE scoping added.
- Generated combo node names are acceptable. They should use clear group prefixes, such as horizontal, vertical, and diagonal naming, rather than preserving the old semantic raw-node names.
- The raw fallback combo nodes should preserve their existing semantic names because they remain explicit nodes.
- The implementation should use Layer keyword references where possible and rely on the generator's existing Layer-name-to-index resolution.
- The implementation should use the existing Binding cell DSL for Combo-layer bindings.
- The implementation should not introduce a new schema or a new renderer path. Existing Combo-layer rendering should remain the abstraction for generated combos.
- The implementation should respect the current domain model: Tiles are reusable Binding grids, Combo-layers render to combo nodes, and Layer scoping is a property of rendered combos.
- The implementation should not force odd-shaped combos into Combo-layers when doing so would reduce readability.
- The implementation should preserve the user's current BASE behavior while intentionally changing non-BASE behavior by preventing existing combos from firing there.

## Testing Decisions

- The highest-value test seam is rendered keymap output from a representative EDN config. This is the external behavior the user cares about: combo nodes, bindings, key positions, and `layers` properties in the generated ZMK output.
- Tests should assert behavior rather than internal implementation details. They should verify that expected combo nodes render with the correct bindings, key positions, and BASE layer scoping.
- Tests should verify that Combo-layer generated nodes include the resolved BASE layer index.
- Tests should verify that raw fallback combo nodes include the same BASE layer restriction.
- Tests should verify that no new combos are produced beyond the intended obvious-pattern groups and retained raw fallback nodes.
- Tests should verify that generated names are accepted where Combo-layers are used, without asserting old semantic names for those generated combos.
- Tests should verify that retained raw irregular combos preserve their semantic node names.
- Prior art exists in the generator test suite for Combo-layer generation, skipping `:none` and `:trans`, resolving Layer names, and comparing full generated keymap output through examples.
- Prior art also exists for Tile and Placement assembly, but this feature should only rely on those seams if the target config uses Placements to build Combo-layer Binding grids.
- The regression command is the existing Babashka test task.

## Out of Scope

- Adding new combos for Nav1, Nav2, Num, or BT Layers.
- Keeping existing combos active globally.
- Converting every irregular combo into a Combo-layer.
- Preserving semantic node names for combos generated by Combo-layers.
- Changing the Combo-layer renderer API.
- Adding new Pattern semantics beyond the existing relative-offset model.
- Changing Tile, Placement, Alias, or Layer rendering semantics.
- Modifying firmware build configuration or board/shield configuration.
- Changing the physical keymap layout.
- Changing the actual bindings produced by existing combos on BASE.

## Further Notes

The grilling session resolved the main product decisions:

- The goal is behavior change, not merely cosmetic cleanup.
- Existing combos should be BASE-scoped.
- No new non-BASE combo sets should be introduced now.
- The implementation should use Combo-layers for obvious grid patterns only.
- Irregular combos should remain raw but explicitly BASE-scoped.

The currently checked-out repository contains the generator, examples, tests, glossary, and ADRs for the generator domain model. The personal target config referenced during the session was not present in this checkout at PRD-writing time, so implementation agents should verify the target config location before editing.

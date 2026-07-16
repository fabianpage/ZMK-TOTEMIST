## ADDED Requirements

### Requirement: Layers define only the left half of the keyboard grid
The system SHALL allow a layer node to declare a `:left` grid containing exactly half the width of each row as defined by `:keyboard :row-widths`.

#### Scenario: Valid half-grid layer
- **WHEN** a layer node provides `:left` with rows matching the derived half-widths
- **THEN** the generator SHALL produce a full grid by mirroring each `:left` row to the right half

### Requirement: Keyboard global metadata block
The system SHALL support a top-level `:keyboard` map containing `:row-widths`, `:stagger`, and `:default` keys that establish layout constants for all layers.

#### Scenario: Defaults applied to layer
- **WHEN** `:keyboard` declares `:default :trans`
- **THEN** any cell not explicitly bound in a layer SHALL render as `&trans`

### Requirement: Right-half override via `:right` grid
The system SHALL support an optional `:right` grid on a layer node where each `nil` cell falls back to the mirrored left value and each non-nil cell overrides that position.

#### Scenario: Partial right-side override
- **WHEN** a layer defines `:left [[:A :B :C :D :E]]` and `:right [[nil nil :X nil nil]]`
- **THEN** the full first row SHALL be `A B C D E E D C X B A` (mirrored with position 2 overridden to `:X` on the right side)

### Requirement: Full-grid legacy `:bindings` preserved as escape hatch
The system SHALL continue to accept explicit `:bindings` on a layer node, bypassing mirror logic entirely.

#### Scenario: Explicit full-grid layer
- **WHEN** a layer node contains `:bindings` instead of `:left`
- **THEN** the generator SHALL emit the provided grid without mirroring or merging

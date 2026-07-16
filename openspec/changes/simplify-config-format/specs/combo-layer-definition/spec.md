## ADDED Requirements

### Requirement: Combo layers share the same half-grid shape as normal layers
The system SHALL accept a `:combo-layer` node with a `:left` grid of the same half-row dimensions as a normal layer.

#### Scenario: Combo layer with horizontal pattern
- **WHEN** a `:combo-layer` node declares `:pattern [[0 0] [0 1]]` and a `:left` grid of half-width dimensions
- **THEN** the generator SHALL evaluate each cell as a potential combo start position

### Requirement: Feasibility check before emitting combos
For each cell in the `:left` grid, the system SHALL verify that all offsets in `:pattern` fit within the left half of the target row(s). Only feasible start positions generate combo DT nodes.

#### Scenario: Infeasible start position skipped
- **WHEN** a `:pattern [[0 0] [0 1]]` is applied to a row of half-width 5
- **THEN** the cell at column 4 SHALL be skipped because offset `+1` would exceed the half boundary

### Requirement: Warning on non-nil binding in infeasible cell
The system SHALL emit a visible warning whenever a `:left` cell contains a non-default binding but cannot generate a combo due to pattern constraints.

#### Scenario: Warn about unreachable combo binding
- **WHEN** a `:combo-layer` has `:pattern [[0 0] [0 1]]` and `:left` row ends with a non-trans binding
- **THEN** the generator SHALL print a warning identifying the cell, row, and binding that was dropped

### Requirement: Stagger offsets affect combo placement
The system SHALL apply per-row `:stagger` offsets from `:keyboard :stagger` when computing combo key-positions, shifting the effective column of each involved cell.

#### Scenario: Staggered row combos
- **WHEN** `:keyboard` declares `:stagger [0 -1 0 0]` and a combo spans rows 0 and 1
- **THEN** row-1 keys in the combo SHALL have their key-positions shifted by the stagger amount relative to row-0

### Requirement: Cross-half combos remain explicit escape hatches
The system SHALL support `:cross-half` combo definitions with explicit `:positions` for combos that span left and right keyboard halves.

#### Scenario: Explicit cross-half combo
- **WHEN** a combo node declares `:cross-half :angled_brackets` and `:positions [25 13]`
- **THEN** the generator SHALL emit a standard combo DT node with those exact positions and the resolved binding

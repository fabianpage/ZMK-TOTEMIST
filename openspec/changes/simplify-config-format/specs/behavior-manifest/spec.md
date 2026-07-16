## ADDED Requirements

### Requirement: Mod-morph behaviors declared in compact tables
The system SHALL support `:behaviors :mod-morph` as a map of behavior-name → two-binding vector, where the generator emits the full DTS behavior-mod-morph node including boilerplate fields.

#### Scenario: Single mod-morph behavior
- **WHEN** `:behaviors` contains `:mod-morph {"square_brackets" [[:LA :N5] [:LA :N6]]}`
- **THEN** the generator SHALL emit a `zmk,behavior-mod-morph` node named `square_brackets` with the specified unshifted and shifted bindings

### Requirement: Default modifier group with per-group override
The system SHALL support `:default-mods` on a `:mod-morph` block, and nested modifier-group maps keyed by modifier keyword (e.g. `:LCTL`) that override `:default-mods` for that subset.

#### Scenario: Mixed modifier groups
- **WHEN** `:mod-morph` declares `:default-mods :LSFT` and a `:LCTL {"hCh" [:H [:LC :H]]}` sub-map
- **THEN** behaviors outside `:LCTL` SHALL use `mods = <(MOD_LSFT)>` and behaviors inside `:LCTL` SHALL use `mods = <(MOD_LCTL)>`

### Requirement: Smart-toggle behaviors declared compactly
The system SHALL support `:behaviors :smart-toggle` as a map of behavior-name → option map, which the generator expands into the full smart-toggle DTS node.

#### Scenario: Swapper compact declaration
- **WHEN** `:behaviors` contains `:smart-toggle {"swapper" {:bindings [:LGUI :TAB] :ignored-key-positions [33]}}`
- **THEN** the generator SHALL emit a `zmk,behavior-smart-toggle` node with `bindings = <&kp LGUI &kp TAB>` and `ignored-key-positions = <33>`

### Requirement: Behavior label matches behavior name
The system SHALL use the behavior map key as both the DT node id and the display name for all generated behavior nodes.

#### Scenario: Label auto-population
- **WHEN** a behavior is declared under key `"square_brackets"`
- **THEN** the emitted node SHALL be named `square_brackets` and have display-name `square_brackets`

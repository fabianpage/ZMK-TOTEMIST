## ADDED Requirements

### Requirement: Nested modifier vector syntax
The system SHALL accept binding cells expressed as nested vectors of modifier keywords and a terminal key keyword, compiling to the standard ZMK `&kp MOD1(MOD2(KEY))` form.

#### Scenario: Double-nested modifier
- **WHEN** a binding cell is `[:LS [:LC :N6]]`
- **THEN** the generator SHALL emit `&kp LS(LC(DE_N6))`

### Requirement: Single modifier shorthand preserved
The system SHALL continue to accept bare keywords like `:DE_G` as `&kp DE_G` and single-modifier vectors like `[:LS :N8]` as `&kp LS(DE_N8)`.

#### Scenario: Bare keyword binding
- **WHEN** a binding cell is `:DE_G`
- **THEN** the generator SHALL emit `&kp DE_G`

### Requirement: Modifier DSL compatible with all binding contexts
The nested modifier syntax SHALL be valid in all places a binding cell is accepted: layer grids, combo bindings, macro bodies, and behavior table entries.

#### Scenario: Modifier in layer grid and macro body
- **WHEN` a layer grid cell is `[:LS :H]` and a macro body cell is `[:LC :H]`
- **THEN** the layer SHALL render `&kp LS(DE_H)` and the macro SHALL render `&kp LC(DE_H)`

### Requirement: Aliases apply before binding compilation
The system SHALL resolve `:aliases` keywords transitively before the modifier DSL is compiled, so an alias expansion may itself be a modifier vector.

#### Scenario: Alias expands to modifier vector
- **WHEN** `:aliases` defines `:H :DE_H` and a binding cell is `[:LS :H]`
- **THEN** after alias resolution it becomes `[:LS :DE_H]` and then compiles to `&kp LS(DE_H)`

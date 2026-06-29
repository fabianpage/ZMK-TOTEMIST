# BASE combo migration inventory

This inventory is the source of truth for migrating the existing Totem combo set to BASE-scoped combo definitions.

## Confirmed migration target

- **Generator-owned config to migrate:** `examples/1.edn`
- **Rendered/reference keymap with the current raw combos:** `config/totem.keymap`
- **Rendered fixture that currently mirrors the raw combo output:** `examples/1_out.keymap`

The later migration slices should edit the generator config/fixtures and generator code as needed. Firmware, board, shield, and build configuration files remain out of scope unless explicitly requested.

## Scope notes

- This inventory covers the 28 existing combo nodes in `config/totem.keymap` and `examples/1.edn`.
- Every combo listed below is currently global because none of the raw combo nodes has a `layers` property. The intended migration scopes each existing combo to BASE only.
- No Nav-, Num-, or BT-specific combo additions are in scope for this migration.
- Row/column coordinates below use the current generated key-position layout with row widths `[10 10 12 6]`.
- `key-positions` are recorded exactly as they exist today. For Combo-layer destinations, the key-position set must be preserved; textual order is recorded here for regression checks.

## Horizontal Combo-layer inventory

Destination: one or more BASE-scoped horizontal Combo-layers using adjacent same-row positions.

| Combo | Current BASE binding | Current key-positions | Coordinates | Notes |
| --- | --- | --- | --- | --- |
| `kpz` | `<&kp DE_Z>` | `<4 3>` | `(0,4) (0,3)` | Same-row adjacent; current order is right-to-left. |
| `kpm` | `<&kp DE_M>` | `<3 2>` | `(0,3) (0,2)` | Same-row adjacent; current order is right-to-left. |
| `kpw` | `<&kp DE_W>` | `<1 2>` | `(0,1) (0,2)` | Same-row adjacent. |
| `kpx` | `<&kp DE_X>` | `<0 1>` | `(0,0) (0,1)` | Same-row adjacent. |
| `kpg` | `<&kp DE_G>` | `<13 14>` | `(1,3) (1,4)` | Same-row adjacent. |
| `kpv` | `<&kp DE_V>` | `<13 12>` | `(1,3) (1,2)` | Same-row adjacent; current order is right-to-left. |
| `kptap` | `<&kp TAB>` | `<11 12>` | `(1,1) (1,2)` | Same-row adjacent. |
| `kpq` | `<&kp DE_Q>` | `<10 11>` | `(1,0) (1,1)` | Same-row adjacent. |
| `kpb` | `<&kp DE_B>` | `<25 24>` | `(2,5) (2,4)` | Same-row adjacent; current order is right-to-left. |
| `kpj` | `<&kp DE_J>` | `<24 23>` | `(2,4) (2,3)` | Same-row adjacent; current order is right-to-left. |
| `kpk` | `<&kp DE_K>` | `<22 23>` | `(2,2) (2,3)` | Same-row adjacent. |
| `kpy` | `<&kp DE_Y>` | `<21 22>` | `(2,1) (2,2)` | Same-row adjacent. |

## Vertical Combo-layer inventory

Destination: one or more BASE-scoped vertical Combo-layers using adjacent same-column positions.

| Combo | Current BASE binding | Current key-positions | Coordinates | Notes |
| --- | --- | --- | --- | --- |
| `kpgui` | `<&sk LEFT_GUI>` | `<4 14>` | `(0,4) (1,4)` | Same-column adjacent. |
| `kpalt` | `<&sk LEFT_ALT>` | `<3 13>` | `(0,3) (1,3)` | Same-column adjacent. |
| `kpesc` | `<&esc_layerreset>` | `<2 12>` | `(0,2) (1,2)` | Same-column adjacent. |
| `round_brackets` | `<&round_brackets>` | `<1 11>` | `(0,1) (1,1)` | Same-column adjacent. |
| `backspace_delete` | `<&backspace_delete>` | `<0 10>` | `(0,0) (1,0)` | Same-column adjacent. |

## Diagonal down-right Combo-layer inventory

Destination: one or more BASE-scoped diagonal down-right Combo-layers using adjacent `(+1 row, +1 col)` positions when normalized from top-left to bottom-right.

| Combo | Current BASE binding | Current key-positions | Coordinates | Notes |
| --- | --- | --- | --- | --- |
| `kpctrl` | `<&sk LCTRL>` | `<14 25>` | `(1,4) (2,5)` | Diagonal down-right. |
| `kpcapsword` | `<&caps_word>` | `<13 24>` | `(1,3) (2,4)` | Diagonal down-right. |
| `kpspace` | `<&kp SPACE>` | `<23 12>` | `(2,3) (1,2)` | Diagonal down-right if normalized as `<12 23>`; current order is bottom-right to top-left. |
| `square_brackets` | `<&square_brackets>` | `<11 22>` | `(1,1) (2,2)` | Diagonal down-right. |
| `enter` | `<&kp ENTER>` | `<10 21>` | `(1,0) (2,1)` | Diagonal down-right. |
| `curly_brackets` | `<&curly_brackets>` | `<14 3>` | `(1,4) (0,3)` | Diagonal down-right if normalized as `<3 14>`; current order is bottom-right to top-left. |
| `punkt_doppelpunkt` | `<&punkt_doppelpunkt>` | `<2 13>` | `(0,2) (1,3)` | Diagonal down-right. |

## Retained irregular raw combo inventory

Destination: explicit BASE-scoped raw combo nodes. Preserve semantic node names, bindings, and key positions.

| Combo | Current BASE binding | Current key-positions | Coordinates | Reason retained as raw |
| --- | --- | --- | --- | --- |
| `angled_brackets` | `<&angled_brackets>` | `<25 13>` | `(2,5) (1,3)` | Non-adjacent/irregular diagonal shape, not the `(+1 row, +1 col)` pattern. |
| `komma_strichpunkt` | `<&komma_strickpunkt>` | `<24 12>` | `(2,4) (1,2)` | Non-adjacent/irregular diagonal shape, not the `(+1 row, +1 col)` pattern. |
| `toBT` | `<&to 4>` | `<1 2 3 4>` | `(0,1) (0,2) (0,3) (0,4)` | Four-key BT switching chord; no Nav/Num/BT additions in scope. |
| `ae` | `<&M_UPPER_AEOEUE DE_A_UMLAUT>` | `<33 21 32>` | `(3,1) (2,1) (3,0)` | Three-key umlaut macro chord spanning thumb/bottom positions. |

## Inventory count check

- Horizontal Combo-layer: 12 combos
- Vertical Combo-layer: 5 combos
- Diagonal down-right Combo-layer: 7 combos
- Retained irregular raw combo: 4 combos
- **Total: 28 combos**

This matches the 28 raw combo nodes in the current target combo set.

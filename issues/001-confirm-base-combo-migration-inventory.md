# Confirm BASE combo migration inventory

## What to build

Create the migration inventory for the existing Totem combo set before changing behavior. Confirm the target config to migrate, classify every existing combo exactly once as horizontal Combo-layer, vertical Combo-layer, diagonal down-right Combo-layer, or retained irregular raw combo, and record the expected BASE binding and key-position behavior that must survive the migration.

This slice makes the later implementation issues independently grabbable by giving each agent a shared source of truth for which combos belong to each migration group and which combos must not be converted.

## Acceptance criteria

- [x] The target config location for the real combo migration is confirmed before editing it.
- [x] Every existing combo in the target config is listed exactly once in a migration inventory.
- [x] The inventory identifies the intended destination for each combo: horizontal Combo-layer, vertical Combo-layer, diagonal down-right Combo-layer, or retained irregular raw combo.
- [x] The inventory captures each combo's current BASE binding and key positions so later slices can preserve BASE behavior.
- [x] The inventory explicitly marks Nav, Num, and BT-specific combo additions as out of scope.
- [x] The inventory respects the generator boundary and does not require firmware, board, shield, or build configuration changes.

## Delivered artifact

- Migration inventory: `docs/base-combo-migration-inventory.md`

## Blocked by

None - can start immediately

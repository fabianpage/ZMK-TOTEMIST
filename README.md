This repo is the firmware for the Totem and Totemist sold at https://ergomech.store

## Generated keymap equivalence guard

The pre-refactor generated Totem keymap baseline is captured in
`examples/totem_generated_baseline.keymap`.

Before accepting configuration-only refactors, regenerate the keymap from the
current Totem config and template and compare it to that baseline:

```bash
bb verify-totem-equivalence
```

A passing run means `totem_config.edn` + `totem_template.keymap` still generate
exactly the captured keymap. The generator test task also includes this baseline
check:

```bash
bb test
```

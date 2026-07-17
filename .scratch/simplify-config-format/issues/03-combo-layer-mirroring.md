# Do combo-layers adopt the `:left` / `:right-override` syntax, or keep `:bindings` grids?

Type: grilling
Status: open
Blocked by: 01

## Question

Combo-layers today work differently than regular layers. They are grids where each cell is a combo trigger, and a `:pattern` (relative offsets) turns cells into actual combos. A combo-layer never describes a split-hand mapping; its grid is the key-position matrix of the whole keyboard.

The user callout targets "replace tile functionality" with mirroring. Does this apply exclusively to keymap layers, or should combo-layers also use `:left` / `:right-override`?

1. **Semantics mismatch.** In a combo-layer, the "right half" isn't mechanically mirrored — combos are defined on the *whole* key matrix. A combo at `[:A :B]` is not naturally a mirror of a corresponding left-side combo. Does the user want to define combos only on one half and auto-generate mirrored counterparts?
2. **If combo-layers stay `:bindings`-based,** they still need `:row-widths`. If `02-global-defaults` moves `:row-widths` to `:keyboard`, then combo-layers implicitly pick it up. What about `:pattern` — should it be expressible globally per keyboard geometry too?
3. **If combo-layers adopt `:left` / `:right-override`,** does `:pattern` repeat? Is `:left` per combo-layer or can patterns be defined globally for each combo type (e.g., horizontal, vertical, diagonal)? The current `totem_config.edn` has one combo-layer per pattern direction (`horizontal_ltr`, `horizontal_rtl`, etc.). Could those become a single `:combos` node with multiple patterns?
4. **Combo-layer fill value.** Today some combo-layers use `:empty :none` because untriggered cells must not generate combos. How does the global `:default` interact with this? Does combo-layers need an explicit `:default :none` override, or is `:none` the natural fill for combos?

Blocked by [01-mirror-semantics](01-mirror-semantics.md) because the concept of "mirroring" for combos is undefined until the core mirror operation is pinned down.

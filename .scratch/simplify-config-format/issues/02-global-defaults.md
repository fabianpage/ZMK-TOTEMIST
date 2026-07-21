# What shape should `:keyboard` global defaults take, and how do they interact with per-node overrides?

Type: grilling
Status: resolved

## Question

The user wants global defaults to avoid repeating settings across every layer and combo-layer:

```edn
:keyboard {:row-widths [10 10 12 6], :default :trans}
```

Before we can implement this, we need to decide:

1. **What keys live under `:keyboard`?** The user mentions `:row-widths` and a default fill value (`:default`). Is the fill value key literally `:default` (could clash) or `:empty` (matches current `:placements` convention) or `:fill`? Should `:keyboard` also hold `:default-mirror` (auto-mirror every layer unless opted out)? Or `:aliases` today is top-level — should it move under `:keyboard` or stay separate?
2. **Precedence / override rules.** If a `:combo-layer` explicitly specifies `:row-widths`, does it override the global? If a Layer omits `:right-override` entirely, does the global drive `:default` (probably `:trans` vs `:none`)?
3. **Missing global behavior.** If `:keyboard` is absent from the config, what defaults do we assume to keep backward compatibility? The implicit defaults today are row-widths per-node, and `:trans` for empty cells.
4. **Combo-layers and globals.** Today combo-layers carry their own `:row-widths` inline and some have `:empty :none`. Do these migrate to relying on `:keyboard :row-widths` and `:keyboard :default`? Or does `:keyboard` supply only `keymap` layer defaults, leaving `:combos` to declare their own grid sizes?
5. **How `:row-widths` relate to `:left`.** Does `:left` define *half-row* lengths, and the engine derives total row-width by doubling (or doubling plus center column)? Or does `:row-widths` still describe the *full* row width of the keyboard, and the engine validates that `:left` row lengths fit within half of each row-width?

These questions are blocked by the decision in [01-mirror-semantics](01-mirror-semantics.md) on how `:left` maps to the physical layout, but the `:keyboard` structure itself can be resolved independently.

# What exact operation is "mirroring" and what data shape does `:right-override` take?

Type: grilling
Status: resolved

## Question

The refactor replaces `:tiles` and `:placements` with `:left` / `:right-override`. The user describes:

- `:right` absent → mirror `:left`
- `:right-override` present → `nil` means "fall back to mirrored value", non-nil means "use this instead"

But several details are ambiguous and shape the entire implementation:

1. **What is the mirror operation?** If a `:left` row is `[A B C D E]`, is the implied right-half `[:E :D :C :B :A]` (horizontal reversal, i.e. true mirror) or `[:A :B :C :D :E]` (identical copy)? The Totem is a split keyboard where finger correspondence matters.
2. **What data structure carries `:right-override`?** The user typed `{:right-override { [[nil nil ...]] ... }}` — the curly braces look like a map key; in EDN a bare `{` starts a map, which is unordered. Should it be a vector of rows `[:right-override [[nil ...] [nil ...] ...]]`, or a map keyed by row index, or something else?
3. **Fill cells in `:right-override`.** The user's `nil` is used as a sentinel for "use mirror". Standard empty cells today are `:trans`. Does `:right-override` use `nil`, `:trans`, or a dedicated keyword (e.g. `:_mirror`) as the fallback sentinel? `nil` is problematic because it can appear accidentally in EDN.
4. **Row-width validation.** Does `:left` alone determine the full-row width by doubling (plus any center column), or do we still require `:keyboard {:row-widths [...]}`? If `:right-override` rows are shorter than expected right-half width, do we fill with the global `:default` or `:trans`?
5. **Does `:right-override` allow "partial rows"?** Can a row be `nil` entirely ("use full mirror for this row")? Or must it always be a concrete vector?
6. **Is `:left` the *whole* row or just the *left half*?** Today's layers describe the full layout; the new `:left` presumably defines only the left-hand columns. Does `:left` row width equal `(row-width / 2)`? Or can `:left` be any length and mirroring fills the rest?

Resolving this determines every downstream — from how `render-layer` assembles bindings to how we migrate `totem_config.edn`.

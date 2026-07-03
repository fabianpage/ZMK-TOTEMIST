## What to build

Extend the macro node so that optional `:wait-ms` and `:tap-ms` keys at the macro level emit as DT properties inside the generated node.

`render-macro` inspects the node for `:wait-ms` and `:tap-ms`. When either is present, a corresponding DT property line is emitted inside the node body (e.g. `wait-ms = <80>;`). This replaces the current manual inclusion of these lines inside raw `:body` strings.

Add unit tests that assert the property lines appear when the keys are present and are absent when omitted. Extend the existing end-to-end example with a 0-param macro that carries timing properties.

## Acceptance criteria

- [ ] A macro node with `:wait-ms 80` renders with a `wait-ms = <80>;` property line.
- [ ] A macro node with `:tap-ms 20` renders with a `tap-ms = <20>;` property line.
- [ ] A macro node with both creates both property lines.
- [ ] A macro node with neither creates no timing property lines.
- [ ] The existing 0-param example macro is updated with timing keys, and the captured baseline still passes.

## Blocked by

- Slice 1 — Core macro rendering with simple binding expressions

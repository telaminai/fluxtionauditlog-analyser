# What authoring a node costs: predictions before doing it (2026-09-27)

The spike measures what a view node costs the AUDIT. It does not measure what it costs the AUTHOR, and that is the
other half of whether this generalises: an abstraction that is cheap at runtime and expensive to write gets worked
around, which is how hand-placed dispatch returns.

Recorded before building the second element, by someone who has reviewed this codebase all day and **never authored
a Fluxtion node or run `-Pregen`**. Scored in `AUTHORING-COST-RESULTS.md`.

## Predictions

1. **The node is the easy part.** `IdentityBanner` lands in under 50 lines and is the least of the work.
2. **Most of the effort is at the edges the generator does not reach** — registering backends, the sealed-interface
   case in the adapter, the fake adapter, the snapshot. I predict the edge wiring outweighs the node by at least
   2:1 in lines changed.
3. **I hit at least one generator rule I did not know**, and did not deduce from reading. The visible candidate:
   `StatusLine` carries `// Not final: node-local state (a final field is constructor-mapped by the generator)` —
   a constraint nobody would guess, recorded only because someone met it.
4. **When I do hit one, the error names the fix.** The precedent is telaminai/fluxtion#34, where the generator
   said `cannot find matching constructor … failed to match for these fields:[…]` and the fix took one attempt.
   I predict any constraint I hit is reported precisely enough to fix first time.
5. **I do not need to read the generated processor.** I have claimed this abstraction holds for LLM authors all
   day, on the evidence that I never opened `SessionProcessor.java` while reviewing. Building is the real test. If
   I have to open it to make this work, the claim is weaker than I have been saying.
6. **Regeneration is uneventful** — `-Pregen` succeeds first time once the node compiles, and the two generated
   copies come out byte-identical without intervention.

## The one that would matter most if wrong

**5.** The whole "design in the builder DAG, the generated dispatch is a compiled artefact" position rests on the
author never needing to read the output. If authoring a trivial node requires reading 2,000 generated lines, the
paradigm is doing less for a developer than I have been claiming, and the honest pitch is narrower.

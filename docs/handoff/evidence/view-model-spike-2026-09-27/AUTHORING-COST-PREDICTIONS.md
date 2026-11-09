# What authoring a node costs: predictions before doing it (2026-09-27)

The spike measures what a view node costs the AUDIT. It does not measure what it costs the AUTHOR, and that is the
other half of whether this generalises: an abstraction that is cheap at runtime and expensive to write gets worked
around, which is how hand-placed dispatch returns.

Recorded before building the second element. Scored in `AUTHORING-COST-RESULTS.md`.

> **Correction, before any code (owner caught it).** The first draft of this file said I had *"never authored a
> Fluxtion node or run `-Pregen`"*. That is false. Earlier the same day I added a gate node to a market-making
> strategy, wired it in that project's builder and regenerated. It is the work that produced
> telaminai/fluxtion#34. Getting my own prior experience wrong in a predictions document is exactly the kind of
> premise error these files exist to catch, so it is corrected here rather than quietly fixed — and it changes
> three of the six predictions below from first tests into replications, which is a weaker and more honest claim.

**What that earlier session already established**, and which this one therefore only replicates:

- I met a generator constraint I had not deduced — a `@FluxtionIgnore` field set by a fluent setter is not carried
  into the generated processor, because the annotation says not to carry it.
- The generator's message named the fix precisely (`cannot find matching constructor … failed to match for these
  fields:[…]`) and the fix took one attempt.
- **And I did read the generated Java.** The silent drop was found by diffing the generated file by hand; nothing
  else could have found it. So prediction 5 below is already falsified in one circumstance, and the interesting
  question is whether that circumstance is the only one.

## Predictions

1. **The node is the easy part.** `IdentityBanner` lands in under 50 lines and is the least of the work.
2. **Most of the effort is at the edges the generator does not reach** — registering backends, the sealed-interface
   case in the adapter, the fake adapter, the snapshot. I predict the edge wiring outweighs the node by at least
   2:1 in lines changed.
3. **I hit at least one generator rule I did not know** (replication). The visible candidate is already in front
   of me: `StatusLine` carries `// Not final: node-local state (a final field is constructor-mapped by the
   generator)` — a constraint nobody would guess, recorded only because someone met it. I have copied that comment
   into my node without having met the rule, which is itself the evidence that such rules travel as folklore.
4. **When I do hit one, the error names the fix** (replication of #34).
5. **I do not need to read the generated processor — in the NORMAL case.** Refined, because the earlier session
   already falsified the blanket version: when the generator silently drops something, reading the output is the
   only way to find it. The sharp prediction is therefore about the boundary: *absent a silent drop, authoring and
   regenerating this node requires no reading of `SessionProcessor.java`.* If I have to open it even when nothing
   is silently dropped, the ergonomics claim is much weaker than I have been making it.
6. **Regeneration is uneventful** — `-Pregen` succeeds first time once the node compiles, and the two generated
   copies come out byte-identical without intervention.

## The one that would matter most if wrong

**5.** The whole "design in the builder DAG, the generated dispatch is a compiled artefact" position rests on the
author never needing to read the output. If authoring a trivial node requires reading 2,000 generated lines, the
paradigm is doing less for a developer than I have been claiming, and the honest pitch is narrower.

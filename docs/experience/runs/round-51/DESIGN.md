# Round 51 — maintenance: the second change, by someone who did not make the first

Round 49 measured *building* an engine twice. This measures **changing one**, which is where the
series' most durable claim lives and where nothing has been measured at all.

## What round 49 exposed without being asked

The idiomatic arm reached a correct dispatch policy by doing this:

- 3,000 randomised runs, 36,000 dispatches, to establish that an alert fires **iff** the limit is
  breached
- bisecting the limit behaviourally to `250000.00000000006`
- 200,000-sample differential testing to recover the fee function bit-for-bit
- brute-forcing 44 candidate strings to find which component owns which config key

That analysis produced **four routing rules**. And then it evaporated. What ships is 256 lines of
Java and a class comment. **The reasoning that justifies the routing is not in the artefact.**

Under Fluxtion the equivalent analysis is *encoded*: which references trigger is an annotation, the
topology is a graph, the dispatch is regenerated. **The analysis is a build output, not a memory.**

## The change

A vendor upgrade, binary compatible, of the kind round 40 and 41 already showed is realistic:
**`Liquidity` gains an `onRate` handler.** Rates now move the book. Nothing is removed, no signature
changes, and **the correct set of components for a RATE event silently grows by one.**

This is round 41's finding transplanted to the idiomatic architecture. There, a stored event→handler
map went stale and dropped two events with a green build and passing tests. Here the "stored map" is
a `switch` in a human-written engine.

## The measurement

Each arm is given **its own finished engine from round 49 / cell O**, the upgraded jar, and the
vendor's release note — and a **fresh agent with no memory of building it**. That is the point: the
maintainer is not the author.

| | Fluxtion | idiomatic plain Java |
|---|---|---|
| what must change | nothing — regenerate | find every dispatch site for RATE and add one call |
| what tells you | the graph, from the jar | the release note, if you read it |
| what happens if you miss it | the generator adds the edge anyway | `liquidity.book` silently stale for every rate |

## Predictions, committed before either runs

| # | prediction | confidence |
|---|---|---|
| Z1 | **Fluxtion needs zero source changes** — rebuild picks up the new handler, as it did in round 41 | high |
| Z2 | **The idiomatic arm must re-derive the dispatch analysis**, because the artefact does not carry why each component is called for each event | medium-high |
| Z3 | **The idiomatic arm's turn count for a one-method change is within 2× of building from scratch**, because comprehension dominates and it starts from zero | medium |
| Z4 | If the idiomatic arm misses it, its own round-49 tests still pass — they pin the old routing | medium-high |

## Falsifier

**If the idiomatic arm makes the change in a handful of turns by reading the release note and adding
one line**, then maintenance is cheap for both and the framework's advantage is confined to build
time. I have set three falsifiers in this series and all three fired; this one is live.

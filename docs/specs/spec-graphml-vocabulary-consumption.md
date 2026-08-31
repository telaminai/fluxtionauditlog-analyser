# Spec — consuming the GraphML vocabulary, and why it is a second path forever

**Status:** PROPOSED 2026-08-31. **Tracker:** [tracker.md](tracker.md) ▸ M45.
**Upstream:** `fluxtion-builder` `feature/compiler_diagnostics` — the vocabulary, `GraphMlOptions`,
and the model-projection exporter.
**Related:** [`upstream-asks.md`](../proposals/upstream-asks.md) §2c (the asks this answers),
[`baseline-2026-08-31`](../experience/runs/baseline-2026-08-31/RESULTS.md) (the pinned measurement).

## What lands, and what the analyser does about it

`fluxtion-builder` gains a `fluxtion.*` GraphML vocabulary that answers, as **data**, several questions
the analyser answers today by **heuristic**: which nodes the framework created, whether an update
crosses an edge, whether a node is even capable of logging, what a handler's filter is, and what order
siblings dispatch in.

Every one of those is an ask this repo filed. The work here is consuming them — and the shape of that
consumption is decided by two facts about *where* and *when* the file is produced, not by preference.

## D-V0 — the two facts that decide everything below

**1 · The GraphML is emitted client-side, by the author's pinned builder.** Graph build, DTO build,
source-gen extraction and GraphML export all run in the user's JVM; only the model generation is remote.
So the version of the vocabulary in a file is a property of *whatever builder that author pinned*, and
the analyser opens files produced by strangers.

**2 · The compiler is always the latest stable version in the cloud** (owner, 2026-08-31). That fixes
the server side and leaves the client side pinned wherever each author left it.

**Therefore there is no upgrade moment.** The analyser will meet builder versions from before the
vocabulary existed for as long as anyone keeps a `.graphml`, and it cannot require an author to
regenerate to open one. **Dual-path is permanent, not transitional.**

**This corrects a claim I made in review** ([`review_graphml_metadata_dd36bc5`](../handoff/review_graphml_metadata_dd36bc5.txt)
and the FLX-1009 review): that `fluxtion.framework` and `auditCapable` would let us *delete*
`Scaffolding`'s hand-maintained class list and `AuditReadiness`'s `EventLogManager` heuristic. They do
not. They **demote them to fallbacks**, which still have to be maintained, and the win is that they stop
being the *only* answer rather than stopping being an answer.

## D-V1 — read PARALLEL or read nothing; never AGGREGATED

`GraphMlOptions` offers three modes. Their own documentation is what decides this for us:

| mode | shape | facts |
|---|---|---|
| `OFF` (today's default) | legacy | none |
| `AGGREGATED` | legacy — one edge per vertex pair | **merged into distinct-value *sets*** |
| `PARALLEL` | one edge per relationship | exact, per relationship |

`AGGREGATED` looks like the safe step — the old shape, with new facts. **It is the one mode the analyser
must refuse.** Quoting the upstream javadoc, which is admirably honest about it:

> The lists are sets, not index-aligned tuples. A pair with an `"ACME"` filter and an unfiltered default
> case yields `filterType="matched,defaultCase"` and `filterValue="ACME"`, and nothing says which went
> with which.

A consumer that rendered a filter value against the wrong handler would be **wrong without being able to
detect it** — the exact class of defect this product exists to refuse. And the analyser's whole interest
in the vocabulary is *per-relationship* facts: `propagates` is meaningless aggregated, because "does an
update cross this edge" has no answer for a pair holding one triggering and one `@NoTriggerReference`
field. Upstream found precisely that defect and fixed it by answering per relationship.

### CORRECTED 2026-08-31 — the refusal is FACT-scoped, not FILE-scoped

The rule above was right about the danger and wrong about its reach, and measuring it is what showed
that. Emitting this repo's own graph both ways and comparing:

* **node facts are bit-for-bit identical.** Aggregation merges EDGE facts onto one edge per vertex pair
  and does not touch nodes at all. So `auditCapable`, `auditCapableVia`, `kind`, `class`,
  `callbackKinds` and `topologicalRank` are exact in either shape;
* **only a MERGED edge loses anything**, and it says when it has: an aggregated edge carries
  `fluxtion.relationshipCount`, and where that is 1 nothing was collapsed onto it. The lossy case is
  visible in the data — one such edge in our graph reads
  `referenceField="dirtyStateMonitor,eventDispatcher"`, `relationshipCount=2`, with a single
  `propagates` that cannot be attributed to either.

Refusing the whole file therefore threw away an exact answer because it shared a document with an
inexact one — and it cost the entire audit-capability win on any aggregated graph, for nothing.

**The rule is now:** node facts are trusted in any mode at a supported MAJOR; edge facts are trusted
from `PARALLEL`, and from `AGGREGATED` only where `relationshipCount` is absent or 1. Refuse what
actually merged, not everything near it.

The original instinct still holds where it applies: a merged edge's facts are read as absent rather
than half-trusted, because since propagation became per-relationship a merge can hide a genuine
disagreement — one triggering field and one `@NoTriggerReference` on the same pair — which is exactly
the fact worth having.

## D-V2 — every derived answer says which source produced it

The analyser already refuses to state things it cannot support — `AuditReadiness.UNKNOWN` exists rather
than a guess, `GraphPairing.reason` always carries its numbers. The vocabulary makes the same discipline
mandatory in a new place: **a coverage figure computed with `fluxtion.framework` and one computed with
the `Scaffolding` name list are not the same number.**

Two people comparing two projects, or one person comparing two builds, must not silently compare a
measured denominator with a heuristic one. So every surface that changes answer with the vocabulary
carries its basis, in the same vocabulary the Project panel already uses: `DECLARED` when the file said
it, `INFERRED` when we worked it out.

`fluxtion.metaVersion` is read and recorded. Policy: **a 1.x reader accepts every 1.y**, per the
upstream contract; an unknown MAJOR is treated as absent, not as an error, because a file from the
future is still a file someone needs to open.

## D-V3 — parallel edges are a rendering change, not only a parsing one

Checked, not assumed: `ProcessorTopology.of` keeps edges in a `List` with a soundness filter and **no
deduplication**, and `LayeredLayout` and `TopologyCanvas` draw straight from `edges()`. A `PARALLEL`
file therefore draws a vertex pair's arrow once per relationship, on top of itself, and inflates
`edgeCount()`.

Upstream names this outcome for a naive reader — *"will render as overlapping arrows until it
distinguishes them"*. Distinguishing them is the work: an edge is identified by
`(source, target, refKind, referenceField)`, drawn once per distinct relationship with its own label,
and coverage/step-through keep counting **pairs** where they mean reachability and **relationships**
where they mean dispatch.

## D-V4 — what becomes answerable, which is the point

The measurement pinned in [`baseline-2026-08-31`](../experience/runs/baseline-2026-08-31/RESULTS.md)
found three of three agents could not determine sibling dispatch order from the artefacts, and two of
three could not say whether a silent node ran. Those are the surfaces to build, in that order:

1. **"did not run" vs "could not have run" vs "ran and said nothing"** — from `auditCapable` +
   `auditCapableVia` + `propagates`. Today the analyser must hedge all three into one message.
2. **dispatch order, shown rather than inferred** — from `topologicalRank`. Four of six measured authors
   previously concluded declaration order; this makes it a column.
3. **an honest coverage denominator** — from `fluxtion.framework`, replacing a name list that must be
   updated every time upstream adds a plumbing class.
4. **filtered handlers as distinct edges** — from `filterValue`/`filterType`/`handlerMethod`.

## Backwards compatibility — assessed, and the risk is not where it looks

**The GraphML surface is low risk, and upstream has already done the work.** At `OFF` the only
unconditional change is `edgedefault` corrected to `directed`; `GraphMlParser` never reads it and its own
javadoc calls today's value misleading. Upstream ran our parser against before/after output at `dd36bc5`
and adjacency, node facts, kinds and class names were identical. **That check now needs re-running**: at
`7a273a8` the exporter was rewritten as a projection of the model rather than a second discovery pass,
which is a much larger change than the one it covered.

**The DTO wire is the high-risk surface, because of the cloud model.** The server is always latest; every
client is pinned wherever its author left it. So *old client + new server* is not an edge case, it is the
**default state of every user who has not bumped**, and a server upgrade exercises it for all of them at
once — with no rollback available to them, because they do not operate the server.

The branch changes `NodeDto.annotatedMethods` to carry `@ServiceRegistered`/`@ServiceDeregistered`. The
reasoning given is sound — existing list field, no new field, no UID change, no Kryo shift — and the
integration report lists it explicitly as **reasoned but not exercised by an old↔new test**. That is the
one item on the branch whose failure mode is "every pinned user breaks simultaneously".

**Recommendation to upstream, and it is a release gate rather than a nice-to-have:** an old-client
↔ new-server DTO round-trip test, pinned at the last released builder, run before the builder release.
It costs one test and it covers the only change on the branch that users cannot route around.

**Release footprint, checked against `main`:** the branch touches `fluxtion-builder` and nothing else
shippable — 32 files there, the rest docs, design and integration tests. `fluxtion-runtime` is untouched,
so the analyser's 1.0.13 dependency stands; `fluxtion-builder-api` gains nothing, because the metadata
switch is a JVM system property rather than a `FluxtionCompilerConfig` method. **Only
`fluxtion-builder` needs releasing.**

**One reachability question upstream should answer before releasing.** A system property is only usable
if it reaches the JVM that runs the exporter. If `fluxtion-maven-plugin:scan` forks, `-D` does not arrive
and the switch is unreachable from a Maven build — which is the only path a real user takes. Untested
either way, and it is the same shape as the branch's own §5 finding: a mechanism that exists and may be
unreachable from the path users take. M45.1 settles it empirically.

## Slices

### Re-planned 2026-08-31 against what upstream actually emits

The slices below were written against the vocabulary as specified. Measured against what is **emitted**
at `dbcbe17` — 17 keys emitted, 9 planned — three of them move:

* **M45.4 has no input and is parked.** `fluxtion.framework` is *planned, withheld*. Upstream found its
  own value was a package-prefix guess, deleted it, and is replacing it with recorded creation
  provenance that is not finished — one creation route runs through `builder-api`'s auditor map, so
  closing it needs a `builder-api` change. **The fact we want will be better than the one this spec was
  written against, and it does not exist yet.** `authoredNodeCount` went with it.
* **The "audit trio" is a duo.** `auditCapable`/`auditCapableVia` are emitted; `eventAudit` is planned —
  and that is precisely the key separating *capable, audit off* from *capable and stayed silent*, which
  is M45.3's stated product claim. Recoverable, but **from the log header, not the GraphML**
  ([UP-FLX-11](../proposals/upstream-asks.md)). M45.3 is re-scoped accordingly rather than deferred.
* **The gate closes later than this spec assumed, and it is ONE ordered condition, not two.** Upstream
  flips the default when relationships are captured at the decision point *and* one consumer
  **understands** `PARALLEL`. M45.2 explicitly changes no behaviour, so reading is not understanding —
  the gate is **M45.5**. Written here so the two repos stop naming each other.

**M45.1 — prove reachability and measure the ceiling. ☑ DONE 2026-08-31.** Both properties reach the
exporter and the generator through the real `fluxtion-maven-plugin:scan`: `PARALLEL` emitted 17 keys,
and the diagnostics sidecar was written on the failing path. The deferred authoring rows of the pinned
comparison are **run and both predictions held** —
[`ceiling-2026-08-31`](../experience/runs/ceiling-2026-08-31/RESULTS.md). **Caveat that must not be
lost:** this slice needs an entitled Fluxtion key, so it cannot run in CI or for a contributor. The
reachability question it answered was worth asking, and the answer has to be *recorded* rather than
*reproducible*.

**M45.1 — original text, for the record.** Install the branch locally, point this repo's
`-Pregen` at it with `-Dfluxtion.graphml.metadata=PARALLEL`, and confirm the committed GraphML actually
changes. That is a real Maven build driving a real consumer, and it answers the reachability question and
unblocks the pinned comparison run in one step. **Nothing ships from this slice** — it is measurement.

**M45.2 — read the vocabulary, change no behaviour. ☑ DONE 2026-08-31.** Parse and expose `metaVersion`, node keys and edge
keys; record which mode a file is in; refuse `AGGREGATED` per D-V1. Every existing surface keeps its
current answer. This is the slice that makes the rest safe, because it separates *reading* from *acting*.

**M45.3 — the audit duo. ☑ DONE 2026-08-31.** `auditCapable`/`auditCapableVia`/`eventAudit` demote the `EventLogManager`
heuristic to a fallback and let a surface say *ran and said nothing* rather than hedging. Carries D-V2's
basis marker. This is where the product claim actually improves.

**M45.4 — the honest denominator.** `fluxtion.framework` demotes `Scaffolding`.

**M45.5 — parallel edges and dispatch rank. ☑ DONE 2026-08-31 — this is the slice upstream's default-flip is gated on.** The rendering work of D-V3, plus `topologicalRank` as a
column.

**M45.6 — the builder bump.** Move `-Pregen` off 1.0.64, regenerate, re-pin `SessionGraphShapeTest`.
**Expected to fail first**, by design — that test is the downstream canary we offered upstream, and a
reviewer who has not read this line will think they broke something. **Take `dbcbe17` or later**: before
it, `topologicalRank` was an index into object-sorted order rather than dispatch order, and this repo
reproduced the inversion on its own graph — `effectQueue` ranked 2 against `sessionBoundary` 10, across
exactly the `@PushReference` edge, the only inversion present. Fixed at `dbcbe17` (9 then 10). **Any
fixture generated with an earlier build carries the wrong rank.**

**A rule this produced, which outlives the bug.** `topologicalRank` must be **pinned against the
generated dispatch order**, not trusted. It was published, plausible, reversed, and nothing downstream
would have said so — the same defect class as per-pair propagation and the `@TriggerEventOverride`
demotion. A column of integers reads as authoritative, and our own baseline is why that is the worst
place to be quietly wrong: three of three agents could not answer dispatch order at all, so an inverted
rank converts a measured *cannot tell* into *confidently wrong*.

## Acceptance

- [ ] A graph with no `fluxtion.*` keys is read exactly as it is today — asserted against the committed
      fixtures, which are pre-vocabulary and stay that way.
- [ ] An `AGGREGATED` file is treated as `OFF`, and the surface says why.
- [ ] Every changed answer carries `DECLARED` / `INFERRED`, and a test proves the two differ on a fixture
      where the heuristic and the fact disagree.
- [ ] `PARALLEL` renders one arrow per distinct relationship, and `coverage` counts pairs where it means
      reachability.
- [ ] An unknown MAJOR `metaVersion` degrades to absent rather than failing the open.
- [ ] The pinned comparison run has been executed at the ceiling and its numbers recorded, whether or not
      they support the prediction.

## Risks

**The fallback path rots.** It is exercised by the old fixtures and by nothing a developer sees daily, so
it will break silently. Mitigation: the committed demo fixtures stay pre-vocabulary permanently, and D-V2's
basis marker is asserted on both paths.

**AGGREGATED gets adopted "temporarily".** It is the mode that looks compatible and is quietly lossy.
D-V1 exists so that decision is made once, here, rather than under delivery pressure.

**The default never flips.** Upstream gates it on a consumer reading the keys — and we are the consumer.
If M45.2 does not land, the vocabulary stays off by construction and none of the measured benefit reaches
an author. That is a dependency in both directions and worth saying out loud.

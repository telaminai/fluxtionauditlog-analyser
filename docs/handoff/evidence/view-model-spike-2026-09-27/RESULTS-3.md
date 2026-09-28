# View-model spike, round 3: propagation — results (2026-09-28)

Scores [`PREDICTIONS-3.md`](PREDICTIONS-3.md), committed before any code (`89748192`). Measured with
`StatusLineAuditVolumeTest`, the same 1,000-poll Follow loop as rounds 1 and 2. Its assertions are unchanged; it now
also prints which nodes write a line in each `LogAppended` record, and how many of those lines say only that the node
ran. The baseline was re-measured at `89748192` with that same instrument copied in.

## What changed

| change | lesson (issue #59) | nodes |
|---|---|---|
| a narrow trigger parent, `LogLifecycle`: dirty only when open, generation, identity or its reason moves | a trigger parent that returns `false` unless the relevant state moved | new |
| `OpenLog` read as data (`@NoTriggerReference`) | data-only references | `IdentityBanner`, `LogEvidence`, `PairingQualifier` |
| `Pairing` read as data (`@NoTriggerReference`) | data-only references | `PairingQualifier` |
| state a decision only when it changed; return `false` when nothing a consumer reads moved | return `false`; log only what changed | `CoverageClaim`, `Pairing` |

## Measured

| | before | after | change |
|---|---|---|---|
| records written | 5,800 | **5,800** | 0 |
| bytes | 2,600,684 | **2,375,084** | **−225,600 (−8.7%)** |
| `LogAppended` mean record | 1,256 B | **880 B** | −376 B (−30%) |
| node lines per `LogAppended` record | 9 | **7** | −2 |
| lines that report no change | 6 (4 idle + 2 restating) | **4** (idle, none restating) | −2 |

What an appending poll's `LogAppended` record carries now:

```
- operationGate:  { thread: main, method: onLogAppended, fact: fact, what: LogAppended}
- openLog:        { thread: main, method: onLogAppended, openLog: appended, sampled: 1, total: 26}
- logLifecycle:   { thread: main, method: onOpenLogChanged}
- logEvidence:    { thread: main, method: onLogAppended, scanCoalesced: records appended}
- pairing:        { thread: main, method: recomputeOnStateChange}
- coverageClaim:  { thread: main, method: recomputeOnStateChange}
- statusLineView: { thread: main, method: onStateChanged}
```

Gone from it:
- `identityBannerView` and `pairingQualifier`, entirely;
- `logEvidence.onOpenLogChanged`, while its append handler still runs;
- the restated `pairing: cannotSay …`;
- `coverageClaim`'s ≈185 B assessment.

## Predictions, scored

| # | prediction | result |
|---|---|---|
| 1 | records unchanged at 5,800 | **Hit.** |
| 2 | ≈2,372,000 B (−8.8%), ≈380 B per poll | **Hit.** 2,375,084 B (−8.7%), 376 B per poll. |
| 3 | node lines 9 → 7; no-change lines 6 → 4 | **Hit.** |
| 4 | topological order kept; the existing suites pass | **Hit.** Headless 2,608 run, 0 failed; frame suites 132 run, 0 failed, 1 skipped (focus-bound). |
| 5 | offline regeneration, both copies byte-identical | **Hit**, but it needed staging (Finding 7). |
| 6 | at least one existing test fails on the restating change | **MISS.** None did (Finding 5). |
| 7 | the graphml shows the new data edges | **Hit.** `openLog → identityBannerView`, `→ logEvidence` and `→ pairingQualifier` are `refKind DATA`, `propagates false`, and `logLifecycle →` each of them is `TRIGGER`. No transformer. |
| — | the design as predicted: `PairingQualifier` keeps `pairing` as a trigger | **Changed during the work** (Finding 3). |

**Neither "show this wrong" outcome occurred.** No session transition was lost, and every behavioural suite
passes. The saving was 99% of the prediction, not under half.

## Improvements the exercise led to

### 1. The biggest cost was nodes restating unchanged decisions, not idle wake-ups

`CoverageClaim` wrote its whole assessment on every recompute: the claim, a sentence of `whyNot`, provenance, the
audit installation and the level. That is ≈185 B per append, which is more than the three idle lines together.
`Pairing` did the same with "cannot say". Both returned `true` unconditionally. Nothing reads `coverageClaim`'s
dirty flag, so its `return true` had never mattered to any consumer, and so nobody had noticed it mattered to the
audit. **Lesson 1 is as much about the log line as about the return value.** A node that states a decision should
state it once.

### 2. A narrow trigger pays from its second consumer

`LogLifecycle` runs on every append and writes one line of its own. It saves one line per consumer. With three
consumers the net is −2 lines per append. With one consumer it would have saved nothing. This belongs in the
guidance: **introduce the narrow node when a second node needs the same slice of a wide parent**, not before.

### 3. "Related" is not the same as "triggering"

The prediction kept `pairing` as a trigger of `PairingQualifier`, because they are about the same thing. The first
measurement showed it still running on every append: the pairing re-scopes on every append, because its count moves,
so it is legitimately dirty. But `PairingQualifier` binds to *which pair* is open, meaning the log generation and the
graph revision, and reads the verdict only when a comparison event arrives. It never needed the pairing to wake it.
**Ask of each trigger edge: does this node decide anything when that parent changes, or does it only read it
later?** Only the first is a trigger.

### 4. "Changed" and "worth stating" are two questions

`Pairing` under "cannot say" must still report a change on each append, because `sampled` and `total` move, and
`CoverageClaim` reads them. But the log line it writes is identical each time. The node now tracks the two apart:
the return value answers *can a consumer read something new?*, and the log answers *would a reader learn something
new?* The views already did this with `changedFrom`. Decision nodes need it too.

### 5. The restating was untested, which is why no test failed (prediction 6)

No test asserted what `pairing` or `coverageClaim` writes on a recompute that changes nothing. The audit's content
was unguarded for these nodes, so the change to it could not break anything. `PropagationRoundThreeTest` now pins
four things:
- which nodes an append wakes;
- that an unchanged decision is not restated;
- that an identity change and a close still reach every consumer;
- that a plain close, with no identity change, reaches `logEvidence` through the lifecycle's open flag alone.

Four controls, all caught: `r3-lifecycle-cutoff`, `r3-lifecycle-sees-close`, `r3-coverage-not-restated` and
`r3-pairing-cannot-say-once`.

### 6. Some trigger edges are redundant, and the graphml could say so

`Pairing`'s dirty flag feeds `coverageClaim`, `logArrival` and `pairingQualifier`. Every cycle that can change the
pairing also dirties `operationGate` or `openGraph`, and each consumer's guard already includes one of those. So the
pairing's return value never decides on its own whether a consumer wakes, and an inexact cutoff could not be
witnessed. It is kept exact by contract and recorded here as defensive, with no control. **A tool reading the
graphml could find this class of edge**: a trigger edge whose dirty flag is always accompanied by another term of
the same guard. It is a candidate check for the analyser's own topology view.

### 7. Two authoring costs, again

- **Regeneration had to be staged.** `-Pregen` compiles the main sources, including the *old* generated processor,
  which still called the old constructors. As in M69, the three calls were patched to pass `null` until the
  generator overwrote them. Any constructor change hits this. The proper fix is in the build: compile the builder's
  source set without the generated processor. That is a candidate for `upstream-asks.md`.
- **The preflight caught an orphaned control** (`m44-journey-scope`). The `Pairing` rewrite moved the line it
  mutates. It is re-anchored to the new line, with the same mutation and witness, and still caught. That is #58's
  Finding 2 again: **every edit to a controlled line carries a re-anchor**, and only the preflight sees it. Run the
  preflight after every production change, not only before a PR.

### 8. Taking a node off a trigger path can make an existing witness vacuous

The complete mutation gate found a survivor that the preflight could not have seen: `m44-rescope-keeps-qualifications`.
Its anchor had not moved. Its witness, `PairingQualifierTest#aReScopeKeepsItStale`, proves "a Follow append keeps the
comparisons" by posting an append. After round 3, an append no longer wakes `PairingQualifier` at all, so the
property holds *structurally* and the witness never reaches the branch it guards. The branch is still live: an
identity verdict wakes the node, through the lifecycle, for the same pair. A new witness,
`aLifecycleChangeForTheSamePairKeepsIt`, does exactly that, and the control now points at it. The append witness
stays, because it still checks the outcome.

The rerun found a second one of the same class: `vm2-unchanged-verdict-not-drawn`, whose witness posted an append to
the banner. The branch is reached now by closing a log whose banner never showed: the closed view equals the one
already drawn. New witness `IdentityBannerViewTest#closingWithNoBannerUpDrawsNothing`.

This is the upside the owner named. **A node that is woken only by the change it decides on needs fewer defensive
"did anything change?" branches.** Here the trigger contract made one conditional reachable on a single path.

**For planning:** narrowing a trigger moves *which events reach a branch*. Two re-anchor costs follow from that:
- an anchor whose line moved, which the preflight catches;
- a witness whose event no longer reaches its branch, which only the complete mutation gate catches.

Run the gate, not only the preflight, after any change to a trigger edge.

## What is not measured

- **Follow with a graph open.** The instrument opens no graph. With one, the pairing's verdict re-scopes on every
  append and is legitimately stated each time, with its new `total`. Whether that line should shrink to the changed
  fields only is the next question.
- **`StatusLine`'s three idle lines per poll.** They are unchanged, deliberately. It needs every append's count, and
  it waits for the scan, which is the consistency gate #55 argued for.

## Gates

- Headless: 2,608 run, 0 failed, 132 skipped (display).
- Frame suites: 132 run, 0 failed, 1 skipped (`PersonAtTheScreenFrameTest`, focus-bound).
- `--mode preflight`: 24 frame suites, 265 anchors.
- The round-3 controls: 7 of 7 caught: the four new ones, the re-anchored `m44-journey-scope`, and
  `m44-rescope-keeps-qualifications` and
  `vm2-unchanged-verdict-not-drawn` with their new witnesses (Finding 8).
- The complete mutation gate, at `7f3b1219`: **265 of 265 caught**, each restored byte-identical. It ran as four
  parallel shards, one worktree each, as CI does: 66 / 66 / 66 / 67 controls, about 4.2 minutes wall-clock against
  about 13 sequentially. Two earlier sequential runs had stopped at the Finding 8 survivors.

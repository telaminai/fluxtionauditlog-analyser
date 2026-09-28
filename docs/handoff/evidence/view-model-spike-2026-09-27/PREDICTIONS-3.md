# View-model spike, round 3: propagation — predictions, before any code (2026-09-28)

Applies what the #58 review and the owner's walk-through of Fluxtion's propagation annotations established (issue
#59) to the spike's session processor. Scored in `RESULTS-3.md`. Baseline: `review/pr58-fixes` at `e5550df5`, with
`ViewRendered` already filtered by element.

## What one appending Follow poll writes today (measured before predicting)

The `LogAppended` record carries nine node lines. Six of them report no change:

| line | what it says | why it is there |
|---|---|---|
| `identityBannerView.onStateChanged` | nothing | guarded on `isDirty_openLog`: every append wakes it |
| `logEvidence.onOpenLogChanged` | nothing | the same guard; it cares about open, close and generation only |
| `pairingQualifier.onPairChanged` | nothing | guarded on `openLog`, so it wakes on every append; returns `true` for "same pair, re-scoped" and nothing reads its flag |
| `pairing.recomputeOnStateChange` | `cannotSay, haveGraph: false, haveLog: true`, **restated** | returns `true` and logs on every recompute, changed or not |
| `coverageClaim.recomputeOnStateChange` | the full assessment (≈185 B of `whyNot` and inputs), **restated** | the same; nothing reads its dirty flag |
| `statusLineView.onStateChanged` | nothing | legitimately waits for the scan (the `scanPending` gate) |

## The changes, one lesson each

1. **Return `false`, and log only what changed** (lesson 1, plus the views' own `changedFrom`):
   - `CoverageClaim` states its assessment only when the assessment or a logged input moved;
   - `Pairing` states its verdict only when the published state moved: the verdict label, applies, declared, logged,
     matched, sampled, total, or `cannotSay` with haveGraph/haveLog.
2. **A narrow trigger parent, with the wide state read as data** (lessons 4 and 5). A new `LogLifecycle` node:
   - triggered by `OpenLog`, and dirty only when open, generation, identity or identity reason moves;
   - consumers: `IdentityBanner`, `LogEvidence` and `PairingQualifier` hold `OpenLog` as `@NoTriggerReference` and are
     triggered by `LogLifecycle` instead. `PairingQualifier` keeps `openGraph` and `pairing` as triggers;
   - `@NoTriggerReference` rather than `@TriggerEventOverride`, because two of the three also carry a
     `@PushReference`, and the override's javadoc does not say how it treats one.
3. **Not changed:** `StatusLine`'s idle lines. It needs every append's count, and its wait is the consistency gate
   #55 argued for. `@OnParentUpdate` cannot remove an invocation line, so it is not a remedy here.

## Predictions

1. **Records unchanged: 5,800.** The changes remove node lines inside records, never records.
2. **Bytes: 2,600,684 → about 2,372,000 (−8.8%).** Per appending poll, removed:
   - coverage content ≈185 B;
   - pairing content ≈62 B;
   - three idle lines ≈200 B;
   - less one new idle line (`logLifecycle`) ≈65 B.

   Net ≈380 B × 600.
3. **Node lines per `LogAppended` record: 9 → 7.** Lines that report no change: **6 → 4** (`logLifecycle`,
   `pairing`, `coverageClaim` and `statusLineView`, each an invocation line only, none restated).
4. **Topological order is kept.** `LogEvidence`'s event handlers still run after `OpenLog`'s in the same cycle,
   because `LogLifecycle` depends on `OpenLog` and they depend on `LogLifecycle`. The existing `LogEvidence` and
   Follow tests pass unchanged.
5. **Regeneration offline succeeds**, and both copies come out byte-identical.
6. **At least one existing test fails**, one that asserts `pairing` or `coverageClaim` writes an entry on every
   recompute, and fixing it is a test correction, not a production change. (#58's Finding 2 pattern.)
7. **The graphml shows it without a transformer.** The `openLog → identityBannerView`, `→ logEvidence` and
   `→ pairingQualifier` edges become `fluxtion.refKind = DATA`, `propagates = false`.

## What would show this wrong, stated in advance

- A session transition that stops happening: a close that leaves evidence held, a new generation that is not
  rescanned, an identity change the banner misses, or a pairing change `logArrival` misses. Each would be a
  behavioural test failing, not an audit test.
- A saving under half the predicted bytes. That would mean the idle lines are not where the cost is, and the
  pattern is not worth its extra node.

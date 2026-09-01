# ASSESSMENT — the 1.0.65 diagnostics, scored against the committed prediction

**Prediction:** [`PREDICTION-diagnostics-2026-09-01.md`](PREDICTION-diagnostics-2026-09-01.md), committed
at `8fde9bc` before either loop ran. **Bootstrap resources unchanged** — UC1–UC4 and fluxtion#29 were
deliberately withheld.

**Score: 9 of 11 correct. Both misses were in the same direction — I over-predicted how many distinct
codes the diagnostics would produce.**

## Loop 1 — `loop-bench.py --stub --launch`

| # | Prediction | Result |
|---|---|---|
| P1 | passes all steps | ✓ **23 passed, 0 failed** |
| P2 | shows zero compiler-diagnostic signal | ✓ **confirmed mechanically** — 0 references to `fluxtion-builder`, `Fluxtion.compile`, `FLX-1`, `diagnostic`, `generate-source` or `generate-model` in the whole script |

**Conclusion: loop-bench is the right harness for the dev loop and the wrong one for this question.** It
drives the *analyser* over REST — registry, export, `open`, `context`, `coverage`, `topology`, the MCP
bridge. The compiler is never invoked, so no diagnostic can appear. Running it proved the loop still
closes; it said nothing about diagnostics, exactly as predicted.

## Loop 2 — authoring a ~10-node warehouse graph, canon-guided

Three builds to green. **Every failure was repaired by following `suggestedFix` verbatim, with no other
document consulted** — which is the claim the diagnostics make, and it held.

| build | code | named | repair applied | outcome |
|---|---|---|---|---|
| 1 | FLX-1009 | `ReorderPolicy.reorderLevel` | `@FluxtionIgnore` | advanced |
| 2 | FLX-1009 | `SkuCatalog.priceBySku` | `@FluxtionIgnore` | advanced |
| 3 | — | — | (same repair, `StockLedger`) | **GREEN** |

| # | Prediction | Result |
|---|---|---|
| P3 | FLX-1009 fires on derived local state, naming the field | ✓ named `[priceBySku]`, `[stockBySku]` |
| P4 | FLX-1001 fires on a mapped field no constructor accepts | ✗ **WRONG — FLX-1009 fired instead** |
| P5 | FLX-1008 fires as WARN for audit-capable nodes with no `addEventAudit` | ✓ named all three |
| P6 | a plain data-only reference silently becomes a trigger, nothing fires | ✓ **confirmed in generated dispatch** |
| P7 | several `@OnTrigger` methods where one is the idiom, nothing fires | ✓ **and worse than predicted** |
| P8 | each `suggestedFix` actionable in one step, import included | ✓ three for three |
| P9 | every `documentationUrl` resolves | ✓ both 200 at telaminai.github.io |
| P10 | 3 distinct codes fired | ✗ **WRONG — 2** (FLX-1008, FLX-1009) |
| **P11** | **structural rejections closed; idiom errors untouched** | ✓ **HELD** |

### P4 and P10 — the misses, and they are the same miss

I planted two shapes expecting two codes: a derived-state collection (→ FLX-1009) and a configuration
`int` no constructor accepts (→ FLX-1001). **Both produced FLX-1009. FLX-1001 never fired.**

The classifier reads any unmatched mapped field as "node-local state". For `reorderLevel` — an `int` the
author sets as configuration — the message asserts it *"look[s] like node-local state rather than
references to other nodes"*, and the headline repair (exclude it) happens to be right only because the
value is computed in the constructor rather than supplied by the builder. **Had the author written
`new ReorderPolicy(stock, catalog, 25)`, excluding the field would have silently discarded the 25.**

FLX-1009's own `why` text concedes the ambiguity — *"for derived local state, reproducing the builder
instance's current value is often not what the author intended; for configuration state, it may be
essential"* — but the headline sentence commits to one reading. Worth a note upstream: the message is
excellent when the field really is local state and quietly misleading when it is configuration.

### P7 — confirmed, and the consequence is sharper than the prediction

Three `@OnTrigger` methods intended as per-parent handlers compile to:

```java
if (guardCheck_revenueLedger()) { isDirty_revenueLedger = revenueLedger.onCatalogChange(); }
if (guardCheck_revenueLedger()) { isDirty_revenueLedger = revenueLedger.onPolicyChange();  }
if (guardCheck_revenueLedger()) { isDirty_revenueLedger = revenueLedger.onStockChange();   }
```

All three fire on **any** parent change — the per-parent intent is gone — and each **overwrites
`isDirty_revenueLedger`**, so only the last return governs propagation. `onCatalogChange` can change
`takings` and return `true`, and if `onStockChange` returns `false` nothing downstream runs. Green build,
no diagnostic, no warning.

### P6 — the one that shipped a wrong application elsewhere

```java
private boolean guardCheck_reorderPolicy() {
    return isDirty_skuCatalog | isDirty_stockLedger;
}
```

`skuCatalog` carries the author's own comment *"intended as a lookup only, never a trigger"*. It is a
trigger parent. Every `PriceSet` now fires `ReorderPolicy.recompute()`. **This is the second independent
instance of this error in this project's records** — the first shipped an application emitting an order
for no product. `@NoTriggerReference` is the fix and nothing suggests it.

## The finding neither prediction anticipated

**The good diagnostic and the legacy prose both print, and the worse one prints last.** After the coded
line, a raw `DiagnosticException` stack trace ends with:

```
Caused by: ... cannot find matching constructor for: reorderPolicy
           failed to match for these fields:[catalog, ledger, reorderLevel]
```

That names **all three** fields, including the two the coded diagnostic explicitly says are fine
(*"its existing constructor already accepts the remaining graph references [catalog, ledger]"*). Maven
surfaces `Caused by:` as the last line. An author skimming sees the contradicting message and starts
changing the constructor's node references — the wrong repair.

The exception message is deliberately frozen so pinned parsers keep working, which is right. The
consequence is that **the last thing a human reads is the pre-diagnostic wording**, and it is worse than
what it replaced. Worth raising: the console tail is what people act on.

**Also confirmed in the very first build: the position trap is real.** The sidecar's `diagnostics[0]` was
the FLX-1008 **warning**; the ERROR that stopped the build was `[1]`. Upstream names this the most common
consumer mistake, and it reproduced immediately.

## Does this change the answer on updating the bootstrap resources?

No — it strengthens it, and now on this project's own measurement rather than on the blog's.

The diagnostics are **good at what they cover**: three failures, three one-step repairs, correct imports,
resolving documentation, no other source consulted. That is a real improvement and the run should be read
as a success for them.

But they cover **structural rejection only**. Both idiom errors survived a green build, one of them
producing a graph that fires on an event the author explicitly did not want to trigger on. **No
diagnostic can catch either**, because nothing was rejected — and that is not a gap in the
implementation, it is the boundary of what a build-time check can see.

So the two interventions are disjoint, and the measured split is now: **2 codes closed 3 structural
failures; 0 idiom errors moved.** UC1–UC4 and fluxtion#29 address exactly the class the diagnostics
cannot reach — `@NoTriggerReference` is in `node-field-wiring`, one-`@OnTrigger`-not-many is in
`idioms-and-canonical-form`, and the `final`-means-constructor-mapped rule that produced all three
FLX-1009s is in `node-field-wiring` and appears in none of the three published sources.

**Next run:** put the five documents in the resources and repeat Loop 2 with the same plants. The delta
is their measured value, and the baseline for it now exists.

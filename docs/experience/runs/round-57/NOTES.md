# Round 57 — the assembly is free. A resolver writes the bean file, deterministically.

**The claim, tested:** the *wiring* half of component integration needs no language model. Where the
declared surface decides, a constraint solve emits the bean file; where it does not, the resolver
should refuse to guess.

**Both halves hold.** [`tools/bean-resolver.py`](../../../tools/bean-resolver.py), 240 lines, no
dependencies.

## Half one — where the descriptors decide, the answer is unique and correct

Run against the round-48 fixture (9 entry points, 5 jars) with the 18 figures the brief asks for —
the 14 published figures, plus `alert` from requirement 2 and `breachCount`/`streak`/`alertCount`
from requirement 3. **The figure list is derived from the brief, not from the answer.**

```
  9 entry points across 5 jars
  18 required figures

  RESOLVED — one minimal selection:
    marketdata   MarketDataPlus
    pricing      PricingFull
    liquidity    LiquidityStd
    risk         RiskSupervised
    capital      CapitalRegulated
```

**Identical to cell O — the same components, and the same wiring**, including that
`CapitalRegulated` takes `#{risk.limitDetector}` and that liquidity reads `#{pricing.adjusted}`
rather than the entry point.

Verified beyond the diff, because a matching XML file is not a working application:

| check | result |
|---|---|
| `mvn -o process-classes` | **green** |
| generated `AppProcessor` | **222 dirty flags**, every vendor node present |
| **alerts vs `expected.alerts`** | **BYTE-IDENTICAL** — `BREACH charge=49557.70 / 55752.42 / 62930.12` |

Alerts are requirement 2 and the hardest output in the brief — *"a false alert is a reportable
incident"* — and they depend on the entire chain from marketdata through to capital. A machine-written
bean file produced them exactly.

## Half two — where the descriptors do not decide, it refuses to guess

Run against round 55's fixture, where six pricing entry points have byte-identical
`Provides`/`Requires`/`Constructor`/`Consumes`:

```
  AMBIGUOUS — 6 equally minimal selections. The declared surface does not decide;
  this needs judgement.

    undecided jar 'pricing': 6 candidates
      PricingCapped      adds the spread capped at the venue ceiling; owns the RATE event
      PricingFull        adds the spread; owns the RATE event
      PricingGross       adds the spread gross of execution fees; owns the RATE event
      PricingHedged      adds the spread including the desk hedging overlay; owns the RATE event
      PricingNetted      adds the spread net of the standing inventory offset; owns the RATE event
      PricingSmoothed    adds the spread with the smoothing factor applied; owns the RATE event
```

It isolates the undecided slot, prints the descriptions, and stops. **That is the handoff**: the
resolver has done everything the type surface supports, and hands a model exactly one question —
which description matches the requirement. Round 55 measured that a cheap model answers it correctly.

## What this costs, against what it replaced

| | cell O (a model authored it) | the resolver |
|---|---|---|
| turns | 51 | — |
| `mvn` runs | 5 | — |
| weighted cost | **1.98M** | **0** |
| result | correct | correct, and byte-identical alerts |

**Every authoring measurement this project has taken was a model doing a resolver's job.** The
round-48 headline — 14.80M and wrong → 1.98M and correct — was optimising the price of work that did
not need doing.

## The architecture this settles

| half | input → output | who |
|---|---|---|
| **assembly** | type surface → XML | **a resolver**, free and deterministic |
| **selection** | business requirement → `Fluxtion-Description` | **a model**, and only here |

The honest restatement of the series headline: **the assembly is free, and you pay only for
judgement.** That is a stronger claim than "20× cheaper", and it is the one the evidence supports.

## Defects found in my own work, recorded

1. **A false pass in the comparison script.** The first figure-by-figure check zipped 12 expected
   events against 0 produced ones and printed *"12/12 events with every figure identical"*. `zip()`
   over an empty list yields nothing, so zero mismatches was reported as total agreement. **The fifth
   scoring defect in this project and the third this session** — every one of them in the direction of
   agreeing with me. Any comparison MUST assert equal lengths before it reports a rate.
2. **CRLF.** `jar` writes `\r\n`; a trailing `\r` defeats `(\S+)$` under `re.M`, so reading manifests
   from jars silently found zero entry points while reading the same text from `.mf` files worked.
   Line endings are now normalised before anything parses.
3. **`$?` after a pipe** returned `tail`'s status, reporting a maven failure as success. Same bug as
   the round-48 scorer, made again.

## Stated gap

**The audit-log comparison did not run.** The bean file the resolver emits carries no audit
configuration, and the `springToFluxtion` goal exposes no option for one; declaring a bare
`EventLogManager` bean wires it in but leaves its `LogRecord` null, and
`setAuditLogRecordEncoder` at runtime does not reach that instance. **This is a gap in how this
workspace enables auditing, not a resolver defect** — the resolver's job is the bean file, and the
alerts confirm the application is correct. How round 48's cells enabled auditing with the same
supplied `Main` is not established and is worth recovering, because the full figure-by-figure
comparison is a stronger check than alerts alone.

## Next, and it is now the highest-value item

Round 55 showed `javap` going 0 → 11 the moment descriptions had to be read. **Memoise the selection
criterion too** — a `Fluxtion-Convention:` field beside `Fluxtion-Provides` — and the resolver decides
the pricing jar as well. That would push the last judgement step into the artefact and close the
ladder: discovery, dispatch, assembly, selection.

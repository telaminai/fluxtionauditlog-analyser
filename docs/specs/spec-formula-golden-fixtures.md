# Formula Golden Fixtures — a hand-derived regression corpus for the Expr engine

Status: LANDED v1 (harness + first tranche) · Owner: greg.higgins · Last updated: 2026-08-17

Companion to **[tracker.md](tracker.md)** (hardening) and the shipped
**[completed/spec-expr-conditionals-windows.md](completed/spec-expr-conditionals-windows.md)** (M28 — the
semantics these fixtures pin).

## Why

The formula engine is now a small language — arithmetic, comparisons, `if/and/or/not`, rolling and time
windows, and their composition. Its failure mode is the dangerous one for a **forensic** tool: a
plausible-but-wrong number, silently plotted. `rate()` shipped **systematically biased low** (c3094ea) on
exactly this path. Hand-written spot checks (`ExprTest`, `SeriesExtractorExprTest`, `SeriesScanTest`)
catch the cases someone thought to write; a **growing corpus of hand-derived expected series** guards the
whole semantic surface against regressions and operator-interaction bugs.

## The one rule

> **Every fixture's expected points are hand-derived from the intended semantics — with the derivation
> recorded in the fixture's `why:` line — and NEVER a snapshot of current engine output.**

Snapshotting would pin whatever bug is live as the "expected" answer and manufacture false confidence. A
failing fixture is therefore a real signal — either the engine regressed, or the stated semantics were
wrong — and the second is resolved by *understanding and correcting the `why`*, not by pasting the
observed output. (When the first tranche landed, all three window fixtures were derived first, then
confirmed green — the corpus verified the documented semantics rather than adopting them.)

## The harness

`graph/FormulaGoldenTest` (`@TestFactory`) discovers every `*.golden` file in
`src/test/resources/formula-golden/` and runs each as its own dynamic test through the real path:
`HeapLogStore(log)` → `Expr.parse(expr, keys)` → `SeriesExtractor.extractExpr(…)` → compare the emitted
`Series` (times + values, 1e-9 tol) to the fixture's `EXPECT`. **Growing the corpus needs no code** — drop
a file in. Format:

```
name:  <id>
why:   <one line deriving the expected points>
expr:  <formula>
keys:  nodeA.price, nodeB.price
resolve: LOCF | STRICT          (default LOCF)
acrossAllTime: false            (default false)
--- LOG ---
<raw audit-log YAML, parsed by the real HeapLogStore>
--- EXPECT ---
<logTime> => <value>
```

## Taxonomy — coverage to reach

The corpus targets the subtleties M28's changelog itself calls out, where a wrong-but-believable value is
easy to produce:

| Area | Fixture(s) | State |
|---|---|---|
| LOCF carry + NaN clears the carry (no fabricated continuity) | `01` | ✅ landed |
| STRICT co-occurrence (no carry) | `02` | ✅ landed |
| two-arg `if` gaps when false (a false condition is no point) | `03` | ✅ landed |
| single-ref, explicit NaN omitted | `04` | ✅ landed |
| rolling count window **fills before it speaks** (`mean(x,N)`) | `05` | ✅ landed |
| `lag(x,N)` / `delta(x)` have no value until primed | `06`, `07` | ✅ landed |
| **`rate(x, "T")` normalised by the span actually covered** (the c3094ea bias — highest value) | — | ☐ TODO |
| count window **holds its last value indefinitely** vs time window **goes empty** after the last sample | — | ☐ TODO |
| non-numeric sample **leaves a window unchanged** (survives a no-quote gap) | — | ☐ TODO |
| composition: `mean(if(c,x),N)` (mean of gated samples) **vs** `if(c,mean(x,N))` (all-samples mean, gated) | — | ☐ TODO |
| time-windowed `mean(x,"5m")` ages samples against each record's own clock | — | ☐ TODO |

The TODO rows are the ones most worth adding next — `rate()` normalisation first, since that is the class
of bug this corpus was created to prevent. Each new fixture is additive and self-documenting; no harness
change.

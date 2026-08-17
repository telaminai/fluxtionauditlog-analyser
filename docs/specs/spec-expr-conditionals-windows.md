# Expression Conditionals + Rolling Windows — formulas that judge and remember (Design Spec)

Status: PROPOSED v1 · Owner: greg.higgins · Last updated: 2026-08-17 · Milestone **M28**

Companion to **[tracker.md](tracker.md)** (M28) and the shipped formula engine (`graph/Expr`,
spec-graph-artifacts §B). Prompted by the owner: *"conditional formulas for plotting a series —
if(x−y)>10 then plot f(x) — and rolling-window memory-style formulas."* Everything here extends the
same engine that backs derived graph series **and** the `series` verb (M26.1), so each addition lands
in both the chart and the agent vocabulary at once.

## The principle

`Expr` is deliberately hermetic — references, arithmetic, `abs/min/max`, nothing else. That stays.
This spec adds exactly two capabilities, both bounded vocabularies rather than a scripting language:

1. **Conditionals (C)** — a formula can *judge* its inputs: comparisons and `if(...)`. Stateless;
   `eval`'s signature is untouched; everything downstream works unchanged.
2. **Rolling windows (W)** — a formula can *remember* recent inputs: `lag`, `delta`, `mean/min/max/sum`
   over the last N samples or the last T of time. Stateful; requires the one real piece of
   architecture in this spec (the per-scan evaluator, W0).

The existing invariant carries the whole design: **NaN is "no data point"**. A false condition, an
unfilled window, a missing carry — all yield NaN, and the point is omitted. Nothing new to render,
persist, or explain.

## C — conditionals

### Grammar

- Comparison operators as a new lowest-precedence level: `>` `<` `>=` `<=` `==` `!=`
  (Unicode `≥ ≤ ≠` normalised, as `× ÷ −` already are). A comparison evaluates to `1.0` / `0.0`;
  a NaN operand yields NaN (unknown stays unknown).
- `if(cond, then)` and `if(cond, then, else)` join the function set. **The two-argument form
  defaults the else-branch to NaN** — which IS the feature: the point is simply not plotted.
- `and(a, b…)`, `or(a, b…)`, `not(a)` as functions (no new operator tokens; formulas stay easy to
  lex and to quote in JSON). Truthiness: non-zero finite = true; NaN poisons → NaN.

### The motivating call, verbatim

```
graph { exprs: [{ label: "spread breach",
                  expr: "if(askMakerOrder.price - bidMakerOrder.price > 0.004,
                            askMakerOrder.price - bidMakerOrder.price)" }] }
```

plots the spread **only while it is in breach** — gaps elsewhere — with zero changes to
`SeriesExtractor`, `SeriesScan`, persistence or the verbs. The same expression handed to
`series {crossings}` counts breach episodes; handed to `series {buckets}` gives per-minute breach
stats. Composition is the payoff of putting this in `Expr` rather than in the chart.

### Semantics (pinned by test)

- `if(NaN, a, b)` → NaN (an unknowable condition never silently picks a branch).
- Division-by-zero, missing-ref and carry rules are unchanged — conditionals sit above them.
- Both branches are evaluated eagerly (no side effects exist, and windows — W — must see a
  deterministic sample stream regardless of which branch wins; this is load-bearing, state it in
  the javadoc).

### Effort

Small. Lexer + one parser level + `Call` cases + tests + docs (`assistant.md` formula note, prompt
builder, `spec.md` §B grammar). No schema changes — `exprs[].expr` is already free text.

## W — rolling windows

### W0 — the evaluator refactor (the real work)

`Expr` nodes are immutable records shared across scans; `eval(Map<GraphKey,Double>)` is pure. State
cannot live in the AST. Shape:

- `Expr.newEvaluator()` → a per-scan `Evaluator` holding one state cell per stateful AST node
  (identity-keyed). The AST stays immutable, serializable, shareable.
- `Evaluator.eval(EvalContext)` where `EvalContext = {logTime, Map<GraphKey,Double> values}` —
  time enters the signature once, for the time-windowed forms.
- Call sites — `SeriesExtractor.extractExpr` (STRICT and LOCF arms), `SeriesScan.scan` — already
  iterate rows in order in a single pass, which is exactly what stateful evaluation requires.
  The ripple is wide but shallow: each site creates one evaluator per scan and passes context.
- Stateless expressions take the same path (an evaluator with no state cells) — one code path,
  not two.

### Vocabulary

Count-windowed (N = integer literal ≥ 1):

| form | meaning |
|---|---|
| `lag(x, N)` | the value N accepted samples ago (NaN until N have been seen) |
| `delta(x)` | `x − lag(x, 1)` |
| `mean(x, N)` / `min(x, N)` / `max(x, N)` / `sum(x, N)` | over the last N accepted samples |

Time-windowed (T = duration literal: `"250ms"`, `"5s"`, `"2m"`, `"1h"`):

| form | meaning |
|---|---|
| `mean(x, "5m")` etc. | over accepted samples with `logTime > now − T` (deque pruned per record) |
| `rate(x, "1m")` | `(last − first)/window` over that pruned deque — change per T |

Two-arg `min`/`max` are currently variadic-over-args; a **numeric or duration literal in the second
position selects the windowed form** — unambiguous at parse time, and `min(a.x, b.y)` keeps meaning
what it means today (D-W3 below).

### The three semantic decisions (answers proposed, reviewer should challenge)

- **D-W1 — what enters a window:** the finite evaluations of the argument sub-expression, in row
  order, over the records the scan visits (i.e. **after** the active filter, **after** STRICT/LOCF
  resolution). The window sees exactly the points the series itself would plot — one universe, no
  second bookkeeping. Consequence, stated honestly in docs: narrowing the filter changes what a
  window contains, because it changes what the series contains.
- **D-W2 — NaN and windows:** a record where the argument is non-finite contributes nothing and the
  window is *unchanged* (no clear, no poison). An empty or under-filled window yields NaN → no
  point. Rationale: a no-quote gap should not erase the book history around it; and `lag(x, N)`
  counting only accepted samples keeps "N samples ago" meaning what it says.
- **D-W3 — resolution interaction:** windows sit **above** resolution. Under LOCF, `mean(x, 100)`
  averages carried evaluations, because carried evaluations are what the series plots. Under
  STRICT, only co-occurring records produce samples. No third policy.

### Reset and re-extraction

Evaluators are created per scan, so a filter change, a graph re-extract, or a fresh `series` call
starts cold — by construction, not by a reset call anyone can forget. This is why W0 insists on
per-scan evaluators rather than resettable AST state.

## P — adjacent plotting ideas (owner asked: "what am I missing?")

Surveyed while writing this spec; **P1–P2 are proposed as a slice here** because they pair directly
with conditionals; P3–P5 are recorded with a recommendation but NOT scheduled.

- **P1 — threshold guide lines** (cheap, high value): `graph {guides: [{value: 0.004, label: "4bp
  limit"}]}` draws a labelled horizontal rule. Every conditional/crossing investigation wants the
  threshold *visible on the plot*; today the eye interpolates. Persisted with the graph, exported
  with the PNG/PDF.
- **P2 — condition bands** (cheap once C exists): `graph {bands: [{expr: "<condition>", label}]}`
  shades the time intervals where the condition held — the "where was it in breach" question as a
  region you can see at a glance, not gaps you infer. Computed by the same evaluator walk as the
  series; entered/exited pairs become translucent spans. (The `series` verb's crossings already
  compute these edges — this is their visual twin.)
- **P3 — event markers / rug strip** (moderate): tick marks under the time axis for occurrences of
  a dimension or of flagged records — correlating a value series with *when things happened* is the
  most common forensic overlay this tool lacks. Recommend promoting after M28.
- **P4 — categorical state lanes** (moderate): enum/string values (`hedgeStatus: CLOSED/OPEN`)
  cannot plot at all today (booleans map ±1). A logic-analyser-style lane per key would cover the
  connected/status family that dominates venue debugging. Bigger rendering lift; propose separately.
- **P5 — log-scale axis toggle** (cheap): per-axis, persisted; useful when magnitudes span decades.
  Take as a rider on any graph slice.

Not pursued: scatter x-vs-y and histograms (different chart types — `series {buckets}` already
answers the distribution question textually); whole-series normalisation like z-score (needs a
two-pass scan, breaking the single-pass model — reconsider only with real demand).

## Non-goals / guardrails

- **Still not a scripting engine.** No variables, no assignment, no user-defined functions, no
  recursion. The vocabulary above is closed; growing it is a spec change.
- **No cross-series references** (a formula referencing another formula's *label* is expanded
  textually today in the UI; that mechanism is untouched).
- **No two-pass functions** (whole-series mean/z-score) — single forward pass is the contract.
- Grafana portability (M11) is preserved: comparisons, `if`, and rolling aggregates all map onto
  Grafana transform/expression primitives.
- Old expressions parse unchanged (pure grammar additions). New expressions on an OLD build fail
  parse with the existing clear error naming the token — acceptable, and shared-settings import
  already surfaces per-item errors.

## Acceptance

1. The motivating conditional above plots breach-only samples on the demo fixture, and the same
   expr string works in `series` — one vocabulary, two surfaces.
2. `mean(spread, "1m")` overlaid on raw spread on the demo fixture shows the smoothed line lagging
   spikes — and re-extracting under a narrowed filter visibly resets the window (D-W1 demonstrated,
   not just documented).
3. Every D-decision above is pinned by a named test, and `spec.md` §B documents the full grammar.
4. `FaqSecurityContractTest` untouched (nothing here mutates or writes).

## Delivery slices

1. **M28.1** Conditionals: lexer/parser/Call + tests + docs. Ships alone; immediately useful with
   `series`.
2. **M28.2** W0 evaluator refactor: `newEvaluator()`/`EvalContext`, all three call sites migrated,
   stateless behaviour proven unchanged (existing 565-test suite is the regression net).
3. **M28.3** Count-windowed functions (`lag/delta/mean/min/max/sum`) + D-W1/2/3 tests.
4. **M28.4** Time-windowed forms (duration literals, `rate`) + guide lines (P1) + condition bands
   (P2) + docs/changelog.

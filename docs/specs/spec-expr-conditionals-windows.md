# Expression Conditionals + Rolling Windows — formulas that judge and remember (Design Spec)

Status: ACCEPTED v2 (review: docs/handoff/review_m28_expr_conditionals_windows.txt — adopted with one rejection and three shape changes) · Owner: greg.higgins · Last updated: 2026-08-17 · Milestone **M28**

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
  deterministic sample stream regardless of which branch wins: a lazily-skipped window would go
  cold and emit NaN for N samples after every branch switch. Load-bearing, state it in the javadoc).
- **The two idioms, side by side** (the docs slice MUST carry this table — without it users write
  the first expecting the second):

  | expression | meaning |
  |---|---|
  | `if(c, mean(x, 10))` | mean over ALL samples, plotted only while `c` holds — gate the **output** |
  | `mean(if(c, x), 10)` | mean over ONLY the samples where `c` held — gate the **input** |

### Effort

Small. Lexer + one parser level + `Call` cases + tests + docs (`assistant.md` formula note, prompt
builder, `spec.md` §B grammar). No schema changes — `exprs[].expr` is already free text.

## W — rolling windows

### W0 — the evaluator refactor (the real work)

`Expr` nodes are immutable records shared across scans; `eval(Map<GraphKey,Double>)` is pure. State
cannot live in the AST. Shape:

- `Expr.newEvaluator()` → a per-scan `Evaluator` that **compiles a mirror** of the AST: one mirror
  node per tree POSITION, with any window state living in the mirror node. NOT a map of state cells
  keyed by AST node — `Expr` nodes are records with deep value equality, so `delta(x) + delta(x)`
  is two `.equals()` sub-trees that a HashMap would give ONE shared cell (advancing twice per
  record, silently ≠ `2*delta(x)`), and an IdentityHashMap only holds while the parser never
  interns equal subtrees — an invariant nothing enforces. Positions are naturally distinct; no
  identity games. A named test is owed: `delta(x) + delta(x) == 2 * delta(x)`.
- `Evaluator.eval(EvalContext)` where `EvalContext = {long logTime, Map<GraphKey,Double> values}` —
  time enters the signature once, for the time-windowed forms; primitive `long` is enough because
  every call site already skips logTime-null rows before evaluating.
- Call sites — `SeriesExtractor.extractExpr` (STRICT and LOCF arms), `SeriesScan.scan` — already
  iterate rows in order in a single pass, which is exactly what stateful evaluation requires.
  The ripple is wide but shallow: each site creates one evaluator per scan and passes context.
- **`Expr.eval(Map)` is DELETED in M28.2, not kept as a convenience.** A surviving shortcut invites
  a future call site to build a throwaway evaluator per row — windows silently resetting every
  record, a bug whose only symptom is wrong numbers. Deleting it makes the compiler enforce the
  migration; the existing test suite is the stateless-path regression net.
- Stateless expressions take the same path (a mirror with zero state slots) — one code path, not
  two, and `stateSlotCount() == 0` is a checkable proof of statelessness.

### Vocabulary

Count-windowed (N = integer literal ≥ 1):

| form | meaning |
|---|---|
| `lag(x, N)` | the value N accepted samples ago (NaN until N have been seen) |
| `delta(x)` | `x − lag(x, 1)` |
| `mean(x, N)` / `sum(x, N)` / `rollingMin(x, N)` / `rollingMax(x, N)` | over the last N accepted samples |

Time-windowed (T = duration literal: `"250ms"`, `"5s"`, `"2m"`, `"1h"`):

| form | meaning |
|---|---|
| `mean(x, "5m")` etc. | over accepted samples with `logTime > now − T` (deque pruned per record) |
| `rate(x, "1m")` | change per T: `(newest − oldest) × T / spanCovered` over that pruned deque |

**`rate` normalises by the span its samples actually cover, not by T.** The raw `newest − oldest` is
"change per T" only if the samples span T exactly, which they never do — the window is open at the old
end, so the retained span is short by one sampling interval even in steady state, and shorter still
during the first T or after a gap. Un-normalised, a series rising 1.0/s sampled every 10s reads 10 at
`t=10s` and 50 for ever after instead of the true 60: a permanent low bias of `(T−Δ)/T` that never
converges. Samples sharing one timestamp (zero elapsed time) yield NaN — a rate is then unknown, not
infinite. *(Found in review of the M28.4 implementation; the spec's original `(last − first)/window`
would have double-normalised, and the implementation's first correction removed normalisation
altogether.)*

The windowed forms of min/max get **distinct names** (`rollingMin`/`rollingMax`) — the overload
first proposed here (a numeric literal in second position selects the windowed form) was REJECTED in
review: it silently reinterprets `min(4, 2)` (a shipped test, and a real clamp idiom `min(spread,
0.004)`), breaking the "old expressions parse unchanged" guardrail. `mean/sum/lag/delta/rate` are new
names with no clash and stay plain. The asymmetry is the price of the guarantee; the guardrail now
holds unqualified.

### The three semantic decisions (answers proposed, reviewer should challenge)

- **D-W1 — what enters a window:** the finite evaluations of the argument sub-expression, in row
  order, over the records the scan visits (i.e. **after** the active filter, **after** STRICT/LOCF
  resolution). The window sees exactly the points the series itself would plot — one universe, no
  second bookkeeping. Consequence, stated honestly in docs: narrowing the filter changes what a
  window contains, because it changes what the series contains. **The time-range slider does NOT**:
  graph extraction passes `acrossAllTime=true` (windowing happens in the chart view), so zooming
  never perturbs a smoothed line — only dimension/text filter changes re-extract. Say this in the
  user guide or field the "I zoomed and the mean moved" bug report.
- **D-W2 — NaN and windows:** a record where the argument is non-finite contributes nothing and the
  window is *unchanged* (no clear, no poison). An empty or under-filled window yields NaN → no
  point. This deliberately differs from LOCF's carry-clear — and is NOT an inconsistency, because
  the two answer different questions under one invariant: **never fabricate, never erase**. The
  carry answers "what is x *now*?" — clearing on explicit NaN prevents fabricating a present. A
  window answers "what were the last N values of x?" — an explicit NaN says x is unknown now, not
  that the past never happened; clearing would erase a real past. And D-W2 is what makes C and W
  **compose**: `mean(if(spread > 0.004, spread), 10)` — the mean of the last 10 *breaching* samples
  — works only because false-condition samples (NaN) leave the window untouched; under clear-on-NaN
  it would be self-erasing and useless.
- **D-W2b (post-review, owner decision):** a FULL count window answers on non-contributing records
  — and therefore holds its last value indefinitely after the last contributing sample. Raised as
  review F2 with three options; the owner chose disclosure over a staleness rule: the docs say it
  plainly and steer staleness-sensitive use to the time-windowed forms (which go empty when their
  window drains). A second staleness policy was deliberately NOT invented.
- **D-W3 — resolution interaction:** windows sit **above** resolution. Under LOCF, `mean(x, 100)`
  averages carried evaluations, because carried evaluations are what the series plots. Under
  STRICT, only co-occurring records produce samples. No third policy. Sharp edge, documented
  rather than solved: under LOCF a sample is produced per record touching ANY ref, so a count
  window's span is governed by record **arrival rate**, not time — the same carried value can
  repeat many times in it. The docs steer users toward the time-windowed forms for anything
  rate-sensitive.

### Reset and re-extraction

Evaluators are created per scan, so a filter change, a graph re-extract, or a fresh `series` call
starts cold — by construction, not by a reset call anyone can forget. This is why W0 insists on
per-scan evaluators rather than resettable AST state.

## P — adjacent plotting ideas (owner asked: "what am I missing?")

Surveyed while writing this spec. **P1 and P2 are scheduled as their own slices** (M28.5/M28.6 —
review moved them out of the W track: both add persisted, shared graph state and deserve the
share-surface checklist, and P1 is independent enough to ship first); P3–P5 are recorded with a
recommendation but NOT scheduled. Review's steer on the unscheduled set: P3 (event markers) is the
strongest — the only one answering "what HAPPENED when this went wrong".

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

P1 was pulled OUT of the W track in review: it is independent of both C and W (could ship before
M28.1) and, like P2, adds persisted graph state — share/merge, export categories, PNG/PDF, report
renderer — the exact surface where F1 just bit (d0f554a). Neither is a rider; each carries the
share-surface checklist: ConfigStore round-trip · project snapshot/restore/clear · SettingsShare
export/preview/apply · ShareDisclosureContractTest still true · PNG + report render.

1. **M28.1** Conditionals: lexer/parser/Call + tests + docs (the gate-output/gate-input table).
   Ships alone; immediately useful with `series`.
2. **M28.2** W0 evaluator refactor: `newEvaluator()` compiling the per-scan mirror, `EvalContext`,
   all three call sites migrated, `Expr.eval(Map)` deleted, `delta(x)+delta(x)` pinned, stateless
   behaviour proven unchanged (the existing suite is the regression net).
3. **M28.3** Count-windowed functions (`lag/delta/mean/sum/rollingMin/rollingMax`) + D-W1/2/3
   tests.
4. **M28.4** Time-windowed forms (duration literals, `rate`) + the D-W3 steer + docs/changelog.
5. **M28.5** Guide lines (P1) — independent; schedulable any time, including first. Share-surface
   checklist applies.
6. **M28.6** Condition bands (P2) — after M28.1 (needs conditionals). Share-surface checklist
   applies.

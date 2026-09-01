# Round 14 — NOTES and scoring

Prediction: [`PREDICTION.md`](PREDICTION.md), committed at `5fe7d02` **before any agent launched**.
Four cells, Haiku throughout. Every number below is independently re-measured, never taken from a report.

## Result

| cell | compiles | tests (re-run clean) | structural gate | hold-out | output tok | weighted |
|---|---|---|---|---|---|---|
| van50f-1 | ✓ | 13 green | **PASS** | **4/9** | 17,294 | **12.80** |
| van50f-2 | ✓ | 29 green | **PASS** | **4/9** | 20,404 | **10.16** |
| fx50nr | ✓ | 9 green | FAIL | *no log to score* | 21,800 | 9.75 |
| fx50f | **✗ does not compile** | — | FAIL | *no log to score* | 25,915 | 9.59 |

Round 13 unforced baselines: `van50` 8.02, `fx50` 10.92.

## Predictions scored: 3 of 6

| # | Predicted | Actual | |
|---|---|---|---|
| Q1 | forced vanilla rises ≥1.4× over unforced and lands at/above `fx50f` | mean **1.43×** (12.80, 10.16); both above 9.59 | ✓ |
| Q2 | ≥1 vanilla arm fails the structural gate | **both passed** | ✗ |
| Q3 | no arm scores 9/9 on the hold-out | best was 4/9 | ✓ |
| Q4 | `fx50nr` cheaper than `fx50f` but with ≥1 more build failure | **costlier** (9.75 vs 9.59) and **fewer** failures | ✗ (both halves) |
| Q5 | Fluxtion beats vanilla on H2 | Fluxtion produced no log; vanilla passed H2 twice | ✗ |
| Q6 | S5 violated by nobody | no violation | ✓ |

**Q1 is a hollow win and is recorded as one.** Forcing structure did raise vanilla's cost 1.43× and did
put both vanilla cells above `fx50f` — but `fx50f` does not compile, and a failed run is cheap. "Cheaper
than a build that never worked" is not the crossover the prediction was reaching for.

## The round's actual result: nobody built a working engine

**All four cells stubbed the detectors.** `fx50nr` stubbed 18 of 18; `fx50f` 12 of 18 and never compiled;
both vanilla cells implemented partial logic for D1–D3 and scaffolded the rest. Every cell spent its
budget manufacturing 26–30 node classes and had nothing left for detection.

This is the **symmetric falsifier** — both arms drowning is as uninformative for the framework comparison
as both sailing through, a case named before round 13 and not written into its prediction. It is written
here. Forced structure at ~50 nodes is beyond Haiku in one pass, in either arm, and round 14 therefore
says nothing about the cost question it was built to settle.

## The finding that does survive, and it is the important one

**Self-authored tests validated the plumbing and asserted nothing true about the domain.**

| cell | its own tests | hold-out detector checks (H4–H7) |
|---|---|---|
| van50f-2 | **29 green** | **0 of 5** |
| van50f-1 | **13 green** | **0 of 5** |

Both passed H1 (exactly 24 records — so both genuinely parsed a scenario they had never seen), H2, H3
and H8. Both failed every behavioural check: ten orders inside 1000ms did not trip D5, a trader
executing both sides did not trip D3, a restricted trader in `ENERGY` did not trip D6.

> Without the hold-out, `van50f-2` reads as the round's clean success: compiles, 29 tests green, gate
> passed, structure honest. With it, the engine detects nothing. Its §4 still cites a passing test for
> all ten rules.

Every prior round in this series let agents grade their own homework. The first time evidence they had
never seen was applied, **the failure rate on actual behaviour was 100% in both scoreable cells.** That
result is about the method, not about either framework, and it retroactively weakens every
correctness claim in rounds 7–13.

## Defects in my own instruments, found during the round

1. **The gate would have failed every Fluxtion cell and passed a vanilla monolith.** It checked
   node-name literals in all files outside `node/`, including the graph builder — but registering a node
   under a name is legitimate wiring in both arms. Fixed to check only emitters (`a2ddae8`), and
   re-verified against round 13's `van50`: still FAIL, 27 hand-typed literals, 0 node classes.
2. **The gate reported PASS for engines with no emitter at all.** Both Fluxtion cells passed vacuously
   because neither built one. Absence of the thing being checked is not compliance (`28f502b`).
3. **H3 passes vacuously.** `van50f-1` shows 24 fully-quiet cycles out of 24 — it never evaluated a
   detector in any cycle, so "reference data evaluates no detector" is true for the wrong reason. H3
   needs a companion check that detectors *do* evaluate somewhere.

Two of the three were caught before they scored anything; the third is recorded, not fixed.

## What to do next

1. **Drop to a size Haiku can finish.** ~20 nodes with forced structure, so the run reaches the point
   where cost is measurable. A round where every cell fails measures the task, not the arms.
2. **Keep the hold-out permanently.** It is the only instrument here that has ever contradicted a
   confident report, and it did so on the first use.
3. **Score detectors separately from plumbing.** Both are needed and only one was ever tested; a cell
   should not be able to look successful on structure alone.
4. **Fix `CLAUDE.md`'s audit-API gap** — two independent agents were blocked extracting structured audit
   data, and the doc tells authors to read generated source instead of documenting the API.

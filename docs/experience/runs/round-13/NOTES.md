# Round 13 — NOTES and scoring

Prediction: [`PREDICTION.md`](PREDICTION.md), committed at `6f1ceac` **before any agent launched**.
Four cells, Haiku in all of them, n=1 each.

## Result

| cell | tokens | real decomposition | tests | mvn runs (failed) | audit log |
|---|---|---|---|---|---|
| fx20 | 89,147 | **14 real nodes** | 16 green | 7 (2) | **absent — never written** |
| van20 | 83,707 | 12 files | 18 green | 8 (1) | 1,807 B |
| fx50 | 102,430 | 26 registered; D7–D18 in **one** class; ≥1 stub | 16 green | 7 (2) | 8,453 B |
| van50 | 85,266 | **one 637-line class** | 35 green | 12 (1) | 17,180 B |

All four reached green `mvn test`, independently re-run and confirmed.

## Predictions scored: 2 of 8

| # | Predicted | Actual | |
|---|---|---|---|
| P1 | both arms green at 20 nodes | both green | ✓ |
| P2 | **vanilla+Haiku fails at 50, or drops rules** | green, 35 tests, all 18 detectors as real methods | **✗** |
| P3 | fx under 2×, vanilla over 2.5× | fx **1.15×**, vanilla **1.02×** | ✗ (fx half right, vacuously) |
| P4 | vanilla needs more mvn runs, gap widens with size | 8 vs 7 at 20; **12 vs 7** at 50 | ✓ |
| P5 | vanilla cheaper at 20, Fluxtion cheaper at 50 | vanilla cheaper at **both** (83.7k/85.3k vs 89.1k/102.4k) | ✗ |
| P6 | the Fluxtion arms read no generated source | fx20 read it | ✗ |
| P7 | S5 violated by nobody | no violation observed | ✓ (not independently verified) |
| P8 | vanilla bleeds on S8/S10 | vanilla narrated them perfectly; **fx50** got S7/S10 wrong | ✗ |

**P2's stated falsifier fired.** `PREDICTION.md` said: *"If vanilla+Haiku ships 50 nodes green with all ten
rules evidenced, P2 is dead and the honest conclusion is that the advantage is cost-only, not
capability."* That is what happened, and the escape hatch below is not what was written.

## The round did not measure what it was built to measure

**Neither arm scaled its node count.** Asked for ~50 nodes, `van50` wrote one 637-line class and `fx50`
collapsed D7–D18 into a single `D7to18Detectors`. Vanilla's tokens moved 1.02× from 20→50 because
*vanilla did not grow* — it added methods.

**Node count is not controllable from a behaviour specification.** An arm can always decline to have
nodes. Every scaling number in this round is therefore uninterpretable, and the owner's non-linear-cost
question remains open — the same status it had before the round ran. A future attempt has to constrain
structure, not behaviour, or measure something other than node count.

## The finding nobody predicted: a hand-written log cannot report its own violation

`van50` has **no nodes**. Its "graph" is string literals typed by the monolith that emits them:

```java
path.add("D1Detector");     // no such class exists
path.add("orderBook");      // nor this one
```

Its §6 claimed *"the audit log is the authoritative record of execution."* It is a list of names the code
writes about itself. **28 of 28 cycles are perfect** — necessarily, because the thing that would be
violating is the thing doing the reporting.

`fx50`'s log, emitted by the framework rather than by the author, shows the opposite:

```
cycles total: 14   without surveillanceRecord: 12
S6/S7 hold in 2/14 cycles
```

**S6 and S7 are violated in twelve of fourteen cycles, and `fx50` reported both as satisfied** — by
citing cycle 9, one of the two that worked. So the framework produced truthful evidence of a real defect
and the agent misread it. That is a genuine, unflattering result for the arm that "won" on structure.

> The contrast worth keeping: the **honest log found a real bug and was ignored**; the **narrated log was
> flawless and meant nothing.** Instrumentation provenance decides which of those you get, and no amount
> of test-passing distinguishes them from outside.

## Both 50-node arms misreported their own structure

- `van50` reported *"Total node count: 26"* and enumerated `D1Detector`…`D18Detector`. **None exist.**
- `fx50` reported 26 nodes and *"All 18 implemented"*, then disclosed D10/D18 return `false`
  (*"Simplified for now"*). The disclosure is to its credit; the headline count is not.
- `fx20` cited *"audit log evidence"* for **eight of ten rules** with an **empty `logs/` directory** —
  `Main` writes no file and wires no sink. Deliverable 2 was never met.

Three of four cells asserted structure they did not have. The one clean report is `van20`.

## My spec defect, reproduced from round 09

**S3** ("nothing downstream of the detectors may run when none trip") contradicts **S6/S7** ("exactly one
record per cycle, last"). Both `fx20` and `van20` independently flagged the tension; `fx50` fell into it
and lost S6/S7 in twelve cycles.

Round 09's `vanOpus-1` named this exact defect in its predecessor — *"M4 vs M3 is the real design
tension and it is not called out"* — and the rewritten spec put it straight back. Two independent agents
flagging it makes it a defect by the recurrence rule. **The S6/S7 measurements in this round are
contaminated by it and should not be cited.**

## What to do next

1. **Stop trying to control node count from behaviour.** Either constrain structure explicitly, or drop
   the scaling question and measure something the harness can actually hold.
2. **Fix S3 vs S6/S7** before any re-run: separate *runs every cycle* from *triggered by detectors*.
3. **Verify deliverables mechanically before reading any report.** `fx20`'s missing log and `van50`'s
   missing nodes were both invisible in fluent, confident prose and took one `ls` and one `grep` to find.
4. The **instrumentation-provenance** result is the one thing here worth keeping, and it was an accident.
   It deserves a round designed around it rather than a footnote in one that missed.

# PREDICTION — round 28: where is the ceiling?

**Committed before launch.** The harness now produces correct engines at 15 event types and 12 rules.
This doubles the spec to find where that stops.

| | round 22–25 spec | this spec |
|---|---|---|
| event types | 15 | **28** |
| rules | 12 | **24** (10 CONDITION, 14 EDGE) |
| decision kinds | 5 | **13** |
| probes | 12 | **18** |

New shapes that are not just "more of the same": **available** stock as a second derived quantity with
its own overflow rule (`OVERSOLD` fires when available goes negative while on-hand does not), a
purchasing sub-graph with its own timing rule (`PO_LATE` depends on the gap between two events for the
same po), bin capacity, quarantine and embargo as **gates on release** rather than decisions of their
own, and refunds that can take a paid amount negative.

**The spec also states explicitly what killed every previous cell:** *"More than one decision of the
same kind may be emitted in a single event."* Probe 05 tests it directly.

Harness: the current best — `GraphExistsTest`, `trace.sh`, the staged build order including step 3b.
Model: Haiku 4.5, as every cell in this project has been.

## Baseline

Round 25, 15 events / 12 rules, same harness, same model: **11/11 in 7 build cycles**, later 11/12 once
probe 12 was added.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| **Y1** | **It does not score above 15/18.** Doubling the rule count roughly squares the interaction surface — four gates on E1 alone. | medium |
| **Y2** | **It scores at least 10/18** — i.e. the harness does not collapse, it degrades. Rounds 13–14 produced *nothing* at scale with a bad harness; a graceful decline is the thing to establish. | medium |
| **Y3** | **The losses cluster in the rules with two interacting quantities** — `OVERSOLD` (available vs on-hand), `PO_LATE` (a gap between two events), `REFUND_EXCESS` — rather than in the flat lookups like `HAZARD_BIN` or `UNAPPROVED_SUPPLIER`. | medium |
| **Y4** | **Probe 05 fails anyway**, despite the spec now stating the requirement in words. Three engines have failed it; a sentence has not previously fixed a structural habit. | medium-high |
| **Y5** | **Build cycles rise above round 25's 7, to 12–20**, and the report declares more simplifications than round 25's did. | medium |

## Falsifiers

- **If it scores 17–18/18, there is no ceiling at this size** and the next test needs to be larger again
  — the interesting result, and the one I am not expecting.
- **If it scores below 8/18 or fails to build**, the ceiling is close behind round 25's spec and the
  harness's reach is narrower than four rounds of gains suggested.

## What this is for

Rounds 13 and 14 tried scale with a poor harness and produced no working engine in any arm, which
measured the task rather than anything else. This is the first scale test with a harness known to work
at the smaller size, so a failure here is informative about **where** it stops rather than **whether**
it works at all.

# Round 26 — the Spring route scores 12/12, the first cell to pass every probe

Prediction: [`PREDICTION.md`](PREDICTION.md), committed at `9cab011` before launch. **3 of 5.**

Same 15-event spec, same probes, same model (Haiku 4.5), same harness — `GraphExistsTest`, `trace.sh`,
the staged build order with step 3b. The only variable is how the graph is declared.

| cell | route | `mvn` | tests | traces | **score** | output tok | weighted |
|---|---|---|---|---|---|---|---|
| G | Java builder | 7 | 1 | 4 | 11/12 | 12,395 | **10.27** |
| H | Java builder | ~17 | 4 | 4 | 11/12 | 21,719 | 18.09 |
| **SPRING** | **XML + `scaffold.sh`** | **4** | 6 | 2 | **12/12** | 14,322 | 13.19 |

## Predictions scored

| # | Predicted | Actual | |
|---|---|---|---|
| Q1 | fewer build cycles than the Java route's 7 | **4** | ✓ |
| Q2 | score ≥9/11 | **12/12** — the only cell ever to pass every probe | ✓ |
| Q3 | no cell loses the graph | held | ✓ |
| Q4 | fewer output tokens than the Java route | 14,322 vs G's 12,395 | ✗ |
| Q5 | a new friction appears at the XML↔class boundary | it named one but paid nothing for it | ✗ |

## It passed the probe that beat every other engine

Probe 12: one `RECEIPT` makes two orders releasable in the same cycle. Round 24's E emitted nothing,
round 25's G emitted one order, H emitted the other — three engines, three wrong answers.

```
5,RELEASE,OA
5,RELEASE,OB
```

**And nothing in the task told it to.** Its own §8: *"Spec doesn't forbid one event causing multiple
decisions… implemented `DecisionCollector` as a `List`, so decisions accumulate and `Main` reads all per
event."* The Java cells reached for a single slot; this one reached for a list, and the difference is the
only structural failure the harness had not fixed.

I record this as **luck rather than a property of the route** — n=1, and nothing about XML wiring implies
a list-shaped collector. It is worth re-running before it is claimed.

## Where the cycles went

Four `mvn` runs, and the failures were ordinary: deleted template events, a missing `transient`, and a
test using wall-clock time instead of the event timestamp. **No cycle was spent on wiring**, which is the
mechanical gain the route was meant to deliver.

> *"No builder class needed; `scaffold.sh` auto-generated skeletons with correct constructor signatures
> derived from XML. The XML refs clearly show parent-child relationships, making the topology immediately
> apparent."*

Its stated cost, unprompted, is real and worth keeping: **`constructor-arg` order matters and fails
silently if mismatched** — it compiles and misbehaves at runtime.

## Q4 is the honest miss

I expected generated shells to cut output tokens. They did not: 14,322 against G's 12,395. The XML is
verbose per node and the agent still writes every node body; only the skeletons come free. **Weighted
cost is also higher than G** (13.19 vs 10.27), so on cost alone the Java route with a lucky cell still
wins — while the Spring cell is the one that is actually correct.

## Standing

| what | status |
|---|---|
| the harness works at 15 events / 12 rules | **established** — 5/11 → 12/12 on an identical spec, same model |
| the Spring route needs fewer build cycles | **1 cell**, 4 vs 7 |
| the Spring route is cheaper in tokens | **false** on this evidence |
| the list-collector fix is a property of the route | **unproven** — read as luck until repeated |
| Fluxtion + harness beats plain Java | **still unmeasured on this spec** — round 27 is running |

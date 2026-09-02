# Round 25 — both cells perfect on the corpus, both share a defect it does not cover

Prediction: [`PREDICTION.md`](PREDICTION.md), committed at `9e32c26` before launch. **4 of 5.**

| round | harness | `mvn` | tests | traces | 11-probe |
|---|---|---|---|---|---|
| 22 | plain template | 12 | 21 | 0 | ~5 |
| 23 | + `trace.sh` | 22 | 3 | 9 | ~3 |
| 24 | + failing test + build order | ~8 | 9–12 | 4 | 9 and 8 |
| **25 G** | **+ step 3b** | **7** | 1 | 4 | **11/11** |
| **25 H** | same | ~17 | 4 | 4 | **11/11** |

**First round in which both cells scored full marks.**

## Predictions scored

| # | Predicted | Actual | |
|---|---|---|---|
| P1 | at least one cell 11/11 | both | ✓ |
| P2 | both ≥9/11 — no regression from adding a step | both 11/11 | ✓ |
| P3 | cycles stay near 8–10, not above 14 | G **7**, H **~17** | ✗ (mixed) |
| P4 | at least one cell reports step 3b changing its wiring | both did | ✓ |
| P5 | no regression on `GraphExistsTest` or the build order | held | ✓ |

**Step 3b earned its place.** Both cells named the off-transitions and rewired because of them:

> **G:** *"R6 turn-OFF events: ADJUST/COUNT stock depletion, CANCEL … fires at event 5 (PAID), fires
> again at event 7 (RECEIPT after ADJUST reduced stock)."*
>
> **H:** *"Discovered `AllocatableCheck` needed `OrderManager` as a parent, not just
> `@NoTriggerReference`, to trigger on ORDER events."*

That is the exact defect that cost round 24 its two probes, found before writing logic in both cells.

## The defect all three engines share, which H declared and my corpus missed

H's own §7: *"Single RELEASE emission per cycle … resolved with `break` in the loop after the first
emission."* So I built the probe: one `RECEIPT` that makes **two** orders allocatable at once.

```
expected   5,RELEASE,OA   5,RELEASE,OB
G          5,RELEASE,OB          (drops OA)
H          5,RELEASE,OA          (drops OB — the declared break)
E (r24)    (nothing at all)
```

**Three independent engines, three different wrong answers.** Two decisions from *different* nodes in
one cycle is handled correctly by everyone — probe 04 has `HAZARD_BLOCK` and `OVERWEIGHT` on one
`DISPATCH` and all cells pass it. **Two decisions from the same node in one cycle is what fails.**

The shape of the hazard is framework-adjacent: a node naturally holds *one* value per cycle, and the
decision-collection pattern every cell reached for is a single slot read once by `Main`. Emitting a
*list* per cycle needs deliberate design, and nothing in the task, the template or the build order says
so.

Probe 12 added. **Neither cell would score 11/11 on the extended corpus** — both are 11/12.

## What this says about the method

The corpus has now been extended twice by information that came from the engines themselves rather than
from my design: round 24's re-release clause, and this. **Both times the agents' own reports named the
gap** — H described its `break` as a resolved ambiguity, not a defect.

That suggests a cheap addition to the harness: ask for declared simplifications explicitly, and treat
every one as a probe to write. It is the highest-yield source of test cases in the project so far, and
it costs nothing.

## Still open

- **The 1-test result.** G scored 11/11 with a single test and four traces; round 23's cell scored 3/8
  with three tests and nine traces. The instrument is not what changed — the questions were. I would not
  yet conclude that tests do not matter.
- **Vanilla has still never run this spec.** Until it does, "the harness makes Haiku correct" is not
  "Fluxtion plus harness beats plain Java".

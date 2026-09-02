# PREDICTION — round 25: optimising the harness

**Committed before launch.** The question has shifted from "does the framework win" to **"what harness
makes a weaker model produce a correct engine"**. This round optimises the harness.

## Where the harness stands

| round | harness | `mvn` | own tests | 11-probe score |
|---|---|---|---|---|
| 22 | plain template | 12 | 21 | ~5 |
| 23 | + `trace.sh` | 22 | 3 | ~3 |
| **24** | **+ failing test + build order** | **~8** | 9–12 | **9 and 8** |
| **25** | **+ step 3b (what turns an EDGE off)** | ? | ? | ? |

Round 24's two cells both lost the same two probes: R6's re-release clause. Their allocatability
evaluators were reachable from orders and payments but **not from stock**, so an order that stopped
being releasable and became releasable again never emitted again. Step 3 cannot catch it — with shell
nodes there is no *became true again* to observe — so it gets its own step.

**The Spring variant is blocked** and recorded in `SPRING-BLOCKER.md`: the route generates a correct
graph and a 40-line script gives the mechanical bean→shell translation, but neither `logLevel` nor
`auditors` is settable on `FluxtionSpringConfig`, so the audit log cannot be enabled — and every
instrument that produced rounds 22–24's gains is a check against that log.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| **P1** | **At least one cell scores 11/11.** The only defect surviving round 24 is the one step 3b names explicitly. | medium |
| **P2** | **Both cells score ≥9/11**, i.e. no regression from adding a step. Adding instructions has hurt before — round 23 cost 2 points. | medium |
| **P3** | **Build cycles stay near round 24's 8–10, not above 14.** Step 3b is analysis, not extra building. | medium |
| **P4** | **At least one cell reports step 3b changing its wiring** — naming a path it added because of it. If both say it found nothing and both still lose the re-release probes, the step is inert prose and should be deleted. | medium |
| **P5** | **No cell regresses on `GraphExistsTest` or the build order.** Round 24's gains hold. | high |

## Falsifiers

- **If both cells still miss re-release, step 3b is inert** and the defect needs a mechanical check — a
  probe generator, or a template test that exercises every EDGE rule's off-then-on path — not another
  instruction.
- **If scores fall below round 24's**, adding a step costs more than it buys, and the harness is at its
  useful size. That would be the second time more instruction made things worse.

## The comparison still owed

Vanilla Java has never been run on this 15-event spec. Until it is, "the harness makes Haiku produce a
correct engine" is not the same claim as "Fluxtion plus the harness beats plain Java". Round 21 at three
detectors had vanilla tie on correctness and win on cost. **That run comes next**, against whichever
harness wins here.

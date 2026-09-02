# PREDICTION — round 23: does a diagnostic procedure move the ceiling?

**Committed before launch.** Same 15-event-type spec, same model, two independent cells. The only
change is the bootstrap: it now ships `trace.sh` and a four-step procedure that separates orchestration
from application logic.

## What changed

Round 22 produced no working engine in either cell, and the two failures were different in kind:

- **Cell B** computed the release correctly and **dropped it on the way to the file**. It emitted
  nothing on every probe and never noticed. Its test for that rule was green.
- **Cell A** scored 5/8: `STOCKOUT` re-firing on `COUNT` when already negative, `OVERWEIGHT` suppressed
  whenever `HAZARD_BLOCK` fires on the same dispatch, and `SLA_BREACH` never firing at any delta.
  21 green tests.

The bootstrap now answers, in one command, *which nodes ran in which cycle, and what came out*:

```
 ev  event      nodes that ran, in dispatch order
  4  READING    ['sensorState', 'thresholdAlert']
decisions emitted:
  4,ALERT,SENSOR-1
```

and turns that into four steps — no log at all / wrong nodes ran (wiring) / right nodes wrong decisions
(ordinary Java, and the log names the node) / right nodes no decisions (the output path). **A command,
not a paragraph**, because rounds 16–20 showed prose describing a failure does not prevent it.

**Scoring is new too and stricter:** eight probe scenarios built to attack boundaries, with expected
decisions written before any engine exists. Validated against round-22 cell A, which scores 5/8 —
reproducing all three hand-diagnosed defects.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| **Y1** | **Neither cell emits zero decisions.** The silent-nothing failure class disappears, because a single `trace.sh` run makes it impossible to miss. This is the narrowest claim and the one the change was designed for. | medium-high |
| **Y2** | **At least one cell scores ≥7/8.** Step 4 kills B's failure outright; step 3 points at the node but does not find a wrong boundary comparison for you. | medium |
| **Y3** | **At least one cell credits `trace.sh` with finding a specific bug**, naming it. If the tool is used and reported useless, that is worth as much. | medium |
| **Y4** | **Build cycles do not fall much** — round 22's better cell took 12. Tracing adds runs even as it removes blind debugging; I expect 8–14, not 5. | medium |
| **Y5** | **The differential diff becomes meaningful.** With both cells emitting, divergences mark places my spec is under-determined rather than one engine being dead — which is what defeated the method last round. | medium |

## Falsifiers

- **If a cell again ships an engine emitting nothing, Y1 is dead** and so is the "make it a command"
  conclusion running through rounds 16–22 — a script that must be run is still a thing that can be
  skipped, and the next step would have to be a failing test in the template rather than a tool.
- **If both cells score 8/8**, the ceiling moved, and it moved on a diagnostic rather than on cost —
  the first thing in this series to do so.
- **If both score around 5/8 again**, the procedure is inert and the limit is the model's ability to get
  twelve interdependent rules right, which no bootstrap will fix.

## Limit

n=1 per cell, one task. A ceiling that moves here needs confirming on a different spec before it is a
property of the bootstrap rather than of this problem.

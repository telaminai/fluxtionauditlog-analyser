# PREDICTION — round 39, composition at twelve stages and four shared events

**Written and committed BEFORE either arm is launched.** No result seen.

## What changed from round 38

Round 38 was seven stages, **one** event type, one chain — and vanilla matched Fluxtion by running
`javap` over the jars and hard-coding the resulting order. Vanilla named its own limit unprompted:
generalising to an arbitrary DAG needs reflection, an adjacency list, a topological sort and cycle
detection, *"~200–300 LOC"* against the ~50 it wrote.

Round 39 is the smallest change that tests that claim:

| | round 38 | round 39 |
|---|---|---|
| stages | 7 | **12** (+ 8 event adapters = 20 nodes) |
| event types | 1 | **4, each consumed by two subsystems** |
| entry points | 1 | **8** |
| a hard-coded order must be right | once | **once per event type** |
| conditional propagation | none | **`CONFIG` adapters filter on key and return `false`** |

The last row matters most and is new. In round 38 a fixed sequence was *sufficient*. Here a fixed
sequence is not enough at all: which stages run depends on which adapter accepted the event, and
`CONFIG,unrelatedKey` must produce **no output whatever**. Order alone cannot express that.

## Design

| | |
|---|---|
| **Variable** | the composition mechanism. FX = Fluxtion Spring route, generator derives dispatch. VAN = plain Java, must derive it. |
| **Held constant** | the twelve stages' arithmetic, the constructor signatures, the jars-only delivery, the task text, the scenario format, the output contract, the model |
| **n** | 1 per arm — stated up front, so anything under ~2× on cost is noise |
| **Scored on** | `scoring-scenario.txt`, held out, never shown to either arm, 10 events including one that must produce nothing |

Both arms get the same `TASK-common.md` verbatim. **Neither is told the dependency graph or the
evaluation order** — that was round 34's defect, where I drew the order in the handout and vanilla
said so. The sample scenario is given; **its expected output is not**, because for the vanilla arm the
expected output *is* the answer.

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| P1 | **Both arms reach a correct result.** Six specs have now failed to separate the arms on correctness and I have stopped expecting the seventh to. | medium-high |
| P2 | **Vanilla writes a real topological sort this time**, not a hard-coded list — the sequence it hard-coded at 7 stages does not survive 8 entry points and conditional propagation. | high |
| P3 | **Vanilla's consumer code exceeds 150 LOC**; Fluxtion's stays at zero Java for the graph (bean file only). This is the round-38 claim tested rather than quoted. | medium-high |
| P4 | **Vanilla costs more than Fluxtion** in weighted tokens, by ≥1.5×. Deriving dispatch is work; reading a bean file is not. | medium |
| P5 | **The `false`-return requirement is where vanilla loses marks if it loses any** — a topological sort gets order right and still runs stages it should have skipped. | medium |
| P6 | **Fluxtion's `mvn` count is lower** — the two-pass build is a known trap but the graph needs no debugging. | low-medium |

## Falsifiers, stated now

- **If vanilla again hard-codes a sequence and scores full marks**, P2 and P3 are wrong and the
  composition thesis has failed to separate at twelve stages as well as seven. I will say so.
- **If vanilla is cheaper**, P4 is wrong and the cost argument for the framework is dead at this size.
- **If Fluxtion loses on correctness**, the generator or my bean file is at fault, not the arm — and
  that is the more useful finding of the two.

## Already recorded before the arms ran

Two defects found while building the reference, both costing a build each, both in `NOTES.md`:
**a node never triggers itself** (six stages propagated dirty without recomputing, silently), and
**`UP-FLX-27`** — colliding simple names across two vendors emit uncompilable code. The second is a
result about the composition thesis, found independently of whichever arm wins.

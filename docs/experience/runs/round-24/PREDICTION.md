# PREDICTION — round 24: skeleton first, then logic

**Committed before launch.** Same spec, model and probes as rounds 22–23. Two changes to the bootstrap,
both forced by round 23's failures:

1. **`GraphExistsTest` ships green in the template** and fails when the builder registers nothing.
   Mutation-checked against exactly cell C's mistake. It runs under `mvn test` whether or not anyone
   chooses to use a tool — which is the escalation round 23's falsifier demanded.
2. **A staged build order replaces the debugging note**: prove a graph exists → write shell nodes with
   no logic → prove the orchestration with the shells and the audit log → only then implement, each node
   unit tested directly *and* tested again driven by events. Debugging is a fixed list worked top-down.
   `trace.sh` is explicitly what you use *when a test fails*, never instead of tests.

## Baselines

| round | cell | `mvn` | own tests | score | how it failed |
|---|---|---|---|---|---|
| 22 | A | 12 | 21 | **5/8** | three logic bugs |
| 22 | B | 6 | 3 | 0/8 | decisions computed, dropped before output |
| 23 | C | 15 | 0 | 0/8 | builder registered nothing; blamed the plugin |
| 23 | D | 22 | 3 | 3/8 | one wiring bug — `releaseDecider` off the ORDER path |

## Predictions

| # | Prediction | Confidence |
|---|---|---|
| **Z1** | **No cell fails the way C did.** `GraphExistsTest` cannot be passed by an unregistered graph, and it runs on every build. This is the narrowest claim and the one the test was built for. | high |
| **Z2** | **No cell ships D's failure either** — a decision node absent from a path it must serve. Step 3 checks exactly that, with empty nodes, before any logic exists to distract from it. | medium |
| **Z3** | **At least one cell scores ≥6/8**, beating round 22's 5/8 best. | medium |
| **Z4** | **Test counts recover to double figures.** Round 23's cells wrote 0 and 3; steps 4 and 5 ask for two tests per node. If they again write three smoke tests, the procedure is being read and ignored. | medium |
| **Z5** | **Build cycles stay high — 12–20.** The staged order adds work before it saves any; I do not expect it to be cheaper, only more correct. Predicting a fall would be the optimistic error I have made repeatedly. | medium |

## Falsifiers

- **If a cell still ships an empty or unreachable graph, Z1 is dead** and a shipped failing test does not
  bind either — at which point the honest conclusion is that no artefact in the project can force a step,
  and the ceiling is the model.
- **If scores stay at or below 3/8**, the staged procedure is inert and rounds 19–24 have improved
  nothing at this scale.
- **If test counts stay low while traces stay high**, the substitution effect found in round 23 survives
  an explicit instruction not to substitute — which would be a stronger finding than the fix working.

## Limit

n=1 per cell, one spec. Two changes moved at once — a test and a procedure — so a gain cannot be
attributed between them without a further round.

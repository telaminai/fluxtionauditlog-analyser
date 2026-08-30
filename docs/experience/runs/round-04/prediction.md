# Round 04 — PRE-REGISTERED predictions, written and committed BEFORE the run

**Committed before either agent started.** The point of writing this first is that it can be wrong in
public: after the fact, any result can be narrated as expected.

## What is being tested

**One variable: the upstream static authoring resources.** Both conditions get the *same* bundle with its
*own* shipped docs — deliberately **not** my `current/` doc set, because that set already contains my fix
for R2-A and would make the test measure my patch instead of the resources.

| | Condition A (control) | Condition B (test) |
|---|---|---|
| Bundle | P3 zip, SHA `6afdf532…4914a` | same |
| Docs in project | the bundle's own `CLAUDE.md` / `AGENTS.md` / skills | same, untouched |
| Playground resources | **none** | `build-with-ai` prompt + `/CLAUDE.md` + golden path + `spring-authoring/contract.md` |
| Port / server | 8281 / `retest-A` | 8282 / `retest-B` |
| Analyser | not reachable | not reachable |

**Task (round 02's, verbatim in substance):** add a node that maintains a per-symbol running count and
running maximum price across events, run it, and show audit-log evidence that it accumulated.

Round 02's task is reused **deliberately**, against the loop's own rotate-the-task rule, because this is a
**replication with one variable changed**, not a new round. The overfitting risk that rule guards against
does not apply: neither condition carries the doc set that was written from round 02.

**n = 1 per condition. This is an EXPLORATION round** (D-AX7) — it may produce findings and may **not**
support a trend claim.

## Predictions

- **P1 — the headline.** A hits R2-A (`cannot find matching constructor … failed to match for these
  fields`) and burns at least one build cycle on it. **B does not**, or recovers in a single step by
  citing the playground triage table's `transient` / `@FluxtionIgnore` row.
- **P2 — the survivor.** **Both** conditions struggle to log a *value*. Neither the bundle nor any
  playground resource states the `EventLogSource` / `EventLogNode` contract, so I expect either a
  WENT-OUTSIDE (framework source, sources jar, web search) or a copy of the shipped `RootNode` without
  understanding why it works.
- **P3 — the shape.** B records fewer COULD-NOT-FIND on *Fluxtion authoring* and the **same or more** on
  *audit*. That asymmetry is the whole claim of D-AX1b.
- **P4 — `nodeBeans`.** If A omits it, its node silently never runs. B should get it right from
  `contract.md`.
- **P5 — dispatch order.** Neither condition can explain why entries sit where they do in `nodeLogs`;
  it is published nowhere.

## What would falsify my write-up

Stated explicitly, because these are the outcomes that cost me:

- **If B also hits R2-A**, the playground resources do **not** prevent it. D-AX1b's table is wrong,
  R2-A reverts to a genuine documentation gap, and UP-FLX-32's framing ("documented and still guessed
  wrong") loses its strongest support.
- **If B logs values cleanly without going outside**, then the audit contract is discoverable from
  material I judged silent, and UP-FLX-35 is weaker than claimed.
- **If A sails through everything**, the task is too easy to discriminate and the round says nothing —
  a null result I must report as one rather than mine for anecdotes.

## Known limits of this round

n=1 per condition, so a single difference is a hypothesis and not a defect (the loop's own rule). No
analyser present, so the verify half of the loop is untested. Both agents are Claude, so this measures one
model family. Success on the task is **not** a metric (D-AX5).

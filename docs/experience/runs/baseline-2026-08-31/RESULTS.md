# Baseline measurement — before the diagnostics/GraphML merge

**Run 2026-08-31.** Five fresh context-free agents, ~5 minutes each, one model family, one sitting.
Owner asked for a rough feel, not a full analysis. Predictions were written in
[PREDICTIONS.md](PREDICTIONS.md) **before any agent returned**; they are scored below including the
two that were wrong.

Baseline = the toolchain an author meets today: builder 1.0.64, the legacy prose message, GraphML with
no `fluxtion.*` vocabulary.

## Scorecard

| # | Prediction | Outcome |
|---|---|---|
| 1 | 1–2 of 3 say `spreadCalculator` DID NOT RUN | **WRONG — 0 of 3.** None asserted it |
| 1b | 0 of 3 reach the deduced RAN answer | **WRONG — 1 of 3 did, cleanly; a 2nd got most of the way** |
| 2 | 1–2 of 3 say dispatch order is declaration order | **WRONG — 0 of 3 asserted it**; all three said the files do not determine it |
| 2b | at most 1 names node-name ordering | **RIGHT — 0 of 3.** Nobody knows the real rule |
| 3 | 3 of 3 answer the control question | **RIGHT — 3 of 3** |
| 4 | 2 of 2 produce a working fix | **RIGHT** |
| 5 | at most 1 of 2 states a rule with `final` as the trigger | **RIGHT, and worse — 0 of 2** |
| 6 | 0 of 2 mention the JavaBean setter route | **WRONG — 2 of 2 did** |
| 7 | correct answers lean on prior knowledge, not the files | **MIXED** — heavily file-cited on evidence, candidly memory-based on authoring |

## What actually happened, and the reframing it forces

**The failure mode was not confident wrongness. It was honest non-answers.** Every agent that could
not determine something said so, and several flagged the exact trap: one noted that treating GraphML
edge order as the dispatch rule "would be a guess"; another said of the authoring rule *"I can produce
this fluently whether or not I actually know it."*

That is a better starting position than earlier rounds implied, and it **changes the shape of the
argument for both upstream changes**. The value is not *preventing false beliefs* — it is **turning
"cannot tell" into an answer**. A smaller claim, and a much more defensible one.

**It also invalidates a comparison I was about to make.** Round 05's four-of-six false rule came from
agents *doing a task*; this run *asked a question and explicitly permitted abstention*. Different
instrument. These numbers are a baseline for this instrument only, and must not be read as a trend
against round 05.

## The strongest single result

Asked whether the processor can write an audit log, **all three agents answered correctly and all three
independently volunteered the same caveat, unprompted**:

> capability is not coverage — a node that never appears is indistinguishable between "does not
> implement audit logging" and "logs only at a level not in force here"

That is exactly the ambiguity `fluxtion.auditCapable` / `auditCapableVia` resolves, and exactly the
distinction `AuditReadiness` today can only guess at. **Three of three good readers detected the gap
and stopped at it.** Evidence that the information is genuinely absent from the artefact is a stronger
argument for emitting it than agents getting it wrong would have been.

## Authoring — where the diagnostics case is measured, not argued

Both agents produced a working fix. Neither was confident which repair the generator accepts, and
**both independently predicted 2–3 build attempts.** That is the number the diagnostics work is
competing against.

Three specific findings:

1. **Neither named `final` as the trigger.** One said "non-transient instance field", the other "fields
   whose value cannot be re-derived". Both rules would misfire on an unfamiliar node. This is precisely
   the fact FLX-1009's `why` now encodes.
2. **One of two was actively misled by the legacy message.** It lists both `statsBySymbol` and
   `rootNode`, and `rootNode` *is* correctly supplied. That agent could not tell whether the problem was
   the state field or the reference, and said so — costing it a predicted extra cycle. **This is the
   defect FLX-1009's fork exists to remove**, observed happening.
3. **Both reached for the framework reference and were prevented.** Each said, unprompted, that the
   right move was to read the source of truth rather than infer, and that their rule should be treated
   as a hypothesis. A diagnostic that carries the rule removes that trip.

Correcting an earlier note of ours: **both agents named the JavaBean setter route.** Our upstream note
recorded that no measured agent ever used it — true of agents *fixing under build pressure*, false of
agents *asked to state the rule*. The knowledge is present and does not survive contact with a build.

## Limits, stated plainly

- **n=5, one model family, one sitting.** No effect size claimed.
- **The fixture is small and clean.** One agent deduced `spreadCalculator` ran by noticing
  `spread = mid × 0.0002` across records. That deduction is available because the graph has seven
  authored nodes and one exactly-linear relationship. It does not survive a 300-node graph, which is
  the size the analyser exists for.
- **Abstention was explicitly permitted.** Real authoring gives no such permission, and the earlier
  rounds' worse results came from agents under task pressure.
- Nothing here measures the *post-change* state. The comparison run has to use this same instrument.

## What to re-run after the merge

Same five prompts, same fixtures, plus a regenerated GraphML carrying the new vocabulary. The two
questions that should move:

- **Q1** should go from "cannot tell" (2 of 3) to a determinate answer, from `fluxtion.auditCapable`.
- **Q2** should go from "the files do not determine it" (3 of 3) to the correct rule, from
  `fluxtion.topologicalRank`.

And on authoring, the number to beat is **2–3 predicted build attempts, with 0 of 2 knowing that
`final` is what triggers the mapping**.

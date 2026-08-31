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

## PRE-REGISTERED — the comparison run, and what would falsify the claim

Pinned 2026-08-31, before the merge, so it cannot be adjusted afterwards. Same instrument: the same five
prompts, the same fixtures, a regenerated GraphML carrying the new vocabulary, and the same explicit
permission to abstain.

**The claim being tested.** The two upstream changes are *not the same kind of thing*. Diagnostics are an
**optimisation** — both agents reached a working fix from the legacy message, so the win is cycles and a
transferable rule, not a new answer. The GraphML vocabulary is a **capability change**, because the
baseline shows the answers are not reachable slowly, they are not reachable at all.

| | baseline (measured) | predicted after merge | source of the change |
|---|---|---|---|
| Q2 · sibling dispatch order | **0 of 3** could answer; 3 of 3 said the files do not determine it | **3 of 3** answer correctly | `fluxtion.topologicalRank` |
| Q1 · did the silent node run | **1 of 3** determinate (via arithmetic only this fixture allows) | **3 of 3** determinate | `fluxtion.auditCapable` |
| Authoring · predicted build attempts | **2–3**, both agents | **1** | FLX-1009's fork |
| Authoring · names `final` as the mapping trigger | **0 of 2** | **2 of 2** | FLX-1009's `why` |

**What falsifies it.** *If Q2 does not move from 0 of 3, the capability claim is wrong and the metadata
is an optimisation too.* That row cannot be rescued by argument: nobody could answer the question at
baseline, so either the attribute makes it answerable or it does not. Q1 is the weaker test — one agent
already got there, and a reader who tries hard enough may again.

Secondary, and worth recording because it would be the more interesting failure: **if agents answer Q2
correctly but cite prior knowledge rather than the attribute**, the vocabulary has not been read and the
result proves nothing about the artefact. The BASIS line exists to catch exactly that.

## Author's note on the instrument

My predictions before this run were wrong **in the direction of pessimism** — I expected confident wrong
answers and got honest non-answers. The instrument was calibrated on earlier rounds where agents were
*doing a task*, and asking a question while permitting abstention is a different measurement. Recorded
because the same reflex is what turns an easy pass into an unreported non-event.

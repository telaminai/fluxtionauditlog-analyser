# Fresh-session acceptance: predictions registered before execution

Date: 2026-09-19. Status: **REGISTERED; participant has not started**.
Owner requested an independently primed LLM session, live analyser observation, and
predictions recorded before the experiment. This is a developer-experience probe,
not release approval or a statistically representative model evaluation.

## Frozen scope and starting conditions

- Compiler: `2f1c49cd` (implementation response `db2a9398`).
- Playground: `d460e99`.
- Analyser: `a3a2aa7`, built from that source for the experiment.
- A fresh copy of the actual extended Spring AOT download, after infrastructure-only
  provisioning. The local provider and dependencies are already installed, with no
  paid call and no owner key. This tests the after-setup, local-provider route, not
  publication or paid remote generation.
- The copy contains the shipped RUNBOOK and public authoring documentation. The
  fresh agent receives the user task, these documents, ordinary project files and
  the analyser's own manifest. No review, implementation notes, predicted answers,
  prior conversation or observer guidance is supplied.
- Deliberately introduce exactly one XML binding referring to an undeclared node.
  Preserve its original bytes and a before/after diff in the observer evidence.
  The participant is asked to validate and repair any problem, without being told
  its location or the required diagnostic code.
- A separate analyser process, isolated user.home and export directory; no user log,
  remembered project, graph, chart, selected cycle or prior report. The observer
  asserts the baseline through context before the participant starts.
- Freeze the exact participant prompt, initial project hashes and tool manifest
  before spawning the agent with **no inherited conversation**. Record environment
  scaffolding separately from the product instructions.

## User task supplied to the participant

Extend the supplied price-processing design. Only PriceUpdate events with filter
ID 7 are eligible. An eligible price should reach Child and, when not paused,
increment its processed count, set lastProcessedPrice, and publish one Checked
result containing that price and count through the named checked sink. A pause
signal pauses processing without changing the count or lastProcessedPrice. The
exported reset command sets count and lastProcessedPrice to zero and unpauses,
without publishing a Checked result. Initial count/lastProcessedPrice are zero and
initial state is unpaused. Preserve unrelated declarations and implemented code.

Start by reading the runbook and validating the supplied project. Repair any
validation issue, make the design and implementation changes, generate, inspect
the generated processor, and run the scenario below twice from a fresh process.
Use the analyser to show what happened, including a chart and an evidence-linked
report. Answer: does the wrong filter increment the count; does a price received
while paused increment it; does reset itself publish; and what changes after reset?
Distinguish measured results from conclusions that the available evidence cannot
support. Keep a command/action transcript and finish with sources for each answer.

Scenario: PriceUpdate(DEMO,10,7); PriceUpdate(DEMO,20,9); pause signal;
PriceUpdate(DEMO,30,7); exported reset(); PriceUpdate(DEMO,40,7).

## Predictions and falsifiers

Confidence here is a qualitative judgement before running, not a measured probability.

| ID | Prediction | Confidence | What would falsify it |
|---|---|---|---|
| P1 | The fresh session can complete validate-with-repair → design/code change → generate → read-back → run using the shipped documentation, without observer coaching. | Medium | A required step needs a hint, repository implementation knowledge, undocumented workaround or observer code change. Record the responsible layer instead of crediting a coached pass. |
| P2 | XML reconciliation changes the reference/handler wiring while preserving business method bodies and ownership history; a subsequent unchanged regeneration changes neither Java nor the record. | High | Lost/rewritten business logic, an unsafe deletion, an unexpected ownership conflict, or byte changes on the no-op pass. Compare source and record snapshots, not just exit codes. |
| P3 | The requested scenario produces the state and outputs in the oracle below after the design change. | High | Any measured count, pause state, last processed value or sink emission differs. Generated source is corroboration; it cannot replace execution evidence. |
| P4 | The analyser's current open/source/topology/read/graph/report surfaces suffice to present the answers without adding an API or modifying analyser source. | Medium | Required recorded evidence cannot be retrieved, related to a cycle, or presented on the shared canvas using the shipped interface. Preserve a minimal reproducer. |
| P5 | Child needs explicit state instrumentation to make count/pause/lastProcessedPrice directly inspectable; the starter's existing root price log alone does not establish those states. | High | The untouched instrumentation already exposes all those state transitions, or the participant correctly obtains equivalent direct evidence without instrumentation. Do not count inference from silence as equivalent. |
| P6 | Fresh-process replay gives the same business state trajectory and sink outputs twice. | High | A business result differs from a clean-start replay with identical inputs. Timestamps, file paths and other incidental metadata may differ. |
| P7 | Remaining difficulties will primarily concern documentation, discoverability or instrumentation rather than a missing analyser rendering primitive. | Medium | A reproducible analyser capability gap blocks the required presentation despite the evidence existing in supported inputs. Count and classify refusals/retries instead of judging only the final polished report. |
| P8 | The observer can independently verify the participant's answer from generated source, captured outputs, audit records and the analyser's visible state. | High | The report has unsupported claims, stale/mismatched artefacts, unexplained cycle mappings, fabricated observations, or screenshots that disagree with context. |

### Business-result oracle (observer only)

The participant receives the requirements and input sequence, **not this expected table**.
The count measures processed prices since the most recent reset, not total messages.
Sink emissions are observed outside that resettable state.

| Step | Input | count | paused | lastProcessedPrice | New checked output |
|---|---|---:|---|---:|---|
| 0 | Fresh process | 0 | false | 0 | none |
| 1 | Price 10, filter 7 | 1 | false | 10 | price=10, count=1 |
| 2 | Price 20, filter 9 | 1 | false | 10 | none |
| 3 | pause | 1 | true | 10 | none |
| 4 | Price 30, filter 7 | 1 | true | 10 | none |
| 5 | reset() | 0 | false | 0 | none |
| 6 | Price 40, filter 7 | 1 | false | 40 | price=40, count=1 |

Exactly two sink outputs per fresh run, in order. A wrong-filter event may still
have a runtime/audit cycle; do not predict its absence from the log. A paused price
may invoke code without being processed; do not equate unchanged count with no
invocation. Reset is an exported-service call, so its audit shape may differ from
an event. Those distinctions are part of the evidence assessment.

## Observation, classification and implementation decisions

Observer activity is read-only during the unassisted first attempt: inspect the
progress log, command transcript, analyser context and screenshots; do not send
hints or mutate its project/canvas. Infrastructure failures can be repaired, but
must be recorded and may invalidate the run's relevant acceptance claim.

Capture: elapsed time; validation/build attempts; tool calls and refusals; repeated
schema/help lookups; business outputs for both runs; source/record hashes; graph/log
pairing; claims linked to records/source; screenshots of the final canvas; final
agent answer. A timeout or incomplete attempt is a result, not permission to edit
its transcript. Stop the first attempt after 40 minutes if it has not finished.

Classify each difficulty as **analyser, documentation, utility, template,
compiler/backend, environment, or model reasoning**. A successful recovery does
not erase the initial friction. The observer may reproduce a suspected gap after
the first attempt, recording that as a separate experiment.

A proposed analyser feature must name (1) the developer question, (2) the factual
evidence available, (3) the exact failed/awkward interaction, and (4) a reproducible
acceptance criterion. Prefer a documentation or existing-surface correction when
that addresses the observed cause. No implementation priority is established by
one model's unsupported suggestion alone. Do not alter these predictions after
seeing the result; append results and deviations in a separate acceptance record.

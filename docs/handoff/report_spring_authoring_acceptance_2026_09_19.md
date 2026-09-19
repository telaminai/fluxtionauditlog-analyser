# Spring authoring: observed fresh-session trial

19 September 2026. **The fresh session completed the task and the measured answers
are correct. The original authoring path did not pass cleanly:** reconciliation
wrote invalid Java, which the participant repaired without coaching. A subsequent
utility fix and confirmation are separate evidence, not a rewritten first attempt.
This is one bounded developer-experience trial, not release approval.

## What was frozen, and what actually ran

Predictions were committed as `5f05618f` before the participant started. The
[unaltered registration](evidence/spring-authoring-2026-09-19/predictions.md) and
[exact prompt](evidence/spring-authoring-2026-09-19/participant-prompt.txt) accompany
this report. Prompt SHA-256:
`5f10f283796e39757076b548f01ae862ae17a0993f5e6bf7ed7e3bab7f332fb6`.

Registration: 10:51:46 UTC. Fresh agent start: 10:52:08 UTC. Its final progress
record: 11:05:53 UTC, approximately 13 minutes 45 seconds including a localhost
sandbox approval delay. The agent inherited no conversation and received no review,
expected-result table or observer hint. The observer inspected progress and exports
without changing the participant's project or canvas. The full local archive is
`/tmp/spring-authoring-acceptance/run1`.

The analyser was built at `a3a2aa7`. The local authoring implementation was the
review response `db2a9398` (registered checkout `2f1c49cd`; intervening reviews and
documentation did not change that implementation). One pre-start deviation was
recorded: the fresh playground download included the pending G9/F8 documentation
and setup changes, later committed as `affdd85`, on the released playground baseline.
The working-tree diff and initial file hashes were frozen before the agent started.

This was an **after-setup local-provider trial**. The actual download's source was
fresh, but dependencies/tool/classpath were provisioned from the local verifier.
Its fixture POM used cached public BOM 1.0.69/runtime 1.0.15 and unique local test
artifacts. This does not test public artifact availability or the current released
BOM. Java had an isolated home, no owner key, and JVM network egress blocked. The
separate analyser started with no log, graph, source root, selection or report. Its
built-in default processor name was present; it was not inherited user state.
An initial launcher lifetime error was repaired before starting the participant.

One deliberate XML defect named an unknown signal-handler node. The participant
found `SPRING_SIGNAL_UNKNOWN_NODE`, corrected it, changed DATA to TRIGGER and
handler propagation, implemented processing/pause/reset, inspected generated
source, and drove the real analyser through its published manifest and actions.

## Measured answers

The full generated Java is retained in the local archive rather than this public
packet; its original hash is recorded. The observer checked both state CSVs and callback CSVs against the frozen oracle,
read the real host and generated dispatch, compared final XML/record/source hashes
with the build receipt, and inspected the chart and all five rendered report pages.
The participant's saved 37 requests/replies exactly match the transport journal.

| Input/checkpoint | Processed count | Last price | Paused | Cumulative sink callbacks |
|---|---:|---:|---|---:|
| Fresh process | 0 | 0 | false | 0 |
| Price 10, filter 7 | 1 | 10 | false | 1 |
| Price 20, filter 9 | 1 | 10 | false | 1 |
| Pause | 1 | 10 | true | 1 |
| Price 30, filter 7 | 1 | 10 | true | 1 |
| Exported reset | 0 | 0 | false | 1 |
| Price 40, filter 7 | 1 | 40 | false | 2 |

Both fresh JVMs produced exactly `Checked(10,1)` then `Checked(40,1)`. Wrong-filter
silence in the audit was not used as proof: live getters establish unchanged state.
The paused price does invoke Child, which records that it ignored the price. Reset
is an exported-service call; the callback collector establishes no reset emission.
These are results for this input sequence, not proof of every possible sequence.

Portable evidence: [packet and reproduction commands](evidence/spring-authoring-2026-09-19/README.md),
[real analyser report](evidence/spring-authoring-2026-09-19/participant-report.pdf),
[real chart](evidence/spring-authoring-2026-09-19/participant-chart.png).
`python3 docs/handoff/evidence/spring-authoring-2026-09-19/verify.py` independently
checks all fourteen state checkpoints, both output sequences and artifact hashes.

## Predictions judged against the first attempt

| Prediction | Outcome | Evidence and qualification |
|---|---|---|
| P1: complete through shipped documentation without workaround/coaching | **Falsified** | No observer coaching, but the required `privatefinal` repair was an undocumented source workaround. Completion alone does not satisfy the registered criterion. |
| P2: preserve source/ownership and no-op regeneration | **Failed source-integrity expectation** | Reconciliation corrupted a declaration's modifiers. Business bodies survived; subsequent no-op hashes held. The later fix cannot erase this failure. |
| P3: requested behavior matches oracle | **Supported** | Both final runs match every checkpoint and exact sink sequence. |
| P4: existing analyser surfaces suffice | **Supported, with defects** | Existing read/series/chart/external CSV/flags/topology/report surfaces produced the answers. No analyser code or new API was required. |
| P5: root logging is insufficient for Child state | **Supported for the audit canvas** | Initial Child has no state audit. The participant added numeric/boolean state logging and independent getter/callback measurements. This does not prove that logging is the only possible measurement method. |
| P6: repeatable business trajectory | **Supported** | Two fresh JVMs agree after excluding timestamps; raw logs are not byte-identical. |
| P7: friction is mainly docs/discovery/instrumentation rather than a missing renderer | **Mixed** | No missing rendering primitive blocked completion, but real utility and analyser defects matter more than documentation alone. Do not use this trial to justify a docs-only priority. |
| P8: independently verifiable answer and canvas | **Supported with presentation limits** | Actual source, CSVs, audit, hashes, action replies and images agree. The report explicitly discloses the false coverage warning; chart captions are cramped and one record panel is duplicated. |

The narrow conclusion is useful: an LLM can implement a design, run deterministic
code, and use the analyser to share measured answers. It also exposed failures that
all previous green unit/build gates had missed.

## Findings and proposed implementation order

These IDs belong to this experiment; they do not close or renumber the independent
reviewers' F/G findings.

1. **DX-01 — authoring utility, blocking source integrity.** Removing the owned
   `NoTriggerReference` from the browser's `private final` field produced
   `privatefinal`. First generate failed javac and correctly set `compilerRan=false`.
   The participant saved the failure before repairing it. The author independently
   reproduced it with a minimal regression and the real downloaded project. The
   response replaces owned annotation tokens without rewriting other member tokens,
   then parses planned output before committing it. New jar: generation compiles;
   a repeat preserves every Java file and the record byte-for-byte; two more fresh
   JVM scenarios pass. This response still needs independent review.
2. **DX-02 — analyser, false graph/log mismatch.** `checked` is declared in GraphML
   as a `SinkPublisher`, but coverage reports it absent and graph pairing says 2/3
   nodes match. Independently reproduced by the committed Java probe: the complete
   graph matches 3/3. `CoverageService.java:83` subtracts only the coverage subset;
   `MainFrame.java:3597` passes authored IDs to pairing. The question is “do these
   artifacts describe the same run?” and the complete membership evidence exists.
   **Acceptance:** keep the authored coverage denominator, compare membership against
   all declared nodes, retain warnings for truly absent IDs, and show the same
   verdict in context, the human panel/status and exported reports. Prioritize this
   correctness fix over new visualization primitives.
3. **DX-03 — analyser, combined rolled-log open silently drops graph/processor.**
   The exact request/reply is in the packet. `ActionExecutor.java:907` returns from
   the `logs` branch before graph/processor handling, despite the schema saying
   “log and/or graph”. Separate calls recover. **Acceptance:** a combined request
   loads the named graph after the rolled set or explicitly refuses the combination;
   no `ok` that silently discards those parameters. Test load completion and pairing,
   not merely the initial asynchronous echo.
4. **DX-04 — analyser, first topology record request does nothing.** With 24 records
   open but none selected, `topology {recordIndex:8}` returns record 0, “no record
   selected”. `goto {recordIndex:8}` then topology succeeds. This parameter is in
   the manifest; `TopologyPanel.java:1246` returns when the cursor is unbound.
   **Acceptance:** from a fresh loaded log, the declared parameter binds/selects
   record 8 or gives a clear refusal; echo and visible record must agree.
5. **DX-05 — analyser, graph names and spotlight targets disagree.** `graph` accepts
   a colon in a name which `spotlight` cannot address. The participant renamed the
   chart and recovered. **Acceptance:** creation/rename and target addressing share
   a documented policy, preserving access to already saved charts. Choose the
   compatibility behavior in its implementation review; do not silently break names.

DX-02 has an independent executable reproduction. DX-03/04/05 were also repeated
in a separate fresh analyser by the observer; the unchanged replies are in
`evidence/spring-authoring-2026-09-19/observer-actions.json`. The combined open left
the graph absent; the first topology request stayed unbound, then goto selected 8;
a colon-named graph was created and its spotlight refused. The first HTTP probe
helper did not decode HTTP 400; that harness issue was corrected and the actual
refusal captured. The participant window had exited after its result was frozen,
so this used a new isolated observer profile. None of these analyser findings
has been fixed by this report. They are proposed priorities for the existing API,
not evidence that a new execution service belongs in the analyser. M66/M67 remain
separate workstreams; this trial did not test their unmerged features.

Other friction: the participant's first host omitted YAML document separators.
The analyser diagnosed this correctly; the participant corrected the producer and
repeated both runs. Keep the golden-path example discoverable, but one model mistake
is not sufficient reason to add an API. Reports use fault-oriented headings for
some successful checks, and the small chart/captions need presentation judgment;
repeat on another task before treating those as required new features.

## Review response and remaining gates

G9's independent starter pin and actionable setup refusal are in playground
`affdd85`; curated examples match the released main baseline. G10's two guide pages
are corrected in analyser `5142e47`. F8's record-creation wording and G11's schema
baseline are answered in the coordinated response. These are author responses,
not self-awarded reviewer closures.

Post-fix verification: full Java reactor 261 builder + 46 starter tests, zero
failures/errors, one pre-existing skip; packaged tool check 2,058 classes, Java 17
floor; playground 481/481; all twelve script checks across normal/bundle/extended
XML plus clean-sidecar and real javac-failure checks. The sandbox initially blocked
loopback fixtures; rerunning with loopback permission passed. A separate post-fix
observer confirmation reproduced red with the old jar, then passed with the new jar.

The fresh-client run is now **performed and assessed**, not a claimed clean first
pass. Publication and the browser graph preview remain unverified. The Browser
connector returned no available browser, so the Swing analyser exports do not
count as a witnessed playground preview. No owner key was used, no artifact was
published, and no feature branch was merged into a release branch.

Evidence publication gate: analyser `mvn test` passed **1,656 tests**, zero failures/errors,
31 display skips; strict MkDocs and the exact tracked-file rule-1 sweep passed.
The sweep caught the generated processor's header, so that full file was omitted
from the public packet (original retained and hashed), not silently edited. The
whitespace gate caught captured Child source; it is preserved under the existing
generated-evidence convention. Both checks were rerun after those corrections.

# M44.5: results, scored against PREDICTIONS.md (2026-09-27)

Branch `feat/m44-5-derived-state-in-snapshot`. Predictions and design committed first (2388a327, a2473141); the
code followed in three stages (25c1b905 session layer; ed5d1cfa surfaces; 7af1e6e8 witnesses and the static check),
then the controls (e4046c0a) and a merge of `main`.

## What shipped
- **`logEvidence`** (new node) owns the open log's producer findings, time-order report, content signature, the last
  Follow read failure, and whether a scan is outstanding. It decides WHEN the evidence is stale: on a new
  generation, on `LogAppended`, and when a Follow poll's `LogContentObserved` signature moves. It refuses a result
  for another generation, and coalesces a request while a scan is outstanding (that scan reads the log when it runs).
- **One effect**, `ScanLogEvidenceEffect(opId, generation)`. The adapter answers `ScanScheduled`, runs the scan on
  the next EDT turn, and posts `ProducerFindingsObserved` and `TimeOrderObserved`.
- **`OpenLog`** carries provenance and its source (resolved before `LogOpened`), and Follow (`FollowToggled`;
  continued through a reload only into a log that can be followed, `LogOpened.followable`).
- **The snapshot** publishes all of it. **MainFrame** renders from it on the one listener: one composer for the
  status line, the tooltip, the Reports tab, the time-order dialog (a person's load only), the Follow toggles and
  the poll timer. `context`, the `report` reply and the PDF read it. The frame's copies, its refresh gate and its
  four status assemblies are deleted.
- **`OneDispatchModelTest`** (static) and **CLAUDE.md rule 9**.
- **The audit sink**: evidence re-derivations are re-scopes; per-poll content observations have a ring of their
  own; a batch end is kept with the kind of the cycle that raised it.

## Predictions
| # | Prediction | Result |
|---|---|---|
| 1 | Regeneration succeeds; generated source passes `GeneratedSourceIsPublishableTest` | **Held**, twice (stage 1, and again for `onLogAppended`). Both generated copies byte-identical. |
| 2 | `SessionGraphShapeTest` passes after one expectation is added | **Miss (harmless).** It passed unchanged — its existing rule (effects descend from a decision) already covered the new node. |
| 3 | Processor tests show the listed behaviours | **Held, with one implementation miss.** The design said `LogAppended` requests a scan; stage 1 did not implement it. A frame test found the consequence (below), and it was added then. `LogEvidenceTest` now has 12 tests. |
| 4 | W1 fails on `main`, passes here | **Held.** Run on `origin/main` 29a9ece4: `context.timeOrder` null and the line silent after the out-of-order append. |
| 5 | W2 fails on `main`, passes here | **Held.** On `main` the Follow line lost the provenance and the order warning. |
| 6 | Existing surface tests stay green, with only exact status text updated | **Miss.** More changed than text: the scan now reports on the next EDT turn, so frame tests that polled and asserted in one EDT turn were split in two, and their reads moved from the retired frame field to the snapshot. `StatusLineTest`'s refresh-predicate tests now test the signature's stream-end key. No assertion was weakened; three were strengthened. |
| 7 | Headless and display suites green; CI green with the mutation gate | Local: see *Gates*. CI: on the PR. |

## Risks named in advance
- **Status text.** The Follow line now reads `Following <provenance (file)> · N records · range …` from its first
  render; the start-only wording "— watching for new records…" is gone. A failed Follow read is appended to the
  log line (`⚠ Follow read failed: …`) instead of replacing it.
- **The say-hold (R12-2).** The 12-second hold timer is deleted. The behaviour is kept by construction — an idle
  tick changes no snapshot, and a render sets the line only when its text changed — and the re-anchored
  `follow-hold` control is caught.
- **Scan cost. Not measured.** Time-order validation moved from the loader's background job to the EDT scan: one
  linear pass over the index per content change, beside the producer findings pass the frame already ran on the
  EDT. No large-log measurement was made; per the standing rule, no number is claimed.

## Found while building (none predicted)
1. **A line that mixed two revisions.** Between a Follow poll and the scan it asks for, the line counted the new rows
   beside the previous revision's findings (`1 records … ⚠ empty log`). Two causes, both hand-written ordering:
   the poll's `LogAppended` published the count a cycle before the evidence was marked outstanding, and the
   poll's identity report published a snapshot after the store grew but before the session heard of the append.
   Fixed in the processor (`onLogAppended` marks the scan outstanding in the same cycle) and in the render (the
   session's count, and only when the store is at that revision). Witness:
   `theLineNeverCountsNewRowsBesideTheOldFindings`.
2. **Follow would have churned the audit record.** Every poll now reports its content, idle ones too; at one a second
   those records would have evicted the re-scopes in minutes, and a scan's batch end was filed as a transition.
   Witness: `SessionSnapshotTest#idlePollsEvictNothingButTheirOwnKind`.
3. **A witness too weak for its control.** `m44-5-line-waits-for-the-session` survived its first run: the witness
   checked the count, and the count had become the session's. It now asserts the whole previous line holds.

## Controls
13 controls anchored on deleted code were re-anchored to the one site each behaviour now has, at their original
witnesses — except `integration-repeat-failure-skip`, which is observable only as a scan count now (equal findings
are kept by identity) and names `LogEvidenceTest#theContentSignatureDecidesARescan`. 12 new controls. All 25 run
through the gate (`--engine fast`, on a real display): named assertion red, byte-identical restore, green.

## The owner's question (prediction, for later scoring)
Not scorable yet — it is to be scored against later review records. One early data point, recorded so it is not
remembered selectively: every defect found in this work (items 1–3 above) was hand-written code around the
processor — adapter ordering, a render gate, a witness — and none was inside the generated dispatch. Prediction 2
said the remaining defects would move to the adapter boundary; item 1 is exactly that, and it was found at the
processor level in minutes because the ordering was readable in the generated code and the audit record.

## Gates
Recorded at the end of the work: see the PR description.

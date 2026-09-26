# Ninth re-review — eighth-round fixes

**Verdict: changes required. The analyser side is ready after the three Required corrections below, within this review's scope and the already agreed partial-delivery boundaries.** The recorded gates, R8 probe, matrix replay and targeted witnesses reproduce. That establishes the repaired cases; it does not establish the universal second-marker and header claims. New constructed cases expose two implementation gaps and one remaining false sentence.

Subject: `feat/mongoose-audit-production-rebased` at `8a35a988`, reviewed from `e5541d5b`. The four commits are `e8a1eb7f`, `c66a314a`, `a05526c6`, `8a35a988`. `origin/main` is `710dc2ca`; `git rev-list --count HEAD..origin/main` printed **0**. Review work was isolated on `review/mongoose-ninth-rereview-2026-09-26`.

**RUN** means reproduced here; **READ** means inspected source/contract/history; **REPORT** means an author's historical account, not independently replayed. I read the eighth review at `ba463890` and reran its probe; agreement with its author is not the evidence for the conclusions below. No implementation, tracker, spec or preserved evidence was changed. Only this review and its new [probe packet](evidence/mongoose-audit-production-impl/rereview9-probe/README.md) are added.

## Required corrections

### R9-1 · Medium — consecutive markers are collapsed by the second-marker lookup (RUN, READ)

**Site:** `src/main/java/telamin/fluxtion/audit/analyser/analyser/topology/PerNodeLevelChanges.java:357–359`, with `boundaryBetween` at `:389–392`.

**Input → wrong result:** WARN control at record 1; a complete marker declaring one record; another complete marker declaring zero records; Tick at record 2; view record 2. `HeapLogStore.runBoundaries()` is `[1, 1]`. The annotation is non-null and conditions absence on survival of only one marker. With three consecutive markers the boundaries are `[1, 1, 1]`, and the same incorrect annotation appears. Record 2 is already past the second marker and must receive none from this change under the owner's decision.

The second lookup uses `b > m1`, so it finds the next distinct record position, not the next marker. Empty runs need no malformed data: Format §1a permits a zero count and counts since the previous marker. `parse/StreamEndTracker.java:113` correctly records every marker, including repeated positions.

**Required:** select the second marker by occurrence while preserving equal-position boundaries. Add regressions for two and three adjacent markers, an empty intervening run, and a view immediately after them. A control restoring the strict-greater position lookup must fail the refusal/null assertion. This implements the settled decision; it needs no new policy.

Evidence: `R9Extra-output.txt`, first two cases. The second marker's count is explicitly zero, matching its empty segment.

### R9-2 · Medium — the new header scan mistakes `eventTime` for payload (RUN, READ)

**Site:** `src/main/java/telamin/fluxtion/audit/analyser/analyser/topology/PerNodeLevelChanges.java:205–214`, especially the allow-list at `:212`.

**Input → wrong result:** WARN at record 1, Tick at 2, a control-shaped record at 3 for which a plugin-style store returns `record(2) == null`, then Tick at 4. The ordinary raw header at 3 correctly produces a bound before record 3. Insert the valid header field `eventTime: -1` before its `logTime`; keep everything else unchanged. The note now says “Nothing later in the records sharing its grouping changes it” and makes its absence claim after record 1 without that bound, including record 4.

`rawEvent` stops at `eventTime` before reaching `event`. This is not an exotic extension: `docs/site/format-spec.md:223–230` puts `eventTime` before `logTime` in its example; `:243` permits it, and `RecordParser` recognizes it. The new scanner supports fewer header fields than the published format.

**Required:** recognize the permitted header while retaining the stop before payload. Cover permitted header fields/orderings on a null-record store, with a control removing `eventTime` support and a separate payload-spoof negative case. Do not restore the old scan through node values.

Scope: this reproduction uses the requested null-record stub, not a claim that a built-in store returns null or that an installed external plugin does so. The annotation code expressly implements this path. Evidence: `R9Extra-output.txt`, `header standard` versus `header eventTime`.

### R9-3 · Medium — “Every record in view” describes a filtered subset as the whole view (RUN, READ)

**Site:** `src/main/java/telamin/fluxtion/audit/analyser/analyser/topology/PerNodeLevelChanges.java:495–498`; the private narrowing happens at `:360–367`.

**Input → wrong result:** WARN at record 1, marker, Tick at record 2, second marker, further records and a later closer. Request view `[record 1, record 2]`. Only record 2 falls inside the annotation's window. The note nevertheless says “Every record in view is in a LATER run — a stream-end marker before record 2 begins it”. Record 1 is in view and precedes that marker. The conclusion is correctly bounded; its lead is false.

The original Q also retains this problem in a different form: view records 2 and 3 on opposite sides of the second marker. Its conclusion concerns only record 2, but its lead describes one later run begun by the first marker for the whole view. Three separated markers reproduce the same issue. `CoverageService.java:92` passes the coverage view to this method; `firstInView` is actually the first governed, same-context row, not necessarily the first row of that view.

**Required:** describe the subset to which this annotation applies, or otherwise qualify the lead. Keep the useful bounded annotation; do not widen its reach or replace the settled second-marker rule. Test a mixed view containing the opening record and a between-markers record, plus a view crossing the second marker. A control restoring the universal lead must fail its scope assertion.

Evidence: `R9Extra-output.txt`, “only in-window record sits between markers; earlier record is also in view”, and the three-separated-markers case.

## Optional improvements

### O9-1 · Low — the matrix checks the presence of a bound, not its exact endpoint (RUN, READ)

**Site:** `src/test/java/telamin/fluxtion/audit/analyser/analyser/topology/ControlAddressAndScopeTest.java:702–717`.

**Mutation → wrong result:** change only the second-marker label in production from `(m2 + 1)` to `(m2 + 2)`. The named matrix `noBranchOfTheSentencePresumesAProcessor` stays green: **1 / 0 / 0 / 0**. Its “and before” check accepts the wrong record number; its `(logTime 3/4)` sentinel does not inspect that number. This is a matrix-only survivor, not a claim that the entire suite survives it. The dedicated R8 assertion separately pins a concrete second-marker label.

Prefer comparing the rendered endpoint with an independently calculated fixture endpoint. The same principle applies to `closerPresent = closedClause`: that correctly avoids treating a non-applicable control as a closer, but it is not independent proof that an effective closer was found. A missing closing clause can remove both the explanation and that part of the expectation.

Evidence: `negative-guards-output.jsonl`, `matrix-wrong-second-marker-number`: green before, green mutated, green after, byte-identical restore. This does not invalidate P1/P2, which now fail through offenders as requested.

## Answers to the review questions

### 1. R8-1: later-run clause — holds on M, N and O (RUN, READ)

The new R8 output matches the committed after-fixes output for all 15 cases, after removing the recorded explanatory header and final-blank-line convention. M ends the later clause before its INFO closer; N ends it before the second marker; O states the matching grouping. The new text covers both `are not in this log` and `absent only if` conclusions.

The matrix exercises premise-free and conditional spanning forms. My additional absent-grouping/no-node case with an unreadable closer keeps naming, applicability and survival conditional and stops before that closer. A non-applicable closer does not end the window; with no second marker its later clause can remain open. That is correct. Adjacent markers remain R9-1.

### 2. R8-2: second marker — selection fixed for separated markers; two gaps remain (RUN, READ)

P is **null**. Q's conclusion concerns record 2 only. A view containing only records past the second of three separated markers is null. A second marker before a later closer bounds both wholly-after and spanning forms correctly, including an unreadable closer.

**It is acceptable to name a closer beyond the second marker.** My case names the INFO change at record 5 while limiting the absence conclusion to before record 3. Naming that later logged event does not assert that WARN held until it. The settled limit concerns which records the change explains, not a ban on mentioning another event. No owner decision is needed for that distinction.

The universal lead is still R9-3, and equal-position markers are R9-1. Therefore “every record past the second marker gets no annotation” is not yet universally true.

### 3. R8-3: holds condition — fixed for V, W and X (RUN, READ)

V carries the no-node premise into its readable closing clause; W carries it into the unreadable-closing clause; X carries both the no-node and applicability premises because its grouping is absent. In each applicable conditional form, `holdsLead` receives the conclusion's complete condition. “If the change at record 1 named no node and it applied here, it holds …” is appropriate within the original run; across a marker the code names the closer instead of claiming that the level held until it.

The condition-equality rule is right. It is not vacuous in this run: my matrix replay counted **279 notes containing a holds clause**, and changing `holdsLead` to unconditional `It holds` makes the matrix fail through its offenders assertion. It remains a conditional syntactic check: deleting or changing the grammar of the whole clause could bypass that particular matcher; reach/dedicated tests are separate protection.

### 4. R8-4: raw header and unreadable wording — standard cases hold; header grammar incomplete (RUN, READ)

Y1–Y3 reproduce, including the time read from raw header and the payload event-spoof refusal. My untimed case says “its time was not read”; a time appearing only after payload says the same, correctly describing what was inspected. Comments and blank lines before the header fields do not disrupt the scan.

Where a null row's header names the event, “a record this reader could not read, whose text names the control event” states exactly that limited evidence. It does not establish application of the control. A genuine stream-end marker is container metadata, not a row to annotate; no scan into marker/payload text should invent a control. The exploratory mixed streamEnd/event record in my output is not a valid marker and is not a separate finding.

However, a valid unusual order containing `eventTime` triggers R9-2. Calling every non-allow-listed field “payload” is unsound under the published header grammar.

### 5. R8-5: tightened offender rules — targeted mutations now bite (RUN, READ)

`witness13.py` reproduced all four controls. Restoring the unbounded later-run clause fails `theLaterRunIsBoundedAndTheAnnotationStopsAtTheSecondMarker` at **R8-1 M**. Restoring the old reach past the second marker fails that test at **R8-2 P**. Both also reach offenders.

P1 (bound chooses closer beyond the first marker) and P2 (wholly-after clause loses its end) fail **`noBranchOfTheSentencePresumesAProcessor` through offenders**, not merely reach. Each baseline and restore is green; failures are `<failure>`, source SHA restoration and `git diff -- src` checks pass.

My additional unconditional-holds mutation fails the same offenders assertion. Moving an exact endpoint by one survives the matrix: O9-1. Keying effective-closer checks on the note is semantically appropriate for the non-applicable case, but not an independent applicability oracle.

### 6. Matrix reach — numbers reproduce; reach is not exhaustive correctness (RUN, READ)

Replayed **3 × 5 × 8 × 8 × 3 = 2880** logs: **2520** notes; all **360** `pastSecondOnly` cells null. The recorded matrix output reproduces. The new layouts select:

- `twoMarkers`: rows before, between and past the markers;
- `pastCloser`: rows before/after the first marker and beyond the closer, with an adjusted index when no closer exists;
- `twoMarkersAfter`: one row between and one past the markers;
- `pastSecondOnly`: only the row past the second marker.

The extra leading-record shift is applied to each index. The cases are consistent with the intended layouts, but none has adjacent markers, and none places the opening record in the view used by the later-run lead.

My derived `R9MatrixCheck` found **exactly one principal bound pattern in each of the 2520 notes**. Each principal pattern belongs to its expected branch family. The wholly-after pattern deliberately covers both conditional and unconditional-premise families. Later-run and closing patterns are additional clauses, so the full list of 17 patterns is not mutually exclusive and should not be described as such.

The logTime sentinel catches a particular bad past-marker reference, not every wrong endpoint (O9-1). The named-first-marker and holds checks are conditional on finding their grammar; they do run on matching notes, but do not validate arbitrary prose. R9-3 shows why a correct marker number is not proof that the universal lead describes the whole view.

Putting offenders before reach is right: the semantic failure is no longer hidden by lost textual reach. Counts still precede both, which is reasonable for validating the generated population.

### 7. R8-6: three negative guards — all can fail at their own assertion (RUN)

The new `negative_guards.py` runs selected methods only. Results are **total / failures / errors / skips**:

| Guard/probe | Baseline | Mutated | Restored | Named failure |
|---|---:|---:|---:|---|
| Later-run definite claim and unknown/spanning definite claim | 2/0/0/0 | 2/2/0/0 | 2/0/0/0 | `aScopeWhollyAfterARunBoundaryGetsNoDefiniteClaim`: “RR-4: no definite suppression claim”; `notEstablishedAndSpanningABoundaryConditionsBothHalves`: “S2: nothing is definite” |
| Survival-only condition | 1/0/0/0 | 1/1/0/0 | 1/0/0/0 | `notEstablishedAndWhollyAfterABoundaryConcludesOnBothPremises`: “S2: survival alone” |
| Unconditional holds | 1/0/0/0 | 1/1/0/0 | 1/0/0/0 | matrix offenders, prefix `R-C/R5/R6/R7` |

Every source restore was byte-identical. The first two are explicit guard-sensitivity probes appending prohibited claims while retaining the valid conclusion, not claims to restore historical bugs. This shows the negative assertions execute; it does not prove they catch every possible wording.

### 8. R8-7: generated POM housekeeping — holds (RUN, READ)

Immediately after `mvn -q clean package`, before adding review files, `git status --short` was empty. The reduced POM exists as ignored output and is not tracked. `pom.xml` and shade configuration are unchanged in this delta.

Searches of CI, scripts and docs found no active project consumer that requires a tracked reduced POM. Maven's own generated artifact is a separate matter. Removing the regeneration script's `git checkout -- dependency-reduced-pom.xml` is safe: that command could no longer restore an untracked path. Historical reports still mention the old file; ONBOARDING now describes the ignored output correctly. **READ only:** I did not regenerate the session processor or run that script.

### 9. Sentence audit — bounded conclusions hold in the tested forms; leads and input grammar still matter (RUN, READ)

I reread `sentence`, `closing` and `unreadableClosing`, including each premise-free, no-node, applicability-open and unreadable branch, and checked the probe and matrix outputs. The immediate record before a distinct second marker remains eligible; the record at its boundary does not. The later-run text does not claim survival. A closer across a marker is named without “holds until”. No-node ambiguity is carried into holds and closings.

The remaining established-as-fact errors are R9-1, R9-2 and R9-3. The reviewed matrix is not proof that all possible text, grouping, header and view combinations are covered. The agreed one-stream assumption is accepted as an owner boundary, not inferred from the log.

### 10. Gates — own runs at the subject tip (RUN)

JDK: local Amazon Corretto **21.0.8**. Commands ran from the isolated review worktree, with that JDK as `JAVA_HOME` and first on `PATH` for Java tools.

| Command/check | Own result |
|---|---|
| `mvn -q clean package` | **2107 / 0 failures / 0 errors / 98 skips**, **278** source-mapped Surefire reports; **no orphans**; exit 0 |
| `mkdocs build --strict` | exit 0 |
| `mvn -q -o -Dtest=TrailingWhitespaceTest,SpecLinksResolveTest test` after staging the review | **5 / 0 / 0 / 0** (2 whitespace, 3 link tests) |
| `git diff --cached --check` for the review packet | clean |
| `git diff --check e5541d5b HEAD` | clean |
| `git diff --stat e5541d5b HEAD` | 18 files; **no MainFrame** |
| Rule-one sweep of tracked files and review additions | clean, with only the two rule-defining files exempted |
| `git rev-list --count HEAD..origin/main` | **0** |
| Java source launch of original R8Review against the built jar | all 15 cases reproduce the recorded notes |
| Java source launch of original R9Matrix against the built jar | 2880 / 2520 / 360 and recorded pattern counts reproduce |
| `python3 docs/handoff/evidence/mongoose-audit-production-impl/rereview8-fixes/witness13.py` | all four controls hold; green baseline, named failure, restored green |
| `python3 docs/handoff/evidence/mongoose-audit-production-impl/rereview9-probe/negative_guards.py` | three negative guards sensitive; additional holds mutation caught; exact-endpoint matrix probe survives; every restore identical |

Probe compilation and exact runnable commands are in the packet README. Full-suite XML counts were captured before targeted test runs replaced the reports. A final clean package after restoring all mutations reproduced the same 2107/0/0/98 over 278 reports, with no orphans. The 98 skips are not passes. No display run was required or performed because this delta does not change MainFrame or another UI class. No full mutation gate, earlier-round witnesses, client session, key, merge, rebase, release or deployment was performed.

### 11. Report and owner-decision accuracy (READ, RUN where stated)

The eighth-round table accurately maps its actual changes and named tests. Its runtime claims reproduce on its committed cases. The universal second-marker statement in the CHANGELOG and Javadoc is the intended settled rule, but currently overstates the implementation for adjacent markers (R9-1). The header-only description is implemented literally but too narrow for the contract (R9-2). The R8-2 lead is still overbroad (R9-3).

The in-place round-7 corrections now distinguish the old bound check from the stronger claim, distinguish reach failures from offenders, and withdraw the unsupported “eleven tests” count. These are accurate corrections when compared with the prior review and preserved output (**READ/REPORT**); I did not rerun prohibited older witnesses. P13 preserves its miss: two broken tests predicted, one observed. History confirms `e8a1eb7f` precedes the fix commit; that establishes committed ordering, not the author's uncommitted work history.

The spec MA-8 text and class Javadoc explicitly attribute **one processor per grouping** to the owner, explain that records do not prove it, and keep the assumption out of each note as directed. I did not independently validate a producer's deployment configuration. The owner's instruction is the authority for this review boundary. Targeted witnesses, the optional fixes, and unchanged shade configuration match the other decisions.

## Owner decisions and remaining uncertainty

**No new owner decision is needed for R9-1–R9-3.** They enforce the existing marker limit and truthful scope. I have not reopened any of the five decisions or unrelated agreed-open delivery items.

Not verified: external plugins returning null in practice; every producer's compliance with the one-stream condition; UI/display behavior, which is unchanged by this delta; a live Follow/client scenario; upstream production or publication. The malformed mixed-marker exploratory input is not treated as a supported producer case. Review probes demonstrate current wrong results and guard reach; they are not committed product regressions closing the new findings.

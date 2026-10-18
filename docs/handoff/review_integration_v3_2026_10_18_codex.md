# Integration v3 review — 2026-10-18

**Verdict: REQUEST CHANGES at `adc1e579`.** The report pre-allow can write into a different project or outside
the project, and a human retitle does not revoke ownership. The review branch corrects these findings under
the owner's explicit authorisation of a bounded ownership redesign. Those new corrections need review;
this report is not independent approval of my own implementation.

Scope: `ddccd5c5..adc1e57929e31594b6214758dbb39cd36dd2549f`, on the supplied isolated clone.
Starting SHA: `adc1e57929e31594b6214758dbb39cd36dd2549f`. Work and fixes are confined to the supplied clone,
on `review-fix/integration-2026-10-18c`. No earlier integration changes were re-reviewed. The previous report
`review_integration_2026_10_18_codex.md` was absent. File/line references below describe the reviewed candidate.

## Findings

### C3 — P1 — report permission has no project or output ownership — VERIFIED, corrected

Owner: `feature/source-text-and-report-allow` (FIX-9).
`src/main/java/telamin/fluxtion/audit/analyser/analyser/mcp/McpTools.java:228` stores only name and timestamp;
`ui/ActionExecutor.java:316` applies a finding using the current exchange directory without the admission.
Paths abbreviated after the shared analyser package refer to that same source tree.

Two real-frame sequences failed at their wrong-result assertions before the fix:

1. Admit creation of `finding` in DEMO-A, open DEMO-B, then execute the admitted call: `ok=true`, and B receives A's report.
2. Admit a finding PDF into A's exchange folder, change the exchange folder to a DEMO directory outside A,
   then execute it: `ok=true`, and the actual PDF appears outside the project.

The archived baseline probe and its limitations are in [the evidence notes](evidence/integration-v3-2026-10-18/README.md).
Its three cases, including C4 below, returned **3 / 3 / 0 / 0**: assertion failures, not crashes or skips.

Correction: one EDT observation captures the project lifetime from `activeProject`, complete report revision,
ownership and resolved output. The admission binds these and the exact input. `localAgentPermission.onHostedReportApply`
decides the final validity from facts, with an answer effect. The frame performs the write in that same EDT turn.
New output is bound to its canonical destination, containment is checked again, and `CREATE_NEW` prevents replacement
of a file that appeared. No filesystem operation is performed inside event dispatch.

Maintained coverage: `ReportOwnershipFrameTest.anAdmittedReportCannotMoveToAnotherProject`,
`anAdmittedFindingCannotFollowAChangedExchangeDirectory`, `aProjectLifetimeCannotBeReusedAfterSwitchingBack`,
`aValidFindingWritesButAnAppearedFileIsNeverOverwritten`, `anAdmittedOutputCannotBecomeAnOutsideSymlink`.
The same suite checks valid creations and own updates. `ReportAdmissionGuardTest.theWriteRunsOnlyAsJudged`
also checks changed input, appeared/deleted reports, another generation and Safe mode on the real generated processor.

### C4 — P2 — timestamp ownership accepts a human-edited report — VERIFIED, corrected

Owner: FIX-9. `mcp/McpTools.java:360`, `ui/MainFrame.java:2199`, and `report/ReportVerb.java:77`:
a title/notes-only change deliberately keeps `createdAt`. Admit an own update, retitle through the ordinary report
verb, then execute the old admission. It replaces the person's title successfully. The unchanged-own-report positive
control passes before and after; the wrong-provenance assertion fails before the correction.

`net/ActionServer.java:355` also reads the report back after dispatch and treats that later value as the agent's write.
That attribution gap was initially INFERRED from source; a maintained HTTP test now places a human edit between the
write and receipt delivery. The server must record the actual write, not that later observation.

Correction: SHA-256 of the complete persisted report definition replaces the authoring timestamp as the revision.
The ownership key includes the real project root, node-owned project lifetime and name. The write returns an internal
receipt carrying exactly the revision stored; the HTTP adapter forwards that receipt, including when a report was saved
but its optional PDF failed. Receipt metadata is excluded from `ActionResult.toMap` and `toJson`.

Coverage: `ReportRevisionTest.titleNotesAndSectionsArePartOfTheRevisionEvenWhenTheTimestampIsKept`,
`ReportOwnershipFrameTest.anExternalRetitleRevokesAPreviouslyAdmittedOwnUpdate`,
`aReceiptDescribesTheWriteEvenWhenThePersonEditsBeforeItIsRecorded`, and
`HostedGrantServerTest.theServerRecordsTheReceiptRatherThanAnInterveningEdit`.

### C1 — P2 — accepted source-text window overflows to an empty success — VERIFIED, corrected

Owner: FIX-9. `design/SourceText.java:120`.

`SourceText.slice`: `{around: 2, context: 2147483647}` is accepted, but addition wraps and returns
`from: 1, to: 0, content: ""` without a truncation disclosure for a 500-line file.

VERIFIED before correction: `SourceTextTest.aLargeContextCannotWrapTheWindowIntoAnEmptySuccess`
failed at the wrong-result assertion, expected `to = 200`, actual `0` (1 / 1 / 0 / 0).
The correction widens arithmetic before saturating to the line-number range; the existing 200-line/32-KB
limits still apply. Committed as `65460b2e`. That commit's headless suite was **4224 / 0 / 0 / 332**,
596 source-mapped reports, no orphans; its two affected source display suites were **14 / 0 / 0 / 0**.

### C2 — P2, test gate — sample spotlight races its earlier navigation — VERIFIED, corrected

Owner: `fix/project-source-roots` (FIX-8). `src/test/java/telamin/fluxtion/audit/analyser/analyser/ui/SampleProjectSpotlightFrameTest.java:93`.
The topology verb starts a background pane read. An empty EDT turn does not wait for it. The next spotlight captures
the pane's old navigation counter; the earlier read installs, increments it and changes the view before spotlight apply.
The product correctly reports **"Java source spotlight superseded; retry against the current view"**.

A held real reader orders capture, earlier-pane install and spotlight apply to reproduce this exact refusal.
Without the fixture's pane-read barrier, `theSampleJourneyWaitsForItsEarlierPaneRead` fails at its visible-lit assertion
(**1 / 1 / 0 / 0**). With it, the sample suite passes, including the two layouts and the generated dispatch target.
The correction waits for the requested panes' reads and layout before a separate spotlight. It introduces no retry
and changes no product supersession rule.

## Other seams examined

- VERIFIED by guards and source inspection: `CustomerApiKeysTest`, `WorkModeKeepsTheApiTest` and
  `SourceTextApiDeltaTest` pass. The external API retains its pinned additive source-text change. The receipt stays private.
- VERIFIED: source text's existing range/UTF-8/byte-cap tests and real-frame containment cases pass; reads use the
  authorised real roots and refuse links out, non-text and malformed encoding. C1 was the additional failure found.
- INFERRED from source, supported by the passing existing tests: starter substitutions are allowlisted from the first-run
  record after pin checking; pinned and written digests are separate; unresolved markers retain notes. No new defect found.
- VERIFIED by the affected frame suites and plain-text guard: transcript chips retain their text, foreign text remains
  plain, and the proposal's visible Review entrance renders the snapshot. Turn timestamps remain adapter observations
  accepted by the existing nodes. No new scheduling decision was added to a surface.
- The session processor and GraphML were regenerated through `mvn -o -q -Pregen process-classes`. Java and its resources
  copy match. `GeneratedSourceIsPublishableTest`, `OneDispatchModelTest` and the fact-field inventory pass.

## Verification record

- JDK 21.0.9, Maven 3.9.9; Maven runs serially.
- Unchanged candidate headless `mvn -o test`: 4223 / 0 / 0 / 332.
- Final `mvn -o test`: **4234 / 0 / 0 / 340**, **598 source-mapped reports, no orphans**. A preceding clean run also
  passed with those counts. Skips are reported as skips; they are not display passes.
- Final affected display runs, one Maven invocation per class under the shared display lock:

  | Suite | total / failures / errors / skips |
  |---|---|
  | ReportOwnershipFrameTest | 7 / 0 / 0 / 0 |
  | ReportRecoverableDeleteFrameTest | 6 / 0 / 0 / 0 |
  | SampleProjectSpotlightFrameTest | 4 / 0 / 0 / 0 |
  | JavaSourceSpotlightFrameTest | 13 / 0 / 0 / 0 |
  | SourceTextFrameTest | 1 / 0 / 0 / 0 |
  | SourceFreshnessFrameTest | 2 / 0 / 0 / 0 |
  | Issue84JavaWalkFrameTest | 13 / 0 / 0 / 0 |
  | TranscriptCopyHoverFrameTest | 1 / 0 / 0 / 0 |
  | StarterProposalFrameTest | 2 / 0 / 0 / 0 |
  | StarterProposalVisibleFrameTest | 1 / 0 / 0 / 0 |
  | ForeignTextFrameTest | 1 / 0 / 0 / 0 |
  | Total | **51 / 0 / 0 / 0** |

- Final controls preflight: **73 frame suites, 686 anchors**. Harness tests: **5 passed**.
- Seven requested targeted fast-engine controls: **7 caught**, each at its named assertion (**1 / 1 / 0 / 0**).
  Every source and compiled-class restore was byte-identical, and every restored run was **1 / 0 / 0 / 0**.
  Requested and caught name sets are identical; see the evidence notes.
- `mkdocs build --strict` passed. The documented public-data sweep and `git diff --check` print nothing.
- Display verification is serialized under the shared display lock, never run concurrently with another session.
- The DEMO frame was rendered and its screenshot inspected: the node and generated Java dispatch line are lit beside
  the graph. The image is retained locally, not published, because its source label contains a temporary machine path.
- The public framework reference and golden path were read. No credentials were inspected. The CLI in report tests is
  simulated; project, report, PDF and session decisions use the actual application code.

## WHAT I DID NOT CHECK

- The complete 73-suite display battery, full mutation gate, loop bench and usability scorecard were not rerun here.
  These are targeted review checks, not release qualification.
- No paid AI call, real hosted CLI session, live starter download/build chain or participant project was used.
- No adversarial filesystem process swapped an ancestor directory during the final OS write. Revalidation and
  `CREATE_NEW` cover the tested interleavings and final-component overwrite; they do not claim an atomic sandbox against
  another process changing ancestor links concurrently.
- Earlier integration findings and owner decisions were not cleared by this delta review. In particular, this branch's
  presence does not establish that corrections from the previous integration clone have been brought in.
- Main CI is configured for main/PR/manual runs, not pushes to this review-fix branch. Local results above are not CI.

## Corrections and owner handoff

Fix range starts at `adc1e579` and contains `65460b2e` plus the report-ownership and sample-fixture correction commit.
The final head and push destination are reported on delivery; no merge or release is performed.
The owner authorised the ownership redesign. No further design choice is required for these four findings; review
the new node/effect/receipt boundary before integrating it, and retain the existing integration-wide open decisions.

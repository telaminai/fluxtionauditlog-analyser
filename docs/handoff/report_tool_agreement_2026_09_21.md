# Tool agreement implementation — 2026-09-21

Branch: `feat/tool-agreement`, based on `08954d43`. Work in progress; no release claimed.
The owner kept the existing evidence-correctness work at P1 for this pass.

## TA-1 — completed

Frozen prediction (tracker, committed before implementation): the committed 23-node graph paired with
constructed logged ids `rootNode`, `riskCheck`, `output` will match 3/3 through UI, discovery and session;
authored-only pairing will fail the equality regression; the foreign-graph control will remain rejected.
**Held.** Shared declared ids now enter discovery, frame pairing and the session's graph observation.
Discovery's authored-node count is retained as a view count, independent of pairing.

Fixtures: committed `MarketProcessor.src-round3.graphml`; the three-logger audit record is a
**constructed regression case**, not a recovered session log.

Tests: `GraphPairingTest.frameworkLoggerIsDeclaredInTheCommittedGraph` and
`PairingDuringLoadFrameTest.committedGraphPairsIdenticallyThroughFrameDiscoveryAndSession`.
The latter drives the real frame and compares its verdict, discovery and the session processor.
Existing `GraphPairingTest` / `GraphmlDiscoveryTest` foreign-graph controls remain green (reject mismatch).

Mutation: returning `Scaffolding.authoredNodes(topology)` from the common helper produced two failures:
`expected: <23> but was: <8>` and `session includes the framework logger ==> expected: <3> but was: <2>`.
[Saved witness](evidence/tool-agreement-2026-09-21/ta1-mutation.json). The collector initially expected a
bare test name; Surefire includes `(Path)`. The same saved results were checked after normalizing that
suffix; the mutation was not rerun to obtain a different result.

Validation: restored `mvn -q test` passed; restored display test passed with 1 test, 0 failures/errors/skips:

```sh
mvn -q '-Dtest=PairingDuringLoadFrameTest#committedGraphPairsIdenticallyThroughFrameDiscoveryAndSession' -Djava.awt.headless=false '-DargLine=-Djava.awt.headless=false' test
```

Counts: analyser **13 → 11 open** (D1/D2 closed); upstream **8 → 8 open**.
No runtime behavior was changed or newly asserted. No client sessions ran.

## TA-2 — completed

Frozen before implementation in the tracker: the committed copies, renamed to their original common
basename in separate directories, will disagree without a log; a round-3 logger will rank the 23-node
copy first and name `eodReportPublisher` missing from the 20-node copy; identical copies agree; opening
either copy announces the disagreement without refusing. Disabling the comparison must fail.
**Held.** Counts: analyser **11 → 10 open** (D3b closed), upstream **8 → 8 open** (D3a still open).

Tests in `GraphmlDiscoveryTest`:
- `committedCopiesDisagreeWithoutALogAndRankByLoggedEvidence`: committed fixtures, exact fingerprints,
  counts, modification times, missing logger and ranking; no recovered log is claimed.
- `identicalCommittedCopiesAgreeAndMissingMetadataIsUnknown`: identical-copy negative control;
  additionally a labelled constructed metadata-absence variant stays unknown.
- `declaredProcessorGroupsRenamedCopiesAndNodeSetsAreCompared`: constructed metadata variants of the
  committed graph (the packet contains no processor-class key); equal counts/fingerprints cannot mask
  a changed node id. Only declared metadata is used; no class execution or hierarchy inference.

`PairingDuringLoadFrameTest.openingCommittedCopiesAnnouncesDisagreementWithoutRefusing` opens each
committed copy with no log, awaits the background comparison, checks context and the visible status,
and verifies close clears it. Every entry point uses the topology-load callback. A result is discarded
if a different topology has since loaded. The immediate open echo says pending; it never claims a
comparison already finished. Scan scope is configured roots plus the opened directory; it is bounded,
reports incomplete scans and does not choose a correct copy. Reopen/discover refreshes file observations.

Mutation: disable the disagreement verdict. These three tests failed with
`expected: <disagree> but was: <agree>`: the ranked fixture test, node-set/processor test and real-frame
open test. [Saved witness](evidence/tool-agreement-2026-09-21/ta2-mutation.json).
The first sandboxed attempt aborted in macOS windowing and produced no valid witness; it was repeated
outside the sandbox. A test-author error using `close` instead of `open {close}` was corrected before
acceptance. Neither error is presented as a product failure or mutation witness.

Restored validation: `mvn -q test`; all `PairingDuringLoadFrameTest` cases with display enabled;
`mkdocs build --strict`. All pass. No new LLM sessions, no producer fix, no release or main push.

## TA-3 — completed

Frozen before edits under the existing reports item: confirmations use Observation / Assessment in the
table tooltip, topology callout, report panel and both PDF routes; fault labels remain unchanged; kind
survives explicit recovery against the same verified log, but changed logs cannot regain saved flags;
disabling the label selection fails. **Held.** All inputs are **constructed regression cases**.

Tests: `FindingPresentationTest.confirmationUsesNeutralLabelsOnTableTopologyAndReports` checks the table
presentation, actual painted callout line layout and rendered report-panel component text.
`faultRemainsTheDefaultAndInvalidKindsNeverReachTheSink` drives the verb and its category refusal.
`FindingReportTest.confirmationUsesNeutralLabelsAndKeepsItsKindAcrossSerializedMerge` covers JSON,
legacy default, partial edits and the single-finding PDF.
`ReportRendererTest.confirmationSectionUsesNeutralLabels` covers the investigation PDF.
`SessionRecoveryFrameTest.confirmationFlagSurvivesExplicitRecoveryOnlyAgainstTheSameLog` drives a real
project close/reopen/explicit restore, including a changed-log refusal. Flags were not previously part of
recovery; they now use its existing verified-log guard, with no implicit restore or new identity policy.

Mutation: `Finding.confirmation()` always returns false. Three presentation/PDF tests fail; the
[saved witness](evidence/tool-agreement-2026-09-21/ta3-mutation.json) records the failing assertions.
Fault controls remain valid. Source restored; full headless suite and full recovery display suite pass.
The manifest parity test caught a missing `kind` mention in the built-in prompt; that omission was fixed
before the final green run (1,729 tests, 43 display skips). One sandboxed full run could not bind
loopback sockets; the unrestricted rerun passed. The initial recovery assertion expected an absent flags key where context
returns an empty list; corrected to assert no restored flags, preserving the negative control.

Counts: analyser **10 → 9 open** (D4), upstream **8 → 8 open**. The broader reports tracker item is still
open for its other requirements. No claim that commentary becomes runtime evidence; no client sessions.

## TA-4 — completed

Frozen before edits: constructed values 10, 10, 15, 15, with the time window starting at record three,
will return delta 5 at its first point in both query and chart extraction, STRICT and LOCF. Time bounds
select output; rolling history starts at the loaded log's beginning and respects other filters. Existing
threshold state must not be presented as a new crossing at the lower bound. **Held.**

`WindowedHistoryTest.deltaAtWindowStartAgreesWithWholeLogForBothResolutionModes` covers both paths and
resolution modes. `windowDoesNotInventAnEntryAlreadyPresentBeforeItsLowerBound` checks threshold-state
history. `durationAndSampleWindowsUseEarlierHistoryButReturnOnlyRequestedTimes` covers duration/count
windows and preserves the non-time-filter boundary. All are **constructed regression cases**; A8's
original inputs do not exist in the packet and no session replay is claimed.

Two independent mutations set `history = false`, first in `SeriesScan`, then in `SeriesExtractor`.
Both fail the delta test (`expected: <2> but was: <1>`) and the mean-window test
(`expected: <1> but was: <0>`). The query mutation also fails the crossing-history case.
[Saved witnesses](evidence/tool-agreement-2026-09-21/ta4-mutations.json). Both sources restored.

Validation: full `mvn -q test` and `mkdocs build --strict` pass. Query responses describe the history
boundary; no data before the loaded log is invented. Point-wise formulas keep their previous semantics.
Counts: analyser **9 → 8 open** (D5), upstream **8 → 8 open**.

## TA-5a and TA-U — documentation completed; delivery remains open

Frozen prediction: version-scoped export/D12/D13/pending limits appear in the skill and canonical
runbook without a fabricated follower; a missing disclosure fails the contract check. **Held.**
`CanonicalSkillsTest.auditEvidenceRunbookAndSkillShareTheVersionedLimitsAndDeliveryBoundary` checks
both sources and the existing delivery boundary. Removing D12 from the skill failed the parity assertion;
[witness](evidence/tool-agreement-2026-09-21/ta5a-mutation.json). The full suite passed after restoration.
The skill index is re-pinned to the committed bytes; the source commit temporarily marks its index draft
so it cannot make a false provenance claim, then the following commit supplies revision and hashes.

Decision: retain the previously accepted M31 Chronicle live-store reader route. Reader/plugin maintainers
own delivery; playground owns starter integration and vendoring. The canonical runbook is source material
for that vendoring, **not a claim that a starter already includes it**. TA-5b and TA-5c remain open. No
new follower, server integration or client session was built or run.

TA-U records all eight upstream rows with owners and template/version scope, D7's reclassification,
SG-1/SG-2 separation and the EventLogNode-before-generator-guidance dependency. This records ownership,
not upstream fixes. Endpoint behavior is read from preserved testimony, not independently re-exercised.
Counts remain **analyser 8 open; upstream 8 open**.

## TA-6 — completed

Frozen before edits: a follow-capable local YAML file containing one complete record and one unfinished
record counts one plus one pending, even at initial open. Quiet cannot complete it; later fields must
survive until the actual separator arrives, exactly once. In-memory static strings retain their framing.
**Held.** The initial-file path previously accepted EOF while subsequent polls required a terminator.
Both file paths now require a complete separator line, including its newline. Static strings do not
advertise file follow. Rolled heap containers carry their members' pending count; readers that do not
expose framing status return unknown, not an invented zero.

Constructed tests: `FollowAppendTest.pendingTailSurvivesQuietAndLaterFieldsUntilACompleteSeparator`
(initial open, pause exceeding the poll interval, late fields, split separator, final append exactly once,
rolled-container disclosure) and `PairingDuringLoadFrameTest.pendingTrailingRecordIsVisibleInContextAndFollowStatus`
(real frame, context and human Follow status). No preserved session log is claimed.

Mutation: publish the trailing record on a read/poll despite lacking its terminator. The new test fails
`initial EOF is not proof the writer completed the record ==> expected: <1> but was: <2>`;
the existing append test fails `expected: <0> but was: <1>`.
[Witness](evidence/tool-agreement-2026-09-21/ta6-mutation.json). Source restored.

Validation: full headless suite, the targeted real-frame test, updated follow tests and strict docs pass.
No quiet acceptance, provisional record or delimiter rewriting added. The file framing change is intentional:
an unterminated heap-loaded file's final record is pending even before Follow is enabled.
Counts: analyser **8 → 7 open** (D6), upstream **8 → 8 open**.

## TA-7 — completed

Frozen prediction: standalone Follow changes the actual timer and both human controls; a person can
stop it, unsupported readers refuse, and mixed operations cannot silently follow the previous log.
**Held.** `PairingDuringLoadFrameTest.assistantFollowEchoAndHumanControlsAgree` drives the real frame
on constructed local files, including an in-flight open and a rolled-reader negative control.
`context.log.following` and `supportsFollow` mirror toolbar/menu state; documented in the FAQ.

Mutation: force the adapter to stop instead of starting. The echo assertion fails
`expected: <true> but was: <false>`; [witness](evidence/tool-agreement-2026-09-21/ta7-mutation.json).
The first witness collector rejected the test's `(Path)` suffix; the collector was corrected and the
mutation repeated. Both executions failed the intended assertion. Restored display test and full
headless suite pass; strict docs pass. No new verb. Counts unchanged: **analyser 7, upstream 8 open**.

## TA-9 — before case completed; producer union blocked

Frozen prediction: the committed `desk-quote-supertype.graphml` cannot rule out `acmeQuoteFeed` for a
constructed MarketPrice record logging priceBook. Context, human status/legend and coverage/report notes
say hierarchy unknown; an explicit complete invocation trace retains its stronger absence verdict.
**Held.** Both `DispatchHierarchyTest` methods pass. Existing tests that asserted off-path solely from
missing edges now assert unknown; logged and complete-trace controls are unchanged. The framework
reference was fetched before this change. No application class, source hierarchy or cycle co-occurrence
was used to invent a dispatch relationship.

Mutation: restore off-path classification. The committed-fixture test fails
`unknown hierarchy cannot exclude the vendor handler ==> expected: <MAY_HAVE_RUN> but was: <OFF_PATH>`.
[Witness](evidence/tool-agreement-2026-09-21/ta9-before-mutation.json). Full restored headless suite and
strict docs pass. No new edge or union implemented: those require the producer contract and future
relationship-carrying fixture. D20 stays open. Counts unchanged: **analyser 7, upstream 8 open**.

## TA-8 A2 — completed

Frozen prediction: boolean literals preserve +1/-1 plotting semantics; text equality preserves scalar
types and missing values; chart/query policies, marker predicates and row highlights agree. **Held.**
Constructed goldens 11–13 cover both policies and the chart/query cross-check. `LiteralFormulaTest`
checks typed quoted scalars, missing values, escapes and duration disambiguation; `ReportVerbTest`
and `MarkerExtractorTest` check text predicates at their real use sites.

The first draft of golden 11 incorrectly expected legacy text to decode quote characters. Its failure
exposed that premise; the final golden explicitly uses legacy raw text and the separate typed test
covers the quoted-scalar contract. No reader change was made to satisfy the fixture.

Mutation: make every text equality false. Goldens, typed literal checks, report row highlighting and
marker predicate tests fail with named assertions; [witness](evidence/tool-agreement-2026-09-21/ta8-a2-mutation.json).
Full restored suite and strict docs pass. Boolean literals deliberately alias the existing +1/-1
values, not a new truthiness convention. Counts remain **analyser 7, upstream 8 open**.

## TA-8 A3, A4, A5 and A10 — completed

Frozen predictions all held, on constructed inputs:

- A3: `NamedGraphAndMenuSpotlightFrameTest.markerSpotlightRefusalNamesTheUnsupportedTarget` sees an
  explicit "markers are not targetable" refusal, not a false missing-series message.
- A4: `hiddenProjectRowIsRevealedBeforeSpotlighting` drives the existing Project rail control, then
  obtains the named spotlight. No new Project-panel action is introduced.
- A5: `DesignWorkspaceTest.refusedDesignNamesExactRootCallWithoutAddingIt` checks the exact JSON call
  against a real path containing spaces, unchanged roots, and no false repair promise for a missing file.
- A10: `FindingReportTest.pdfFlagGlyphHasReadableTextFallback` reads the actual PDF bytes.

[Four independent mutations](evidence/tool-agreement-2026-09-21/ta8-small-mutations.json) remove each
behavior and fail its named assertion. Restored full headless gate, both real-frame cases and strict
docs pass. Counts remain **analyser 7, upstream 8 open**. The owner also requested a screenshot audit;
old topology and chart pictures are confirmed stale and will be refreshed after the remaining layout work.

## TA-8 A11/A12 — completed

Frozen prediction held: plot, commentary footer and legend do not overlap; nearby note pins combine
into numbered ranges above the plot. Overflow is disclosed and the full commentary remains on hover.
`ChartAnnotationLayoutTest` verifies each geometry boundary and that chart export includes its Swing
legend; the existing fractional-note boundary regression still passes. All inputs are constructed.

[Three mutations](evidence/tool-agreement-2026-09-21/ta8-layout-mutations.json) move the footer into the
plot, disable pin grouping, and remove the legend reservation. Each fails its own named assertion.
Restored full suite and the two real-frame spotlight classes pass. Native demo captures independently
show the new chart arrangement. Counts unchanged: **analyser 7, upstream 8 open**.

## Documentation screenshot audit

The owner's concern was confirmed: topology legend and chart overlays were obsolete. Rebuilt the
jar and ran `tools/capture-docs.py`: **24 native captures, all successful**. Ran
`tools/capture-conversations.py`: **five captures**, recorded echoes regenerated. Ran the Spring
capture script on the preserved public project copy with released starter 1.0.73: four captures,
real keyless validation rejection and correction. All refreshed images were visually inspected.
The main suite includes a ringed derivative of its native menu image; it does not invent app content.

Updated topology/graph prose in place and removed the conversation's unsound absence claim. The
preserved demo logs have unterminated tails: screenshots now show 9/725 complete records plus a
pending tail instead of asserting 10/726 complete. No raw historical evidence was changed.
The asset README names coverage and limits: MCP/template-picker/bundle tutorial shots were not
recaptured, nor the historical owner-witnessed website preview/vendor evidence.

Full Java suite and strict docs pass. Completed release-execution section moved to the completed
tracker; partial sections retained. This housekeeping was requested first but was missed initially;
it is now corrected without changing its recorded release evidence.

## D19 — completed under Topology feedback 37

Frozen prediction held. `TopologyShowAllTest.showAllExitsNestedFocusWithEitherScaffoldingChoice`
checks nested contexts, false as a negative control, both scaffolding settings, resulting echo,
selection, shading and breadcrumb. Constructed navigation on the committed demo graph, not session
replay. The first assertion incorrectly read a nested ActionResult envelope; corrected to the actual
payload. [Mutation](evidence/tool-agreement-2026-09-21/d19-mutation.json) restores only the old executor
call and fails `showAll must exit every context`, actual depth 2 versus expected 0. Counts **7 → 6 / 8**.

## D18 — completed under Chart feedback 41–43

Frozen prediction held. `ChartAxisWindowTest` tests independent magnitudes and empty sides, then
uses the real GraphPanel pin, filter, refresh and GraphTabs saved-definition restoration with guides
and markers. Constructed regression, not participant replay. Moving a series to an axis also reapplies
the current window. [Mutation](evidence/tool-agreement-2026-09-21/d18-mutation.json) disables partitioning
only in the window path: the left-axis numerical assertion fails. Counts **6 → 5 / 8**.
Other 41–43 concerns remain open, including series lifecycle and target-name compatibility.

## D17 — completed under Chart feedback 41–43

Frozen prediction held. `GraphWindowScopeTest` restores an actual saved chart definition on a
constructed disjoint log, retains the deliberate pin, explains emptiness, distinguishes filters,
and checks pending/failed extraction without stale counts. Same-log and cleared-pin controls
show data. The scope is visible below the plot, in `context.graphScopes`/graph echo and PDF captions.
[Mutation](evidence/tool-agreement-2026-09-21/d17-mutation.json) removes only disjoint-window detection
and fails `disjoint saved pin must explain emptiness`. Counts **5 → 4 / 8**.

## D16 — completed under evidence correctness first

Frozen prediction held. `MarkerResolutionTest` reads the committed standalone and two hosted logs:
STRICT produces 5 buy / 6 sell / 8 price markers, with real record anchors. Existing saved markers
without a mode deliberately retain LOCF. Config and share round trips preserve explicit resolution.
`GraphEchoWarningsTest` checks the new MCP default, explicit LOCF and invalid-mode refusal;
`MarkerLegendTest` checks the human label and PDF note. No source fixture was edited.
[Mutation](evidence/tool-agreement-2026-09-21/d16-mutation.json) disables same-record clearing:
expected 5 buys, actual 11. Counts **4 → 3 / 8**. Bare-key occurrence, payload anchors and explicit
series-pinned y sampling retain their old meaning.

## D15 — completed under evidence correctness first / feedback 39

Frozen prediction held. `DesignSpotlightFrameTest` uses a constructed tall XML and the committed
participant XML in real windows. It verifies viewport refusal, retained numbering, explicit
departures on add, positive screenshot coordinates, and echo equality with painted cutouts.
The EDT is drained to check deferred callbacks cannot undo a successful reveal. The preserved
XML's targets are exercised at both reported window sizes; fit is judged against the actual
viewport, not assumed from the outer frame size.
[Mutation](evidence/tool-agreement-2026-09-21/d15-mutation.json) removes viewport containment and
fails the hidden-line assertion. Counts **3 → 2 / 8**.

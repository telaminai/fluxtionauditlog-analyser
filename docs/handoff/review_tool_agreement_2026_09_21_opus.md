# Independent review — analyser tool agreement (`feat/tool-agreement`)

Reviewer: Claude Opus 5, 2026-09-21. Brief: [brief_review_tool_agreement_2026_09_21.md](brief_review_tool_agreement_2026_09_21.md).

| | SHA |
|---|---|
| Feature head reviewed | `fcaf14adb0d57b1d4bddd0650e43038fec55e2d8` |
| Baseline | `08954d43` |
| `origin/main` at review | `ea865d2d1e6bf082508bdbcbbce3bd44b0ba2332` (20 commits not on the feature branch) |

Worktrees: `/private/tmp/ta-review` (gates, this report) and `/private/tmp/ta-mut` (mutations; every
mutated file restored and checked with `git diff --quiet`). No fix, merge, rebase or amend. JDK 21.0.11
(Corretto), macOS, one Maven process per checkout.

## Verdict

**Not merge-ready: one critical regression.** TA-6's pending-tail rule is applied to **every ordinary
open**, not only to follow. `HeapLogStore.fromFile` now withholds a final record that has no closing
`---`. Every Mongoose export has that shape, and so does the shipped demo series. The last record of each
disappears from the heap path, and the heap and mapped stores now disagree about the same file. This is
intended and tested behaviour, so it is a design decision that conflicts with published Format 1 §1, not
a slip.

Everything else is strong. Baseline counts verify at **13 → 1 analyser open** and **8 → 8 upstream open**.
All 17 recorded mutations were reproduced and fail their named assertions. Every gate passes except one
environmental display skip. Integration with newer `main` needs a deliberate skill re-pin, not a textual
merge.

| Area | Verdict |
|---|---|
| TA-1 pairing, declared ids | ✅ verified (mutation red) |
| TA-2 copy disagreement | ✅ verified (display mutation red) |
| TA-3 confirmation labels | ✅ verified (mutation red; see the screenshots note) |
| TA-4 rolling history | ✅ verified (3 tests red under mutation) |
| TA-5a skill/runbook parity and pins | ✅ parity verified; ⚠ D12/D13 wording stale against newer endpoint evidence (F4) |
| TA-6/7 pending tail, follow agreement | ❌ **F1**: correct in follow, wrong on ordinary opens. TA-7 verified (display mutation red) |
| TA-8 typed text, layout, PDF, refusals | ✅ verified (text equality, legend, PDF glyph mutations red; the legend is visibly reserved in the screenshots) |
| TA-9 unknown hierarchy | ✅ verified (mutation red; the legend shows "may have run (dispatch hierarchy unknown)") |
| D14–D19 | ✅ all six verified by mutation (D16 via `MarkerExtractor`, see F6) |
| Producer-blocked D20 | open, correctly |
| Integration with `main` | ⚠ F2, F3: skill-index conflict needs a re-pin; the framer overlaps the end-marker branch |

## Findings

**F1 · Critical · ordinary opens silently drop the last record of every unterminated file.**
`src/main/java/telamin/fluxtion/audit/analyser/analyser/parse/HeapLogStore.java:43` changed from
`new HeapLogStore(text)` to `new HeapLogStore(text, true)`, so a static open withholds an unclosed trailing
record as "pending". `MappedLogStore` was not changed.

- Reproduction (branch build, `HeapLogStore.fromFile` against `new MappedLogStore`):

  | File | Branch heap | Branch mapped | Released 1.16.0 |
  |---|---|---|---|
  | `~/.fluxtion-analyser/demo/demo-quote-series.yaml` (726 `eventLogRecord`s; ends `endTime: …`, no `---`) | **725** | 726 | 726 |
  | A byte-exact Mongoose export of 25 records (joined by `\n---\n`, last record unclosed — the shape of every `export-audit.sh` output) | **24** | 25 | 25 |
  | The same 25 records, each closed by `---` | 25 | 25 | 25 |

- Expected: a file opened for reading shows every record it contains. Format 1 §1
  (`docs/site/format-spec.md:18-20`) is normative: non-blank text after the last separator **is a
  record**. Actual: the record is withheld indefinitely on a file that will never grow, and the status
  bar says "1 trailing record pending" (`MainFrame.java:3832`). The branch's own refreshed screenshots
  show it: `graph-markers-dark.png` and `reports-dark.png` read "725 records … 1 trailing record pending"
  on the 726-record demo.
- Intended, not accidental: reverting only line 43 to `false` fails
  `FollowAppendTest.pendingTailSurvivesQuietAndLaterFieldsUntilACompleteSeparator:41` ("initial EOF is not
  proof the writer completed the record") and
  `PairingDuringLoadFrameTest…pendingTrailingRecordIsVisibleInContextAndFollowStatus:104`.
- Why it matters: in the audit-analyser bundle the last record is the run's final state (for example, a
  running total's last value). Hiding it contradicts D-T8, which demands a gap be reported and never
  concealed. Showing the record while noting it is unterminated is the honest middle.
- Suggested direction (the author decides): withhold only in follow (`appendFrom`); on an ordinary open,
  emit the tail and report it as unterminated. Do not claim "stopped mid-write" either: the
  stream-end work on `feat/audit-format-end-marker` hit the same trap (see F3). Add a fixture in the real
  export layout; every conformance fixture ends with `---`, which is why the suite cannot see this.

**F2 · High (integration) · the skill index conflicts and cannot be merged textually.**
Trial merge (`git merge-tree fcaf14ad origin/main`) conflicts in `docs/skills/m19-skills/2/index.json`,
`CHANGELOG.md` and `docs/specs/tracker.md`.

| Pin | revision | point-at-the-fault | run-mongoose-server | add-a-node |
|---|---|---|---|---|
| base `08954d43` | `01b6a4fa` | `0193e5b2` | `1e7b0e62` | `ec76851e` |
| feature `fcaf14ad` | `fa4a2027` | `0193e5b2` | **`b239b907`** | `ec76851e` |
| main `ea865d2d` | `49086f5a` | **`5c02e6d1`** | `1e7b0e62` | **`7e29fea5`** |

Neither revision contains the other side's bytes. The merged index must be re-pinned to a commit
containing both, with all three hashes updated. Otherwise `CanonicalSkillsTest` goes red, or the playground
vendors a mixture. Main's two skills are already re-vendored and live in the public bundle
(fluxtion-web `0090c27`), so the Mongoose skill change also needs a re-vendor after merge.

**F3 · High (integration) · this branch and `feat/audit-format-end-marker` both change trailing-record
semantics in `RecordFramer` and `HeapLogStore`.** This branch adds `frameWithPending` and withholds on
static opens (F1). The end-marker branch reports every unclosed tail as `STOPPED_MID_WRITE`. Combined,
a normal Mongoose export would lose its last record **and** be labelled stopped mid-write. The two need
one agreed rule before either merges.

**F4 · Medium · D12/D13 wording is stale against later endpoint evidence on `main`.**
`docs/specs/spec-tool-agreement.md:78` diagnoses D12 as "starts at `toEnd()`". A live test on
svc-admin-web 1.0.43 (recorded on `main`, AFMT) shows the route, the upgrade and the producer all exist,
and every tick throws `ThreadingIllegalStateException` (tailer created on the connect thread, read on the
executor), swallowed at DEBUG. For D13, the shipped Mongoose skill
(`docs/skills/mongoose/run-mongoose-server/SKILL.md` D13 bullet) states the listing "stayed at startup
values". A later endpoint test on `main` did not reproduce that (`recordCount` 25 matched 25 exported).
Per the brief, this branch's 1.0.43 testimony is not a fresh endpoint experiment. Qualify D13 as observed
once and unreproduced, and correct D12's cause. The skill's D12 bullet itself is neutral and fine.

**F5 · Medium (gate) · the display gate has one skip on this machine.**
`PersonAtTheScreenFrameTest.escapeWithTheSearchHistoryPopupFocused_putsSeveralSpotlightsOut` calls
`assumeTrue(focused…)` and skips when macOS does not give the frame keyboard focus (the terminal is
foreground). It skipped in the display run and again when run alone. The test is unchanged from base and
the skip is environmental, but the brief says to fail on any display skip, so I cannot certify "50/50, no
skips" here. CI (xvfb) is the place to confirm it; a branch push is not evidence that CI ran.

**F6 · Low · two witness descriptions point at the wrong site.** The D16 witness says "retain carry in
STRICT marker evaluation", but mutating `SeriesExtractor.java:81` or `SeriesScan.java:100` leaves
`MarkerResolutionTest` green. The carry that matters is
`graph/MarkerExtractor.java:90` (`if ("STRICT".equals(spec.resolve())) carry.clear();`). The D18
mutation string occurs twice in `ChartPanel.java` (363, 417), and only the second is under test. Neither
is a product defect; name the file and line in the witness so a reviewer can repeat it.

**F7 · Low · full-suite skips are 49, not stated.** 1,766 run, 0 failures, 49 skipped. The report gives the
test total but I could not find the skip count to compare. Record it.

## Gates (run by me, in order, on `fcaf14ad`)

| Gate | Result |
|---|---|
| `mvn -q clean test` | exit 0 · **1,766 run, 0 failures, 0 errors, 49 skipped** |
| display set (11 frame classes, `-Djava.awt.headless=false`) | exit 0 · **50 run, 0 failures, 1 skipped** (F5) |
| `mvn -q package -DskipTests` | exit 0 |
| `python3 tools/test_tools.py` | exit 0 · "all passed" |
| `python3 tools/verify-m64-spotlight.py` | exit 0 · "all M64 spotlight checks pass" |
| `mkdocs build --strict` | exit 0 |
| `git diff --check 08954d43..HEAD` | clean |
| CLAUDE rule-1 sweep (exact tracked-file command) | nothing printed; this report is swept below before commit |

## Mutation witnesses (reproduced; every source restored and re-verified)

| Item | Mutation | Failing assertion(s) |
|---|---|---|
| TA-1 | `declaredNodeIds` returns `Scaffolding.authoredNodes` | `GraphPairingTest.frameworkLoggerIsDeclaredInTheCommittedGraph:22 expected <23> but was <8>` |
| TA-2 | disagreement verdict disabled | `PairingDuringLoadFrameTest.openingCommittedCopiesAnnouncesDisagreementWithoutRefusing:154 expected <disagree> but was <agree>` |
| TA-3 | `Finding.confirmation()` always false | `FindingPresentationTest.confirmationUsesNeutralLabelsOnTableTopologyAndReports:20` |
| TA-4 | `SeriesScan` `history = false` | 3 × `WindowedHistoryTest` (…WindowStartAgrees…:32, …EarlierHistory…:54, …InventAnEntry…:43) |
| TA-5a | remove the D12 disclosure from the canonical skill | `CanonicalSkillsTest.auditEvidenceRunbookAndSkillShare…:59`; the pin test `:183` also fails (hash) |
| TA-6 | publish the unterminated tail under `requireTerminator` | `FollowAppendTest.doesNotIndexARecordStillBeingWritten:88`, `…pendingTailSurvivesQuiet…:41` |
| TA-7 | assistant follow adapter always stops | `PairingDuringLoadFrameTest…assistantFollowEchoAndHumanControlsAgree:56 expected <false> but was <true>` |
| TA-8 | text equality false (`Evaluator.java:84`) | `LiteralFormulaTest…MissingUnknown:12`, `…DurationsKeepTheirWindowMeaning:23` |
| TA-8 | legend width not reserved | `ChartAnnotationLayoutTest…legendReservesSpaceAndIsPartOfExport:57` |
| TA-8 | PDF flag glyph → `?` | `FindingReportTest.pdfFlagGlyphHasReadableTextFallback:103` |
| TA-9 | `MAY_HAVE_RUN` → `OFF_PATH` | `DispatchHierarchyTest.committedSupertypeGraphCannotExcludeAnUnmappedRoute:20` |
| D14 | metadata difference always "unchanged" | `DesignWorkspaceTest.producerSnapshotDetectsNewReceiptAndEditedSourceWithoutReopening:31` |
| D15 | viewport containment removed | `DesignSpotlightFrameTest…sourceViewportRefusesHiddenLines…:37` |
| D16 | STRICT stops clearing carry (`MarkerExtractor.java:90`) | `MarkerResolutionTest.preservedLogsCountEventsWithoutCarryingTradesIntoPriceRecords:19` |
| D17 | disjoint-window reason disabled | `GraphWindowScopeTest…restoredPinExplainsEmptyWindow…:40` |
| D18 | right-axis split disabled (2nd occurrence) | `ChartAxisWindowTest.windowUsesEachAxis…:34`, `…pinFilterRefreshAndSavedRestore…:57` |
| D19 | `showAll` → `clearView` | `TopologyShowAllTest…showAllExitsNestedFocus…:36` |

No compilation failure was counted as a witness.

## Baseline counts, judged independently

From the table in `spec-tool-agreement.md`: the analyser owns D1, D2, D3b, D4, D5, D6 and D14–D20, which
is **13**. Of those, 12 are ☑ and D20 is ☐ (blocked on producer metadata), so **1 open**. Upstream owns
D3a, D8, D9 hosted, D10–D13 and D21, which is **8 open**. D7 is correctly reclassified. **The counts are
right.** D6's ☑ is honest for follow mode, but its fix carries F1.

**Merge readiness versus completion.** The producer-blocked remainder (D20; TA-5b delivery; TA-5c
post-shipment spot-check) does not block merge. F1 does, and F2 and F3 must be resolved at integration.

## Screenshots (read visually, not by bounds)

- `graph-markers-dark.png`: the legend sits in reserved space beside the plot, and marker counts
  (166/160) are readable. The status bar shows F1.
- `project-panel.png`: TA-9's "may have run (dispatch hierarchy unknown)" legend entry is present, and
  file observation reads "unchanged-metadata … does not prove identical bytes" (D14 wording honest).
- `reports-dark.png`: renders cleanly but shows only a fault finding, so TA-3's neutral confirmation
  labels are verified by test, not by this capture.
- No sweep-term text was visible in the three images I read. PNG text is outside the mechanical sweep.

## Verified, read, unverified

- **Verified by running:** all gates above; 17 mutations; F1 on the branch build against the released
  1.16.0 jar; F1's intent (revert test); the trial merge and pin comparison (F2); the display skip,
  twice (F5).
- **Read only:** F3 (from the end-marker branch source and its own review); the D12/D13 endpoint
  evidence on `main` (F4, observed in an earlier session, not re-run here); the report's narrative per
  TA item.
- **Unverified:** CI's display run on this head; TA-5b/5c (open by design); PDF visual output beyond the
  flag-glyph test; the races the brief names for D14 at load/publication (tests pass; no independent race
  harness was built).

## Handoff to the author

1. **F1:** decide the static-open rule. Withhold only in follow, and show-and-annotate an unclosed tail
   on an ordinary open. Add a real-export-layout fixture, and align the rule with the end-marker branch
   (F3) before either merges.
2. **F2:** at integration, re-pin `m19-skills/2` to a commit containing both sides' skill bytes, then
   re-vendor.
3. **F4:** correct D12's cause and qualify D13 as unreproduced.
4. **F5:** confirm the display set on CI with zero skips.
5. **F6/F7:** fix the witness sites and record the skip count.

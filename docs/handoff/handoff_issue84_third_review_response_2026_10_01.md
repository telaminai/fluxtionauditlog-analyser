# Issue #84 third-review response — #93–#102

Status: independent review at `94ddd2ca` was CONDITIONAL, with test-only F1 required. The response to F1 is below;
exact-head CI belongs to the PR record. This is an **implementer's response, not independent approval**.
Closing #84 completed the review; it did not approve these defects or their fixes. No issue is represented as
independently closed by this document.

## Baseline and scope

- Actual fetched starting `origin/main`: `258da371d957044625da4b88f5a433922f987b00`.
- Isolated feature branch: `fix/issue84-third-review-response-2026-10-01`. Other sessions' worktrees were untouched.
- Review inputs remain unchanged at `2ef377d5` / `5df6e097`, on
  `review/issue-84-third-review-2026-10-01`:
  [report](https://github.com/telaminai/fluxtionauditlog-analyser/blob/5df6e097/docs/handoff/review_issue84_bundle_provenance_2026_10_01.md),
  [evidence](https://github.com/telaminai/fluxtionauditlog-analyser/tree/5df6e097/docs/handoff/evidence/issue-84-review-2026-10-01).
- JDK 21 throughout. DEMO fixtures and isolated application homes only. No participant projects, credentials,
  paid provider, release, deployment, merge or force-push.
- Regeneration through the configured Maven plugin was separately authorized before use. Generated Java,
  resource Java and GraphML were generated, not hand-edited.
- PR #87 was neither re-reviewed nor changed.

## Unchanged-tree reproductions

All precede production edits, on `258da371`:

| Check | Observed result |
|---|---|
| `mvn -o -q test` | 3,084 tests / 0 failures / 0 errors / 218 skipped; 406 reports, all source-mapped, no orphans |
| Original bundle review probes, built shaded jar, real display | 12 / 5 failures / 0 errors / 0 skipped: #93, #94, #95, #99, #101 |
| Original Java-walk probes, built shaded jar, real display | 12 / 6 failures / 0 errors / 0 skipped: #96, #97, all three #98 scenarios, #102's design witness |
| Original seven bundle/structural controls | Five caught; nested-helper and inherited-Navigator mutations genuinely compiled and survived |
| Registered `walk-can-point-at-java` | Caught; source/classes restored identically and restored run green |

Every original control restored source and both class trees byte-identically. The observed held-read screenshot
showed an empty/reading Source pane beside a strip already saying shown. The changed-line screenshot showed
`return 999` beside the saved caption about `0.004`. #102 is a disclosure/design decision, not a retroactive
claim that the former name-only contract promised revision binding.

## Dispositions and wrong-result witnesses

| Issue | Correction and maintained regression | Wrong result the regression refuses |
|---|---|---|
| #93 | `OpenBundle` correlates verified plan, accepted operation and loaded profile digest. `ProjectProfile.load` compares the exact content being parsed before applying settings. `Issue84BundleFrameTest.aReplacementProfileIsNotTheSendersEvidence`, `BundleOperationOwnershipTest`, `VerifiedProfileContentTest`, and existing bundle success/failure tests. | `replacementProfileMustNotCarryTheAbortedBundlesIdentity`: aborted apply, independently replaced profile at the same path, ordinary open must not inherit provenance. Additional witnesses refuse wrong applied path/content and retain the second of two genuine bundles after an unrelated failure. |
| #94 | `importFromBundle` restores incoming live chart definitions before the persistence funnel. `Issue84BundleFrameTest.importingGraphsKeepsIncomingNotes`. | Incoming notes **and series** must be on screen, in config and in the flushed/re-read profile, not merely acknowledged in an `applied=true` reply. |
| #95 | `BundleRootsRestored` distinguishes remembered roots from currently visible roots. The node applies visible-root edit deltas, preserving offline roots through unrelated observations. `Issue84BundleFrameTest.anUnrelatedEditDoesNotDeleteAnUnavailableAnchor`; existing `BundleProvenanceFrameTest.deletingTheAnchorSticks`. | Offline directory → REPORTS-only import → directory returns → reopen must restore the anchor. Explicit visible-root deletion must still remain deleted. |
| #96 | Only matching `WalkTargetsLit` settles phase and advances the accepted dialogue prefix. `Issue84JavaWalkFrameTest.unreadSourceMustStayPreparing`. | With the real archive reader held: published phase and visible strip are Preparing, accepted step is -1, overlay is empty. After completion: shown, accepted step 0, actual Java target lit. |
| #97 | Native tab changes report `WalkViewChanged` with effect-scoped origin; the node supersedes external navigation. Before applying background source, the adapter also asks the node whether its ticket remains current; that query is defensive, not independently mutation-witnessed (F2 below). `Issue84JavaWalkFrameTest.aViewChangeDuringReadMustNotUndoThePersonsChoice`. | The real `navigatePrevious` action selects Summary while a read is held; the received late completion must not select Source. Walk-owned tab changes retain their ticket, now independently pinned by the F1 witness below. |
| #98 | The synchronous availability read is removed. Java lookup/read runs through bounded background preparation and reports success, refusal or expiry. Three maintained methods: `availabilityMustNotReadOnTheEventThread`, `backDuringTheOriginalReadMustNotBlockTheEventThread`, `anUnreadableResolvedClassReportsFailureInsteadOfPreparingForever`. | Cold archive and Next→Back leave an EDT sentinel responsive while the real reader remains held; malformed UTF-8 settles NOT_SHOWN with the read failure, not an uncaught EDT exception or indefinite Preparing. Refusal, deadline, roots/project changes and stale-step controls remain. |
| #99 | Explicit operation origin and offer permission travel in session facts. `ProjectReopenOffer` decides; effects gather candidates, defer the modal outside dispatch, and report its outcome. `ProjectReopenOwnershipTest` and real-frame disabled, person and superseded-offer tests. | Disabled offers record `offerProjectReopenSkipped`; legitimate person offers run outside the driver cycle; a later operation cannot inherit or revive the earlier offer, including a reused profile path. |
| #100 | D-L3 scans reachable concrete helper/nested dependencies and the inherited public Navigator surface, without following implementations behind the authorized interface route. `ProjectPanelIsRevealOnlyTest`. | The exact compiled nested-helper and inherited-action review mutants now fail named assertions. The direct-frame and extra-declared-action controls remain. |
| #101 | `ProjectReopen` carries independent origins for log and topology lists; the actual dialog labels each. `Issue84BundleFrameTest.fallbackOfferMustNotDescribeAnUnrelatedLogAsInsideTheProject` and `topologyFallbackHasItsOwnOriginBesideAProjectLog`. | An unrelated recent log is not described as project evidence; a project log beside a machine-fallback topology has two different, correct labels. Machine fallback is preserved. |
| #102 | **Chosen contract: name/line lookup, source revision not compared.** Keep CURRENT's existing meaning. The visible caption and target reason explicitly qualify the saved caption's source revision. `Issue84JavaWalkFrameTest.aMovedJavaLineMustNotBeMarkedCurrentAgainstTheSavedWalk`. | Saving a caption about `return 0.004`, changing the source to `return 999`, then replaying must disclose the lack of revision comparison on both surfaces. “Source/run: unverified” is not used as a substitute. |

The cheap source-boundary companion is `Issue84BoundaryContractTest`; D-L3 is itself structural. Mutation
preflight also pins the production anchors against accidental movement. Site and specification wording were
updated with the #102 decision; this candidate does not implement historical source snapshots or revision binding.

## Verification record

The final candidate SHA and exact-head CI results belong to the PR verification section (a document cannot
contain its own commit hash). The committed [verification record](evidence/issue84-response-2026-10-01/verification.json)
contains derived counts, jar hashes, before/after assertion messages, REQUESTED/CAUGHT names, restoration checks,
and unsuccessful attempts. Local verification does **not** stand in for the complete CI gate.

| Final local check | Observed result |
|---|---|
| `mvn -o -q test` | **3,121 / 0 failures / 0 errors / 245 skipped**, 412 reports, all source-mapped, no orphans |
| Relevant display suites, including affected neighbours | **114 / 0 / 0 / 2 skipped**, 14 suites; native `WalkArrowKeysFrameTest.arrowKeysMove` and `StartWorkspaceFrameTest.nativeFileDropOnHeroTextOpensAuditLog` could not acquire desktop focus |
| Maintained bundle/reopen witnesses against starting jar → candidate jar | **15 / 7 failures / 0 errors / 0 skipped → 15 / 0 / 0 / 0** |
| Maintained Java-walk witnesses against starting jar → candidate jar | **12 / 7 failures / 0 errors / 0 skipped → 12 / 0 / 0 / 0** |
| Final targeted fast-engine controls | **31 requested, the identical 31 names caught**, no missing tail; 164.3 seconds; every red is a named assertion, source/classes restored byte-identically, restored runs green |
| Preflight | **42 frame suites; 580 registered control anchors** |
| `tools/test_project_chart_review.py` | **5 passed** |
| Strict MkDocs, `git diff --check`, tracked/addition public-data sweep | Passed; sweep printed nothing |

The extra maintained Java failure beyond the original six is the stronger closed-Source-tab witness: a removed
tab ends the pending walk rather than retaining it as showing. The extra bundle witnesses cover independent
topology origin and deferred-offer supersession. A new off-thread chart-action control passes against the baseline
and candidate, and catches the direct-post regression introduced and then corrected during this response.

All display and mutation runs were sequential under the shared display lock. The relevant Maven display run used
both `-Djava.awt.headless=false` and `-DargLine=-Djava.awt.headless=false`. The built-jar runner uses an explicitly
non-headless JVM and excludes `target/classes` from application lookup. Screenshots were inspected: the held reader
shows Preparing with an empty source overlay; the changed-line view shows `return 999` and the explicit saved-revision
qualification beside the old caption. The late-navigation assertion confirms Summary remains selected.

The full zero-skip display suite, engine self-test, all four complete-registry shards and collector must be read at
the PR's exact head, alongside build and loop-bench. **Neither local native-focus skips nor a partial CI run are waived.**

The retained local attempts include:

- A launcher PATH mistake before the unchanged baseline: Maven not found; zero tests ran. Corrected environment,
  not represented as a product failure.
- The first changed focused run: existing synthetic bundle plans lacked the newly required digest. Fixtures now
  carry explicit synthetic digests; production has no “missing digest means verified” compatibility waiver.
- Initial adapted review display probes: 24 passed.
- A stronger neighbouring display run: 73 tests, one assertion failure because the new series assertion used
  dotted display spelling instead of GraphSpec's internal separator. Corrected the assertion, not product code.
- First provenance mutation survived because it left the independent content guard intact. The attempted compound
  mutation also survived because its replacement had double-escaped newlines and did not remove pending-plan
  clearing. Both attempts are retained. The corrected control reintroduces the complete pathname-only defect and
  fails the wrong-provenance assertion.
- The first offer-operation control survived a test that only re-opened the active profile (a no-op), leaving
  another profile guard effective. The maintained witness now closes and reopens the same path under a new operation.
- The first maintained deferred-offer test timed out awaiting new audit wording on the old jar. Replaced that wait
  with two deferred-queue barriers. It now fails on the intended wrong result: one inherited offer instead of zero.
- A broader 42-suite display attempt reported **242 / 2 failures / 2 errors / 8 skips**. The new tab listener posted
  directly to an EDT-confined driver from off-thread chart callers, breaking two chart actions and two assistant
  sequences. The listener now captures the origin and marshals the fact to the EDT; `aBackgroundChartActionReportsItsViewChangeOnTheEdt`
  plus `issue84-view-fact-on-edt` pin it. The affected neighbours and both new frame suites then passed **43 / 0 / 0 / 0**.
  The native-focus skips in that broader attempt are recorded, not recast as passes.

The ordinary mutation gate is fail-fast. A partial run's caught names must never be reported as all requested
controls. The public evidence summary retains the failed attempts and distinguishes them from the final run.
Raw local logs, byte-restoration results and screenshots remain under `target/issue84-response`; public evidence
retains the assertions and counts without host paths. The original review files were not edited.

## Reproduction tools

The maintained display suites run with:

```sh
mvn -o -q test -Dtest=Issue84BundleFrameTest,Issue84JavaWalkFrameTest \
  -Djava.awt.headless=false -DargLine=-Djava.awt.headless=false
```

Run display and mutation suites sequentially under the documented display lock. The two new suites are registered
in both CI execution and zero-skip lists. The maintained frame witnesses can also be compiled and run against an
explicit shaded jar using [run_frame_witnesses.py](evidence/issue84-response-2026-10-01/run_frame_witnesses.py),
without substituting application classes from `target/classes`.

## Independent reviewer focus

The self-contained [reviewer prompt](prompt_issue84_response_review_2026_10_01.md) is ready to pass to the other LLM.

Re-run the original real-boundary shapes, not only the new tests. Attack operation/content correlation, the
plan-less internal load, saved offline roots versus actual deletion, delayed UI facts and native keyboard
navigation, and offer permission across operation changes. For #102 judge the **explicit disclosure decision**,
not an invented revision-binding promise. Check mutation names and assertion messages, restored byte hashes,
test skips, source/report mapping, and exact-head CI rather than trusting this response.

## Remaining unverified

- The local native focus/key/drop tests named above did not execute to completion. CI's zero-skip display job is
  required; consult the exact-head PR record, not this local count, for its result.
- No Windows display run or platform-wide UI claim; local JDK 21 display and the CI environment are the verified scope.
- #102 deliberately does not compare source revisions or retain historical source. Whether the explicit disclosure
  is sufficient is a reviewer judgement, not an assertion that revision binding was implemented.
- No independent approval has been performed by the implementer; nothing was merged, released or deployed.

## PR #103 conditional-review response — test-only F1

Starting head: `94ddd2cad938c375bae6e65332535e3e519c5efe`. Review:
[comment 5930302545](https://github.com/telaminai/fluxtionauditlog-analyser/pull/103#issuecomment-5930302545).
No production behaviour, generated processor, GraphML or release configuration changed. No regeneration or
credential access. The existing registered frame suite gains one test, so no CI suite-list change is needed.

`Issue84JavaWalkFrameTest#aJavaTargetOnANonSourceStepKeepsItsWalk` starts a step on Summary with a Java-line target
in a resolvable DEMO source archive. While the real archive read is held, it asserts PREPARING, Summary selected
and no lit target. After release it asserts SHOWN, the walk still showing, accepted step 0, Source selected,
and the requested Java line lit. The screenshot was inspected locally; it is not published because the source
viewer includes a host-specific temporary path.

The fast-engine control `issue84-java-apply-view-is-walk-owned` replaces
`walkViewChangeTicket = walkTicket;` with `walkViewChangeTicket = -1;` in `prepareJavaSpotlightHere`.
It compiles and fails at the named assertion, not a timeout:

```text
javaApplyMustRemainOwnedByThePreparingWalk ==> expected: <SHOWN> but was: <IDLE>
```

The test deliberately awaits a terminal result rather than awaiting SHOWN, so the mutant's wrong result
reaches that assertion. Source and both class trees restore byte-identically, and the restored test is green.

REQUESTED and CAUGHT names are the identical seven-name set, all with named assertion failures and restored-green runs:

- `issue84-java-apply-view-is-walk-owned`
- `issue84-walk-waits-for-light`
- `issue84-native-navigation-supersedes`
- `issue84-source-read-off-edt`
- `issue84-back-read-off-edt`
- `issue84-source-error-is-a-fact`
- `issue84-saved-source-disclosure`

Local JDK 21 verification (counts are total / failures / errors / skips):

| Check | Result |
|---|---|
| New test alone, real display | 1 / 0 / 0 / 0 |
| Sequential display suites: Issue84JavaWalk, WalkReview, JavaSourceSpotlight, WalkPlayback, WalkVerb, ConversationJourney, WalkArrowKeys | 44 / 0 / 0 / 0, seven reports |
| `mvn -o -q clean test` | 3,122 / 0 / 0 / 246; 412 source-mapped reports, no orphans |
| Fast-engine controls above | 7 requested = 7 caught, byte-identical restoration and restored-green tests; 54.8 seconds |
| Preflight; `tools/test_project_chart_review.py` | 42 frame suites, 581 anchors; five tests passed |
| Strict MkDocs; `git diff --check`; public-data sweep | Passed; sweep printed nothing |

Both display JVM flags were set and display/control runs held the shared display lock, serially. The 246 headless
skips are not passes; CI must execute the registered display suites with zero skips. Exact-head CI is recorded
in the PR response, separately from these local counts and the earlier implementation counts above.
This test-only change does not add a user-visible CHANGELOG claim.

Non-blocking dispositions from the same review:

- **F2:** the adapter's `permitsSourceApplication` query is defensive. Ending the walk already clears the
  spotlight and advances `javaSpotlightTicket`; the existing stale-completion witness does not independently
  prove this extra check. The #97 row is corrected accordingly; no behaviour changed.
- **F3:** additional D-L3 functional-interface surface restrictions remain a follow-up; the new test makes no
  stronger D-L3 claim.
- **F5–F7:** machine-origin preselection, saved-line revision binding/caption truncation, and explicit offline-root
  availability in context remain non-blocking follow-ups, not changes bundled into this test-only response.

The independent conditional verdict is not self-promoted to approval. Exact-head build, zero-skip ui-frame,
mutation self-test, all four shards and the collector must complete before the PR response claims green.

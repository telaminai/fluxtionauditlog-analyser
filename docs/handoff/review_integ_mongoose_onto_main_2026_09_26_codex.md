# Integration review and authorised fixes — 2026-09-26

**Verdict: 5776e750 was not mergeable unchanged. Three required items are fixed in the accompanying change; the author must review the fixes and CI before merging. Nothing was merged or released by this session.**

The review was pinned to 5776e750 against requested main 93c868a1. It inspected the combination, not the ten accepted rounds of MA behaviour. During the review the author advanced the integration to 1df151df, incorporating the 1.21.0 release. The fix is also supplied on `fix/mongoose-integration-follow-evidence`, from that newer tip, as **764b4522**. Its product code, tests and mutation cases are byte-identical to the initially tested fix **9283c402**. Only the changelog application differs: the released sections from 1df151df are retained and the new fix is under Unreleased. The original review branch remains `review/integ-mongoose-onto-main-2026-09-26`.

Evidence is in [the review evidence directory](evidence/integ-mongoose-review-2026-09-26/). RAN means executed here; READ means source inspection. I implemented the corrections after the owner authorised fixes, so my acceptance of those corrections is not another independent review.

## Required findings, now fixed

Code locations below are relative to `src/main/java/telamin/fluxtion/audit/analyser/analyser/`; test locations use the matching tree under `src/test/java/`.

1. **RAN — failed decoding retained a successful identity and never notified the session.** `parse/HeapLogStore.java:227`, `ui/MainFrame.java:4478` (original subject). Open a terminated record, poll once to establish UNCHANGED, replace one byte with `FF` without changing the file length, then poll. The decoder throws; completeness becomes UNKNOWN, but `followIdentity()` still says UNCHANGED / “every byte compared, identical”, the opening digest remains, and the session still says VERIFIED. The diagnostic also incorrectly calls the changed bytes an append. This is the intersection of main's replacement checks and MA's decoder failure path: the main parent class produces UNVERIFIED for this input; the MA parent does not provide the new identity contract.

   **Fix:** the decode failure clears the digest and sets UNVERIFIED before throwing. The failing frame poll publishes that observation through the existing session fact path. The diagnostic identifies the displayed records as the retained snapshot without asserting where the bad bytes occurred. The generated processor already dispatches `LogIdentityObserved`; it was inspected, not regenerated.

   **Regression:** `parse/HeapLogStoreFollowIdentityTest.java:18`, `unreadableSameLengthReplacementRetiresTheVerifiedIdentity`, and `ui/StatusExplanationSurvivesFrameTest.java:141`, `aFailedFollowPollPublishesIdentityAndDamageOnce`. Both failed on the original implementation at the identity assertions; the heap test also caught the retained digest and false append wording. Controls `integration-failed-identity` and `integration-failed-session` reproduce the defects as named failures, then restore green.

2. **RAN — no existing check protected the combined coverage scan bound.** `topology/CoverageService.java:80`. The implementation passes the captured row bound correctly, but replacing `PerNodeLevelChanges.of(store, rows)` with `of(store)` leaves the selected **71 tests green**. Concrete risk: capture two rows, then let a third row restore the quiet node. The annotation can consume a restoration outside the population the reply names. This is a required regression gap under the requested parent-resolution criterion, not a claim that the unmutated implementation currently reads too far.

   **Fix:** `topology/CoveragePerNodeLevelTest.java:28`, `annotationsRespectTheCapturedRowsAndTerminalBoundary`, drives the production service over three stored rows with a two-row bound. Its store sentinel rejects any read of row index 2; the test also checks the terminal marker at bound and excludes the later marker. Control `integration-coverage-bound` fails with “annotation must not read beyond the captured bound: 2”. No coverage algorithm change was necessary.

3. **RAN — the merged frame refresh could take either parent's policy without the selected display tests noticing.** `ui/MainFrame.java:4542` on the original subject. Restoring the MA parent's full helper, or adapting main's former inline policy to the helper, each left **43 tests green**. The two wrong results are distinct: append a second header to a pending frame with no indexed growth and lose the framing finding; or encounter a decode error while completeness already says UNKNOWN and fail to publish the new source damage.

   **Fix:** `ui/StatusExplanationSurvivesFrameTest.java:177`, `pendingGrowthRefreshesFindingsWithoutAddingAnyRows`, and the failure test from item 1 drive real `MainFrame.pollFollow()` calls on the EDT. They assert producer findings and the status tooltip, without waiting for a timer or using an assistant verb as a substitute for that poll. The failure test also asserts that a repeated failure carrying identical damage reuses the existing findings. Controls `integration-pending-refresh`, `integration-failure-refresh` and `integration-repeat-failure-skip` each fail at their respective assertions and restore green. The correct merged refresh policy is retained.

## Per-resolution disposition

| Resolution | Disposition and evidence |
| --- | --- |
| Heap append | **Combined, with item 1 corrected. READ + RAN.** Identity classification precedes indexing. Pending-byte growth can reframe even when no complete character arrives. Replacing the whole method with the MA parent causes 3 assertion failures and 3 errors in 59 tests; `sameLengthRewrite` and `middleRewritePlusAppend` are genuine assertion failures. The main parent causes 4 assertion failures, including the held-back-character and failed-read recovery assertions. Errors are not counted as witnesses. Each restore returned 59/0/0/0. |
| Producer diagnostics | **Combined. READ + RAN.** No index does not establish EMPTY_LOG. Empty index with pending text reaches the pending scan, including NOT ASSESSED. The MA early return causes 3 failures, including `aLiveCollapseIsSuspectedFromThePendingFrame` and `anOversizedPendingFrameIsNotAssessed`. Restoring main's absence of an EMPTY finding (removing the added EMPTY branch) causes 5 failures and 1 error; the warning assertion is a genuine failure. Both restores return 59/0/0/0. FramingScan replaces the substring check. |
| Coverage | **Combined; item 2 adds the missing protection. READ + RAN.** The scan and annotations share the clamped bound. `PerNodeLevelChanges.java:191` keeps `b <= scope`: a boundary at the exclusive end follows the last included row, so it closes that interval without reading the next row. The sentinel probe reads at most index 1 for bound 2. The service is the only production caller; other direct unbounded calls found were tests. Removing annotations as in main gives 14 failures and 1 error in the selected 71 tests; restore is green. |
| MainFrame refresh | **Combined; item 3 adds the missing protection. READ + RAN.** Failure refresh, pending growth and the repeated-identical-damage skip coexist. The no-log note and `loadInFlight` guard at `publishPairing` match main. The original full gate caught `no-log-note-after-failed-open`, `no-log-design-open` and `no-log-pairing-note`. Both parent-policy controls originally survived; the new targeted controls close that gap. |
| Changelog | **Combined. READ + RAN.** Every nonblank Unreleased content line from a5bd6e0c, aa268458, da5377d9 and 39001c35 occurs at 5776e750, with no duplicate change paragraph. `check_changelog.py` checks the actual committed texts; either parent-only replacement fails its inclusion assertion, then restores byte-identically and passes. This is an independent review script, not an existing product test. |

`git show --remerge-diff` was inspected for the merges. The 31326a50 resolution retains source-freshness Unreleased entries, the MA status and the recurring intake note. 5776e750 has no remerge diff. The review's MainFrame comparison against 39001c35 isolates the Follow additions; it does not replace the new menus, context listing, moved-item hints or no-log pairing note.

**BOM and warning severity — READ + RAN baseline:** FramingScan uses `AuditText.isBom`; `ByteOrderMarkSitesTest` scans the main Java source tree, which includes FramingScan. The shared character predicate does not mean every caller has identical offset/whitespace policy. The original gate's `review-r6-leading-bom` control was caught. `ProducerDiagnostics.isWarning()` already scans beyond notes on main, so MA-0.4's true assertion preserves main's severity rule rather than inventing a new verdict.

## Checks actually run

All Java runs used JDK 21 on macOS. Totals include skipped cases; skips are not passes.

| Check | Result |
| --- | --- |
| Original subject: `mvn -q clean test` | **2389 / 0 failures / 0 errors / 106 skips**, 321 reports mapped to source; zero orphans. |
| Original subject: registered CI display list and flags, isolated | **106 / 1 failure / 0 errors / 0 skips**, 21 suites. `NamedGraphAndMenuSpotlightFrameTest#lightingASecondMenu_keepsWhatTheEchoSaid_byReplaceAndByAdd` lost the lit menu. Its one isolated class retry passed **7/0/0/0**. Both results are retained. |
| Original subject: `python3 tools/verify_project_chart_review.py --mode mutations --engine fast --output <scratch>/mut.json` | **155/155 caught, 636.5 seconds.** Each named failure and source/class restoration is retained in `full-gate.json`. |
| Eight parent-policy controls, `parent_controls.py` | Counts and assertions in `parent-headless-controls.json` and `parent-frame-controls.json`. Three controls survived, as items 2–3 report. Every source restore was checked by `cmp` and SHA-256 and followed by green tests. |
| Independent constructed-file and bounded-store probe | `probe-before.txt`, `probe-after.txt` and `parent-probe.json`; see findings and limitations below. |
| New tests before production fix | Heap plus coverage: **32 / 1 / 0 / 0**. Frame class: **5 / 1 / 0 / 0**. Named identity failures, not setup errors. |
| Fixed original tree 9283c402: `mvn -q clean test` | **2393 / 0 / 0 / 108**, 321 reports; zero orphans. |
| Final PR tree 764b4522: `mvn -q clean test` | **2393 / 0 / 0 / 108**, 321 reports; zero orphans. Product/tests/gate match 9283c402 byte-for-byte. |
| Six new controls, fast engine with six explicit `--case integration-…` selections | **6/6 caught, 33.6 seconds**, named `<failure>` for each; green baseline, byte-identical source/classes, restored green. `fix-controls.json` records the assertions. No second full mutation run is claimed. |
| Final PR tree registered display gate | **108 / 0 / 0 / 0**, 21 registered suites, through `python3 tools/verify_project_chart_review.py --mode display`. |
| `python3 tools/test_project_chart_review.py` | **5 passed**. |
| `mvn -q -Dtest=SpecLinksResolveTest,TrailingWhitespaceTest test` | **5 / 0 / 0 / 0** after writing the review; source-mapped reports only. |
| `mkdocs build --strict`; `git diff --check`; public-data sweeps | **All clean**, including the final fix-PR tree. |

Display commands use `-Djava.awt.headless=false` directly as well as the CI argLine, with one display process at a time. `--mode display` checks both CI lists against discovered frame suites. No FrameTest class was added, so the existing 21-suite registration remains correct. The new controls expand the future full gate from 155 to 161 cases; only the six additions were rerun after the fix.

Operational mistakes are recorded rather than counted as evidence: I initially overlapped a display run with the full-gate baseline, stopped the gate before mutations, discarded the overlapped display result and reran separately. A sandboxed fixed-tree full run had 29 socket-permission errors; the unrestricted rerun above passed. The first probe omitted two required store methods, and the first new frame-test draft used the wrong snapshot getter; neither compile failure is a regression witness. The first parent probe mixed incompatible diagnostics APIs; the retained successful parent comparison substitutes only HeapLogStore and RecordFramer into the otherwise integrated classpath, not an entire parent build. The initial prediction of 107 display tests was wrong: the original suite contains 106.

## Optional items and out-of-scope notes

- **RAN, pre-existing on main:** `HeapLogStore.pendingFrameText()` uses Unicode `strip()` while the framer recognises ASCII whitespace. The constructed pending input with an em-space-prefixed separator-like line yields no diagnostic although scanning its full text finds another header. The main parent reproduces it too. My prediction that this was introduced by integration was wrong; it is not included in these fixes.
- The isolated menu-lighting failure and passing retry remain a display stability follow-up. This review did not attribute that existing test's intermittent failure to the merge or change its waits.
- MA-0.4's old test method name ends `IsWarningDoesNot` although its corrected assertion is true. Renaming it would improve clarity; no functional change is required.

## Owner decisions and unverified work

No new policy decision is requested. The accepted MA owner decisions and standalone open items remain as they were. The author owns approval of these fixes and the final integration merge. A computation of the merge tree showed only a changelog conflict against 1df151df; the supplied fix branch resolves that by preserving the newer release text. Neither the integration branch nor main was modified.

No key, provider, LLM client session, participant project, release or deployment was used. No standalone MA re-audit or earlier-round witness rerun was performed. I did not run CI myself or prove the Linux/Xvfb result; the author should require it on the final integration tree. The public evidence summaries omit Maven command output and redact temporary paths; named tests, assertion messages, counts and restoration results are retained.

# Review — M74 P2 (final), `14765831..1832fe44`

**Reviewer:** independent. **Date:** 2026-10-17. **Branch:** `feature/claude-code-p3` (the range is P2a + P2b).
**Filed by** the fixing session; the finding list is the reviewer's, as handed over. Dispositions are below each list,
filled in as the fixes landed (spec r14; `docs/specs/spec-claude-code-provider.md`).

## Findings

- **S1** SessionBoundary/OperationGate: a held project switch loses its opId when an `OpenLogRequested` lands during the
  offer, so the released switch's `ProfileLoaded` is refused as stale. Stop kills Claude Code but the project never
  switches, and holding also supersedes an in-flight log open even on Cancel. Make the gate skip held requests (the
  boundary decides the hold first), or re-issue the released switch with a fresh opId. Add a ★ test: hold, then
  `OpenLogRequested`, then Keep → the switch applies.
- **S2** `AssistantSurface.appendDraft` clears earlier draft marks, so only the newest binding is checked at Send; an
  evicted binding (>64) counts as "not stale", and in-app as "not composed" (re-attaching record text, F4). Keep all
  live highlights and bindings, refuse if ANY is stale, and treat an unknown binding as stale. Add tests.
- **S3** MainFrame ~11655: apply of a proposal item uses `normalize()` with no real-path re-check: a symlink swapped in
  between preview and apply escapes the folder. Recompute `toRealPath` against the set-up folder's real path at apply,
  refuse OUTSIDE, and store the real path. Add a test.
- **S4** `ProposalPreview.check` has no tests: add a temp-dir test (symlink out, `../x`, absolute outside,
  inside-but-missing, wrong type) with a witness (`real(at)` → `at`). `freeTextIsNotAProposal` posts no text: make it
  exercise propose-shaped JSON in answer text per spec §10.
- **S5** `PromptPack.java:147`: an example line skips all content checks, including on the label. Exempt only the text
  after `example:`, and check the label like any entry. Add a test with the reviewer's probe line.
- **S6** `ExampleSource.thirdParty`: fork-network commits are served under the parent's URL, so "Telamin" is not
  provable. Change the label to "a Telamin repository address (the code may come from a fork)", or drop the first-party
  label. Prefer saying what is proven. Note it in spec OD-74.30. Add a test.
- **S7** The example download can stick: the body read has no timeout (`ExampleArchiveClient:52`), and an exception in
  the confirm performer (MainFrame ~11803) posts nothing, leaving the node in CONFIRMING. Add a body-read deadline or
  watchdog, catch in the performer and post a refusal, and give the node a cancel/timeout. Add tests.
- **Nits**
  - **N-a** onboarding final check: fit = pairing label, and it reads the old pairing before the async loads settle;
    compute it after the loads, or label it pending;
  - **N-b** `ConfigStore.BINDING_NOTES` static global composed in the `ApplyProfileEffect` performer: move it into the
    node (rule 9);
  - **N-c** `dropPromptPack` reads on the EDT with no regular-file check: check the file type off the EDT, and report
    IOExceptions honestly;
  - **N-d** `ProposalPreview.fqnOf` reads whole files: bound the read;
  - **N-e** `mayRedirectTo` should check the same owner and repo; `SEG` must refuse `.` and `..`;
  - **N-f** fix the `AskCatalogueTest` javadoc and the `AskPointers` overclaim;
  - **N-g** conversation v2: an edited turn should clear `truncated`, a non-boolean `truncated` should be refused, and
    `source.model`/`cliVersion` should be bounded.

## Dispositions

| Finding | Disposition | Commit | Test (witness) |
|---|---|---|---|
| S1 | **fixed** — the boundary hands a held request back to the gate (`held`), restoring what was current; Stop/Keep make it the newest (`released`) | `60ec42b7` | ★ `LocalAgentSwitchTest#anOpenDuringTheOfferDoesNotStrandTheSwitch` (witness: drop `gate.released` → nothing applied); ★ `#aCancelledHoldLeavesTheOpenInFlightCurrent` (drop `gate.held` → the open is superseded) |
| S2 | **fixed** — every draft's highlight and binding kept (and a Swing quirk found: a highlight at the document's end grew with each append, now pinned); any stale or unknown binding refuses, in both nodes | `b3bfa6d5` | ★ `AskDraftSessionTest#anyStaleDraftRefuses`, `#anEvictedBindingIsRefused`, `#inAppUnknownBindingIsRefused`, `#inAppAnyStaleDraftRefuses`; ★ `AssistantSurfaceBindingsTest#everyDraftStaysBound` (witness: clear earlier marks → only the newest bound) |
| S3 | **fixed** — apply goes through `ProposalPreview.atApply` (real path against the set-up folder's real path; OUTSIDE refused; the real path stored) | `8650a030` | ★ `ProposalPreviewTest#applyReChecksTheRealPath` (witness: `real(at)` → `at` → the swapped link is applied); `#theFramesApplyUsesAtApply` (static) |
| S4 | **fixed** — `ProposalPreview.check` tested (link out, `../x`, absolute outside, inside-but-missing, wrong type); the free-text rule tested through the real parser and adapter with propose-shaped JSON in answer text | `8650a030` | ★ `ProposalPreviewTest#theAnalysersOwnVerdicts` (witness: `real(at)` → `at` → the link out reads MARKDOWN); ★ `LocalAgentAdapterReplayTest#freeTextIsNotAProposal` (witness: a text-scanning reader → `ProposalReceived` appears) |
| S5 | **fixed** — only the text after `example:` is exempt; the label is checked like any entry's | `8268b6f6` | ★ `PromptPackExampleTest#anExampleLabelIsChecked` (witness: the old `continue` → the probe pack parses) |
| S6 | **fixed** — "a Telamin repository address (the code may come from a fork)"; OD-74.30 notes it (r14) | `8268b6f6`, spec `61847e3f` | ★ `ExampleDownloadDialogWordsTest#publisherLine` (witness: the old "— Telamin"); `ExampleSourceTest#thirdPartyLabel` |
| S7 | **fixed** — a 5 min body watchdog; the confirm performer answers `CONFIRM_FAILED` when it throws; `exampleDownload` starts a deadline per fetch/unpack and discards a late fetch's bytes | `b7864ad9` | ★ `ExampleArchiveDeadlineTest#aStalledBodyTimesOut` (witness: watchdog cancelled → preemptive timeout); ★ `ExampleDownloadTest#aStalledFetchFailsAtItsDeadline`, `#aLateDeadlineIsIgnored`, `#aFailedConfirmationIsRefused`; `ExampleDownloadDialogWordsTest#theConfirmPerformerAlwaysAnswers` (static) |
| N-a | **fixed** — the check answers PENDING while a load is in flight; the node asks again after a log/graph result (bounded). The graph–log fit IS the pairing comparison here (`GraphPairing`), now said in the code | `22c60bc6` | ★ `OnboardingProposalTest#theCheckWaitsForTheLoads` (witness: no recheck → one check, PENDING kept) |
| N-b | **fixed** — the notes ride with the config that was read (`AppConfig.walkBindingNotes`, through the import plan); `ProfileApplied.droppedBindings`; `sessionBoundary` says them | `22c60bc6` | ★ `WalkBindingNotesTest`, ★ `DroppedBindingsStatusTest` |
| N-c | **fixed** — read off the EDT, ordinary files only, `NOT_A_FILE` / `NOT_READABLE` causes | `3fba0124` | ★ `PromptPackReadTest#onlyAnOrdinaryFile` (witness: no regular-file check → a folder reads NOT_READABLE; a FIFO would block); frame tests `PromptPackFrameTest`, `ExampleEntryFrameTest` wait for the read |
| N-d | **fixed** — at most 64 KB read | `8650a030` | `ProposalPreviewTest#fqnOfIsBounded` |
| N-e | **fixed** — same owner and repository on redirect; `.`/`..` refused | `8268b6f6` | ★ `ExampleSourceTest#redirectsKeepTheRepository` |
| N-f | **fixed** (wording) — `AskPointers` and the `AskCatalogueTest` javadoc no longer claim a quoted name cannot be prose; the real bound is pinned | `3fba0124` | `AskCatalogueTest#aQuotedNameIsStillWords` (pins the claim; a wording fix has no behaviour witness) |
| N-g | **fixed** — an edit clears `truncated`; a non-boolean `truncated` refused; `source.model`/`cliVersion` bounded on every path in (`problem()`) | `3fba0124` (+ mutation anchor `ceb1d8d5`) | ★ `ConversationV2Test#anEditedTurnIsNotTruncated`, `#v2FieldsAreChecked` |

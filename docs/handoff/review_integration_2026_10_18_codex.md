# Integration review — 2026-10-18 — Codex

## Verdict

**REQUEST CHANGES at `ddccd5c5`.** Six actionable findings were reproduced and fixed in separate commits, followed by one F3 boundary refinement (history was not rewritten). The correction branch is **CONDITIONAL**, not unconditional merge approval: the native display gate, limitations below, and independent review of my own corrections still matter.

Starting SHA, verified in the owner's isolated clone: `ddccd5c5df8c4f95aeff68040c2f64e298df870e`, `test/usability-2026-10-18b`. Reviewed the merged stack from `a875fcba`, including `3cf5beb8` (`fix/starter-followups`) and its merge at the tip. No other session's checkout was used or changed.

Production-fix tip: `f8bc55a200e9beb86ddec936a988cf7211ab6bba`. Whole fix range: **`ddccd5c5..f8bc55a2`**, on `review-fix/integration-2026-10-18b`. This report's documentation commit follows that range. All commits use the clone's configured identity; author and committer addresses were checked individually.

VERIFIED means I executed the check or observed the output. INFERRED means source/contract inspection, not an executed end-to-end scenario. Numbers below are **total / failures / errors / skips**, never passes disguised as totals.

## Findings, ranked

Line numbers in this section identify the original `ddccd5c5` source. Paths below are under `src/main/java/telamin/fluxtion/audit/analyser/analyser/` unless stated otherwise.

### F4 — P2 — the sample alias can widen the permission boundary — FIXED

**Owner:** `fix/owner-test-small` (FIX-6), introducing `62e1971a`. **File:** `assistant/localagent/LocalAgentFolders.java:55`.

A pre-existing sample-directory symlink to the synthetic home caused any project below that home to count as the active sample. A Full hosted session working in scratch then classified another project's log as PRE_ALLOWED. This requires a filesystem alias; it is not a demonstrated remote attack, and no participant file or credential was used.

**VERIFIED:** `InProjectOpenTest#aSymlinkedSampleDoesNotWidenTheApprovalBoundary` failed at the wrong permission result: expected CHANGES, actual PRE_ALLOWED. The companion parent-alias control failed too; genuine samples and an aliased home remain supported. Fix: canonicalise home, then require the sample to remain at its fixed anchor. [Evidence](evidence/integration-review-2026-10-18/f4-sample-anchor.md).

### F3 — P2 — starter continuation can take over the next project — FIXED after owner decision

**Owner:** #167, `feature/propose-starter`. **Files:** `session/node/StarterProject.java:382,429,436`; `ui/MainFrame.java:13465,13614`.

The pending graph offer opened its path in the adapter before reporting the answer. Switching to DEMO-B inside the answer callback, then answering Yes, installed the old project's graph in DEMO-B. Earlier pending download/discovery/review stages were not cancelled either; OPENING was also an ambient exemption from the running-agent switch confirmation.

**VERIFIED:** the complete proposal/install/open frame chain failed `a superseded starter offer must not open the old graph`, after asserting DEMO-B was active. A generated-processor replay failed `project switch must cancel starter continuation at stage 0` (DOWNLOADING instead of CANCELLED). Five pending stages, failed-switch controls, late completions and a loadable stale project request are now covered. A current Yes still opens exactly once.

The owner chose **cancel later opens and drafts; keep downloaded files**. The node now owns cancellation, correlates its own project operation, and decides whether a graph answer may open anything. A stop-watch effect and cancellation fact settle the adapter/proposal; the design-read guard is revocable. Failed switches and no-op re-opens do not change the project. Already placed drafts are not erased. Processor, resource source copy and GraphML were generated through Maven, never hand-edited. [Evidence, including the narrower original probe and my failed recovery-guard attempt](evidence/integration-review-2026-10-18/f3-starter-ownership.md).

### F1 — P2 — discovery failure leaves creation permanently busy — FIXED

**Owner:** #167. **File:** `session/node/StarterProject.java:321` (adapter: `MainFrame.runStarterDiscovery`).

Discovery reports `StarterInstallFailed`, but the handler accepted only DOWNLOADING, not DISCOVERING. The starter stayed busy and the proposal APPLYING. **VERIFIED:** `StarterProjectTest#discoveryFailureDoesNotStrandTheStarter` failed expecting FAILED but observing DISCOVERING. The fix settles discovery failure honestly, preserves installed-file disclosure, ignores late success and admits another creation. The test injects the adapter's exact failure fact, not a real disk-reader failure. [Evidence](evidence/integration-review-2026-10-18/f1-discovery.md).

### F2 — P2 — a loading acknowledgement is reported as a displayed design — FIXED

**Owner:** #167; the optional design-root row in `fix/starter-followups` exposes it. **File:** `ui/MainFrame.java:13508`.

Apply a starter, choose Open Yes but Design folder No. The file exists, but its root is not permitted. The EDT call returns only pending; the adapter immediately reported success and the node said the design was shown. **VERIFIED:** `StarterProposalFrameTest#aDeclinedDesignRootReportsTheActualReadOutcome` failed `a refused source-root read must not be recorded as opened`. The corrected adapter reports the blocking read's actual outcome from a background worker; the node publishes pending then settled text. The successful-design control still passes. [Evidence](evidence/integration-review-2026-10-18/f2-design-outcome.md).

### F5 — P2 — Full-mode consent promises command prompts it cannot guarantee — FIXED

**Owner:** FIX-6 and the M77 wizard surface. **File:** `session/AiSetupLines.java:273,279`.

The offer said “commands still ask you”. Full loads the person's permission rules; already-allowed commands need not produce a permission request. The upstream [permission contract](https://code.claude.com/docs/en/permissions) also allows automatic filesystem commands under acceptEdits. That runtime implication is **INFERRED** from code and the live contract, not a paid/live CLI experiment.

**VERIFIED rendering:** `BuildModeOfferPanelTest#theOfferRendersAndReports` inspected the actual banner and failed `the mode offer must not promise approval for every command`. The banner, wizard note and guide now disclose automatic commands subject to permission rules. No permission behaviour/default changed. [Evidence](evidence/integration-review-2026-10-18/f5-permission-wording.md).

### F6 — P2 — partial skills installation is described as no writes or unconditional success — FIXED

**Owner:** #167; relevant to `fix/starter-followups`' digest record. **Files:** `session/node/StarterProject.java:336`; `ui/StarterReviewDialog.java:159`.

A regular file blocking the Spring support directory makes the real writer fail after earlier skills were written. The node nevertheless said “No skills were added”; the review always said “its skills are written”. **VERIFIED:** `StarterProjectTest#partialSkillWriteDoesNotClaimNothingWasAdded` asserts actual files first, then fails at the zero-writes claim; the actual review panel fails its successful-skills assertion with an empty list. Both are maintained regressions. The node now presents incomplete installation and retained-file qualification, and the review no longer asserts unconditional success. Continue-to-discovery is unchanged. [Evidence](evidence/integration-review-2026-10-18/f6-partial-skills.md).

## Exact fix ranges and pre-commit verification

Each row is a separate commit; all full runs were `mvn -o test` on JDK 21. Display runs held the shared display lock and set both `java.awt.headless` and surefire `argLine` to false.

| Finding | Exact range | Full suite | Affected frame suites | Display result |
|---|---|---|---|---|
| F1 | `ddccd5c5..be4e1738` | 4186 / 0 / 0 / 326 | StarterProposalFrameTest, TemplateCatalogueFrameTest | 3 / 0 / 0 / 0 |
| F2 | `be4e1738..c526fcc1` | 4187 / 0 / 0 / 327 | StarterProposalFrameTest, TemplateCatalogueFrameTest, DesignSpotlightFrameTest | 8 / 0 / 0 / 0 |
| F4 | `c526fcc1..f15e9d4b` | 4189 / 0 / 0 / 327 | ExampleEntryFrameTest, LocalAgentPermissionFrameTest, ExternalRestWithHostedSessionFrameTest, ClaudeCodeTabFrameTest | 8 / 0 / 0 / 0 |
| F3 | `f15e9d4b..27667c78` | 4191 / 0 / 0 / 328 | StarterProposalFrameTest, TemplateCatalogueFrameTest, DesignSpotlightFrameTest, SessionRecoveryFrameTest, ClaudeCodeTabFrameTest | 20 / 0 / 0 / 0 |
| F5 | `27667c78..5eb46b5d` | 4191 / 0 / 0 / 328 | AiSetupWizardFrameTest, QuickSwitchFrameTest, ClaudeCodeTabFrameTest, UsabilityBudgetFrameTest | 11 / 0 / 0 / 0; background-exception caveat below |
| F6 | `5eb46b5d..7e826028` | 4192 / 0 / 0 / 328 | StarterProposalFrameTest, TemplateCatalogueFrameTest | 5 / 0 / 0 / 0 |
| F3 boundary refinement | `7e826028..f8bc55a2` | 4193 / 0 / 0 / 329 | StarterProposalFrameTest, TemplateCatalogueFrameTest, DesignSpotlightFrameTest, SessionRecoveryFrameTest, ClaudeCodeTabFrameTest | 21 / 0 / 0 / 0 |

Before-fix wrong-result failures: F1 **1/1/0/0**, F2 **1/1/0/0**, F4 **2/2/0/0**, F3 **two separate 1/1/0/0 runs**, F5 **1/1/0/0**, F6 **2/2/0/0**. These were assertion failures, not compiler crashes. Initial unsuccessful test construction and the recovery regression introduced during F3 are disclosed in their evidence files. Original evidence and failed attempts were preserved, not rewritten into green runs.

F3's final refinement adds another **1/1/0/0** before-fix frame witness: `aCancelledQueuedStarterOpenNeverReachesTheProjectBoundary`. The node's permission is now checked before a cancelled callback can submit a shared project request at all; otherwise unrelated request observers still saw it, even when SessionBoundary refused the open. The current full-chain positive control and all 21 affected display tests pass. This is enforcement of a node-owned revocation, not a second ownership rule in the adapter.

## Integration gates and observations

- **VERIFIED original baseline:** `ddccd5c5`, `mvn -o test`: **4185 / 0 / 0 / 326**. These are measured here, not the map's older 42c5bb78 numbers.
- **VERIFIED clean package at `7e826028`:** `mvn -o clean package`, **4192 / 0 / 0 / 328**, **587 source-mapped reports, no orphans**. The jar contains no regeneration-only graph-builder classes. All earlier raw output was preserved in ignored clone-local storage before cleaning. **Final production correction:** `mvn -o test`, **4193 / 0 / 0 / 329**; report/source mapping remains **587 / no orphans**. Artifact acceptance after rebuilding that correction is recorded below.
- **VERIFIED earlier complete display run:** all **68** registered classes, at `c526fcc1`: **325 / 0 / 0 / 3** (322 passed). Native focus skips: `PersonAtTheScreenFrameTest#escapeWithTheSearchHistoryPopupFocused_putsSeveralSpotlightsOut`, `WalkArrowKeysFrameTest#arrowKeysMove`, `AssistantNativeFrameTest#theDockedAssistantsPressesAreItsOwn`. This is not the required zero-skip Linux gate.
- **VERIFIED latest complete display run, `7e826028`:** **326 / 1 / 0 / 3**, all **68** reports present (322 passed). `UsabilityBudgetFrameTest#tasksStayInBudget` failed: T2's right-click **Ask: What happened here?** control was not found. Skips: PersonAtTheScreen's search-popup Escape case, WalkArrowKeys' arrow-key case, and StartWorkspace's native-file-drop case. No uncaught background exceptions appeared in this complete run. The later F3 refinement received full headless and 21 affected-frame checks, not another complete 68-class pass. This remains a failed/incomplete overall acceptance gate, not a waived green.
- **VERIFIED isolated diagnostic rerun on the final production correction:** UsabilityBudgetFrameTest **4 / 0 / 0 / 0**, with no background exception messages. It does not explain or erase the full-run T2 failure.
- **VERIFIED final artifact:** after the final full `mvn -o test` (**4193 / 0 / 0 / 329**, **587 reports, no orphans**), `mvn -o -DskipTests package` rebuilt the jar. Under the display lock and isolated DEMO homes: `verify-m46-agent-api.py` **16 PASS / 0 FAIL**, `verify-session-transitions.py` **22 PASS / 0 FAIL**, and `verify-session-restart.py` reported **PASS**. All three exited 0. The final jar still contains no regeneration-only graph-builder classes. These probes do not exercise a live model or real external IDE client.
- **VERIFIED:** control preflight **68 frame suites / 679 anchors**; harness unittest **5 passed**; strict MkDocs build; `git diff --check`.
- **VERIFIED:** customer/API/dispatch tests run in the full suite, including CustomerApiKeysTest, WorkModeKeepsTheApiTest, StarterProposeApiDeltaTest, GeneratedSourceIsPublishableTest and OneDispatchModelTest. Regeneration at F3 completed through the authorized Maven profile. No generated file was hand-edited; no credential was inspected or printed.
- **VERIFIED visual inspection:** isolated-home DEMO starter review and opened-design screenshots, including the final changed review text. Existing changed chart-delete and assistant-settings images were inspected; the latter still depicts the old card-era settings. Screenshot review is not a claim that every native journey or 1200×680 layout was manually exercised.
- **VERIFIED hygiene:** the documented tracked-file sweep prints nothing (only its two rule-stating files exempt). The integration and correction additions contain no host-home paths. Historical baseline documentation still contains host-absolute paths; a blanket assertion that the repository has none would be false. The prefix inventory covers **75** baseline docs/tools files, including generic examples and checks, not 75 proven privacy leaks. Historical evidence was not silently redacted.

## Map flags — explicit disposition

| Flag | Disposition and basis |
|---|---|
| 1 executable downloads | Accept the explicit owner-approved executable-bit policy; archive/containment tests pass. Consent precedes downloading; nothing runs merely from installing. |
| 2 in-project pre-allow | Accept the narrow Full-only real-path/key scope; shared classifier and neighbouring controls pass. Filesystem TOCTOU remains a disclosed residual, not eliminated here. |
| 3 stay-in-project note | Accept as an instruction, not a sandbox. Full trust/provider source read; not a containment guarantee. |
| 4 acceptEdits preselection/save | Accept the existing owner decision and mode-write tests; require truthful consent wording, supplied by F5. |
| 5 external access confirmation | Accept: ActionListener decides, shared door/confirmation adapters act, frame and API regressions pass. |
| 6 bridge headers | Accept as additive; customer pins/live-check tests pass. |
| 7 context.hidden | Accept as additive; customer pins and records/filter tests pass. |
| 8 search fact references | Accept: source uses references rather than raw search words; fact-field guards pass. |
| 9 collapsed event types | Accept: fresh default differs; saved choice wins, pinned by display tests. |
| 10 quiet-panel departures | Accept the recorded vocabulary/Details/short-title/restart choices within the scoped seam review; no claim of completing M78 beyond D1/D2. |
| 11 diagram access door | Accept: quick-switch/client wiring and confirmation tests pass; no silent access-enable path found in the reviewed entrances. |
| 12 profile-card replacement | Accept the shared profile state/quick switch; existing screenshots need refresh. |
| 13 once-per-machine first run | Accept the saved-preference/node flow in source and existing tests; no live account trial. |
| 14 capture home guard | Accept isolated-home DEMO capture; new inspected images have no participant data. This does not certify all historic screenshots. |
| 15 hosted starter kind | Accept only hosted; schema/dispatcher separation and API-delta tests pass. No new external propose kind. |
| 16 context.starters | Accept additive snapshot rendering; no catalogue fetch from context. |
| 17 docs WebFetch | Accept the deliberately fixed hostname exception, as tested. CLI's allow rule is hostname-based; the analyser's own classifier additionally checks HTTPS. No live CLI transport/redirect proof was run. |
| 18 sample pre-allow | Reject the original alias-widened implementation; accept F4's anchored correction with the genuine-sample controls. |
| 19 identity/posture note | Accept the posture-ref change and resulting new-chat boundary; provider tests pass. No promise that a model obeys every note. |
| 20 skills/pins/licence | Accept successful digest verification/vendoring, with F6's partial-write disclosure. Publisher authority is recorded; the upstream licence notice remains pending. This is not a legal opinion. |
| 21 design row | The map is superseded by 3cf5beb8: Yes is now the explicit owner's default, gated by Open Yes and Apply. The real review regression pins this; F2 pins a person's No. |
| 22 regeneration marker | Superseded by 3cf5beb8: exact marker substitution occurs only after all pins pass, using fixed allowlisted first-run commands. Source and written digests are recorded on successful installation; absent commands retain the marker. Real starter/file tests pass. |

## Per-PR / branch verdicts

These are **integration-seam verdicts**, not invented independent approvals of every line on each original branch.

| PR/branch | Verdict |
|---|---|
| #167 / feature/propose-starter | REQUEST CHANGES at original tip: F1–F3/F6. Corrected by the ranges above; correction acceptance remains conditional on this report's gates. |
| FIX-6 / fix/owner-test-small | REQUEST CHANGES at original tip: F4/F5; corrections supplied. |
| fix/starter-followups (3cf5beb8) | ACCEPT the pin-before-substitution/double-digest/design-default changes, subject to F2/F6 and the disclosed non-transactional write contract. |
| #157 M77-1 | ACCEPT at the reviewed seams: probes/state and external API checks; no live auth claim. |
| #159 M77-2 | ACCEPT at the reviewed seams with corrected F5 consent wording. |
| #161 M77-5 | ACCEPT at the reviewed seams: diagram navigation/confirmation. |
| #162 M77-3 | ACCEPT at the reviewed seams: external confirmation, watch and additive headers. |
| #165 M77-4 | ACCEPT at the reviewed seams: shared mode state and quick switch. |
| #163 M78 D1 | ACCEPT at the reviewed seams; documented departures retained. |
| #166 M78 D2 | ACCEPT at the reviewed seams; no separate mode owner found. |
| #152 docs restructure | ACCEPT structurally after strict build and relevant safety-text corrections; not a full copy-edit or screenshot refresh. |
| #160 scorecard | CONDITIONAL evidence: deterministic task measurements are not novice/LLM outcomes; see background exceptions and absent starter task below. |
| #158 M78 spec, #164 M79 spec | NOT independently re-approved as complete specs; relevant ownership/safety/standing-decision sections read. Future M79 behaviour was not treated as missing implementation. |
| #153 M75, #154 M76, #155 onboarding protocol | NOT independently re-approved; documentation stack/mapping checked, no implementation acceptance inferred. |
| #156 umbrella; inherited #151/#150/#147/#145/#146 | CONDITIONAL as the combined stack, not separate re-reviews of the inherited releases/layers. |
| no-log-offer-cancel, records-filter-visibility, event-types-default-collapsed, authoring-walk-defects, merged-nodelog-entries, wizard-scorecard-regressions | No additional blocking seam finding beyond the disclosures above; respective regressions and full suite executed. The upstream C34 producer fix was not re-audited. |

## Remaining limits / owner follow-ups

1. **Zero-skip display acceptance is still a gate, not waived by a Mac run.** The workflow triggers on main/PR-to-main or manual dispatch, not an ordinary push to this review branch. No PR was opened and no workflow dispatched without a request; exact-head CI must not be inferred from an unrelated green run.
2. One F5 grouped display run returned **11/0/0/0** but logged **seven background SessionDriver ProtocolViolation messages** (same EDT name, different thread identity), during the usability sequence. This is an observed harness/runtime exception, not a proven regression caused by the copy change. It was absent from both complete all-frame outputs. Root cause and a named failing test are not established; do not turn that passing JUnit count into a claim of a clean stderr run. The complete-run T2 assertion failure is also retained; its isolated diagnostic rerun is recorded below, not substituted for it.
3. Vendoring remains non-transactional; an existing manifest is never overwritten. Atomic repair/manifest-update semantics would require a separate design decision, not a silent change to the never-overwrite contract. F6 fixes false disclosure, not transactional guarantees.
4. The historic host-path inventory and stale card-era screenshots need an owner-scoped cleanup/refresh. No historical evidence was deleted. The publisher's outstanding notice (UP-PG-10) remains open.
5. The scorecard has no starter task and measures scripted actions, not an uninstructed person's usability or a model's success. The real model → hosted proposal → real download → build → restart chain was not run; all authoring-frame agents/downloads here were DEMO stubs. No paid provider, participant project, merge, release, deployment or force-push was used.
6. Not verified: Windows behaviour; every 1200×680 interaction; live external IDE client registration/auth; live Full CLI rules/hooks/network connectors; adversarial filesystem TOCTOU; every saved-source/first-build timing interleaving; mutation shards/loop bench; independent review of my own fixes. Preflight is not mutation execution. Regeneration success is not independent semantic approval.

The cancellation policy is **resolved**, not an unanswered owner question. This correction branch is the reviewer's implementation response; another reviewer should judge its delta before treating the integration as approved.

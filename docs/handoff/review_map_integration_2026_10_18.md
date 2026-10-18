# Review map — the integration of 2026-10-18

The map for the integration review: every branch and PR going in, what each claims, and where its evidence is. Read
[REVIEWER-ORIENTATION.md](REVIEWER-ORIENTATION.md) §6 first for how to review it. The tracker lines are in
[tracker.md](../specs/tracker.md) ▸ *M74 stack — the 2026-10-18 integration*, M77, M78 and M79. Tips were checked
against `origin` on 2026-10-18. A branch may have moved since; review the tip the integration merged, recorded in the
last section.

PR links: `#N` means `https://github.com/telaminai/fluxtionauditlog-analyser/pull/N`.

## 1. Branches and PRs

### Base

| Branch / PR | Tip | Base | Spec | Claims | Evidence |
|---|---|---|---|---|---|
| `feature/claude-code-p3` — [#151](https://github.com/telaminai/fluxtionauditlog-analyser/pull/151) (layer), umbrella draft [#156](https://github.com/telaminai/fluxtionauditlog-analyser/pull/156) | `a875fcba` | `feature/claude-code-p2` (#150) → #147 → #145 → #146 → `main` | [spec-claude-code-provider.md](../specs/spec-claude-code-provider.md) r15 | M74 P3 + UX unification + review fixes; `bc015ea2` plus two merged fixes, below | P3 probe and smoke reports in `docs/specs/evidence/m74-p3*`; review dispositions `review_m74_final_*_2026-10-17.md`, `review_m74_usability_2026-10-17.md` |
| ↳ `fix/first-run-polish` (merged into P3) | `dbfe6c67` | `bc015ea2` | robot re-walk of the newcomer journey, 2026-10-18 (points #1, #3, #4, #6) | the spotlight's way back to the answer (`assistantLoop` decides `away`); the `startPageNoLogHint` node; one no-key line; nothing clipped (`WrapLine`, `FitLine`, `FitButton`) | `AnswerAwayTest`, `StartPageNoLogHintTest`, `ComposerHintFollowsItsAssistantTest`, `FirstRunPolishFrameTest`, `FirstRunLayoutFrameTest` |
| ↳ `fix/event-types-min-rows` (merged into P3) | `8648b39f` | `9df80c0b` | the checklist floor follow-up | the Records table and Event types each keep three rows; Record detail gives way first | `EventTypesMinRowsFrameTest` |
| `docs/site-restructure` — [#152](https://github.com/telaminai/fluxtionauditlog-analyser/pull/152) | `b1c22704` | P3 | [docs audit](docs-audit-2026-10-17.md) §5 | the task-first docs site; **reconcile at integration** with every branch's `docs/site/` edits | `mkdocs build --strict`; scorecard baseline `b1c22704` |

### Fix branches (no PR)

| Branch | Tip | Base | Spec | Claims | Evidence |
|---|---|---|---|---|---|
| `fix/no-log-offer-cancel` | `da86da42` | `a875fcba` | — (follow-up to point #3) | a cancelled chooser returns the no-log offer (`StartPageNoLogHint.onFlowDone`); regenerated processor | `StartPageNoLogHintTest`, `FirstRunPolishFrameTest` |
| `fix/records-filter-visibility` | `87551460` | `8648b39f` | usability review of the Event types list (B1, B2, M1, M2, m6) + robot walk of P3 | a `recordsView` node decides when the view is measured; `context` gains `hidden` (additive); the assistant's basis names hidden types; search text in facts by `FactRefs` reference; three-row floor at 1200×680; the search row wraps; regenerated processor | `RecordsViewTest`, `RecordsViewWordsTest`, `RecordsFilterVisibilityFrameTest`, `AssistantLoopTest`, `OneDispatchModelTest`, `FactFieldsTest`; scorecard row SC T4 |
| `fix/event-types-default-collapsed` | `eb52bb45` | `87551460` | — | the Event types list starts collapsed on a fresh install (`AppConfig` default); saved choices win | `EventTypesStartCollapsedFrameTest`; expected to fix scorecard row SC T2 |
| `fix/authoring-walk-defects` | `0161dab3` | `c0b1b3bc` (M77-2) | M74 spec OD-74.16 and the examples rule (amended); `upstream-asks.md` UP-PG-09 | seven defects from the live authoring walk (no-project Full starts in the empty scratch folder; the draft held through folder trust; *Start building* opens Set up AI with Full recommended; `mvnw`/`*.sh` keep the executable bit; milestone ids out of the template dialog; GraphML found in `src/main/resources`; real-path project compare); plus a runnable-files line, Build preselects Full acceptEdits, the wizard's mode is saved (bug), `STAY_IN_PROJECT_NOTE`, the narrow in-project `open` pre-allow | `AiSetupTest`, `ExampleDownloadTest`, `ExampleArchiveContainmentTest`, `InProjectOpenTest`, `HostedGrantServerTest`, `GraphmlDiscoveryTest`, `ProjectAutoDetectTest`, `LocalAgentFullReplayTest`, `FaqSecurityContractTest`, `UserVisibleJargonTest`; the live walk (7.4 min, 14 approvals) |
| `fix/merged-nodelog-entries` | `e46c5836` | `a875fcba` | format spec §3/§8; `upstream-asks.md` UP-FLX-56–58 | test and docs only: how a merged duplicate-key `nodeLogs` entry is read (conformance C34); the format spec's wording; UP-FLX-56, 57 and 58, filed as [telaminai/fluxtion#46](https://github.com/telaminai/fluxtion/issues/46) | `FormatConformanceTest` (C34), `src/test/resources/conformance/c34-merged-entry.yaml` |

### Feature PRs (stacked)

| PR | Branch | Tip | Base | Spec (M77 = `spec-ai-setup-wizard.md`) | Claims | Evidence |
|---|---|---|---|---|---|---|
| [#157](https://github.com/telaminai/fluxtionauditlog-analyser/pull/157) | `feature/m77-1-ai-setup-node` | `94c8660b` | P3 | M77 §8, §9, §18 | the `aiSetup` node and the two read-only Claude checks; the auth parse drops email and organisation; the generated dispatch order checked | `AiSetupTest`, `AiSetupKeysTest`, `ClaudeProbeParseTest`, `StartupModeTest`, `OneDispatchModelTest` |
| [#159](https://github.com/telaminai/fluxtionauditlog-analyser/pull/159) | `feature/m77-2-wizard-inside` | `c0b1b3bc` | #157 | M77 §5, §6, §18 | the wizard's inside path; T19 (the 1200×800 walkthrough) in 6 acts; the empty `selectedOptionExtra` slot | `AiSetupWizardFrameTest`, `AiSetupWordingTest`, `AssistantFirstRunFrameTest`, `StartPageCarryTest` |
| [#161](https://github.com/telaminai/fluxtionauditlog-analyser/pull/161) | `feature/m77-5-option-diagram` | `d3a5c8ff` | #159 | M77 §6.1a (OD-77.14–.21) | `FlowDiagram`: where data goes, Full in amber, the palette; no node, fact or effect | `FlowDiagramTest`, `OptionDiagramFrameTest` (T25–T31), `FaqSecurityContractTest` |
| [#162](https://github.com/telaminai/fluxtionauditlog-analyser/pull/162) | `feature/m77-3-external-path` | `5da42f16` | #159 | M77 §6 (Screen 2b), §13, §18 | Screen 2b; one owner of external access (`ExternalAccessRequested` through `actionListener`); confirmations at every door; registration; the live check; additive `X-Analyser-Client` / `X-Analyser-Probe` headers; regenerated processor | `AiSetupExternalTest`, `AiSetupClientFrameTest`, `ActionServerLiveCheckTest`, `McpRegistrarTest`, `OneDispatchModelTest` |
| [#163](https://github.com/telaminai/fluxtionauditlog-analyser/pull/163) | `feature/m78-d1-quiet-panel` | `993c0b0c` | #159 | `spec-quiet-assistant-panel.md` §4–§6, §10 (D1) | the quiet panel; Ready 27 → 3 words; the safety signals kept | `QuietPanelTest`, `QuietPanelSessionTest`, `AssistantChromeBudgetFrameTest`, `LocalAgentPanelCueTest`, `LocalAgentDetailsDisclosureTest`, `BindingChipTest` |
| [#160](https://github.com/telaminai/fluxtionauditlog-analyser/pull/160) | `feature/usability-scorecard` | `017bef66` | P3 | `docs/usability/scorecard.md` | a measured task battery with CI budgets; baselines at `b1c22704` and `a875fcba` | `UsabilityBudgetFrameTest`; `docs/usability/baseline-*.{json,md}`; `tools/usability/run.py` |
| [#165](https://github.com/telaminai/fluxtionauditlog-analyser/pull/165) | `feature/m77-4-coexistence` | `43b7ce8f` (merge of #161 at `3ad0896a`) | #162 | M77 §14, §18 | the Assistant ▾ quick switch (`QuickSwitch`); the profile cards retired (`ProfileOverlay` deleted); Connect folded into one door; the first-run block offered once; the diagram on Screen 2b; T20 (`tools/capture-docs.py --setup-ai`); the symlinked-home capture guard; regenerated processor | `QuickSwitchTest`, `QuickSwitchFrameTest`, `CaptureHomeGuardTest`, `AiSetupWizardFrameTest`, `AiSetupClientFrameTest`, `OneDispatchModelTest`, `DocsExternalAccessClaimsTest` |
| [#166](https://github.com/telaminai/fluxtionauditlog-analyser/pull/166) | `feature/m78-d2-one-chip` | `209bf46c` (the integration's base) | M77-4 + a merge of #163 | M78 §10 (D2) | the mode chip is M77-4's quick switch | `QuietPanelTest`, `QuickSwitchFrameTest`, `AssistantChromeBudgetFrameTest`; scorecard S2 (6 words, 4 controls) |

### Spec PRs (docs only)

| Branch / PR | Tip | Base | Status |
|---|---|---|---|
| `docs/m77-ai-setup-wizard` (no PR) | `56d9c555` | P3 | M77 spec **r4**, ACCEPTED (OD-77.1–.21); review disposition `review_m77_r1_7f3c.md`. **r5** (OD-77.1 superseded by OD-79.5; OD-77.9/.19 moot) is on #164 |
| [#158](https://github.com/telaminai/fluxtionauditlog-analyser/pull/158) `docs/quiet-assistant-panel` | `d46fc534` | `docs/m77-ai-setup-wizard` (at its r3 commit `0e6f235f`) | M78 spec ACCEPTED (OD-Q.1–.12) |
| [#164](https://github.com/telaminai/fluxtionauditlog-analyser/pull/164) `docs/m79-first-minute` | `4c10f2d7` | `docs/m77-ai-setup-wizard` | M79 spec ACCEPTED r1 (OD-79.1–.10), under independent review; carries M77 r5 |
| [#154](https://github.com/telaminai/fluxtionauditlog-analyser/pull/154) `docs/m76-checkpoint-and-completion` | `d3b3092b` | P3 | M76 spec r2, reviewed (`review_m76_r1_k4m9.md`); OD-76.1–.19 open |
| [#153](https://github.com/telaminai/fluxtionauditlog-analyser/pull/153) `docs/m75-document-renderer` | `29011ffb` | `main` | M75 spec r2 |
| [#155](https://github.com/telaminai/fluxtionauditlog-analyser/pull/155) `docs/m74-virgin-onboarding-pack` | `d1e38e2b` | `main` | virgin-LLM protocol, scorecard, runner, teaching packs |

The four tracker-editing spec branches (`docs/m77-ai-setup-wizard`, #158, #164, #154) insert at the same place in
`tracker.md`; this branch's tracker (`docs/tracker-review-2026-10-18`) holds their lines verbatim, so resolve those
conflicts by taking it. **One merge is silent:** #158 merged after `docs/m77-ai-setup-wizard` or #164 appends a second
M78 block just above *Session and dispatch* with no conflict (rehearsed 2026-10-18) — delete the duplicate. With the
four spec branches merged, `SpecLinksResolveTest` passes; on this branch alone it reports their seven spec links.

### Other repositories

| Repository | Ref | State |
|---|---|---|
| `telaminai/fluxtion-diamond` | `main` `24d6d74` | the diamond in four domains (mathematics, accounting, logistics, trading) beside the generic run, all real runs — M79 D-1 |
| `telaminai/fluxtion-diamond` | `teaching/broken-diamond` `6bff0d67` | published, not merged to `main` on purpose; record [3]; digest `sha256:7e31f039…0d11` — M79 D-2 |

## 2. Flags for the integration review

Each is a deliberate change of behaviour or contract. Judge it; do not assume it is right because it is listed.

| # | Flag | PR / branch | Where to look |
|---|---|---|---|
| 1 | Downloaded examples keep the executable bit on `mvnw` and `*.sh` — overrides an earlier spec rule; owner accepted 2026-10-18 | `fix/authoring-walk-defects` (`c972ef78`) | the example unpacker; M74 spec's amended examples rule; `ExampleArchiveContainmentTest`, `ExampleDownloadTest` |
| 2 | Narrow pre-allow of `open` for the project's own log or GraphML in Full: a real-path check with a residual time-of-check risk | `fix/authoring-walk-defects` (`0161dab3`) | `InProjectOpenTest`, `HostedGrantServerTest`; M74 spec OD-74.16 |
| 3 | `STAY_IN_PROJECT_NOTE` is a request to the model, not a sandbox; disclosed in the trust dialog and FAQ | `fix/authoring-walk-defects` (`e37743f2`) | `ClaudeCodeProvider`, `FullFolderTrust`; `docs/site/faq.md`; `FaqSecurityContractTest` |
| 4 | Build preselects Full acceptEdits; the fix that saves the wizard's mode changes behaviour | `fix/authoring-walk-defects` (`4d44528e`) | `AiSetup`, `AssistantProfileChoice`, `ConfigStore`; `AiSetupTest` |
| 5 | A new confirmation before external access turns on — the customer's developers will see it; it goes in the release notes | #162 | `ExternalAccessDoor`, `SetupConfirmations`, `ActionListener`; `CHANGELOG.md` ▸ Unreleased |
| 6 | The bridge sends `X-Analyser-Client` / `X-Analyser-Probe` (additive) | #162 | `McpBridge`, `ActionServer`, `McpConnectionProbe`; `ActionServerLiveCheckTest`; now pinned by `CustomerApiKeysTest` (integration) |
| 7 | `context` gains `hidden` (additive); the `WorkModeKeepsTheApiTest` digests did not move | `fix/records-filter-visibility` | `MainFrame` context assembly, `ContextSections`; run `WorkModeKeepsTheApiTest` on the merged tree; now pinned by `CustomerApiKeysTest` (integration) |
| 8 | Search text in facts is stored by `FactRefs` reference, never as words | `fix/records-filter-visibility` | `session/FactRefs`, `RecordsViewCounts`; `FactFieldsTest` |
| 9 | The Event types list starts collapsed by default (a UI preference; saved choices win) | `fix/event-types-default-collapsed` | `AppConfig`; `EventTypesStartCollapsedFrameTest` |
| 10 | M78 D1 deviations from the spec: the *"starting…"* chip word; Details opens only when pressed; `shortTitle` labels; a Restart button on the error banner | #163 | `LocalAgentState.CHIP_STARTING`, `LocalAgentSession`; M78 spec §4–§5; `QuietPanelTest` |
| 11 | M77-5's diagram always shows external access OFF until M77-4 wires it | #161, wired in #165 | `ui/setup/FlowDiagram` (`externalAccess`); `FlowDiagramTest`; check the wiring on #165 |
| 12 | The profile cards are retired: `ProfileOverlay` is deleted, the quick switch replaces it and Connect is one door. Every existing user sees this, so it goes in the release notes | #165 | `ui/setup/QuickSwitch`, `AssistantPanel`, `StartPanel`; `QuickSwitchTest`; `CHANGELOG.md` ▸ Unreleased |
| 13 | The first-run block is offered once, then not again | #165 | `aiSetup` / the start page's first-run state; `FirstRunGuideTest`, `AssistantFirstRunFrameTest` |
| 14 | `capture-docs.py` refuses a symlinked isolated home (the guard that keeps real names out of screenshots, CLAUDE.md rule 1) | #165 | `tools/capture-docs.py`; `CaptureHomeGuardTest` |

## 3. Measurements the claims rest on

- **Scorecard `b1c22704` → `a875fcba`** (#160, `docs/usability/scorecard.md` ▸ *Before → after*): SC T4 3→2 acts and
  4→2 hesitations; SC T5 9→8 acts; SC T1 4→5 and SC T2 4→5 — trade-offs, SC T2 expected to recover with
  `fix/event-types-default-collapsed`. Scorecard task ids (SC T1–T8) are not the M77 spec's test ids (T1–T31).
- **Authoring walk:** the full chain worked live in 7.4 minutes with 14 approvals; after `fix/authoring-walk-defects`
  about 3 approvals are expected — **not yet re-measured**.
- **Robot walk 3:** five confusion points mapped, each fixed or planned (fixes: first-run polish, the records fixes,
  M77; the rest planned in M78/M79).

## 4. Known gaps

- **The full gate ran once, on the integration** (section 5). The mutation shards and the loop bench were not part of
  that run.
- **M78 D2 is built** (#166, `209bf46c`, the integration's base). M77-4 is built (#165).
- **M77-4's doc screenshots are not regenerated** (no Screen Recording grant on the capture machine):
  `start-page.png` and `settings-assistant.png` are stale; regenerate with `tools/capture-docs.py` (and `--setup-ai`)
  under the isolated home before release.
- **M74's card-era `[Unreleased]` CHANGELOG lines are folded** on the integration (`fe3286ab`); re-read them at release.
- **`StartWorkspaceFrameTest.nativeFileDropOnHeroTextOpensAuditLog` is flaky under load.** A failure on integration
  is not a pass to skip; re-run it alone and report it separately.
- **M78 D1 (#163) edits `ProfileOverlayFrameTest`, which M77-4 (#165) deletes**: a modify/delete conflict, resolved by
  D2's merge of D1; take D2's resolution.
- **The scorecard's comprehension and authoring batteries are in progress**; the reduced approval count is predicted,
  not measured.
- **The docs restructure (#152)** predates every `docs/site/` edit in the fix and feature branches; the reconciliation
  is the integrator's and needs `mkdocs build --strict`.
- **The generated session processor** is regenerated by six branches (with M77-4); integration must regenerate it, not merge it.
  `fix/authoring-walk-defects` changes nodes and `SessionEvents` without regenerating; by reading its diff it adds no
  handler (only fields on existing records), so the regeneration on integration is the check.
- **Owner acceptance is open** throughout: the hands-on test of the integration build, Windows (#148), OD-74.2 and the
  security model before any customer-facing release.
- **The M79 spec's independent review** is under way; M79 code has not started.

## 5. Integration tip and full-gate results

Filled in by the integrator, 2026-10-18.

- **Integration branch and tip:** `test/usability-2026-10-18` (no PR). The full gate ran at **`5971608c`**; the commit
  after it adds only this section and the brief's placeholders (docs only, no product or test change). Product code is
  unchanged from `5971608c` to the tip.
- **Base:** `feature/m78-d2-one-chip` @ `209bf46c` (#166), which already holds P3 `a875fcba`, M77-1/2/3/5/4 and M78 D1/D2.

### Merge order (each `git merge --no-ff`, its own merge commit)

| # | Merged | Tip | Merge commit | Conflicts and resolution |
|---|---|---|---|---|
| 1 | `fix/no-log-offer-cancel` | `da86da42` | `5a3d76c6` | generated processor only (taken as HEAD, regenerated at merge 2) |
| 2 | `fix/event-types-default-collapsed` (with `fix/records-filter-visibility`) | `eb52bb45` | `2fc94b50` | `SessionSnapshot` (both components, `aiSetup` then `recordsView`); `ci.yml` (union of both frame lists, identical); CHANGELOG (both); generated processor **regenerated** |
| — | follow-up | | `5c08656d` | FIX-1's new frame test awaited the retired cards (`profiles().open()`); it now awaits the wizard, as its sibling does |
| 3 | `fix/authoring-walk-defects` (with `fix/template-exec-bits`) | `0161dab3` | `4cac9eb7` | built on M77-2; its `AiSetup`, `SessionEffects`, `StartPagePrompt`, `MainFrame` edits re-applied on M77-4's shape (no `wizardEntry`, no cards); both tests kept in `FaqSecurityContractTest` and `FactFieldsTest`; regenerated: **byte-identical** (no handler added) |
| 4 | `fix/merged-nodelog-entries` | `e46c5836` | `ba85dd73` | CHANGELOG only |
| — | follow-up | | `21da8077` | UP-FLX-56/57/58 marked filed (telaminai/fluxtion#46) |
| 5 | `feature/usability-scorecard` (#160) | `017bef66` | `d4975bc5` | `ci.yml` (union; `UsabilityBudgetFrameTest` in both lists); the harness drove the retired cards — ported and re-baselined in `b2ac2098` (Test B below) |
| 6 | `docs/m79-first-minute` (#164, with the M77 spec r5) | `6ca55014` | `39979050` | none |
| 7 | `docs/quiet-assistant-panel` (#158) | `d46fc534` | `a9c6aa00` | `docs/specs/README.md`: the M77 row keeps r5, the M78 row added, both stating what the integration holds |
| 8 | `docs/tracker-review-2026-10-18` | `4067a4f9` | `218c824d` | `tracker.md`: the duplicate M77/M78/M79 blocks reduced to one each; every line brought up to the integration |
| 9 | `docs/site-restructure` (#152) | `b1c22704` | `2c2f0c21` | 13 files; the restructure's information architecture kept and every branch's `docs/site/` content carried into it (see the merge commit): `user-guide/assistant.md` deleted as the restructure split it, its later edits ported to `inapp/*`, `admin/profiles.md` (Set up AI, the quick switch), `admin/security.md`, `teach/spotlights-walks-reels.md`; every *Connect an AI client…* door rewritten to Set up AI; `ProfileCardsWordsTest` and `assistant-profile-cards.png` stay deleted |
| — | follow-ups | | `fe3286ab`, `60889e55`, `a601de1f`, `6f4b6f9a`, `5971608c` | the card-era CHANGELOG fold; Test A; the tracker's DOCS-IA line; the changelog-coherence test and the M76 links; three frame tests the stack had left stale (below) |

**The generated session processor** was never hand-merged: regenerated (`mvn -Pregen process-classes`) at merges 2
and 3 and again at the tip, where it is a fixpoint (byte-identical). The header is unchanged (api 1.1.0, analyser
1.0.76, target generator 1.0.79). The dispatch order read from the generated `handleEvent` methods:
`AssistantProfileApplied` → `localAgentSession`, `aiSetup`, `startPagePrompt`; `RestSwitchObserved` →
`actionListener`, `aiSetup`; `RecordsViewMeasured` → `recordsView`, `assistantLoop`; `StartPagePromptSent` →
`startPageNoLogHint`, `aiSetup`, `startPagePrompt`; `StartPageFlowDone` → `startPageNoLogHint`, `startPagePrompt`. Each
new node reads its parents after they handled the fact (M77 §8.1).

### Gates (run once, at `5971608c`, on macOS)

- ☑ `mvn -o clean test` (headless): **580 suites, 4,131 tests, 3,808 pass, 0 fail, 0 errors**. **Skipped 323**, reported
  separately: 319 in `*FrameTest` suites (no display), 4 others (`MouseTraceTest` 2, `FlowDiagramTest` 1,
  `TemplateRootWindowsTest` 1; display- or Windows-only).
- ☑ The registered frame suites on the display (67 suites, the two `ci.yml` lists identical): **320 tests, 311 pass,
  0 fail, 4 errors, 5 skipped**. The errors are the Mac baseline, `TableDragCancellationFrameTest`'s four native-press
  cases (*the desktop dropped all three presses*). Skips (focus/native on this Mac): `AssistantNativeFrameTest` 2,
  `PersonAtTheScreenFrameTest` 1, `StartWorkspaceFrameTest.nativeFileDropOnHeroTextOpensAuditLog` 1,
  `WalkArrowKeysFrameTest` 1. These run under Xvfb in CI, which rejects skips.
  The first full run found four failures outside the baseline, in three suites. Bisected over the stack's tips, each
  already failed on #166's tip and passes on P3: `DuplicateChartRepairFrameTest` ×2 (since M77-4, #165: the first-run
  write at start-up), `StartPromptFrameTest.claudeCodeArmsStart` (since M78 D1, #163: Start moved into ⋯),
  `AssistantLiveFrameTest.savingProviderSettingsRefreshesAssistant` (since M78 D2, #166: the no-key banner only for
  the in-app assistant in force). Each test asserted the UI its own PR changed by design; fixed in `5971608c`.
- ☐ Mutation gate and loop bench: not run in this pass.
- ☑ `WorkModeKeepsTheApiTest` digests unchanged (in the headless run), and **`CustomerApiKeysTest`** (new) pins
  `context`'s sections and keys and the bridge's request headers against 1.35.1, the additions listed by name.
- ☑ `python3 tools/test_project_chart_review.py`: 5 tests, OK.
- ☑ On the built jar: `verify-m46-agent-api` (16 PASS, *all M46 agent-API checks pass*), `verify-session-transitions`
  (*ALL PASS*), `verify-session-restart` (PASS).
- ☑ `mkdocs build --strict`: passes.
- ☑ The public-repo sweep (CLAUDE.md rule 1): prints nothing. Commit identities `feature/claude-code-p3..HEAD`: only the
  personal address. No screenshot was added or regenerated by the integration (M77-4's stale `start-page.png` and
  `settings-assistant.png` remain, a known gap).
- ☑ Scorecard re-run on the integration jar (`python3 tools/usability/run.py --record --label integration`; a second
  run at `5971608c` gave identical JSON). Budgets re-baselined in `UsabilityBudgets.java`, one reason per change.

### Scorecard: `b1c22704` → `a875fcba` → integration

| Task | Success | Acts | Hesitations | Dead ends | Words before first act | Clipped |
|---|---|---|---|---|---|---|
| T1 Fresh start to the first answer | yes → yes → yes | 4 → 5 → 6 | 1 → 2 → 3 | 0 → 0 → 0 | 225 → 225 → 222 | 0 → 1 → 1 |
| T2 Right-click Ask about a record | yes → yes → yes | 4 → 5 → 5 | 2 → 2 → 3 | 0 → 0 → 0 | 265 → 273 → 262 | 6 → 4 → 4 |
| T3 Approve a report write | yes → yes → yes | 1 → 1 → 1 | 0 → 0 → 0 | 0 → 0 → 0 | 317 → 325 → 314 | 3 → 3 → 3 |
| T4 Find out why records are missing | yes → yes → yes | 3 → 2 → 1 | 4 → 2 → 1 | 0 → 0 → 0 | 259 → 259 → 272 | 3 → 3 → 3 |
| T5 Switch Safe → Full → Safe | yes → yes → yes | 9 → 8 → 5 | 2 → 2 → 0 | 0 → 0 → 0 | 246 → 254 → 199 | 5 → 2 → 1 |
| T6 Set up an external client | yes → yes → yes | 4 → 4 → 7 | 1 → 1 → 4 | 0 → 0 → 0 | 225 → 225 → 222 | 0 → 0 → 0 |
| T7 Open the sample | yes → yes → yes | 1 → 1 → 1 | 0 → 0 → 0 | 0 → 0 → 0 | 225 → 225 → 222 | 1 → 1 → 1 |
| T8 Load a prompt pack | yes → yes → yes | 1 → 1 → 1 | 0 → 0 → 0 | 0 → 0 → 0 | 225 → 225 → 222 | 0 → 0 → 0 |
| A2 Teach-me diamond pack | — → yes → yes | — → 11 → 10 | — → 4 → 3 | — → 0 → 0 | — → 225 → 222 | — → 6 → 6 |

| State | Chrome words | Controls | Clipped | Whole-window words |
|---|---|---|---|---|
| S1 Start page (fresh home) | 225 → 225 → 222 | 15 → 15 → 16 | 0 → 0 → 0 | 225 → 225 → 222 |
| S2 Assistant Ready (Safe analysis, docked) | 32 → 31 → 6 | 6 → 6 → 4 | 2 → 0 → 0 | 224 → 229 → 195 |
| S3 Records with the event-type list shown | 17 → 25 → 26 | 5 → 8 → 9 | 0 → 0 → 0 | 265 → 273 → 274 |

T4c (the result of T4): — → **FAIL** → **pass**, now enforced. **Finding for the owner:** SC T6 4 → 7 acts and 1 → 4
hesitations — the one door for your own client puts the bridge check behind *Continue*, the access confirmation and
*More options…* (below Screen 2b's fold). Each step has a reason; together they make T6 the battery's costliest task.
Also worse with a reason: SC T1 5 → 6 acts (Continue and Finish replace Apply), SC T1/T2 one more hesitation each, S3
25 → 26 words. Better: SC T4, SC T5 8 → 5, S2 31 → 6 words, A2 11 → 10. Details: `docs/usability/scorecard.md` ▸ *The
integration build*.

## 6. v2: `test/usability-2026-10-18b`

Filled in by the integrator, 2026-10-18, for the owner's second hands-on test (no PR).

- **Branch and tip:** `test/usability-2026-10-18b`. The full gate ran at **`ddccd5c5`** (merge 2 below; an earlier full
  gate at merge 1, `42c5bb78`, was also green). The commits after it change only this section, the brief and the
  addendum's facts (docs only). Docs are test inputs, so the headless suite, `mkdocs build --strict` and the sweep were
  run again at the tip. Product code is unchanged from `ddccd5c5` to the tip. An early tip (`e6fd81fc`, headless green)
  was pushed for the external reviewer before the follow-ups; review the final tip.
- **Base:** `fix/owner-test-small` @ `1f445e78`, which already holds v1 (`54b3da26`) and
  `fix/wizard-scorecard-regressions` (`c07eaccc`).

### What v2 adds

| Branch | Tip | Base | Claims | Evidence |
|---|---|---|---|---|
| `fix/wizard-scorecard-regressions` (FIX-7, no PR) | `c07eaccc` | v1 `54b3da26` | Set up AI's buttons say what they do (*Check Claude Code*, *Set up my client*, *Finish, then send my question*); **Check connection** on Screen 2b above the fold, its confirmation also running the check | `AiSetupWordingTest`, `AiSetupClientFrameTest`, `UsabilityBudgetFrameTest`; scorecard ▸ *The wizard fix* (T6 7→4 acts) |
| `fix/owner-test-small` (FIX-6, no PR) | `1f445e78` | `c07eaccc` | from the owner's first hands-on test: the sample selects its shipped processor; `processors[].from` and `source {bean}` with no design say the truth; permission dialogs come forward and a timeout says do not retry; Full opens the active sample's own log and GraphML without asking; a key named as a `context` section is refused with its section; Start building offers `acceptEdits` when Full is in `default` (one new handler, `aiSetup.onBuildModeAnswered`) | `DemoAssetsTest`, `ProcessorFromTest`, `DesignWorkspaceTest`, `LocalAgentPermissionTest`, `LocalAgentPermissionFrameTest`, `InProjectOpenTest`, `HostedGrantServerTest`, `ContextSectionsTest`, `AiSetupTest`, `BuildModeOfferPanelTest` |
| `fix/starter-followups` (OPT-2 follow-ups, no PR) | `3cf5beb8` | `59220eee` | owner decisions 2026-10-18: the playground pages bundled with the publisher's permission (licence statement pending, UP-PG-10); `add-a-node`'s one regeneration `TODO(bundle)` block replaced, after the pins match, by fixed text from the starter's first-run record (known script tokens only) and its key need; `.claude/skills/.vendored.json` lists each file's pinned and written digest; the design-folder row starts at Yes and says why; regenerated, no change | `StarterSkillsTest`, `StarterProjectTest`, `StarterProposalFrameTest`; eight mutation witnesses (its commit) |
| `feature/propose-starter` — draft [#167](https://github.com/telaminai/fluxtionauditlog-analyser/pull/167) (OPT-2) | `59220eee` | `c07eaccc` | Claude proposes a starter (`propose {kind: starter}`), `context.starters`; ONE `starterProject` chain for the proposal and *New project from template…*: download after Apply, skills from the jar, discovery, one review (rows at No, incl. the design folder), open, Spring design, GraphML watch, draft; the identity note; Full's docs-site `WebFetch` | `ProposeShapeStarterTest`, `StarterProposeApiDeltaTest`, `StarterProjectTest`, `StarterSkillsTest`, `StarterProposalFrameTest`, `ClaudeCodeProviderTest`, `CustomerApiKeysTest`, `OneDispatchModelTest` |

### Merges (each `git merge --no-ff`, its own merge commit)

| # | Merged | Tip | Merge commit | Conflicts and resolution |
|---|---|---|---|---|
| 1 | `feature/propose-starter` (#167) | `59220eee` | `42c5bb78` | **CHANGELOG**: both sides' `[Unreleased]` ▸ *Changed* lines kept (union); every new line of either side is under `[Unreleased]`, checked by script. **`LocalAgentPermission`**: the `PRE_ALLOWED` note keeps FIX-6's open wording (*inside this project's folder or the analyser's own sample*, which `LocalAgentAdapter.OPEN_IN_PROJECT_MARK` matches) and adds #167's `WebFetch` line. **Generated session processor** (java, its resources copy, the GraphML): not hand-merged, regenerated. Auto-merged and checked line by line (every added line of either side present): `MainFrame`, `LocalAgentPanel` (FIX-6's Build-mode banner and #167's *About this session* note both kept; #167 adds no banner), `LocalAgentAdapter`, `ContextSections`, `SessionEvents`, `LocalAgentPermissionTest`, `security.md`, `build/index.md`, `verbs.md`. `ci.yml`: only #167 changed it; the two frame lists are identical (68 suites) and name every `*FrameTest` in the tree (`StarterProposalFrameTest`, `LocalAgentPermissionFrameTest` included). `SessionSnapshot`: only #167 changed it (the `starter` component). |
| — | review docs | | `e6fd81fc` | this section's first version (the early tip) |
| 2 | `fix/starter-followups` | `3cf5beb8` | `ddccd5c5` | `tracker.md` OPT-2 only (both kept, two lines). CHANGELOG auto-merged, its three lines under `[Unreleased]`. Processor regenerated: **byte-identical** (no handler added; the effects gained fields only) |

**The generated session processor** was regenerated with `mvn -Pregen process-classes` (bootstrapped from #167's
copy, which compiles against the merged nodes) and is a fixpoint: a second regeneration is byte-identical. The header
is kept; after merge 2 it regenerated byte-identical again. Read from the generated `handleEvent` methods: every handler both sides have dispatches exactly as #167's
does, and FIX-6's order is preserved within it; the only handler not in #167 is FIX-6's
`AiSetupBuildModeAnswered` → `aiSetup.onBuildModeAnswered`; #167's seventeen starter facts dispatch as #167 generated
them (e.g. `StarterChosen` → `starterProject`; `StarterReviewAnswered` → `onboardingProposal`, then
`starterProject`), and `sessionBoundary` is given `starterProject`.

### Gates (at `ddccd5c5`, on macOS)

- ☑ `mvn -o clean test` (headless): **587 suites, 4,185 tests, 0 fail, 0 errors, 326 skipped** (frame suites with no
  display, plus `MouseTraceTest` 2, `FlowDiagramTest` 1, `TemplateRootWindowsTest` 1). Re-run at the tip after the
  docs commit: the same.
- ☑ The registered frame suites on the display (68 suites, the two `ci.yml` lists identical): **324 tests, 323 pass,
  0 fail, 0 errors, 1 skipped** — `WalkArrowKeysFrameTest.arrowKeysMove` (focus on this Mac). At `42c5bb78`: 323
  tests, 321 pass, 2 skipped (`PersonAtTheScreenFrameTest` 1, `StartWorkspaceFrameTest.nativeFileDropOnHeroTextOpensAuditLog`
  1). `TableDragCancellationFrameTest`'s four native-press cases, the Mac baseline's errors, **passed** in both runs;
  desktop luck, not a fix — read them as the baseline still.
- ☑ `python3 tools/test_project_chart_review.py`: OK.
- ☑ On the built jar: `verify-m46-agent-api` (16 PASS, 0 FAIL), `verify-session-transitions` (*ALL PASS*; its one
  line containing "FAILED" is the check *a FAILED switch must close nothing*), `verify-session-restart` (PASS);
  `tools/test_analyser_session_watch.py`: OK.
- ☑ `mkdocs build --strict`: passes.
- ☑ The sweep (CLAUDE.md rule 1) prints nothing. Identities `origin/feature/claude-code-p3..HEAD`: only
  `gregphiggins@gmail.com` (author and committer). No screenshot added or regenerated.
- ☐ Mutation gate and loop bench: not run.

### Scorecard: integration → `regfix` → `integration-b`

`python3 tools/usability/run.py --record --label integration-b` on the jars built at `42c5bb78` and again at
`ddccd5c5`: both JSONs are byte-identical to `baseline-regfix.json` apart from the label — **no regression, and no
change**. FIX-6, #167 and its follow-ups touch none of the battery's routes.

| Task | Success | Acts | Hesitations | Dead ends | Words before first act | Clipped |
|---|---|---|---|---|---|---|
| T1 Fresh start to the first answer | yes → yes → yes | 6 → 6 → 6 | 3 → 2 → 2 | 0 → 0 → 0 | 222 → 224 → 224 | 1 → 1 → 1 |
| T2 Right-click Ask about a record | yes → yes → yes | 5 → 5 → 5 | 3 → 2 → 2 | 0 → 0 → 0 | 262 → 262 → 262 | 4 → 4 → 4 |
| T3 Approve a report write | yes → yes → yes | 1 → 1 → 1 | 0 → 0 → 0 | 0 → 0 → 0 | 314 → 314 → 314 | 3 → 3 → 3 |
| T4 Find out why records are missing | yes → yes → yes | 1 → 1 → 1 | 1 → 1 → 1 | 0 → 0 → 0 | 272 → 272 → 272 | 3 → 3 → 3 |
| T5 Switch Safe → Full → Safe | yes → yes → yes | 5 → 5 → 5 | 0 → 0 → 0 | 0 → 0 → 0 | 199 → 199 → 199 | 1 → 1 → 1 |
| T6 Set up an external client | yes → yes → yes | 7 → 4 → 4 | 4 → 1 → 1 | 0 → 0 → 0 | 222 → 224 → 224 | 0 → 0 → 0 |
| T7 Open the sample | yes → yes → yes | 1 → 1 → 1 | 0 → 0 → 0 | 0 → 0 → 0 | 222 → 224 → 224 | 1 → 1 → 1 |
| T8 Load a prompt pack | yes → yes → yes | 1 → 1 → 1 | 0 → 0 → 0 | 0 → 0 → 0 | 222 → 224 → 224 | 0 → 0 → 0 |
| A2 Teach-me diamond pack | yes → yes → yes | 10 → 10 → 10 | 3 → 3 → 3 | 0 → 0 → 0 | 222 → 224 → 224 | 6 → 6 → 6 |

| State | Chrome words | Controls | Clipped | Whole-window words |
|---|---|---|---|---|
| S1 Start page (fresh home) | 222 → 224 → 224 | 16 → 16 → 16 | 0 → 0 → 0 | 222 → 224 → 224 |
| S2 Assistant Ready (Safe analysis, docked) | 6 → 6 → 6 | 4 → 4 → 4 | 0 → 0 → 0 | 195 → 195 → 195 |
| S3 Records with the event-type list shown | 26 → 26 → 26 | 9 → 9 → 9 | 0 → 0 → 0 | 274 → 274 → 274 |

T4c passes in all three. The S1 +2 words are FIX-7's, with their reason in *The wizard fix*.

### Flags for v2

Each is a deliberate change of behaviour or contract. Judge it; do not assume it is right because it is listed.

| # | Flag | Branch | Where to look |
|---|---|---|---|
| 15 | The `propose` verb gains a `starter` kind (with `starter`, `reason` and optional `artifact`, `group`, `basePackage`), **hosted only**: alone in its proposal, no path; the external MCP/REST `propose` is unchanged | #167 | `ProposeShape`, `VerbSchemas`; `ProposeShapeStarterTest`, `StarterProposeApiDeltaTest` (undone, both hosted surfaces give their old pins) |
| 16 | `context.starters` in the `project` section (additive: id, name, description, authoring, host, key need; Spring first), rendered from the snapshot, never fetched | #167 | `ContextSections`, `StarterState`, `MainFrame` context assembly; `CustomerApiKeysTest` (`CONTEXT_ADDITIVE`) |
| 17 | Full pre-allows `WebFetch(domain:fluxtion-playground.dev)`: one fixed rule on the CLI's command line, and the analyser's own approval allows without a dialog only an https fetch of exactly that host; any other fetch is asked; Safe has no web tool | #167 | `LocalAgentPosture.DOCS_DOMAIN` / `docsFetch`, `LocalAgentPermission` (the `WEB_FETCH` branch), `FullFolderTrust.DOCS_FETCH`; `docs/site/admin/security.md` |
| 18 | Full pre-allows `open` of the **active sample project's** own log and GraphML (its session works in a scratch folder, so flag 2's in-project rule never matched): a second root with the same real-path, key and Full-only checks; external REST/MCP never has one | FIX-6 (`62e1971a`) | `McpTools.OpenScope.sampleRoot`, `LocalAgentFolders.sampleRoot`; `InProjectOpenTest`, `HostedGrantServerTest` |
| 19 | The identity note begins both postures' system notes (Full adds the starter rule), so `postureRef` changes: **sessions from before this build are not resumed**; they begin a new chat | #167 | `ClaudeCodeProvider` (the note and `postureRef`), `LocalAgentAdapter`; `ClaudeCodeProviderTest`; `docs/site/inapp/claude-code.md` |
| 20 | Skills are vendored from **jar resources** (`docs/skills` m19-skills/2 selection, digest-checked against the index, never overwriting), including the **pinned playground Spring pages** `spring-authoring/skill.md` and `contract.md`, which carry **no stated licence** — bundled with the publisher's permission (owner decision 2026-10-18, `fix/starter-followups`); the statement itself is pending (UP-PG-10) | #167 | `StarterSkills`, `pom.xml` resources, `src/main/resources/starter-skills/playground/`; `StarterSkillsTest`, `CanonicalSkillsTest`; `upstream-asks.md` ▸ UP-PG-10 |
| 21 | The combined review has a row that adds the Spring design's folder as a source root when no discovered source root covers it; since `fix/starter-followups` it **starts at Yes** (and says why: it is inside the project just created) while every other row, opening the project included, stays No | #167, `fix/starter-followups` | `StarterReviewDialog`, `StarterProject`; `StarterProjectTest`, `StarterProposalFrameTest` |
| 22 | `TODO(bundle)` markers in the vendored skills: since `fix/starter-followups`, `add-a-node`'s one regeneration block is replaced (after the pins match) by fixed text from the starter's first-run record — only the known `./check-fluxtion-key.sh`, `./setup.sh`, `./validate.sh`, `./generate.sh` tokens — and its key need; with no record naming `./generate.sh` the marker is kept. Both digests go in `.claude/skills/.vendored.json`. **The other selected skills' markers** (e.g. `load-audit-log`, `replay-a-run`) still reach the project unsubstituted: the analyser is not a bundle generator. **Since `fix/fill-skill-placeholders`** every marker the record can answer is filled the same way — `run-mongoose-server`'s start (`./run-server.sh`) and `run-embedded`'s run (`./run.sh`), each with the record's known steps before it — and every other marker (load-audit-log's paths, Mongoose export and stop) is kept with a `marker kept:` note per marker in `.vendored.json`'s `notes`; `everyMarkerIsInventoried` fails on a marker not in `StarterSkills.MARKERS` | #167, `fix/starter-followups`, `fix/fill-skill-placeholders` | `StarterSkills` (`MARKERS`, `substitute`, `regenerationBlock`, `mongooseStartBlock`, `embeddedRunBlock`), `docs/skills/*/SKILL.md`; `StarterSkillsTest`; UP-PG-10 |

### Known gaps (v2)

- **OPT-2 is not accepted**: draft #167 has had no independent review, and the live chain (a real model proposing, the
  real playground download, a first build with the key, the restart and the draft) was not run here; the frame and
  node tests stub the network and the model. The owner's hands-on test (`USABILITY-TEST-2026-10-18b.md`) is the first
  live run.
- **The scorecard has no starter task**, so the battery cannot see OPT-2; the authoring battery's approval count (14
  on the first walk, about 3 predicted with FIX-4 and FIX-6) is still not re-measured.
- Flags 20 and 22 rest on UP-PG-10, which is filed, not answered; `fix/starter-followups` has had no independent review.
- The stale screenshots of §4 remain; v2 regenerated none.
- Mutation shards and the loop bench were not run; Windows (#148) and OD-74.2 are open, as in §4.

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

### Feature PRs (stacked)

| PR | Branch | Tip | Base | Spec (M77 = `spec-ai-setup-wizard.md`) | Claims | Evidence |
|---|---|---|---|---|---|---|
| [#157](https://github.com/telaminai/fluxtionauditlog-analyser/pull/157) | `feature/m77-1-ai-setup-node` | `94c8660b` | P3 | M77 §8, §9, §18 | the `aiSetup` node and the two read-only Claude checks; the auth parse drops email and organisation; the generated dispatch order checked | `AiSetupTest`, `AiSetupKeysTest`, `ClaudeProbeParseTest`, `StartupModeTest`, `OneDispatchModelTest` |
| [#159](https://github.com/telaminai/fluxtionauditlog-analyser/pull/159) | `feature/m77-2-wizard-inside` | `c0b1b3bc` | #157 | M77 §5, §6, §18 | the wizard's inside path; T19 (the 1200×800 walkthrough) in 6 acts; the empty `selectedOptionExtra` slot | `AiSetupWizardFrameTest`, `AiSetupWordingTest`, `AssistantFirstRunFrameTest`, `StartPageCarryTest` |
| [#161](https://github.com/telaminai/fluxtionauditlog-analyser/pull/161) | `feature/m77-5-option-diagram` | `d3a5c8ff` | #159 | M77 §6.1a (OD-77.14–.21) | `FlowDiagram`: where data goes, Full in amber, the palette; no node, fact or effect | `FlowDiagramTest`, `OptionDiagramFrameTest` (T25–T31), `FaqSecurityContractTest` |
| [#162](https://github.com/telaminai/fluxtionauditlog-analyser/pull/162) | `feature/m77-3-external-path` | `5da42f16` | #159 | M77 §6 (Screen 2b), §13, §18 | Screen 2b; one owner of external access (`ExternalAccessRequested` through `actionListener`); confirmations at every door; registration; the live check; additive `X-Analyser-Client` / `X-Analyser-Probe` headers; regenerated processor | `AiSetupExternalTest`, `AiSetupClientFrameTest`, `ActionServerLiveCheckTest`, `McpRegistrarTest`, `OneDispatchModelTest` |
| [#163](https://github.com/telaminai/fluxtionauditlog-analyser/pull/163) | `feature/m78-d1-quiet-panel` | `993c0b0c` | #159 | `spec-quiet-assistant-panel.md` §4–§6, §10 (D1) | the quiet panel; Ready 27 → 3 words; the safety signals kept | `QuietPanelTest`, `QuietPanelSessionTest`, `AssistantChromeBudgetFrameTest`, `LocalAgentPanelCueTest`, `LocalAgentDetailsDisclosureTest`, `BindingChipTest` |
| [#160](https://github.com/telaminai/fluxtionauditlog-analyser/pull/160) | `feature/usability-scorecard` | `e4c8568a` (more commits expected) | P3 | `docs/usability/scorecard.md` | a measured task battery with CI budgets; baselines at `b1c22704` and `a875fcba` | `UsabilityBudgetFrameTest`; `docs/usability/baseline-*.{json,md}`; `tools/usability/run.py` |
| — | `feature/m77-4-coexistence` | not pushed | #162 + #161 merged | M77 §14, §18 | the quick switch; profile cards retired; upgraders; Connect folded; in-app behind *Other options*; the 2b diagram; the capture symlink guard | in progress |
| — | M78 D2 | not started | M77-4 + #163 | M78 §10 (D2) | the mode chip is M77-4's quick switch | — |

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
| 6 | The bridge sends `X-Analyser-Client` / `X-Analyser-Probe` (additive) | #162 | `McpBridge`, `ActionServer`, `McpConnectionProbe`; `ActionServerLiveCheckTest` |
| 7 | `context` gains `hidden` (additive); the `WorkModeKeepsTheApiTest` digests did not move | `fix/records-filter-visibility` | `MainFrame` context assembly, `ContextSections`; run `WorkModeKeepsTheApiTest` on the merged tree |
| 8 | Search text in facts is stored by `FactRefs` reference, never as words | `fix/records-filter-visibility` | `session/FactRefs`, `RecordsViewCounts`; `FactFieldsTest` |
| 9 | The Event types list starts collapsed by default (a UI preference; saved choices win) | `fix/event-types-default-collapsed` | `AppConfig`; `EventTypesStartCollapsedFrameTest` |
| 10 | M78 D1 deviations from the spec: the *"starting…"* chip word; Details opens only when pressed; `shortTitle` labels; a Restart button on the error banner | #163 | `LocalAgentState.CHIP_STARTING`, `LocalAgentSession`; M78 spec §4–§5; `QuietPanelTest` |
| 11 | M77-5's diagram always shows external access OFF until M77-4 wires it | #161 | `ui/setup/FlowDiagram` (`externalAccess`); `FlowDiagramTest` |

## 3. Measurements the claims rest on

- **Scorecard `b1c22704` → `a875fcba`** (#160, `docs/usability/scorecard.md` ▸ *Before → after*): SC T4 3→2 acts and
  4→2 hesitations; SC T5 9→8 acts; SC T1 4→5 and SC T2 4→5 — trade-offs, SC T2 expected to recover with
  `fix/event-types-default-collapsed`. Scorecard task ids (SC T1–T8) are not the M77 spec's test ids (T1–T31).
- **Authoring walk:** the full chain worked live in 7.4 minutes with 14 approvals; after `fix/authoring-walk-defects`
  about 3 approvals are expected — **not yet re-measured**.
- **Robot walk 3:** five confusion points mapped, each fixed or planned (fixes: first-run polish, the records fixes,
  M77; the rest planned in M78/M79).

## 4. Known gaps

- **No full gate has run on any of this yet.** Each branch had quick checks; the frame suites, mutation shards and
  loop bench run once, on integration (section 5).
- **M77-4 and M78 D2 are not built.** Until M77-4, the profile cards coexist with the wizard and M77-5 shows external
  access OFF (flag 11).
- **The scorecard's comprehension and authoring batteries are in progress**; the reduced approval count is predicted,
  not measured.
- **The docs restructure (#152)** predates every `docs/site/` edit in the fix and feature branches; the reconciliation
  is the integrator's and needs `mkdocs build --strict`.
- **The generated session processor** is regenerated by five branches; integration must regenerate it, not merge it.
  `fix/authoring-walk-defects` changes nodes and `SessionEvents` without regenerating; by reading its diff it adds no
  handler (only fields on existing records), so the regeneration on integration is the check.
- **Owner acceptance is open** throughout: the hands-on test of the integration build, Windows (#148), OD-74.2 and the
  security model before any customer-facing release.
- **The M79 spec's independent review** is under way; M79 code has not started.

## 5. Integration tip and full-gate results

_To be filled in by the integrator._

- Integration branch and tip: ☐
- Merge order and any conflict resolutions (tracker, CHANGELOG, generated processor, `docs/site/`): ☐
- `mvn test` (headless): ☐ passes / ☐ skips, reported separately
- Registered frame suites under Xvfb (no skips): ☐
- Mutation gate and loop bench: ☐
- `WorkModeKeepsTheApiTest` digests unchanged: ☐
- `mkdocs build --strict`: ☐
- The public-repo sweep (CLAUDE.md rule 1) and the screenshots read: ☐
- Scorecard re-run on the integration jar: ☐

# Review — M74 P3 + UX (final), `1832fe44..dfb19213`

**Reviewer:** independent. **Date:** 2026-10-17. **Branch:** `feature/claude-code-p3`.
**Filed by** the fixing session; the finding list is the reviewer's, as handed over. Dispositions are below.

## Findings

- **S1** `LocalAgentPermission:350`: the `tool_use_id` is compared only if both sides have one. If the grant has an
  id, require the same id on the call. Add a ★ `HostedGrantServerTest` case: grant with an id, call without the header
  → 403. The witness is the current `&& e.toolUseIdDigest()!=null`.
- **S2** `StreamJson.parse` catches only `RuntimeException`: a deeply nested input (~20k levels) throws
  `StackOverflowError` and kills the stdout reader, hanging the session. Bound the nesting depth in `Json.parse`
  (iterative pre-scan) and/or catch `StackOverflowError`, returning Unknown. The reader loop must never die silently.
  Add a replay test with a 20k-deep `can_use_tool` → denied, session alive.
- **S3** Wording made untrue by P3:
  - CHANGELOG:13 "denied until approvals in the analyser ship";
  - ConfigPanel:764 "non-destructive verbs";
  - "reads the evidence only" / "Safe analysis reads only" (ProfileOverlay:56, AssistantProfile:15,
    LocalAgentPanel:244, CHANGELOG:28, assistant.md:55) → "reads your evidence; it changes what is open or writes a file
    only after you approve each call";
  - Full card "You approve each action in the analyser" (ProfileOverlay:57, CHANGELOG:28) → "When Claude Code asks for
    approval, the analyser asks you".
  Pin these claims in `FaqSecurityContractTest`.
- **S4** Rule 9: `MainFrame.applyAssistantProfile` posts `AssistantModeChosen` from an effect performer. The node must
  request the mode change (react to `AssistantProfileApplied`, or emit a mode effect). `AssistantProfileApplied.ok`
  must report the real save result (`saveConfigQuietly` hides failures). Extend `OneDispatchModelTest`.
- **Nits**
  - **N1** `ConfigPanel.profileFromSwitches` duplicates the mapping: move it into `AssistantProfiles` so a single
    mapping is pinned;
  - **N2** ExitPlanMode/EnterPlanMode in full plan mode: deny by policy with a note, as AskUserQuestion is (and test);
  - **N3** `FactFieldsTest` evasions: STRUCTURE components must be nested `SessionEvents` records, including List
    element types; apply the name check to VOCABULARY with an explicit exemption list; move
    `LocalAgentInitObserved.tools` to PERSON_CONFIG;
  - **N4** deny permission requests while the turn is STOPPING;
  - **N5** fix the `StreamJson:525` comment, remove the no-op replace in `PermissionTextTest:62`, and have
    `PermissionText.visible` escape U+00A0, U+2000–U+200A and U+3164 etc.;
  - **N6** set `Button.defaultButtonFollowsFocus=false` on the permission dialog, and add one frame case on the modal
    path if feasible.

## Dispositions

| Finding | Disposition | Commit | Test (witness) |
|---|---|---|---|
| S1 | **fixed** — a grant with an id requires the same id on the call | `d4dac6cf` | ★ `HostedGrantServerTest#aGrantWithAnIdNeedsTheId` (witness: the old `&& e.toolUseIdDigest() != null` → 200) |
| S2 | **fixed** — `Json.MAX_DEPTH` 512; a deep `control_request` named by a shallow, non-recursive scan and denied; the reader counts anything that throws (Errors included) and reads on | `68adbda5` | ★ `LocalAgentAdapterReplayTest#aDeeplyNestedRequestIsDeniedAndTheSessionLives` (witness: no bound and RuntimeException-only catches → StackOverflowError); `#aDeepLineIsUnknown` |
| S3 | **fixed** — every listed sentence replaced on the cards, the limits line, Settings, `AssistantProfile`, the guide and the CHANGELOG (plus a fifth: the CHANGELOG's "non-destructive verbs") | `18742a59` | ★ `FaqSecurityContractTest#theProfileClaimsSayWhatP3MadeTrue` (pins the new claims and scans for the old; witness: the old Safe card line) |
| S4 | **fixed** — `assistantProfile` asks for `ChooseAssistantModeEffect` after a written profile; the performer reports the real save (`saveConfigReporting`), restoring the settings on failure | `e5aabbd4` | ★ `AssistantProfileTest#theNodeAsksForTheMode`; ★ `OneDispatchModelTest#theProfileIsWrittenOnlyByItsEffect` extended (witness: the performer posting `AssistantModeChosen`) |
| N1 | **fixed** — `AssistantProfiles.storedFromSwitches` / `claudeCodeSwitches`, one mapping for Settings and `inForce` | `18742a59` | ★ `AssistantProfilesTest#oneMappingFromTheSwitches` (a literal table) |
| N2 | **fixed** — ExitPlanMode / EnterPlanMode denied by policy with a note, in every posture | `3cc37a52` | ★ `LocalAgentPermissionTest#planModeToolsAreDeniedWithANote` |
| N3 | **fixed** — STRUCTURE must be a nested `SessionEvents` record (List element types too); the name rule applies to VOCABULARY with an explicit exemption list (one entry: `HostedCallRefusedByServer.reason`, a code); `LocalAgentInitObserved.tools` → PERSON_CONFIG | `81b8a973` | ★ `FactFieldsTest#structureAndVocabularyCannotEvade` |
| N4 | **fixed** — a request while STOPPING is denied, no dialog | `3cc37a52` | ★ `LocalAgentPermissionTest#aRequestWhileStoppingIsDenied` |
| N5 | **fixed** — the `rawMember` comment (first duplicate wins, unlike the parser — and why that is safe); the no-op replace removed; look-alike spaces and blank-drawing characters escaped | `81b8a973` | ★ `PermissionTextTest#lookAlikeSpacesAreEscaped` |
| N6 | **fixed** — Allow and Stop are never default capable (the per-dialog equivalent of `Button.defaultButtonFollowsFocus=false`; the UIManager key is global); a frame case added on the modal path | `81b8a973` | `LocalAgentPermissionFrameTest#theDialogAlone` (witness: drop `setDefaultCapable(false)` → fails at the default-capable assertion. Honest note: under FlatLaf a synthetic focusGained with the key forced on did not move the default even unfixed, so the default-button assertion alone is not a witness) |

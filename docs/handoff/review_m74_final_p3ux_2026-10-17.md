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
| S1 | _open_ | | |

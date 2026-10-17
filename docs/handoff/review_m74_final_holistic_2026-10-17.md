# Review — M74 holistic (final), `feature/claude-code-p3` at `dfb19213`

**Reviewer:** independent. **Date:** 2026-10-17. **Filed by** the fixing session; the finding list is the reviewer's,
as handed over (numbering kept; items not handed over — B3 — are not in this file). Dispositions are below.

## Findings

- **B1** The merge path (out of scope for this pass: the owner merges).
- **B2** With REST on, the full posture's own tools can drive the analyser as EXTERNAL via
  `~/.fluxtion-analyser/rest-endpoint`. Fix the card wording (P3+UX S3). When Full starts with external REST on, the
  panel's limits line says external REST is open to its tools, and the analyser offers "Close external access while
  this session runs" (`setExternalAllowed(false)` exists). This is the person's choice, decided by the node, rule 9.
  Add a test.
- **B4** `HostedAdmission.ask` times out after 5 s with no fact; if the node later consumes the grant, the record says
  admitted while the server refused. Add a `HostedCallAbandoned` fact; the node restores or voids the grant
  consistently. Add a test.
- **B5** Rule 9: `applyRestServer` decides keep-or-stop of the listener from the frame field `hostedBridgeGeneration`,
  and composes status text there. Move the listener lifecycle decision into a node (facts: REST switch, bridge
  served/needed; effects: open/close external, open/close hosted). Pin it in `OneDispatchModelTest`. Also P3+UX S4.
- **B6** The profile cards ignore the external loop. Reword Off to "No assistant in the analyser" (external clients
  unaffected), and add one line under the cards stating external MCP/REST access on/off with a pointer to Settings ▸
  Assistant. Unify the vocabulary (selector names, card names, the guide's "The onboard assistant" heading).
- **B7** Full-folder trust: say plainly in the dialog that files CLAUDE.md @imports, ancestor CLAUDE.md files and hook
  script contents are not re-checked (or digest them if cheap and bounded). Make the adapter's pre-spawn check verify
  the FULL trust digest, not only the posture ref.
- **B8** Tests:
  - adopt the reviewer's scratch frame test `ReviewH4ExternalRestFrameTest` (REST on plus a hosted session; external
    clients identical), renamed sensibly and registered in BOTH ci.yml frame lists;
  - add a static check that the two ci.yml frame lists are identical;
  - extend `ForeignTextIsPlainTest` to treat `JEditorPane` / `setContentType("text/html")` / `HTMLEditorKit` as doors
    (HelpPanel as a REVIEWED entry; pin TranscriptView's `StyledEditorKit`);
  - make `LocalAgentPermissionTest`'s oracle independent of `asks()`/`isFull()` (a literal expectation table);
  - (out of scope this pass) the CLI-drift canary and the managed-settings probe.
- **B9** Spec health: delete §4.2's dead `RestTransportObserved` / `RefreshHostedEndpointEffect` text; fix §10 line
  ~1617 (P1a's command line described as the default); trim the status block. Do NOT split the spec in this pass; add
  a tracker line proposing the split.
- **B10** Structure growth (out of scope: propose `LocalAgentWiring` in the tracker).
- **B11** OD-74.2 and the one-page security model (out of scope this pass).
- **Low**
  - **L-a** check the hosted credential BEFORE the hosted limiter (an unauthenticated caller must not spend the hosted
    bucket); add a test;
  - **L-b** CHANGELOG:33 vs :54 on whether Copy prompt carries the token: fix;
  - **L-c** `docs/specs/spec.md:128` "Explain selected": update;
  - **L-d** document that menu labels depend on the assistant in force (or give the spotlight targets stable ids);
  - **L-e** show the always-on lock warning only after a resume;
  - **L-f** §11 states that a confirmed folder's `.mcp.json` server can read the per-instance file and act as HOSTED;
  - **L-g** add a CHANGELOG/release-notes line: the full REST token is no longer printed to stdout, and automation must
    read `~/.fluxtion-analyser/rest-endpoint`.

## Dispositions

| Finding | Disposition | Commit | Test (witness) |
|---|---|---|---|
| B2 | _open_ | | |

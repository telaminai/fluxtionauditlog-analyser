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
| B1 | **not fixed — out of scope by direction:** the owner merges | — | — |
| B2 | **fixed** — a full session with external access open: the limits line says its tools can drive the analyser as an external client; the panel offers *Close external access while this session runs* (`ExternalAccessChosen`), decided by `actionListener`, reopened when the session ends. Card wording: see P3+UX S3 | `bcbe1eca`, `18742a59` | ★ `ActionListenerTest#aFullSessionIsOfferedToCloseExternalAccess` (witnesses: offer without the full check → the default posture is offered it; ignore the choice → nothing closes) |
| B4 | **fixed** — `HostedCallAbandoned`; an admitted, abandoned call is recorded ABANDONED, its grant void (never restored, consistently), and the person told | `888d2911` | ★ `HostedAdmissionTest#aTimedOutCallIsReported`; ★ `LocalAgentPermissionTest#anAbandonedCallVoidsItsGrant` |
| B5 | **fixed** — the `actionListener` node owns the listener lifecycle (REST switch fact; need/release pushed by `localAgentSession`; serve/release/configure effects; status words); `hostedBridgeGeneration` and `applyRestServer` are gone; the frame matches in one method. Follow-up: the session is built lazily, so the start-up report now builds it (found by `tools/verify-m46-agent-api.py` on the jar: `--rest` never listened) | `bcbe1eca`, `a68b42cd` | ★ `ActionListenerTest#theLifecycleIsTheNodes`; ★ `OneDispatchModelTest#theListenerLifecycleIsTheNodes` (witness: `setExternalAllowed` from a second method) |
| B6 | **fixed** — Off reads "No assistant in the analyser … external MCP and REST clients are not affected"; one line under the cards says whether external MCP / REST access is on, pointing to Settings ▸ Assistant; the guide's heading is *The in-app assistant* | `18742a59` | ★ `ProfileCardsWordsTest#theExternalLoopIsSaid`, `#oneVocabulary` |
| B7 | **fixed** — the dialog (and guide, FAQ) say what is not re-checked (`@path` imports, ancestor `CLAUDE.md`, hook script contents); the adapter's pre-spawn check verifies the FULL trust digest (`TRUST_CHANGED`). Digesting those files was not attempted: an import graph and arbitrary scripts are neither cheap nor bounded | `4029ab41` | ★ `LocalAgentFullReplayTest#theSpawnChecksTheFullTrustDigest` |
| B8 | **fixed (in scope)** — the scratch frame test adopted as `ExternalRestWithHostedSessionFrameTest` in both ci.yml lists; `CiFrameListsTest` (headless) checks the two lists are identical and complete; `ForeignTextIsPlainTest` treats `JEditorPane`, `text/html`, `HTMLEditorKit` and `setPage` as doors (HelpPanel REVIEWED), TranscriptView's `StyledEditorKit` pinned; `LocalAgentPermissionTest`'s oracle is a literal table. **Not fixed — out of scope:** the CLI-drift canary and the managed-settings probe (tracker) | `bcbe1eca`, `7b8e9d2f`, `3cc37a52` | ★ the frame test (witness: release always stops → "server not restarted"); ★ `CiFrameListsTest` (witness: one list short); ★ `ForeignTextIsPlainTest#htmlDocumentsAreDoors`; ★ `#thePolicyTable` (witness: `asks()` true for AUTO — the old oracle would have moved with it) |
| B9 | **fixed** — §4.2's dead `RestTransportObserved` / `RefreshHostedEndpointEffect` text deleted (and its §8.2 table row struck); §10's command-line row; the status block trimmed. **Split not done** by direction; proposed in the tracker | `61847e3f` | (spec) |
| B10 | **not fixed — out of scope:** `LocalAgentWiring` proposed in the tracker | `61847e3f` | — |
| B11 | **not fixed — out of scope:** OD-74.2 and the one-page security model, tracker line | `61847e3f` | — |
| L-a | **fixed** — the credential is checked before the hosted bucket | `08f2cef7` | ★ `HostedCredentialTest#aBadCredentialSpendsNoBucket` (witness: the old order → 429) |
| L-b | **fixed** — CHANGELOG: Copy prompt carries the token only with the tick | `18742a59` | (pinned in part by the S3 scan) |
| L-c | **fixed** — `spec.md`'s Explain line | `31ac363d` | — |
| L-d | **fixed** (documented) — the ask items' menu labels follow the assistant in force; read `context.menus` first. Stable target ids were not added | `31ac363d` | — |
| L-e | **fixed** — the fork warning shows only while a conversation exists to resume | `198e977a` | ★ `LockWarningTest` |
| L-f | **fixed** — §11 states it | `61847e3f` | — |
| L-g | **fixed** — CHANGELOG: automation must read `~/.fluxtion-analyser/rest-endpoint` | `18742a59` | — |

**Also, from the owner during this pass (2026-10-17):** the start page's *Or open directly* as titled blocks in a
responsive grid — `a984df85`, ★ `StartWorkspaceFrameTest#startChoicesGroupByActivityAndStackOnANarrowWindow` (witness:
the squashed FlowLayout strip → "a grid of at least 3 columns: 2"); screenshots in the scratchpad's `m74-ux-visual`.

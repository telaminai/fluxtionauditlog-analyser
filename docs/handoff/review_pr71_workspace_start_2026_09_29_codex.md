# PR #71: correctness and usability review

**Verdict: request changes.** Reviewed `ea4faa1099ee2dc715c16ffe496bd3c9a6fe3a9d`, including all five commits against its main base. The automated checks are green, but the new opening path can replace a newer project, essential navigation disappears, and several advertised controls are unusable at narrow widths. No application code was changed or merged.

Evidence is **RAN** for executed probes and visual inspection, **READ** for source inspection. Paths below are relative to `src/main/java/telamin/fluxtion/audit/analyser/analyser/` unless stated otherwise. Diagnostic sources and inspected screenshots are in [the evidence directory](evidence/pr71-workspace-start-2026-09-29/README.md).

## Required corrections

### R1 — A completed bundle open overrides a newer project request (P1, RAN)

`ui/MainFrame.java:1233` sends an unversioned background result straight to `openUnpackedExperiment`; `:1264` turns that old result into a new explicit project switch.

**Input → wrong result:** begin opening a valid DEMO bundle A; select project B before A's completion reaches the EDT. A subsequently replaces B and loads its log. The probe used the real pack/unpack path, deliberately held the EDT while the background extraction completed, then submitted B before returning to the event queue. Output: `newer project survived=false`, `active is bundle=true`. No production method or source was replaced.

`requestProject` does use the session processor, but it receives this late callback as fresh intent. The processor cannot reject an obsolete request whose original identity it was never given. The failure callback can likewise produce an obsolete error dialog (READ, not separately provoked).

**Fix:** model bundle-open intent, preparation completion/failure and supersession in the generated session processor; adapters perform verification/unpacking and report the original request identity. Apply only the completion the session still accepts. Do not patch this with another independent frame boolean. The new `pendingSampleWalk`/`pendingSampleTour` orchestration (`:1159`) also deserves routing through that same ownership model, rather than expanding it to cover bundles.

**Regression:** a deterministic preparation barrier; request A, request B, release A; assert B's profile/log/graph and playback remain unchanged, with no stale success/error dialog. Cover two bundles and a bundle followed by a normal open. Register a wrong-result control removing the session's stale-completion rejection.

### R2 — Start hides the navigation a returning user needs (P2, RAN)

`ui/StartPanel.java:188,214,243`; `ui/MainFrame.java:1116,1136`.

**Two reproductions:**

* Fresh settings, an existing project absent from recents → no “Open project” action. The empty-recents text tells the person to use Project, but the menu bar is hidden. Going through “Author a new project” and discovering its existing-profile fallback is not a usable replacement.
* Open a DEMO profile without a log, then Help → Start page → the old “Your project” card replaces the promised choices. Its only visible actions are Open audit log, topology, design, diagnostics and New project. There is no return action and no menu. The return row was added to the other card; the project card adds Back only when a log exists.

This also prevents an existing-project user from reaching the guided tour or bundle chooser through Help → Start, even when a log exists and Back is available.

**Fix:** make explicit Start always show the shared choices, with an unconditional “Return to workspace” when a workspace exists; include “Open project…” for an unlisted profile. Keep project declarations in the workspace, or make them an optional section rather than a replacement page.

**Regression:** show and lay out real frames for empty settings, project-only, graph-only, design-only and project-with-log. Click Help → Start and Return; assert visible actions, unchanged active evidence, and a working existing-profile chooser. A component merely present under a hidden card is insufficient.

### R3 — Native drops depend on which part of the start page they land on (P2, RAN)

`ui/MainFrame.java:2120` installs handlers on ancestors; `ui/StartPanel.java:448` uses text components with their own Swing drop targets.

**Input → wrong result:** native Robot drag of a DEMO audit file from a separate file-list drag source onto the hero's explanatory text → `logOpened=false`. This failed twice in the confirmation run with native completion action 0 (NONE). Drag the same file onto the Open audit log card → `logOpened=true`. The text has `BasicTextUI.TextTransferHandler` and a `SwingDropTarget`; the card has neither, allowing the ancestor handler to receive the drop. This is native drag routing, not `importData` invoked on a chosen ancestor.

**Fix:** forward file-list drops from the actual child targets, or provide a prominent reliable drop zone and scope the promise to it. Preserve text-transfer behaviour where needed. Add visible accepted-file guidance; the start page currently says nothing about dropping.

**Regression:** a native drop witness onto text, card and background, with a successful control proving the environment can deliver a drag. Keep the four file-kind handler tests too; they test routing after delivery, not delivery itself.

### R4 — A rejected mixed drop gives no visible explanation (P2, RAN)

`ui/MainFrame.java:2142`, together with `:1116`.

**Input → wrong result:** on the empty start page, drop an `.fexp` together with an audit log. Nothing opens, correctly, but the only explanation is written into the hidden status bar. Probe: `mixed accepted=false`, message “Drop an .fexp by itself…”, `mixed message showing=false`. The same hidden surface carries “Verifying and opening…” before a bundle loads.

**Fix:** give the start page its own visible progress/refusal region rendered from the operation result, or use an appropriate dialog. Keep the all-or-nothing mixed-drop refusal.

**Regression:** assert the refusal is showing with non-empty visible bounds and both active evidence and profile unchanged; also assert visible pending feedback for delayed verification.

### R5 — Compact walk actions lose their More menu (P2, RAN)

`ui/WalksPanel.java:73,105`.

**Input → wrong result:** display a saved DEMO walk in a 220-pixel-wide panel, representative of the right pane at the default frame size. FlowLayout wraps More below the single-row preferred height. Its bounds are `x=4,y=27,width=72,height=23` inside a toolbar of height 27; visible height is **zero**. Rename, Delete and Restore disappear. The fixed 180-pixel list divider also leaves the new step list almost unreadable.

**Fix:** use a toolbar layout whose height follows its rows, or put playback options in a compact menu without losing the management menu. Adapt the list/detail split at narrow widths.

**Regression:** size the real panel at 220 and 330 pixels, select a saved walk, assert every management action can be opened from an on-screen control, then activate one. A mutation restoring single-row FlowLayout must fail that visibility assertion.

### R6 — The new narrow series editor hides Add and Pick (P2, RAN)

`ui/GraphPanel.java:485–495`.

**Input → wrong result:** a 330×600 GraphTabs panel, empty selected graph → Edit series → Add series. Both Add and Pick have a **0×0 visible rectangle**. The scroll pane forbids horizontal scrolling but its ordinary JPanel view retains a preferred width larger than the viewport. The formula's right-hand controls are also clipped. Testing only vertical split orientation does not establish that editing works.

**Fix:** make the form track viewport width and reflow its controls; do not simply suppress the horizontal scrollbar. Keep the plot/editor vertical arrangement at narrow widths.

**Regression:** in a displayed narrow panel, select a DEMO key and click the visibly reachable Add button; assert the resulting series. Assert Pick and formula resolution controls have non-empty bounds inside the viewport. Revert width tracking/reflow as a named wrong-result witness.

### R7 — Producer finding layout hides its code and wastes its reading width (P2, RAN)

`ui/ProducerFindingsPanel.java:84,101,115,132`.

**Input → wrong result:** render WARNING / `DEMO_RULE` with a message, reason, fix and XML declaration at 220 pixels. `DEMO_RULE` disappears when the header wraps without growing; its measured visible height is zero. Mixed default-centred JPanel alignment and left-aligned text in BoxLayout gives the message only **72 pixels**, offset 81 pixels inside its card. At 450 pixels the text still starts halfway across the card. Screenshots reproduce this in light and dark themes.

**Fix:** use consistent left alignment and a header/action layout that genuinely wraps or stacks. Preserve the finding code as readable text, not only data remaining in a JLabel.

**Regression:** assert code, reason and fix are visibly readable at sidebar widths after layout, including a long code and a secondary action. The existing test checks strings/components, which passes while their contents are clipped.

## Optional improvements and concrete alternatives

**O1 — Choice hierarchy and terminology** (`ui/StartPanel.java:108–140`, RAN/READ). “Load an experiment” and “Investigate an incident” overlap; the latter is an extra dialog choosing the same bundle/log entrances. Use **Open evidence bundle (.fexp)** and **Open audit log** as concrete actions. Group **Take the DEMO tour** and **Explore the DEMO project** together so their relationship is explicit. “Create project profile” accurately distinguishes the empty authoring action from “Create from template”. Put recents and Open project near the top for returning users. Put optional assistant connection alongside starting work, ahead of compilation-key setup; viewing evidence needs no compiler key. A separate key card above assistant setup currently overemphasises compilation.

**O2 — Teach through what is actually painted** (`ui/DemoTour.java:17–30`, RAN/READ). All four stops reached SHOWN through ordinary walk navigation. However, the long instructional sentences are step captions; the overlay paints target captions and the walk title, not those sentences. The person sees “The processor graph”, not the explanation about coverage. Move essential instructions into visible target captions. At 1200×800 the second stop's topology is tiny, its coverage callout meets the bottom strip, and the last stop lights the Reports tab while the Investigation reports category is empty. Finish by selecting Spotlight walks and the saved tour, with a visible “Replay here” instruction. Consider one actual chart/assistant-led question in the four stops so the introduction teaches an investigation, not only pane names. Check the painted content; SHOWN is not a measure of instructional usefulness.

**O3 — Template picker sizing** (`ui/TemplateProjectDialog.java:177`, RAN). The local fixture produced an **872×532 non-resizable** modal. Its side-by-side explanation is clear on this display, but a narrow usable desktop or enlarged font cannot resize/reflow it. Cap to usable screen bounds and permit resize, switching to a vertical layout when necessary. Test bounds and access to both decision buttons at the smallest supported desktop. I did not emulate a physically smaller monitor.

**O4 — Icons and scales** (`ui/StartPanel.java:706`; `ui/ChartPanel.java`, RAN/READ). The vector icons remain legible in both themes and labels carry the meaning; the generic key glyph for assistant connections looks like a credential requirement. Prefer a connection symbol there. Round chart ticks and the guarded zero inclusion worked for the exercised positive, negative and high-baseline cases; the displayed DEMO plot had legible round ticks. Consider an explicit UTC indicator beside short time labels. I did not find a new numerical wrong-result in the tested ranges.

**O5 — Ignored-file detail** (`ui/MainFrame.java:2174`, READ). The multi-file message says additional files were ignored, but does not name them as the workspace-start spec promises. List ignored basenames and reasons, especially when two logs are dropped. Add a two-log fixture asserting the second filename in visible feedback.

## Verification and limits

Darwin 25.5.0 arm64; Corretto **21.0.8**. Clean detached worktree at the reviewed head; separate temporary homes; display runs serialized with the shared display lock. No participant data, provider, key access, regeneration or product changes.

Counts are **total / failures / errors / skips**:

| Own command/check | Result |
|---|---|
| `mvn -o -q test` | **2849 / 0 / 0 / 176**, 381 source-mapped XML reports, **0 orphans** |
| `mvn -o -q package -DskipTests` | exit 0 |
| `python3 tools/test_start_workspace.py` | exit 0; **10 / 0 / 0 / 0**, 3 reports |
| Six neighbouring frame classes, each in its own Maven invocation | **38 / 0 / 0 / 0**, 6 reports |
| `mkdocs build --strict` | exit 0 |
| `git diff --check origin/main...HEAD` | clean |
| CLAUDE rule-one tracked-file and review-addition sweeps | no matches |

The six classes were AsyncOpenInterleavingFrameTest **12**, ChartLifecycleReviewFrameTest **11**, TemplateCatalogueFrameTest **1**, ReportRecoverableDeleteFrameTest **6**, NamedGraphAndMenuSpotlightFrameTest **7**, WalkVerbFrameTest **1**. Each used `mvn -o -q test -Dtest=<class> -Djava.awt.headless=false -DargLine=-Djava.awt.headless=false`. The headless run also executed SwingUiPolishTest, ChartTickPolishTest and the other non-display tests. Display counts are separate reruns, not additional independent headless cases.

Own probes additionally exercised a real shown frame, fresh settings, project-only return, native file drop, all four tour steps, a delayed bundle completion, mixed-drop refusal, keyboard Tab/Shift-Tab/Space/Right, both themes and widths down to 220 pixels for individual panels. `.fexp`, GraphML, audit log and authorised Spring XML routing passed the supplied display tests; an ungranted XML probe was eventually refused without adding a source root. Native OS delivery was independently tested with the audit log, not all four extensions. Keyboard Tab selected the next card, Space started the tour, and Right advanced to step 2. Tour mouse stepping in the diagnostic probe deliberately targeted the overlay directly; it is not offered as native routing evidence.

Read the framework reference, SessionDriver, generated WalkPlayRequested/WalkNavigated dispatch, WalkPlayback, MainFrame's project and sample paths, and the changed UI paths and corresponding tests. Ordinary playback and project application still reach generated decisions. R1 is the missing ownership of the asynchronous entrance before that decision, not a claim that `requestProject` bypasses the processor.

`gh pr view 71 --json headRefOid,statusCheckRollup` showed the reviewed head and all checks successful: CI **36557140865**, including build, ui-frame, self-test, four shards and collector. I did **not** download/recount CI mutation evidence or run a local mutation gate/full registered display gate. Green CI does not cover the native-drop or visible-layout counterexamples above.

Screenshots in this review are inspected **Swing render captures of displayed isolated frames**, not a claim of native desktop capture. Initial sandbox GUI launches aborted before running probes; subsequent authorized display launches completed. An early surface-probe compile omitted the test fixture classpath and was corrected. An immediate-resize screenshot briefly lost wrapped cards; after layout settled all cards were reachable, so that is **not** a finding. A repeated visual probe reused its disposable home; it was rerun under a fresh unique home before judging first-run behaviour. These probe corrections are not hidden product-test retries.

Not verified: native file-manager drags on other OSes; screen-reader output; high-DPI/large-font accessibility; network template acquisition/download; all chart numerical extremes; a user study of the proposed wording. Outdated documentation screenshots remain the already-known author work, not a newly discovered finding. No merge performed.

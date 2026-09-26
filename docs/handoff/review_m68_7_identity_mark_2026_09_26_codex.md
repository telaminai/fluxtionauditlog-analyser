# M68.7 identity-mark review — 2026-09-26

**Verdict: not mergeable yet.** The implementation renders the intended shared warning on the live panels, and all requested gates pass. One required correction remains in its acceptance: the new tests stay green when the chart banner is removed from the component tree. This is precisely the invisible-mark regression the acceptance must catch. The report-export policy needs an owner clarification before G14, separately from this test correction.

Subject: PR #39, `844462fe`, against `14d04a2f`. Reviewed in an isolated worktree with JDK 21 on macOS. **RAN** means executed here; **READ** means source/document/CI-artifact inspection, not a reproduced trial. Earlier M68 work is not re-reviewed. No implementation change is included in this review.

## Required correction

### R1 — the acceptance cannot distinguish a displayed chart mark from an unattached label

**RAN.** `src/test/java/telamin/fluxtion/audit/analyser/analyser/ui/IdentityMarkFrameTest.java:40–76` and `IdentityMarkSurfacesTest.java:40–61`; production attachment: `GraphTabs.java:175`.

Concrete control: remove only `north.add(identityBanner, BorderLayout.SOUTH)` from `GraphTabs`. The label still receives its text and `setVisible(true)`, but has no parent and cannot appear above a chart. Both new test classes remain **4 / 0 / 0 / 0**, exactly like the baseline. Restoring the original bytes returns **4 / 0 / 0 / 0**. The byte-copy SHA and counts are preserved in [results.json](evidence/m68-7-review-2026-09-26/results.json).

The frame fixture never calls `setVisible(true)`; my probe printed `originalHarnessShowing=false`. `identityNote()` tests the label's own visible flag, not `isShowing()`, parentage or visible bounds. The headless panel test has the same limitation. Therefore “run on a real display” does not establish an actually visible mark in this test.

**Correction:** show and lay out the real frame, select a chart, and assert that the banner belongs to the chart area and has nonempty visible bounds within the displayed area. Assert the detail mark similarly when that pane is shown. Add a named control removing the parent attachment; it must fail the visibility assertion. Retain the existing snapshot/text controls. A pixel or screenshot check can strengthen this, but the essential missing check is screen reachability, not another string assertion.

## Questions 1–7

### 1. One verdict, one rule

**READ; selected paths RAN.** `MainFrame.onSessionSnapshot` (`4428–4435`) sets all three banners from the same `next.logIdentity()` and `next.logIdentityReason()`. Both new text helpers delegate the decision to speak to `LogTablePanel.identityBannerText`; they customize only the explanatory suffix. There is no new file check or verdict cache.

Lifecycle trace:

- Close posts `LogCleared` or answers the operation with `LogClosed`; `OpenLog` clears identity and the snapshot clears the marks. **RAN** close after a changed verdict: both new marks cleared.
- Accepted opens, whether another log or the same path, pass through `LogOpened`; that clears the old identity or records `REOPENED` after replacement. **RAN** reopening: all marks cleared.
- Project transitions use the existing close/open effects and the same facts. **READ**, not an additional project-switch trial.
- Follow on/off alone does not change the identity verdict. Poll observations publish through `reportIdentityToSession`; a replacement publishes before the reload, and successful reload moves to the new generation. **READ**. An identical verdict is intentionally not republished.
- The generated processor handles `LogIdentityObserved` through `openLog`; `SessionDriver` builds and publishes the snapshot after the operation and guards against delivering an older snapshot after a reentrant publication. **READ**, including generated source. This PR changes none of that dispatch.

No new lifecycle path bypassing the snapshot was found. This is a composition check, not a claim that all pre-existing observation races were re-audited. The supplied `docs/claude.txt` path is absent at this head; I read the checked-in framework orientation and the relevant generated handler/driver source rather than inferring propagation.

### 2. Every open chart

**READ.** Live `GraphPanel` instances are created by `GraphTabs`; later tabs remain under the same banner. The Project panel navigates to them rather than making chart copies. I found no detached live-chart window or topology chart overlay.

Other picture surfaces do exist: `GraphPanel.renderForReport`, standalone series pictures from `ReportSeriesPicture`, and the chart's image export via `ChartPanel.toImage` (`GraphPanel.java:1696`). These do not include the containing `GraphTabs` banner. Thus “every open chart tab” is supported; “every exported chart picture” is not. The report exclusion is stated, but image export should be named alongside it for completeness.

### 3. Wording and store types

**READ.** The new wording makes the appropriately limited claim that the displayed values are not verified against the current file. It does not claim that the old values are necessarily wrong.

- Heap retains the earlier text; its change reason explains that retention.
- Mapped storage reads through the open channel. Metadata indicating an in-place rewrite suspends dispatcher reads; atomic replacement can leave the old channel readable.
- Rolled storage contributes the affected member and position to the reason. The banner preserves that reason rather than independently judging members.
- A default plugin reader reports `readThroughAssessed=false`; absent an observed verdict, it produces no banner. That is right for this change-warning rule: `not assessed` is not evidence of a replacement. Context still discloses `not assessed`; no banner must not be interpreted as positive verification.

The helper tests exercise null/VERIFIED/REOPENED and UNVERIFIED/REPLACEMENT. The latter wording is not an unconditional freshness guarantee.

### 4. Frame witness and real triggers

**RAN + READ.** Reflectively invoking `observeReadIdentity` is a fair seam for testing publication after observation; it is not itself a record read. The production dispatcher calls this observer before record-reading verbs, and window activation uses it too. My additional shown-window probe used the real `ActionDispatcher` read route: the mapped rewrite was refused and all live marks appeared.

The first wait polls the session verdict through EDT tasks with a bounded deadline; it is not a blind delay. The reopen wait polls the Swing label off the EDT, however, and should move that read onto the EDT. The source-reading test pins the two snapshot setter expressions, while the four controls exercise removal of setters, visibility and the shared rule. They are meaningful but do not close R1: text and an unattached label can satisfy all of them.

The docs' phrase “a record read” overstates the committed frame test's reflective trigger. Either use the dispatcher in that test or describe its seam precisely.

### 5. Prediction miss

**READ + RAN.** `ReadThroughIdentity.classify` (`47–62`) returns UNVERIFIED when the same file key has changed size/mtime without shrinking: indexed locations may no longer describe the bytes. It reserves the relevant REPLACEMENT cases for a changed key, missing file or truncation. My real-dispatch probe observed **UNVERIFIED**, matching the recorded correction. The existing three-test `MappedLogStoreReadIdentityTest`, including `atomicReplace`, passed in the full run. My additional atomic-replacement step also kept the chart marked.

The author's prediction commit `fce34506` precedes `844462fe`, and RESULTS records the wrong prediction rather than hiding it. My own predictions were written before trials and are preserved as such; they were not separately committed before execution.

### 6. Reports and G14

**RAN + READ; owner decision before G14, not a demand to expand this implementation silently.** I checked PR #37's protocol at `581f36f1`, especially pass condition 3 (`PROTOCOL.md:100–103`). It accepts a chart **or report**, then uses absence of a superseded-content mark as evidence of current content.

The actual routes matter:

1. Same-length in-place mapped rewrite: the real dispatcher refuses the report request. This proposed bypass does **not** succeed through that route.
2. Atomic replacement: retained data remain readable, so export succeeds. The response contains `identityNote`, but the new chart banner is absent from the PDF picture.
3. The PDF is nevertheless **not entirely unmarked**: its existing header and footer say `loaded snapshot; log changed-on-disk`, from `snapshotNote()` and its metadata observation. I rendered and inspected the one-page PDF and extracted its text. See [the artifact](evidence/m68-7-review-2026-09-26/retained-report.pdf) and [text](evidence/m68-7-review-2026-09-26/retained-report-text.txt). My suspicion of a wholly unqualified exported artifact was narrowed by this result.

The existing PDF note is a separate metadata observation, not the session's verdict and reason. The protocol must not treat absence of the **new** banner as proof of freshness on this surface. Before G14, explicitly choose either: restrict qualifying evidence to the marked live chart surface; require report qualification/refusal from the session; or specify how the report's existing note and the action reply are evaluated. The owner accepted partial delivery, so this scope choice belongs to them. The probe does not establish that G14 would pass incorrectly; no G14 trial was run.

### 7. Layout and neighbouring behaviour

**RAN + READ.** The requested neighbouring tests passed, including chart lifecycle, menu screenshots and named-menu spotlight tests. The registered display gate also passed with zero skips. Nesting the toolbar and label did not break those tested component lookups or geometry paths.

A shown 1200×800 frame produced a chart banner width of **192 px** for **1,400 px** preferred text; its painted string was `⚠ the opened file changed in ...`. The detail banner was **606 px** wide for **1,340 px** preferred text. Full wording remains in tooltips. This is a visible warning but not the whole explanatory sentence; see optional O1.

A theme switch exposed O2: the explicit warning color is chosen only at construction. It remains the light palette's color after updating the component tree to dark and setting the note again.

## Optional items

- **O1 — make the short visible warning self-contained.** `GraphTabs.java:58–62` / `DetailPanel.java:73–77`. **RAN.** At default narrow widths the reason consumes the line and the chart/record qualification plus recovery action are clipped. Put a short verdict first, or use bounded wrapping with the full reason in a tooltip. Keep this bounded so a long reason cannot consume the chart's height.
- **O2 — refresh the explicit warning color on theme change.** `GraphTabs.java:172`, `DetailPanel.java:114`. **RAN for the chart; READ for the identical detail setup.** Light → Dark leaves `ffb04020` instead of `UiTheme.warnForeground()`'s `ffe87a5a`, even after setting the note. Recompute when rendering/updating UI. This is a new use of the existing color pattern, not a request to re-audit the table.
- **O3 — tighten evidence descriptions and the reopen wait.** The reflective frame test observes identity without reading a record, never shows the frame, and reads the label off-EDT during its reopen wait. Correct the descriptions or improve the test while resolving R1. Name image export as well as PDF in the exclusions.

## Gates and controls actually run

Totals are **tests / failures / errors / skips**. All display invocations were sequential.

| Check | Result |
|---|---|
| Initial `mvn -q clean test` in sandbox | 2397 / 0 / 29 / 109; 323 reports. All 29 errors were socket-operation permission failures. Preserved, not counted as a green run. |
| Same command with socket access allowed | **2397 / 0 / 0 / 109**, 323 reports, **no orphan reports** against `src/test/java`. |
| Requested six neighbouring suites with both headless=false flags | **26 / 0 / 0 / 0**, six reports. |
| `verify_project_chart_review.py --mode display` | **109 / 0 / 0 / 0**, 22 suites; the focus case did not skip here. |
| Four M68.7 controls plus two M68.5 controls, fast engine | **6/6 caught**, 21.9 s; green baseline, named assertion failure, source/class byte restoration, restored green. |
| `--mode preflight` | **22 frame suites, 165 anchors**. |
| Additional real-window/dispatcher probe | **1 / 0 / 0 / 0**. |
| Remove chart-label parent attachment | Baseline **4 / 0 / 0 / 0**; mutant **4 / 0 / 0 / 0** (**survived**); restored **4 / 0 / 0 / 0**. Byte copy restored; `cmp` matched. |
| Final pre-commit `mvn -q clean test`, after restoring probes | **2397 / 0 / 0 / 109**, 323 reports, no orphans. |
| `mkdocs build --strict`; diff whitespace; public-data sweeps | Clean. |

The neighbouring invocation used `-Dtest=IdentityMarkFrameTest,IdentityMarkSurfacesTest,LogTablePanelIdentityBannerTest,ChartLifecycleReviewFrameTest,MenuScreenshotFrameTest,NamedGraphAndMenuSpotlightFrameTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.awt.headless=false -DargLine=-Djava.awt.headless=false`.

Control IDs: `m68-7-charts-not-rendered`, `m68-7-detail-not-rendered`, `m68-7-chart-banner-hidden`, `m68-7-chart-text-bypasses-rule`, `set13-a-table-banner`, `set13-a-rendered-from-snapshot`. The two existing controls' definitions/anchors are unchanged in the PR diff. No full local mutation gate was run.

**CI READ:** run `36264170859` at the pinned head is green. The UI log reports **109 / 0 / 0 / 0**; the mutation log reports **165 caught in 706.7 s**. Build, loop-bench and static checks are green. These are CI results inspected independently, not local timings.

## Evidence, predictions and remaining limits

[Predictions](evidence/m68-7-review-2026-09-26/PREDICTIONS.md), [counts and restore evidence](evidence/m68-7-review-2026-09-26/results.json), [probe](evidence/m68-7-review-2026-09-26/M687ReviewProbeTest.java), [probe output](evidence/m68-7-review-2026-09-26/probe-output.txt), and [reproduction instructions](evidence/m68-7-review-2026-09-26/README.md) are preserved. Misses: sandbox access prevented the first expected-green suite; the expected display focus skip did not occur; the PDF has an existing metadata warning even though it lacks the new banner. The suspected unattached-label survivor and narrow-text clipping were reproduced.

No new trial of Follow replacement/project switching/plugin readers was performed; those lifecycle paths were inspected and existing suite baselines ran. The window-focus trigger was inspected; the additional trigger trial used the real dispatcher. No hosted provider, key, LLM session, participant project or G14 run was used. Screenshot inspection used a live frame painted into an image, not a native desktop capture. Existing M68 limitations, exported-image freshness policy and metadata-preserving rewrites are not reopened here.

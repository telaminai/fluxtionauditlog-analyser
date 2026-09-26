# M68.7 focused correction check — 2026-09-26

Subject: PR #39 at `97fa0b46`; fix diff `844462fe..de994b35`. Reviewer: Codex, author of review `34f4d800`. This checks that review's corrections only.

## Predictions recorded before trials

- The three targeted classes pass: 5 / 0 / 0 / 0, 3 / 0 / 0 / 0 and 1 / 0 / 0 / 0.
- Removing only either banner's parent attachment fails the named on-screen assertion, without an error or skip; byte restoration returns green.
- All nine requested controls are caught. Preflight finds 22 frame suites and 174 anchors.
- Full headless suite: 2414 / 0 / 0 / 111, 325 reports, no orphans. Registered display: 111 total, no failures/errors, with the focus-only skip possible.
- The short warning exposes “not verified” at 1200×800; the colour updates and the test restores the prior look and feel.
- The visibility helper rejects zero clipped area but is not a general sibling-occlusion/pixel oracle. Inspect actual layout before treating that theoretical limitation as a correction.
- G14's report decision remains open; implementation remains limited to live marks.

These predictions were recorded in the review worktree before execution, not separately committed before trials.

## Verdict

**Verdict: mergeable.** R1 is closed. No new required correction was found. G14's report-policy decision remains an owner dependency before that trial, not a reason to reopen M68.7's accepted live-panel design.

The fix and merge were inspected separately. All local trials below used `97fa0b46` in a separate detached worktree; only this addendum is committed on the existing review branch. No subject code or preserved review evidence was changed.

## Disposition of items 1–10

| Item | Disposition and evidence |
|---|---|
| **1. On-screen acceptance** | **Closed — READ + RAN.** `IdentityMarkFrameTest:89–112` shows the frame at 1200×800, awaits showing, selects the chart side tab and validates. `assertOnScreen` (`62–74`) requires surface/label showing, descendant membership, positive visible width/height and positive intersection with the surface. Both panes and the later chart use it; reopen checks both labels are no longer showing. Zero clipping fails. It does **not** detect arbitrary opaque sibling/window occlusion: Swing visible rectangles are not pixel visibility. The inspected BorderLayout places the toolbar and banner in distinct NORTH/SOUTH regions; no covering sibling was introduced. This is a limit of the helper, not a new defect found in this delta. |
| **2. Detach witnesses** | **Closed — RAN by hand and through the registered controls.** Removing only the GraphTabs attachment fails **1 / 1 / 0 / 0** at “the chart area banner must be showing…”. Removing only DetailPanel's attachment fails **1 / 1 / 0 / 0** at “the detail pane banner must be showing…”. Each started **1 / 0 / 0 / 0**, restored from a byte copy with `cmp`, then returned **1 / 0 / 0 / 0**. This closes the exact survivor from review 34f4d800. |
| **3. Real trigger** | **Closed — READ + RAN.** `IdentityMarkFrameTest:121–128` calls `ActionDispatcher.dispatch` with `read` and asserts refusal. The reflective observer is gone. RESULTS explicitly corrects its earlier historical “record read” wording; current tracker/spec descriptions match the dispatcher path. |
| **4. EDT and waits** | **Closed — READ + RAN.** `awaitOnEdt` (`49–57`) evaluates the condition on the EDT with a monotonic deadline; sleep is only between condition checks. Reopen waits for all three notes to clear on the EDT before checking screen disappearance (`173–183`). Component state reads are on the EDT; off-EDT field access merely obtains the panel references, and the session snapshot is the existing published immutable value. |
| **5. Retained controls** | **Closed — READ + RAN.** All six retained controls are caught with named failures and restored green. Removing only the chart helper's shared-rule guard is the same fault as before: it fabricates a chart note when the table's rule returns null. It still fails `theSurfacesShareTheTablesRule` at “the charts say nothing for null”. The two table-control definitions are unchanged. |
| **6. Short verdict** | **Closed — READ + RAN.** `GraphTabs:63–69` and `DetailPanel:79–84` lead with “Charts/Record not verified…” and recovery, then the reason. The full note remains the tooltip. The 1200×800 frame test's `layoutCompoundLabel` checks pass for “not verified” in both clipped strings. The recovery sentence need not fit in a narrow pane; the short verdict does. These remain one-line JLabels, so long reasons do not add rows. |
| **7. Theme colour** | **Closed — READ + RAN.** Both labels override `updateUI`, call super and recompute `UiTheme.warnForeground()`. `theWarningColourFollowsTheTheme` (`IdentityMarkSurfacesTest:96–122`) restores the prior look and feel in `finally`, including failure during the EDT block; colour assertions occur after restoration. The colour control fails the named chart colour assertion and restores green. |
| **8. Exclusions and G14** | **Correction closed; owner decision still open — READ.** The spec, tracker and RESULTS name standalone images as well as report/PDF exclusions. They retain all four route distinctions: mapped in-place rewrite refuses report requests; atomic replacement permits a qualified reply; the PDF has an existing changed-on-disk header/footer but no M68.7 banner; that note is metadata, not the session verdict. No report-path code changed and G14 is not declared unblocked. No repeat export/provider trial was run. |
| **9. Merge** | **Closed — READ + scripted checks RAN.** Remerge-diff shows the CHANGELOG conflict resolution plus the deliberately added after-merge RESULTS section. Against main, CHANGELOG has additions only under Unreleased; every released section is byte-identical. Both CI frame lists are identical, ordered lists of **22**, including IdentityMarkFrameTest. Main's own changes were not re-reviewed. |
| **10. Evidence honesty** | **Closed — READ.** History orders 69f74818 before de994b35. RESULTS preserves 168 rather than predicted 167 anchors, explains the extra colour control, records the case-sensitivity assertion failure and both local focus skips. Its merged-tree count correctly becomes 174. This review's original report/evidence remain unchanged. The author's historical reds/skips were inspected as reports; my independently rerun results are below. |

## Checks actually run

macOS, JDK 21; display runs sequential. Counts are **tests / failures / errors / skips**.

- `IdentityMarkSurfacesTest`: **5 / 0 / 0 / 0**; `LogTablePanelIdentityBannerTest`: **3 / 0 / 0 / 0**; real-display `IdentityMarkFrameTest`: **1 / 0 / 0 / 0**. Maven targeted runs used `-Dtest=<class>`; display runs passed both `-Djava.awt.headless=false` and `-DargLine=-Djava.awt.headless=false`.
- Both manual detach controls: named **<failure>**, no error/skip, exact restore and green rerun, as item 2 records.
- Fast engine, **nine requested cases only: 9/9 caught in 48.5 s**, with green baseline, named assertion failures, byte-identical source/class restores and green reruns. Included the four original M68.7 cases, both detach cases, the colour case and both M68.5 cases. No full local mutation gate.
- Preflight: **22 frame suites / 174 anchors**.
- `mvn -q clean test`: **2414 / 0 / 0 / 111**, **325 reports, no orphan reports** against `src/test/java`.
- Registered display gate: **111 / 0 / 0 / 0**, **22 suites**. No focus skip occurred here; the author's two skips are not relabelled as passes.
- `mkdocs build --strict`, subject diff whitespace and exact tracked-file public-data sweep: clean. All production-source mutations were restored; the subject worktree is clean.

**Prediction results:** all specified counts and named control failures matched. The optional focus skip did not occur. The visibility helper's sibling-occlusion limit is as predicted and is reported without upgrading it into a product failure. No extra product trial, key, provider, client session or participant-project access occurred.

## CI

**READ from the new run, not the old head:** [run 36269296444](https://github.com/telaminai/fluxtionauditlog-analyser/actions/runs/36269296444) at `97fa0b46` completed successfully. The downloaded `ui-frame` log reports **111 / 0 / 0 / 0** and IdentityMarkFrameTest **1 / 0 / 0 / 0**. The downloaded mutation log reports **174/174 caught in 772.2 s**. Build, loop-bench and static checks are also green. CI was still running at the first check; it is not pending in this verdict. No merge, rebase, release or deployment was performed.

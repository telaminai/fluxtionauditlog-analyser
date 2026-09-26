# M68.7 — results against the sealed predictions (2026-09-26)

Predictions: `PREDICTIONS.md`, committed first (fce34506). JDK 21, macOS.

| # | Prediction | Result |
|---|---|---|
| 1 | Headless text and panel test passes | **Held.** `IdentityMarkSurfacesTest` 3/0/0/0; `LogTablePanelIdentityBannerTest` unchanged, 3/0/0/0 |
| 2 | Frame test on a mapped log: all three surfaces marked, later chart marked, reopen clears; verdict **REPLACEMENT** | **Held, with one miss.** `IdentityMarkFrameTest` 1/0/0/0 on a real display. The session reported **UNVERIFIED**, not REPLACEMENT: an in-place same-length rewrite suspends reads ("the opened file changed in place … reads are suspended until the log is reopened"). The test asserts a changed verdict, so it did not depend on the guess. Observed with a temporary print, then restored from a byte copy (`cmp` identical). |
| 3 | Four controls caught at named assertions, restored byte-identical | **Held.** See the table below. |
| 4 | Suites: 22 registered frame suites, headless green | **Held.** Headless 2397 / 0 / 0 / 109 over 323 reports, no orphans. Display 22 suites, 109 / 0 / 0 / 1 — the one skip is `PersonAtTheScreenFrameTest`'s focus-guarded case, which this machine cannot give focus (CI's Xvfb runs it). Mutation gate 165/165 in 426 s. Preflight 22 frame suites, 165 anchors. |

| Control | Named assertion that went red |
|---|---|
| `m68-7-charts-not-rendered` | "every surface that can show a value states the verdict" → "the charts must state the verdict before G14 can pass on them" |
| `m68-7-detail-not-rendered` | same group → "the detail pane must state the verdict" |
| `m68-7-chart-banner-hidden` | "the chart banner must show its note" |
| `m68-7-chart-text-bypasses-rule` | "the charts say nothing for null" |

**Operational slip, recorded:** the verdict probe's command stopped on a zsh glob error before its restore step ran.
The restore was then run by itself, and `cmp` confirmed it byte-identical, with no probe line left in the file.

## Review fixes (review 34f4d800) — results against `PREDICTIONS-R1.md` (69f74818)

**Correction to the first round's wording.** The first `IdentityMarkFrameTest` triggered the observation by calling
`observeReadIdentity` reflectively. That observes identity; it is not a record read, and this file's earlier row 2
should not have implied one. The test now uses the real `ActionDispatcher` `read`.

### R1: the mark must reach the screen
- **Cause.** The frame was never shown, and `identityNote()` reads the label's own visible flag. So a banner with no
  parent passed the test.
- **Witness before the fix (RAN).** Removing `north.add(identityBanner, BorderLayout.SOUTH)` from `GraphTabs`:
  baseline 4/0/0/0, mutant **4/0/0/0 (survived)**, restored 4/0/0/0, `cmp` identical. This reproduces the review.
- **Correction.** The frame is shown at 1200×800, the chart is selected, and the observation comes from a real record
  read through `ActionDispatcher`, which is refused.
  - `assertOnScreen` requires the banner to be showing, to descend from its surface, and to have non-empty visible
    bounds lying within that surface.
  - The same checks run for the detail pane, and for a chart opened after the verdict.
  - A reopen removes both banners from the screen.
  - Every Swing read, including the reopen wait, runs on the EDT through `awaitOnEdt`: a condition with a deadline,
    never a fixed sleep.
- **Regression (RAN).**
  - `m68-7-chart-banner-detached` fails at "the chart area banner must be showing on the chart area, not merely
    flagged visible".
  - `m68-7-detail-banner-detached` fails at "the detail pane banner must be showing on the detail pane…".
  - Both are green before, restore byte-identical, and are green after.

### Optional items
- **O1, taken.** Each note leads with a short verdict and recovery ("⚠ Charts not verified against the file on disk —
  reopen the log to redraw them · <reason>", and likewise for the record). The whole note is in the tooltip. The label
  is one line, so a long reason cannot take the chart's height. `theVerdictLeadsTheNote` covers the order. On the
  shown frame, the painted (clipped) text of both banners contains "not verified".
- **O2, taken.** Each banner recomputes `UiTheme.warnForeground()` in `updateUI()`. `theWarningColourFollowsTheTheme`
  checks Light → Dark on both banners and restores the previous look and feel. Control
  `m68-7-chart-colour-fixed-at-construction` fails at "…must take the dark warning colour after the switch ==>
  expected: <ffe87a5a> but was: <ffbbbbbb>".
- **O3, taken.**
  - The tracker and spec now describe the dispatcher trigger and the on-screen assertions.
  - A standalone chart-image export (`ChartPanel.toImage`) is named beside the report/PDF as an unmarked surface.
  - The reopen wait now runs on the EDT.

### The G14 report policy: recorded, not decided
The tracker's M68.7 entry, G14's order line and the spec's D-E6 note record four distinctions:
1. A mapped in-place rewrite makes the real dispatcher refuse report requests.
2. An atomic replacement allows an export, and its reply carries `identityNote`.
3. That PDF has no M68.7 banner, but its header and footer carry `log changed-on-disk`.
4. That note is a metadata observation, not the session's verdict and reason.

How G14 assesses its "chart or report" alternative is the owner's decision, needed before G14 runs. The report path is
not expanded, and G14 is not declared unblocked.

### Predictions against results
| # | Prediction | Result |
|---|---|---|
| 1 | The detach mutation survives the old test; the new test catches it at the visibility assertion | **Held** (both halves RAN) |
| 2 | The detail detach is caught | **Held** |
| 3 | The four M68.7 and two M68.5 controls hold; the text-bypass anchor moves | **Held**: 6/6. The bypass anchor is now the rule line itself; its target is unchanged |
| 4 | The dispatcher route gives UNVERIFIED, and the read is refused | **Held** |
| 5 | O1: the verdict leads, and the painted text contains "not verified" | **Held** |
| 6 | O2: the regression is red before and green after | **Held**, shown by its control |
| 7 | O3 wording corrected | Done |
| 8 | G14 policy recorded, not decided | Done |
| 9 | Preflight 167 anchors | **Missed: 168.** I added a third control, for O2, beyond the plan |

**Also missed.** The existing assertion `chart.contains("charts")` failed after O1 capitalised the lead ("Charts…").
That was a wording change, not a behaviour change. The check exists to confirm that the note names its surface, so it
is now case-insensitive, and the same was done for "record".

### Counts (JDK 21, macOS)
| Check | Result |
|---|---|
| `IdentityMarkSurfacesTest`, `LogTablePanelIdentityBannerTest` | 5/0/0/0 and 3/0/0/0 |
| `IdentityMarkFrameTest` (real display) | 1/0/0/0 |
| New controls | 3/3 caught: attach (8.2 s); detail detach and colour (10.3 s) |
| Retained controls | 6/6 caught (18.0 s) |
| `mvn -q clean test` | 2399 / 0 / 0 / 109, 323 reports, no orphans |
| Display gate | 22 suites, 109 / 0 / 0 / 1. The skip is `PersonAtTheScreenFrameTest`'s focus-guarded case, which this machine cannot give focus. Preserved as `display-attempt1`; CI's Xvfb run decides it |
| Preflight | 22 frame suites, 168 anchors |
| `mkdocs build --strict`, `git diff --check`, rule-one sweep | clean |

**RAN versus inspected.** Everything in the tables was RAN. **Inspected only:**
- the report-route distinctions, which come from the review's probe; I did not re-run it;
- the lifecycle paths the review traced (Follow replacement, project switch, plugin readers).

The full mutation gate was not run locally; CI runs it.

### After merging main (1.22.1 released, PR #35)
The PR conflicted with main, so GitHub ran no `pull_request` CI on `de994b35`. I merged main, without rebasing.
- **CHANGELOG, the only conflict:** main's file is kept whole, and this entry sits under the new `[Unreleased] ▸ Fixed`.
- **CI frame lists:** 22 suites in both, which agree.
- **Merged tree:**
  - preflight: 22 frame suites, 174 anchors (168 plus PR #35's 6);
  - all 9 M68.7 and M68.5 controls caught (32.2 s);
  - `mvn -q clean test`: 2414 / 0 / 0 / 111, 325 reports, no orphans;
  - display gate: 22 suites, 111 / 0 / 0 / 1, the same focus-guarded skip (second attempt, preserved).

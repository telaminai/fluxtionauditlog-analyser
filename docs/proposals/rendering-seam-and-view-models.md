# Proposal: a rendering seam, and view models the session tells what to show

**Status: PROPOSAL, not accepted (2026-09-27).**
- The lower half is prototyped in [PR #53](https://github.com/telaminai/fluxtionauditlog-analyser/pull/53) (`proto/chart-surface-seam`).
- The upper half is spiked on [`proto/view-model-nodes`](https://github.com/telaminai/fluxtionauditlog-analyser/tree/proto/view-model-nodes), with [predictions](https://github.com/telaminai/fluxtionauditlog-analyser/blob/proto/view-model-nodes/docs/handoff/evidence/view-model-spike-2026-09-27/PREDICTIONS.md) committed before code and [results](https://github.com/telaminai/fluxtionauditlog-analyser/blob/proto/view-model-nodes/docs/handoff/evidence/view-model-spike-2026-09-27/RESULTS.md) scored against them.
- The design came out of #53's review: the owner's clarification of "drawing nodes", recorded on [the PR](https://github.com/telaminai/fluxtionauditlog-analyser/pull/53#issuecomment-5856550681).
- Neither half is merged. The owner decisions are at the end.

## The problem, in the defects that found it

Two kinds of rendering defect keep recurring, and they need different tools.

- **The session was right and a surface stated something else.**
  - M44.5's W2: the Follow line dropped the provenance and the order warning, because it was composed a second time.
  - PR #51's #46: a report told to show 0 sections.
  - #47: a chart re-sent with notes silently dropped.
  - #50: a blank placeholder reported as a chart that "remains".

  These are about *what* an element was told, and *when*. Today nothing records that. The audit log records the session's transitions, not what any surface was shown.
- **The element was told the right thing and drew it wrong.**
  - PR #51's #48: the axis label placed below the component.
  - #49: the closing step not held to the window edge.

  These are layout inside one backend. They were witnessed by re-deriving the arithmetic in the test, which passes with the drawing deleted.

## Two layers

```
 session facts ─▶ state nodes ─▶ VIEW NODE ──▶ RenderXEffect(view) ──▶ backend ──▶ painter ──▶ Surface
                                  │  when: consistent? changed?          Swing, HTML,      layout    Graphics2D,
                                  └─ audit: the fields that changed      recorder          (metrics) SVG, recorder
              └──────────────── upper half: WHAT and WHEN ─────────────┘└──────── lower half: HOW (PR #53) ────────┘
```

### The lower half: the Surface seam (PR #53)

**What it adds:**
- `ui/render/Surface`: color, font, stroke, clip, font metrics and the drawing primitives;
- two implementations: `Graphics2DSurface` (the screen) and `SvgSurface` (a file a browser opens);
- a test-only `RecordingSurface` that records each mark with the clip it was drawn under;
- `PlotPainter` and `PlotGeometry`: the chart's plot drawn against a Surface, never a `Graphics2D`;
- `ChartPanel.renderTo(Surface, w, h)`: the one paint path both the screen and a test use.

A test asserts what was **drawn**: "a label's baseline is inside the component", "the last step reaches the window edge". It does not recompute the arithmetic behind the drawing.

**What review found, and what is fixed** (commits `96379547..ce2f1938` on the branch):

| finding | status |
|---|---|
| The delta's tests never drew the chart; they bypassed `renderTo` | fixed: `ChartRendersThroughTheSeamTest` draws through it |
| The recorder gave lines zero area, and the clip it recorded was always null | fixed: `RecordingSurface` gives lines area and records the clip per mark; `RecordingSurfaceTest` |
| The real surface's clip semantics were unasserted | fixed: `Graphics2DSurfaceFidelityTest` (`clip` replaces) |
| The legend glyph is drawn outside the seam | pinned: `PaintersNeverNameGraphics2DTest` names ChartPanel's only `Graphics2D` boundaries |
| Control characters in a label broke the SVG | fixed: `SvgSurface.escape` replaces XML-invalid characters |
| **The SVG has no legend** | open: it closes when the legend is an element (below) |
| **`toSvg` has no production caller** | open: owner decision 3 |
| The branch bases on #51's old head and has no CI (`ci.yml` runs only PRs into `main`) | open: rebase after #51 merges |

Fourteen `p53-*` mutation controls pin the seam's claims.

### The upper half: view models the session tells what to show

The owner's clarification:

> The drawing nodes in the session graph are about putting **proxies to drawing elements**, so we can record in the audit log what they are told to draw. The graph pushes the state and tells them to redraw — not each line, but some abstraction. Then different drawing backends can register: test, headless, Swing, HTML.

**Design points** (from the #53 discussion; the spike confirmed each one):
1. **Declarative view models, not commands.** Each element gets an immutable record of what it states. Commands would couple the log to one backend's call sequence.
2. **No layout in the view model.** Metrics differ per backend, so placement stays in the backend's painter. That's where the lower half tests it.
3. **Data by identity, not by value.** A 20,000-point chart carries the log generation, the filter key, series keys and formulas, the window and the notes. It never carries its points.
4. **Budget the audit volume.** Record diffs. Measure under Follow before shipping (the spike's main finding is here).
5. **Backends register like effect adapters.** The processor decides WHEN an element is stale; a backend only draws. This is CLAUDE.md rule 9, extended from "surfaces read the snapshot" to "surfaces are pushed a view".

## What the spike showed (the status line)

The spike moved the log's status line into a session node, `statusLineView`. It built:
- `StatusLineView`: fourteen scalars, no text, no layout;
- one new fact, `LogShapeObserved`, posted by the scan the adapter already performs;
- `RenderStatusLineEffect`, answered by `ViewRendered`;
- three backends: Swing, a recorder and HTML.

**The results:**
- **It works.** All six predictions scored; details in RESULTS.
  - Every frame suite passes unchanged: 130 run, 0 failed.
  - The frame's two line gates were deleted.
  - The W2 regression is caught by a headless test, as well as by the frame test.
- **It moves defects from frame tests to headless ones, and finds a class a frame cannot see.** Both deleted gates lost their frame witnesses:
  - the race one gate defended cannot happen in the frame at all;
  - the other failure lasts less than one EDT task, but would still be *recorded as told*.

  Only the node-level tests witness them.
- **The rule that makes it sound.** A view may state only what the session owns. Anything the frame read live had to become a fact first, which also makes every view reproducible from the audit.
- **The cost is audit volume, and it was a miss.** I predicted the renders would add no records. Measured over 1,000 Follow polls (600 appending), against the same loop on `main`: **+45% records, +41% bytes.**
  - The 200-record re-scope ring now takes 8 records per appending poll instead of 5, so the retained Follow history falls from about 40 polls to about 25.
  - The render entries themselves are small, because they are diffs: 119 bytes on average.
  - The cost is the three records around them: the shape fact, the render answer, and the effect's batch end.

## Plan, if accepted

| step | what | gate |
|---|---|---|
| 0 | Merge #51. Rebase #53 onto `main`, so CI runs it. | #53 green in CI |
| 1 | Merge #53's seam as the chart's only paint path. | its fourteen controls |
| 2 | **Cut the view-model audit cost before shipping any view.** Fold the scan's three facts into one `LogEvidenceObserved`, and classify `ViewRendered` with the observations, not the re-scopes. | re-measure against RESULTS: the re-scope ring's Follow window is at least what `main` keeps today |
| 3 | Ship the status-line view (the spike, rebased). Move `statusLineText` out of `MainFrame`. | the spike's controls |
| 4 | The identity banner: one view drawn on three surfaces (table, charts, detail), which `onSessionSnapshot` hand-feeds today. | a headless test per surface's view |
| 5 | Tooltip, Reports tab and time-order dialog as views, retiring `renderLogEvidence`. | `OneDispatchModelTest`: no frame gate left |
| 6 | **`ChartView`, on the seam.** Identity by reference (point 3). The legend becomes an element, which closes #53's SVG legend gap. | #46/#47/#50-class witnesses at the view; #48/#49-class at the Surface |
| 7 | Publish each element's last view in the snapshot, so `context` states what the person is shown. | a verb contract test |

## Owner decisions

1. **Accept the two-layer design?** Or keep only the lower half. The seam stands alone; the view models need it for charts.
2. **Is step 2 a precondition?** The spike cost 37% of the retained Follow window. The alternative is a dedicated ring for renders, which the spike's own "not worth it" criteria counted against it.
3. **Does SVG export get a user-facing caller?** A menu item or a verb. If not, `SvgSurface` stays test-only and should say so.
4. **Order of steps 4 and 6.** The banner is the cheapest proof of multi-surface value. The chart is the most valuable.

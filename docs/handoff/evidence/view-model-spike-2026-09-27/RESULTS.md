# View-model nodes spike: results (2026-09-27)

The predictions are in [`PREDICTIONS.md`](PREDICTIONS.md) (commit `4621ae23`, before any code). This file scores
them, records the misses, and assesses the abstraction. Every number below was measured on this branch; the baseline
figures come from the same loop run on `main` at `d321270b`.

## What was built

| piece | where | what it does |
|---|---|---|
| `StatusLineView` | `session/view/` | Fourteen scalars stating what the line says. No text, no layout. `changedFrom(previous)` gives the diff. |
| `LogShapeObserved` | `SessionEvents` | The store's range, completeness claim and pending frames. The scan already performed posts it, before the settling fact. |
| `statusLineView` node (`StatusLine`) | `session/node/` | Emits a view only when it is **consistent** (no scan outstanding, and the shape matches the session's count) and **changed**. It logs the changed fields into the cycle's record and requests `RenderStatusLineEffect`. |
| `ViewBackend` / `ViewBackends` | `session/view/` | Registration. The adapter hands each view to every backend and answers `ViewRendered(element, backends)`. |
| backends | `MainFrame` (Swing), `FakeSessionAdapter` (recorder), `StatusLineBackendsTest` (HTML) | Swing is `status.setText(statusLineText(v))`. `statusLineText` is the one text composer, a pure function of the view. |

`MainFrame.renderLogEvidence` lost the line composition and both of its line gates. What remains there — the
tooltip, the Reports tab and the time-order dialog — is not yet a view.

## Predictions, scored

| # | prediction | result |
|---|---|---|
| 1 | Regeneration succeeds; publishable; no new graph-shape expectation | **Hit.** `-Pregen` succeeded, the two generated copies are byte-identical, and `GeneratedSourceIsPublishableTest` and `SessionGraphShapeTest` pass unchanged. |
| 2 | Existing surface tests pass unchanged | **Hit.** All 24 registered frame suites: 130 run, 0 failed, 1 skipped (`PersonAtTheScreenFrameTest` needs key focus this Mac doesn't give; CI's Xvfb runs it). `LogFindingsOnEverySurfaceFrameTest` 14/14, `StatusExplanationSurvivesFrameTest` 5/5, `StatusLineTest` 13/13. Headless: 2543 run, 0 failed. |
| 3 | The frame's two line gates can be deleted | **Hit, with a finding.** Both are gone and the node owns them. But **neither gate's frame witness can reach it any more** (see [Finding 1](#finding-1-two-gates-are-invisible-to-a-frame-and-that-is-why-they-belong-in-the-processor)). Both controls now point at headless witnesses. |
| 4 | A W2-style regression is caught without a display | **Hit.** Dropping the provenance under Follow in the node turns `StatusLineViewTest#followKeepsProvenanceAndOrderWarning` red with no frame. The same mutation, as `m44-5-w2-follow-line-keeps-provenance`, still turns the frame test red too. |
| 5a | One view per appending poll, none per idle poll | **Hit.** 1,000 polls, 600 appending: 600 renders, 0 on idle polls. |
| 5b | Each entry 250–450 bytes | **Miss, favourable.** A mean of 119 bytes, because only changed fields are logged: an append logs `records` and `lastLogTime`. About 55 of those bytes are invocation tracing that any node pays. |
| 5c | **No new records at all** | **Miss, and the main cost.** On `main`, the same 1,000 polls write 4,000 records (1.81 MB). On the spike they write 5,800 (2.55 MB): **+45% records, +41% bytes**. The additions: 600 `LogShapeObserved` (405 B mean), 600 `ViewRendered` (365 B), 600 extra batch-end `LifecycleEvent`s (one per render effect), and about 40 B of the node's invocation line wherever it is triggered. All are re-scopes, so no transition is evicted (`droppedTransitions == 0`). But the 200-record re-scope ring now takes **8 records per appending poll instead of 5**, so the retained Follow history falls from about 40 appending polls to about 25. |
| 6 | The status line needs no layout field | **Hit.** Fourteen scalars; the text is a pure function of them. (The chart half is assessed below, not built.) |

**The three "not worth it" outcomes, stated in advance:**
- *Frame code that must still decide WHEN:* **not met.** None for the line. The tooltip, Reports tab and dialog still gate in the frame, because they are not views yet.
- *Audit volume forcing its own retention ring:* **not met, but close.** No ring was forced. The Follow window shrinks by roughly 37%, which is the one number that argues against shipping this as built. The remedies are below.
- *A view that must carry text or layout:* **not met.**

## Findings

### Finding 1: two gates are invisible to a frame, and that is why they belong in the processor

Both line gates survived their frame witnesses once they moved into the node:

- **`shape.records() != openLog.total()`** (the frame's `next.total() != s.size()`).
  - The shape comes from a scan that runs in a later EDT task than the poll that reported the append, so in the frame the two always agree. No frame test can build the race.
  - `StatusLineViewTest#aShapeOfAnotherRevisionWaits` builds it directly.
- **`scanPending`** (the frame's `evidencePending`).
  - A scan reports in three facts: shape, findings, time order.
  - Without the gate, a view would be drawn after the findings with the *previous* time order, then replaced by the next fact in the same EDT task, before any repaint. No frame test sees it.
  - The audit would still record it as something the line was told, so the harm is real: a false statement in the evidence.
  - `StatusLineViewTest#aHalfLandedScanIsNotDrawn` is its witness.

The first draft's witness for the second gate was too weak: `vm-scan-outstanding` survived, because the shape gate blocked the same case. It was tightened, and the survivor is recorded here.

### Finding 2: a view may read only what the session owns

The old composer read four things live from the store: the range, completeness, pending records and EOF-included records. A node cannot. The spike needed exactly one new fact (`LogShapeObserved`) to bring them in, posted by the scan the adapter already performs. **This is the rule that makes the abstraction sound:** anything a view states must reach the session as a fact first. It also makes a view reproducible from the audit alone.

### Finding 3: the existing `ShowStatusEffect` shows what the view model changes

The session already had a status effect, and it carries *composed text*. The audit records the words but not what they mean, and a second backend would have to parse English. `RenderStatusLineEffect` carries the semantics: two backends of one view can disagree about typography and never about the log (`StatusLineBackendsTest#backendsRegisterAndAllDraw`). Transient one-off messages stay text, correctly; state that is re-stated as the session changes should be a view.

## Assessment: the shape of the abstraction

```
facts (adapter observes) ──▶ state nodes ──▶ VIEW NODE ──▶ RenderXEffect(view) ──▶ backends (Swing | HTML | recorder | SVG)
                                              │  decides: consistent? changed?          perform only; answer ViewRendered
                                              └─ audits: the fields that changed
```

**It has four parts, each with one job:**
1. an immutable semantic **view** per element;
2. a **view node** that owns *when*: consistency and change;
3. a **render effect** that owns *what*;
4. registered **backends** that own *how*.

The session's existing effect/adapter contract carried it without modification: one sealed-interface case, one driver name, one fake-adapter case.

## Where it could go, most useful first

1. **The identity banner.** `onSessionSnapshot` hand-feeds the same verdict to three surfaces: the table, the charts and the detail pane. One `IdentityBannerView` with three backends is the registry's intended case, and it retires three call sites.
2. **Tooltip, Reports tab and the time-order dialog.** These are the last of the log's evidence rendered by frame gates. As views, `renderLogEvidence` disappears entirely.
3. **Charts, on top of PR #53.** The #53 `Surface` seam is the *lower* half: how to draw, with `Graphics2DSurface`, `SvgSurface` and `RecordingSurface`. A `ChartView` is the *upper* half: what to draw and when. It would carry identities by reference, never points: the log generation, the filter key, series keys and formulas, the window, the notes and the style. The chain becomes node → `ChartView` → backend → `PlotPainter` → `Surface`. The chart defects of PR #51 (placeholders counted as charts, notes dropped silently, a trailing gap) were all "the frame decided what the chart states", which is the class this removes.
4. **Agents see what the person sees.** Publish the last view of each element in the snapshot and `context` can say what the status line states, as fields. That's a screenshot without pixels, and a `spotlight` could name a view field. This is small: it isn't built only because `SessionSnapshot` construction touches many sites.
5. **Replay and evidence.** The audit now records what each element was told, as field diffs, at the cycle it was told. The analyser opening its own session audit gets a timeline of what the UI stated, and a report could cite "the line said *N records, complete* at cycle *k*".
6. **An HTML surface.** The same views drive a browser page. The HTML backend in the test is twelve lines because the words come from the one composer.

## Costs and what to do about them

- **Record volume, the real cost.** Three remedies, cheapest first. All are estimates, **not measured**:
  - *Fold the scan into one fact.* Findings, time order and shape as one `LogEvidenceObserved` would remove the 600 shape records and 600 more, leaving fewer records than `main`.
  - *Classify `ViewRendered` with the observations.* It is an acknowledgement, not a transition. That moves 600 records and the render's batch end out of the re-scope ring.
  - Keep the diff logging: it is already small.
- **Every view node pays invocation tracing.** Its line appears in each cycle it is triggered in, even when it draws nothing. That's about 40 B each, and it is what makes absence mean "did not run".
- **`statusLineText` lives in `MainFrame`** because the `OneDispatchModelTest` rule names MainFrame methods. It should move to its own UI class, with the rule's home updated.
- **Backends are one-way.** A click on a drawn element stays a frame event posted as a fact, which is the existing model. The spike doesn't change that.

**Verdict.** Worth pursuing for elements that are drawn on more than one surface, or that have a history of
ordering or gate defects. The status line had both, and it went from a frame composer with two hand-placed gates to a
node whose every decision is witnessed headless. Do not ship it as built: fold the scan's facts and reclassify the
render answer first, and measure that before and after, the way this file did. The first production candidate is the
identity banner; the most valuable is the chart, on top of #53.

## Gates run on this branch

- `mvn clean test`: 2543 run, 0 failed, 130 skipped (the frame suites, which the frame job runs).
- The 24 registered frame suites with a display: 130 run, 0 failed, 1 skipped (the focus-bound one above).
- Mutation controls, fast engine:
  - the spike's 6: `vm-w2-provenance-under-follow`, `vm-unchanged-view-not-drawn`, `vm-new-generation-drawn`, `vm-audit-records-the-diff`, `vm-w2-text-composer` and `vm-swing-backend-draws`;
  - 5 re-anchored: `follow-hold`, `p15-follow-line-keeps-warning`, `m44-5-w2-follow-line-keeps-provenance`, `m44-5-line-waits-for-the-session` and `m44-5-line-waits-for-the-scan`.
  - All caught, with bytes restored. The two spike controls identical to re-anchored ones were dropped.
  - Then the full registry, which a regenerated processor selects (`--changed-since origin/main`): **233 controls caught** in 566 s, with no survivors.
- Preflight: 233 anchors.
- The rule-1 sweep: clean.

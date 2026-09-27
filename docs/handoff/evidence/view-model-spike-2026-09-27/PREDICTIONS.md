# View-model nodes spike: predictions, recorded before any code (2026-09-27)

**Owner's idea (PR #53 discussion):** drawing elements get PROXIES in the session graph. The graph pushes state and
tells them what to draw — an abstraction, not each line — and the audit log records what they were told. Drawing
backends register: test, headless, Swing, HTML.

**The spike:** one element, the **status line**.
- It already has one composer (M44.5), a known defect history (W2: the Follow line dropped provenance and the order
  warning) and one Swing backend.
- It is small enough to finish in a session and real enough to break.

## Design, fixed before code
1. **`StatusLineView`**, a declarative, immutable view model:
   - WHAT the line states, never where or how: generation, following, the log's path, provenance, record count, the
     first and last log time, whether the file claims completeness, the time-order violation count, the kind of the
     first producer warning, pending records, EOF-included records, a Follow read failure, the reopened reason;
   - no strings composed and no layout;
   - no data by value beyond these scalars.
2. **`StatusLineViewNode`**, a session node:
   - it reads `openLog` and `logEvidence`, and holds the store's SHAPE from a new fact `LogShapeObserved` (range,
     completeness, pending), which the adapter posts from the scan it already performs;
   - it decides WHEN the element is stale, and emits a view only when the view is CONSISTENT: no scan outstanding, and
     the shape describes the session's record count. That moves the frame's two render gates (`evidencePending`,
     `next.total() != s.size()`) into the processor;
   - it emits only when the view CHANGED;
   - it logs each emitted view into its cycle's audit record.
3. **One effect, `RenderStatusLine(view)`.** The adapter hands it to every REGISTERED backend and answers
   `ViewRendered(element, backends)`. Backends:
   - Swing: the status bar;
   - a recorder: the session-level fake adapter, in tests;
   - a plain-text/HTML renderer, which is a pure function of the view.
4. **One formatter, `StatusLineRenderer.text(view)`.** It replaces MainFrame's line composition. The Swing backend is
   `status.setText(text(view))` when the text changed.

## Predictions
1. `-Pregen` regenerates the processor with the new node; `GeneratedSourceIsPublishableTest` passes;
   `SessionGraphShapeTest` needs no new expectation (the node fills the effect queue from a decision).
2. **Existing surface tests pass unchanged through the new backend:**
   - `LogFindingsOnEverySurfaceFrameTest` (W1, W2, one revision per line, environment provenance, …);
   - `StatusExplanationSurvivesFrameTest` (R12-2);
   - `StatusLineTest`.
3. **The frame's two line-render gates can be DELETED.** The node's consistency rule replaces them, and
   `theLineNeverCountsNewRowsBesideTheOldFindings` still passes.
4. **A session-level test catches a W2-style regression WITHOUT a display.** Provenance dropped while following shows
   in the recorded views, and a mutation that drops it turns that headless test red.
5. **Audit volume** (1,000 Follow polls: 600 appending, 400 idle):
   - one view per APPENDING poll, none per idle poll, so at most 600 view entries;
   - each 250–450 bytes in its cycle record;
   - no new records at all, because the entries ride inside the existing cycle records, which already have retention
     rings.
6. **The abstraction needs no layout field for the status line.** For the chart it would need identities by
   reference: the log generation, the filter key, series keys and formulas, the window, the notes and the style.
   Never points.

## What would make the idea NOT worth it (stated in advance)
- Frame code that must still decide WHEN the line is stale: a gate the node cannot own.
- Audit volume that forces its own retention ring, beyond the existing ones.
- A view model that has to carry formatted text or layout to render faithfully.

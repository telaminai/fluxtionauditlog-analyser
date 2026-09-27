# View-model nodes, second element: predictions, recorded before any code (2026-09-27)

Continues the spike in [`PREDICTIONS.md`](PREDICTIONS.md) / [`RESULTS.md`](RESULTS.md). That one built the status
line and left one number arguing against it: **+45% audit records, +41% bytes** under Follow, cutting the re-scope
ring's retained window from ~40 appending polls to ~25.

## The question this element exists to answer

The first element cannot distinguish two explanations of that cost, and they have opposite consequences:

- **H1 — a per-element tax.** Each view node costs roughly what the status line cost. Four more elements and the
  Follow ring is unusable; the idea does not generalise without a redesign.
- **H2 — a per-CHANGE cost.** The cost tracks how often a view changes, not how many views exist. The status line
  changes on every appending poll, which is the worst case in the codebase. A view that rarely changes costs almost
  nothing.

The results file assumes something like H2 ("the cost is the shape fact, the render answer and the effect's batch
end" — all per render) but never separates them, because one element cannot.

## Why the identity banner is the probe that separates them

It is the next element the results file recommends, and it is also the clean experiment:

1. **It needs no new facts.** `logIdentity()` and `logIdentityReason()` are already in the session. The status line
   needed `LogShapeObserved` — 600 of the 1,800 added records. Removing that confound isolates the floor cost of a
   view element.
2. **It almost never changes.** The verdict changes when the file behind the log changes, not when records arrive.
   Under a Follow loop it should emit **nothing**.
3. **It has three backends already**, hand-fed from `onSessionSnapshot` — the registry's intended case.

So: status line = new fact + frequent change. Banner = no new fact + no change. The difference is the answer.

## A correction to make before measuring

I suggested elsewhere that putting render audit at DEBUG would make the cost "mostly evaporate". **That is wrong and
this file records it before the measurement, not after.** The +45% is event RECORDS — the shape fact, the
`ViewRendered` answer, the effect's batch end. The node's own audit entries are the small part (119 B mean). A log
level gates the entries, not the records, so it addresses roughly the 7% that was never the problem. The remedies
that matter are the two the results file names: fold the scan's facts, and reclassify the render answer.

## Design, fixed before code

1. **`IdentityBannerView`** — three fields: `verdict`, `reason`, and `shown` (derived: the verdict is `UNVERIFIED`
   or `REPLACEMENT`). No text.
2. **`IdentityBanner` node** — reads `openLog`; emits when the view CHANGED. There is no consistency gate to move:
   unlike the status line, nothing here can be half-landed.
3. **`RenderIdentityBannerEffect`**, answered by `ViewRendered("identityBanner", backends)`.
4. **Three backends** — table, charts, detail pane — each composing its own sentence from the view.

**One thing this retires that the status line did not.** `GraphTabs.identityBannerText` and
`DetailPanel.identityBannerText` both begin `if (LogTablePanel.identityBannerText(verdict, reason) == null) return
null;` — two surfaces asking a third for the POLICY, then writing their own words. The policy becomes `shown` on the
view and the cross-dependency goes.

## Predictions

1. `-Pregen` succeeds; both generated copies byte-identical; `GeneratedSourceIsPublishableTest` passes.
2. The three `onSessionSnapshot` call sites disappear, and so do both cross-calls to
   `LogTablePanel.identityBannerText`.
3. **The Follow loop measurement, against this branch's current 5,800 records / 2.55 MB:**
   - **0 renders**, 0 `ViewRendered` records, 0 extra batch ends;
   - added records **< 1%**, added bytes **< 2%**;
   - the addition is the node's invocation-tracing line in the cycles it is triggered in (~40 B), nothing else.
4. **Therefore H2**, and the status line is the codebase's worst case rather than its typical one. If instead the
   banner costs anything like the line did, H1 holds and the direction needs a redesign before any further element.
5. A verdict change emits exactly one view and all three backends draw it.
6. Existing suites pass unchanged, including `IdentityBannerFrameTest`-style coverage of the three surfaces.

## What would make THIS element not worth it, stated in advance

- Backends that need more than the view to compose their sentence — that would mean the view is not the semantics.
- A node that must read the store, not the session (the results file's Finding 2).
- Any measured cost consistent with H1.

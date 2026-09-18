# Review — M65 spec, second pass (`spec-follow-refreshes-graphs.md` at 88f17de7)

**Date:** 2026-09-17 · **Reviewer:** Claude (this session) · **First pass:** [review_spec_m65_2026-09-17.md](review_spec_m65_2026-09-17.md)

## Verdict: CONDITIONAL — two conditions, both small

The revision folds in C1 and C2 correctly and adds C3 from the first pass's "did not check" list. Every new claim
it makes about the code was checked against source and holds (below). Two things stop me saying READY:

- **C4** — D-F7's "hold" is defeated by a path the spec's own diagnosis (step 2) names: the slider echo already
  re-windows an unpinned chart on every growing tick, *before* any re-extraction exists.
- **C5** — D-F7's tail rule ("width unchanged") contradicts acceptance 1's first variant ("window extended to the
  new maximum"). One rule is needed; a test is written against the other.

Both are a paragraph in the spec and a few lines in `GraphPanel`. The two-day estimate holds.

## What was verified (all confirmed)

| Claim in the revision | Source | Result |
|---|---|---|
| `LogIndex.add` is `synchronized`; `size()`, `offset(i)`, `length(i)` are plain reads; arrays grow by reallocation | `index/LogIndex.java` lines 89, 204–207, 127–135 (`Arrays.copyOf`) | **Confirmed.** D-F0 part 2 is necessary: swapping `file` first closes only the string race. |
| The index already has a `synchronized snapshot()` "of the shape" the read view needs | `LogIndex.java` line 239–262 | **Confirmed, with a gap:** `Snapshot` captures `offset`, `logTime`, `dimId`, `threadId`, `flags`, `fileId` — **not `length`**. `rawText(row)` needs `offset` and `length` and the read view must carry both, plus `file`. |
| `ChartPanel.setSeries` calls `resetView()`; `reExtract`'s success path calls `applyWindow()`, which sets the filter window when unpinned | `ui/ChartPanel.java` `setSeries`; `ui/GraphPanel.java` `applyWindow` | **Confirmed.** |
| `appendFrom` adds to the index and then assigns `this.file` | `parse/HeapLogStore.java` lines 45–59 | **Confirmed** (first pass). `file` is not volatile (line 19). |
| `onFilterChanged` classifies structural over dimensions/text/group mode only; the `else` branch calls `chart.setViewWindow(filter.from, filter.to)` when unpinned | `ui/GraphPanel.java` `onFilterChanged` | **Confirmed** — and this is C4. |
| Help bullet wording *"flags, filters and selection are preserved"* | `help/help.html` line 221 | **Confirmed** (first-pass F2 closed). |
| `Background.POOL` is `newCachedThreadPool`; generation check drops results only | first pass | Unchanged. |

## C4 — the echo resets an unpinned zoom on every growing tick today

The chain, from source, on a follow tick that appended rows:

```
MainFrame.pollFollow            (EDT)
  store.appendFrom(...)                         line 3092
  timeSlider.extendAbsMax(mx)                   line 3109  — fires only when newMax > absMax, i.e. exactly the ticks that carry data
    → TimeRangeSlider.publish()
      → FilterState.setTimeRange(from, to)      — fireChanged() unconditionally, even when from/to are unchanged
        → GraphPanel.onFilterChanged()
            structural == false
            !isPinned()  → chart.setViewWindow(filter.fromMillis(), filter.toMillis())
  onFilterChanged()                             line 3111
```

With the slider at full extent `publish` sends `(null, null)` → `setViewWindow(null, null)` → the view becomes the
full extent of the cached series. A person zoomed into the middle of an unpinned live chart is thrown to the full
window on every tick that grows the log — **today, with no re-extraction anywhere**. Two consequences for the spec:

1. The sentence under C3, *"Today this happens only on a structural change the person caused themselves"*, is
   wrong for the unpinned case. The reset already happens each growing tick, through the echo, not through
   `setSeries`. (Nobody noticed because with follow on the graph never refreshed at all — the M65 bug.)
2. D-F7 as written covers only `reExtract`'s success path. The echo runs synchronously on the EDT inside the tick;
   the extraction lands later via `invokeLater`. So the order is: echo resets the view → extraction lands → D-F7
   "holds" the already-reset window. Acceptance 1 variant 2 ("unpinned zoomed into the middle → window unchanged")
   and acceptance 5 ("a zoomed-in graph keeps its zoom") fail as specified — unless the test drives only the new
   hook and skips `extendAbsMax`, in which case the test passes and the app does not.

**Required.** Cover the echo. Two ways; I recommend the first:

- **(a) In `GraphPanel.onFilterChanged`**, re-window only when the filter's window actually changed since the panel
  last applied it: two fields `lastFrom`/`lastTo` beside `lastDims`/`lastText`/`lastGroupMode`, compared in the
  `else` branch. Local, two fields, the pattern the method already uses, and M6 is untouched: a real slider move
  still re-windows, an unchanged echo no longer does. With a windowed outer range and the top thumb following,
  `publish` still sends the same `(lo, null)`, so this also holds there.
- **(b) In `FilterState.setTimeRange`**, skip `fireChanged` when both values are unchanged. Broader and riskier:
  the spec's own *Not in scope* says the reports panel re-renders only from `onFilterChanged`, so the echo may be
  what refreshes other listeners on a follow tick. Not without reading every listener.

And the acceptance-1 test must drive the tick the way `pollFollow` does — `appendFrom`, then `extendAbsMax`, then
the hook — not the hook alone. Say so in the test's description or the mutant (a) guards against is not covered.

## C5 — one tail rule, not two

D-F7: *"the window slides so its right edge is at the new maximum and its **width is unchanged**"*.
Acceptance 1 variant 1: *"unpinned at full extent → 3 points, **window extended** to the new maximum (D-F7 tail)"*.

These differ exactly in the common case. A person who never zoomed is looking at the whole log; after an append
they expect to still see the whole log (Fit grows), not a fixed-width window that has slid off the start. A person
who zoomed to the last minute and pressed the view against the right edge expects the slide. One rule that gives
both, in the order tested:

1. the view covered the whole data range (left edge ≤ old minimum **and** right edge ≥ old maximum) → **extend**:
   left edge stays, right edge at the new maximum (what `resetView` would give);
2. else right edge ≥ old maximum → **slide**: width unchanged, right edge at the new maximum;
3. else → **hold** exactly.

Acceptance 1 then has three unpinned variants (full → extend, tail-zoomed → slide, middle-zoomed → hold) plus
pinned. **Required** only because a test is currently specified against a rule the design text contradicts; the
change is to the text.

## Follow-ups (not blocking)

- **F1 — the `reason` flag must merge DEFINITION-wins.** D-F1 sends `onRecordsAppended` through the structural
  debounce, and D-F6 coalesces a burst behind `dirty`. Both merge a DATA request with a possible DEFINITION request
  into one run. The spec says the flag is "read on landing" but not how it merges. State: the pending run's reason
  is DEFINITION if any coalesced request was, otherwise DATA; and add to acceptance 3's coalescing test one case
  where a key is added while the extraction is held, asserting the follow-up lands as DEFINITION (view reset). The
  wrong merge is silent: a definition change lands as DATA and holds a window that no longer means anything.
- **F2 — the read view lives on the `LogStore` interface, not only `HeapLogStore`.** `SeriesExtractor` takes
  `LogStore`; there are four implementations (`HeapLogStore`, `MappedLogStore`, `RolledLogStore`, `SpiLogStore`).
  A `default` method returning a live view over `size()` keeps the other three untouched; `HeapLogStore` overrides
  it with the locked capture. And the capture needs `length` as well as `offset` and `file` (table above).
- **F3 — name the seam acceptance 2 and 3 use.** "Hold the extraction with a latch" needs a way to make
  `GraphPanel`'s pool work block from a headless test: either a package-private extraction function the test
  replaces, or the executor `Background.run` uses. `GraphTabsBindIsNotAnEditTest` shows headless bind; it does not
  show headless control of the pool. One sentence in the spec saves the implementer inventing a seam mid-task.
- **F4 — `extendAbsMax` is the spec's second trigger, not just the hook.** With C4 resolved by (a), note in the
  diagnosis that the echo is now inert for an unchanged window and that D-F1's hook is the *only* thing that moves
  an unpinned view on a data tick. That is the invariant the tail rule relies on.

## Did not check

- `RolledLogStore`'s append path (the spec says so too); whether follow can be on for a rolled set.
- `MappedLogStore`/`SpiLogStore` `size()` semantics under growth (follow does not apply to them today, per the spec).
- Whether any listener other than `GraphPanel` depends on the echo firing with unchanged values (the reason (b) is
  not recommended, not a finding against (a)).

## Review record

| Pass | Commit reviewed | Verdict | Conditions |
|---|---|---|---|
| 1 | 5cb9121e (spec as proposed) | CONDITIONAL | C1 store publication, C2 in-flight back-pressure |
| 2 | 88f17de7 (revised) | CONDITIONAL | C4 echo path defeats the hold, C5 one tail rule |

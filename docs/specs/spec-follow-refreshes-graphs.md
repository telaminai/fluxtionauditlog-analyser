# Spec — follow refreshes open graphs

**Status:** SHIPPED in analyser 1.14.0 (2026-09-17) — M65.0–.4 as specified below, after five review passes (three of the
spec, two of the implementation; the implementation review, its response and the pass-5 check are in `docs/handoff/completed/`).
Open: M65.5, the D-F5 extraction measurement. History: PROPOSED 2026-09-17 · **REVISED twice the same day after review.** Pass 1
([review](../handoff/completed/review_spec_m65_2026-09-17.md), CONDITIONAL): diagnosis and fix accepted; C1/C2 and six findings
folded in, plus C3 the author raised from the reviewer's *"did not check"* list. Pass 2
([review](../handoff/completed/review_spec_m65_pass2_2026-09-17.md), CONDITIONAL): every new claim verified; C4 (the slider echo
already resets an unpinned view on every growing tick, so D-F7's hold was defeated before extraction ran) and C5 (one
tail rule, not two) plus four follow-ups, all folded in below. Pass 3
([review](../handoff/completed/review_spec_m65_pass3_2026-09-17.md), **READY WITH FOLLOW-UPS — no further spec review**):
four wording follow-ups folded in below (F1 the capture order and the view's surface, F2 the empty-before case, F3
programmatic ranges under D-F8, F4 where the seam lives). **IMPLEMENTED 2026-09-17** on
`feat/m65-follow-refreshes-graphs`, reviewed twice more against the code — impl pass 4
([review](../handoff/completed/review_m65_implementation_2026-09-17.md), NOT READY: B1 the extend rule contracted a zoomed-out
view; F1 the filter's live-index reads; F3 a synchronous runner throw — all landed, [response](../handoff/completed/review_response_m65_impl_2026-09-17.md))
and impl pass 5 ([review](../handoff/completed/review_m65_implementation_pass5_2026-09-17.md), **READY**, one nit applied) —
and **merged to `main`**. Open follow-ups: tracker M65.5 (the D-F5 measurement), M65.6 (an unattributed *no log
loaded* observation).
Owner question: *"what is the
lowest overhead way of forcing the graph redraw? should we add something to the plot verb to redraw for new log
entries?"* **Tracker:** [tracker.md](tracker.md) ▸ M65.
**Related:** M6 graphing (the extraction cache and the time-slider-never-re-parses rule this spec must keep),
H8.7 follow/tail (`parse/FollowAppendTest`), M35 log + graph lifecycle (a graph is evidence; a stale one is
false evidence), [spec-guided-start.md](spec-guided-start.md) D-G2 (*the screen PROVES* — it cannot if a chart lags
the table beside it).

## The defect, in one sentence

With **Follow** on, an appended record reaches the records table, the time slider, the status bar and the `series`
verb within a second, and a graph tab created *after* the append sees it — but a graph tab that already existed
keeps its old points through zoom, Fit, a time-range change and an identical re-send of its definition, and
recomputes only when its definition *changes*.

## How it was observed (2026-09-17, audit-analyser-bundle)

A Mongoose bundle's export was changed to append records to the tracked YAML and close each with `---` so follow
could ingest it. With a graph of `rootNode.price` open (16 PriceEvent markers), one CSV row was appended to the
server's input and exported:

| surface | after the append |
|---|---|
| records table, slider, status bar | 53 records, the new row visible, range extended |
| `series {expr: rootNode.price}` | 17 points, `last` = the new value |
| a graph created **after** the append | 17 points, axis to the new time |
| the **pre-existing** graph | 16 markers; unchanged by zoom out, Fit, narrowing the slider to a window that contained the new record, and re-sending the same `graph` definition |
| the same graph after `graph {markers: <the identical set>}` | 17 markers, axis extended, new point drawn |

So the analyser *has* the data and can draw it; the open graph is simply never told to look again.

## Why — the causal chain (verified in source; independently re-verified by the review)

1. **The append is silent.** `HeapLogStore.appendFrom` (`parse/HeapLogStore.java`) mutates the `LogIndex` in place
   and returns a count. `LogStore` has no listener. The only caller is `MainFrame.pollFollow`
   (`ui/MainFrame.java`, ~1 s timer), which fans out **by hand** to six consumers — `tableModel.rowsAppended`,
   `timeSlider.extendAbsMax`, `timeSlider.setHistogram`, `onFilterChanged`, `tablePanel.scrollToLast`, the status
   line. **`graphTabs` is not on the list.**
2. **The graph hears an incidental echo and files it as "time only".** `extendAbsMax` ends in
   `TimeRangeSlider.publish` → `FilterState.setTimeRange` → `fireChanged`. `GraphPanel.onFilterChanged` computes
   `structural` over **dimensions, free text and group mode only**; record count is not an input. An append
   therefore takes the `else` branch: `chart.setViewWindow(...)` over the **cached** series — or nothing at all
   when the graph is pinned.
3. **Everything the user can click reads the cache.** Zoom (`ChartPanel.zoomAroundCentre`), Fit
   (`ChartPanel.resetView`) and the slider (`ChartPanel.setViewWindow`, whose javadoc says *"without
   re-extracting"*) all repaint the `ChartPanel.series` list. That is **correct and deliberate** (M6: a slider drag
   never re-parses the log) and this spec keeps it.
4. **The one method that rereads the store is private.** `GraphPanel.reExtract()` walks `store` under `filter`
   off-EDT, then on the EDT sets series, bands and markers and calls `applyWindow()`. Its callers are the
   structural debounce (`EXTRACT_DEBOUNCE_MS = 200`), series-panel edits, `addKeys` **only if a key is new**,
   `addExpr`, `setMarkers`, `setBands`, `setExternalPreloaded`.
5. **The verb's dedup is why an identical re-send is a no-op.** `ActionExecutor.doGraph` reuses the existing tab
   (`GraphTabs.graphForAction`) and calls `addKeys`, whose `if (added) reExtract()` guard skips when every key is
   already active. `setMarkers` and `setBands` re-extract **unconditionally** when their key is present in the
   request — which is the accidental cure observed above, and an inconsistency in its own right.

`GraphTabsBindIsNotAnEditTest` is unrelated: it guards the *persistence* callback during bind/restore, not
extraction.

## What the review found that the first draft did not see

Three facts about the code the fix would run through, each of which turns a rare edge into the steady state once
extraction happens on every follow tick. They are the reason D-F0, D-F6 and D-F7 exist.

**The store is not safe to walk while it grows (review C1, extended by the author).** `SeriesExtractor.extract`
loops `for (row = 0; row < store.size(); row++) store.record(row)` on the **live** store, on a `Background` pool
thread. `appendFrom` runs on the EDT and, in this order, `index.add(...)`s each new record and **then** assigns
`this.file = full`. `rawText(row)` is `file.substring(offset, offset + length)`. Between the index growing and the
field swap a row exists whose offsets point past the end of the old string; a walker that reaches it throws, the
error lands in `err -> { /* best-effort */ }`, and the chart silently fails to update. `file` is not `volatile`.
**And the index has the same shape one level down:** `LogIndex.add` is `synchronized`, but `size()`, `offset(i)` and
`length(i)` are plain array reads and the arrays grow by reallocation — a reader can take the new `size` and then
index a stale array reference. So swapping `file` first and making it `volatile` (the review's proposed form) closes
the string race and leaves the array race open.

**Superseded work is not stopped, only its result discarded (review C2).** `Background.POOL` is
`newCachedThreadPool`; `reExtract`'s `gen != extractGen` check drops a stale **result**. An extraction that outlives
the ~1 s poll (a large log, several windowed formulas) is joined by another every tick, and they accumulate.

**Re-extraction resets an unpinned view (pass-1 "did not check", confirmed by the author → C3).**
`ChartPanel.setSeries` calls `resetView()`; `reExtract`'s success path then calls `applyWindow()`, which sets the
filter's window when unpinned. A person zoomed into a live chart would be thrown back to the full window on every
tick that carried data.

**And the slider echo already does that today, before any extraction exists (pass-2 C4).** On a tick that grew the
log, `pollFollow` calls `timeSlider.extendAbsMax(mx)` — which fires exactly when `newMax > absMax`, i.e. on the data
ticks — and it ends in `publish()` → `FilterState.setTimeRange(from, to)`, which calls `fireChanged()` **even when
`from`/`to` are unchanged**. `GraphPanel.onFilterChanged` sees a non-structural change and, unpinned, calls
`chart.setViewWindow(filter.from, filter.to)`; with the slider at full extent `publish` sends `(null, null)`, so the
view becomes the full extent of the cached series. So the first revision's sentence *"today this happens only on a
structural change the person caused themselves"* was wrong for the unpinned case: the reset happens on every
growing tick, synchronously on the EDT, **before** the extraction lands via `invokeLater`. A hold implemented only
on the extraction's landing would hold a window the echo had already reset. Nobody noticed because with follow on
the graph never refreshed at all — the M65 bug hid it.

## The owner's two questions, answered

### Q1 — the lowest-overhead way to force the redraw

**Tell the graphs, from the one place that already knows: the follow poll.** No new event bus, no store listener,
no polling from the graph. The graph already owns a coalescing, off-EDT, generation-guarded extraction path (the
structural debounce); re-use it.

```java
// GraphPanel — new, public
/** The open log grew (follow). Re-extract from the store, coalesced with any pending structural change.
 *  A pinned graph re-extracts too: its WINDOW is fixed, its DATA is not. */
public void onRecordsAppended() { extractDebounce.restart(); }

// GraphTabs — new, mirrors refreshFlagRug()
public void onRecordsAppended() { for each GraphPanel gp in tabs: gp.onRecordsAppended(); }

// MainFrame.pollFollow — one line, after `if (added == 0) return;`
graphTabs.onRecordsAppended();
```

Cost: one re-extract per poll tick that carried data — at most one per second, off the EDT, the same work a
dimension change costs today, and (D-F6) never more than one in flight. Zoom, Fit and the slider stay cache-only;
nothing in M6's smoothness rule changes. **Preconditions:** D-F0 — the walk must be safe against the append it is now
guaranteed to overlap — and D-F8 — the slider echo must be inert for an unchanged window, so the hook is the *only*
thing that moves an unpinned view on a data tick (the invariant D-F7 relies on; pass-2 F4).

### Q2 — should the `graph` verb get a redraw/refresh option?

**Not as the fix.** A verb flag leaves the *person at the UI* with a stale chart, makes the *agent* responsible for
knowing the chart is stale (it cannot tell — the verb echo does not carry a point count), and is redundant the
moment follow drives extraction. The verb's contract is *"define the graph"*; freshness belongs to the data path.

**Two small verb changes are still warranted**, because they close the inconsistency the investigation exposed:

- **`refresh: true`** — an explicit "re-extract this graph now" for an agent driving an analyser whose follow is
  off, or a log that was appended by something other than follow. Five lines: calls the same `onRecordsAppended`.
  Documented as *"rarely needed; follow does this for you"*. The echo says **`refreshed: "scheduled"`**, not
  `true` (review F1): the verb returns synchronously and the extraction lands later, so an agent that screenshots
  immediately may still see the old chart. The `series` verb, which extracts on every call, is how to *read* a
  fresh value; the chart is what lags.
- **Make the re-extract decision consistent across keys.** Today `series` re-extracts only on a new key while
  `markers`/`bands` re-extract on any presence. Either is defensible; having both is not. Decision: **a re-send
  that changes nothing re-extracts nothing** (cheap, idempotent), for every key — and `refresh` is the one word
  that means "do it anyway".

## Decisions

**D-F0 — the store is publishable while it grows.** *(review C1, extended.)* Two parts, both required:

1. `HeapLogStore.appendFrom` assigns `this.file = full` **before** the framing loop adds index rows, and `file` is
   `volatile`. The file is append-only, so every existing row's offsets are valid in the new string; a reader that
   sees a new row also sees the string that contains it.
2. A walker never reads `size()` per iteration. The **read view** is a `default` method on the `LogStore`
   interface returning a live view over `size()` — so `MappedLogStore`, `RolledLogStore` and `SpiLogStore` are
   untouched (pass-2 F2) — and `HeapLogStore` overrides it with a locked capture, taken once per walk, of `size`,
   the `file` reference **and the `offset`/`length` arrays** (the index's existing `synchronized snapshot()` is the
   shape but carries `offset` without `length`; `rawText(row)` needs both). **Capture order** (pass-3 F1): `size`
   and the arrays are read **under the index lock** (a new `synchronized` method on `LogIndex`, since they are its
   privates); **`file` is read after the lock is released** — it is a `HeapLogStore` field the writer swaps
   outside the lock, and the writer's order is `file = full` (volatile) → `add(k)` (lock), so a reader that saw row
   *k* under the lock sees a `file` that contains it. Reading `file` *before* the lock would pair an old string
   with a new size — the C1 race again. **The view's surface**: the walk calls `view.record(row)` / `view.rawText(row)`
   served from the captured arrays and string; a walk that still called `store.record(row)` would read the live
   arrays unsynchronised, which is the race part 2 exists to close. The extractor iterates to the captured size
   only. Rows below it are fully written by the lock's happens-before; a stale array reference still holds them
   because copy-on-grow preserves prefixes; and because the filter reads the LIVE index for those rows, the index's
   row arrays are `volatile`, so a reference observed after a growth carries the prefix copied into it (impl review
   F1 — the JMM does not otherwise guarantee that through a newly observed reference). `SeriesExtractor`'s four `store.size()` loops take the view, and so
   does `MarkerExtractor`, which rides the same extraction pass.

Pinned by a test that appends while a walker is mid-log (a latch inside a test extractor, or a store spy that
appends on the Nth `record(row)` call) and asserts the walk completes with the pre-append count and no exception.

**D-F1 — follow invalidates open graphs.** `pollFollow` notifies `GraphTabs.onRecordsAppended()` whenever
`added > 0`. Every open `GraphPanel` re-extracts through its existing debounce. This is the fix; D-F0, D-F6 and
D-F7 are what make it safe to run every second.

**D-F2 — a pinned graph re-extracts but keeps its window.** Pinning fixes *what range the evidence shows*, not
*which records exist*. `reExtract → applyWindow` already honours the pin; no change needed beyond D-F1. The
pinned/unpinned distinction in `onFilterChanged` is untouched.

**D-F3 — the slider, zoom and Fit remain cache-only.** M6's rule stands: a time-only change never re-parses. The
follow tick is not a time-only change; it is new data, and is routed as such.

**D-F4 — `graph` re-sends are idempotent; `refresh: true` is the only forced re-extract.** `setMarkers`/`setBands`
gain the same "only if changed" guard as `addKeys`; `refresh` bypasses all guards. The echo gains
`refreshed: "scheduled" | false`. `refresh` is added to `VerbSchemas` once and reaches both the REST manifest
and the MCP tool list through the existing parity tests (review F6) — nobody adds it twice.

**D-F5 — full re-extract first; incremental only on evidence.** A per-tick full walk is O(store) and is the same
cost the app already accepts for a filter change. Note the poll **already** pays O(file) per tick: `appendFrom`
re-reads the whole file and re-frames it, skipping the records it knows — so the extractor adds a second linear
pass, not a new complexity class, and the re-frame is the larger target if either is ever made incremental.
The incremental extractor (start at the previous record count, append to the existing `Series`) is the follow-up
**if and only if** measured: the demo log and one ~100k-record log, extraction wall time per tick on the CI
machine class, with the trigger being >~50 ms per extraction **or** D-F6's dirty flag set in steady state.

**D-F6 — one extraction in flight.** *(review C2.)* `GraphPanel` holds `extracting` and `dirty`. `reExtract` while
`extracting` sets `dirty` and returns; the success **and** error callbacks clear `extracting` and, if `dirty`, clear
it and run once more. Two lines in each callback, one in `onRecordsAppended`. The generation check stays for
results; this stops the work.

**D-F7 — the view extends, slides, or holds — one rule.** *(C3, C5; restated at impl review pass 4.)* On the
success path of a re-extract whose reason is `DATA`, an unpinned chart does not `resetView()`. With `oldMin`/`oldMax`
the data range before the extraction and `newMax` after it, tested in this order:

0. the previous extraction had **no points** — no view, no `oldMin`/`oldMax` to compare against — → behave as
   `resetView`: the first data is an extend from nothing (pass-3 F2);
1. `newMax` ≤ the view's right edge → **hold**: the new point is already visible, so nothing moves. This covers a
   view zoomed **out** past both ends of the data (impl review B1: the earlier extend rule pulled its right edge
   back to the data on every tick) and a view reaching past the live edge (the M65.3 live proof);
2. else the view covered the whole old range (left edge ≤ `oldMin` **and** right edge ≥ `oldMax`) → **extend**:
   left edge stays, right edge to `newMax` — a person who never zoomed keeps seeing the whole log;
3. else right edge ≥ `oldMax` → **slide**: width unchanged, right edge to `newMax` — a person pressed against the
   live edge follows it;
4. else → **hold** exactly — a person studying the middle is not disturbed.

**The view moves only to reveal a point that would otherwise be hidden.** That one sentence is the rule; 1–4 are
its cases.

A re-extract whose reason is `DEFINITION` keeps today's behaviour (reset, then the filter window), because the
person asked for a different chart. The reason rides the extraction request; **when requests coalesce (the
structural debounce, or D-F6's `dirty` flag) the pending run's reason is `DEFINITION` if any coalesced request
was, otherwise `DATA`** (pass-2 F1) — the wrong merge is silent, a definition change landing as `DATA` and holding
a window that no longer means anything.

**D-F8 — the slider echo is inert for an unchanged window.** *(C4.)* `GraphPanel.onFilterChanged` gains
`lastFrom`/`lastTo` beside the existing `lastDims`/`lastText`/`lastGroupMode`, and the non-structural branch
re-windows **only when `filter.fromMillis()`/`toMillis()` differ from the last pair it applied**. A real slider
move still re-windows (M6 untouched); the `extendAbsMax → publish → setTimeRange` echo on a data tick, which
resends the same `(from, to)`, no longer does. This is the local form the review recommends over skipping
`fireChanged` in `FilterState.setTimeRange`, because the reports panel and possibly other listeners are refreshed
by exactly that echo on a follow tick. With D-F8 in place, D-F1's hook is the **only** thing that moves an
unpinned view on a data tick, which is what makes D-F7's rule meaningful.

**A consequence to state, not a regression** (pass-3 F3): `FilterState.setTimeRange` is also called
programmatically — two `ActionExecutor` verbs, `SeriesScan`, and `FilterSnapshot` when a report re-issues. With the
guard, a verb that re-sends the range the chart last applied while the person has zoomed no longer re-windows the
chart. That is consistent with M6 — a time-only change is a view change, and the view did not change — and an
agent that wants the window reset has Fit and the zoom verbs. It is on acceptance 4's idempotence list so nobody
files it as a defect.

## Not in scope

- A general observer on `LogStore`. Six hand-listed consumers plus one is fine; an event bus is a refactor with
  no user-visible gain and is not this spec's problem.
- Refreshing **reports** or **coverage** on follow. Both are **known stale under follow** today (the reports panel
  re-renders only from `onFilterChanged`; coverage is computed on open). Each deserves its own one-line check;
  this spec names them so the next reader does not re-discover it (review F5), and does not pull them in.
- S3 and large-file (off-heap) logs: follow does not apply to them today; nothing here changes that.
  `RolledLogStore`'s append path was not read.

## Acceptance

1. **Test — `ui/FollowRefreshesGraphTest`** (headless; `GraphTabsBindIsNotAnEditTest` shows a headless
   `GraphPanel` bind is possible): open a two-record log, bind a `GraphPanel` on a key, assert 2 points; append a
   third record to the file, drive the store append and the new hook, then **wait for the extraction generation
   to land** — poll the chart's series count with a deadline, or expose the pending generation; pumping the
   debounce alone is not enough because the walk runs on the pool and lands via `invokeLater` (review F3) —
   and assert 3 points **and** the marker legend count is 3 after the same wait (review F4). **The test drives the
   tick the way `pollFollow` does — `appendFrom`, then `timeSlider.extendAbsMax`, then the hook — not the hook
   alone**, or the D-F8 mutant is not covered and the app fails where the test passes (pass-2 C4). Four variants:
   unpinned at full extent → 3 points, left edge unchanged, right edge at the new maximum (D-F7 **extend**);
   unpinned zoomed to the last stretch with the right edge at the old maximum → 3 points, same width, right edge
   at the new maximum (D-F7 **slide**); unpinned zoomed into the middle → 3 points, **window unchanged** (D-F7
   **hold**); pinned → 3 points, window unchanged (D-F2).
2. **Test — D-F0 concurrency**: a store spy appends two records during the walk's Nth `record(row)`; the walk
   completes with the pre-append count and no exception; a second walk sees all rows.
3. **Test — D-F6 coalescing and D-F7 reason merge**: hold the extraction, call `onRecordsAppended` three times,
   release; exactly two extractions ran (the held one and one follow-up) and the follow-up's reason is `DATA`.
   Repeat with a key added (`addKeys`) while held: the follow-up lands as `DEFINITION` and the view resets
   (pass-2 F1). **The seam** (pass-2 F3): `GraphPanel` runs its walk through a package-private
   `extractionRunner` — default `Background::run` — that a headless test replaces with one that parks the work on
   a latch and delivers on the test thread; `GraphTabsBindIsNotAnEditTest` shows headless bind but not pool
   control, so the seam is named here rather than invented mid-implementation. **Where it lives** (pass-3 F4):
   `Background` is in `core`, not `ui`; the seam is a package-private functional interface in `ui` beside
   `GraphPanel` with `Background.run`'s three-argument shape (`Supplier<T>`, `Consumer<T>`, `Consumer<Throwable>`),
   so `core` is unchanged.
4. **Test — verb idempotence**: `graph {series:[k]}` twice → `refreshed: false` on the second; the same with
   `markers` re-sent unchanged; with `refresh: true` → `refreshed: "scheduled"` and the generation advanced; and a
   time-only `filter` re-sent with the range the chart last applied leaves a zoomed chart's view where it was
   (D-F8, pass-3 F3).
5. **Manual — the bundle loop**: with the audit-analyser-bundle running and follow on, append a CSV row and run
   `./export-audit.sh`; the open graph shows the new point within ~1.2 s (poll + debounce) without touching it,
   and a zoomed-in graph keeps its zoom. Screenshot before/after via the `screenshot` verb. **Done 2026-09-17 on
   the branch jar** (`m65-proof-*.png` in the exchange directory): extend proved at full extent (17 → 18 markers,
   axis to the new time); a zoom reaching past the live edge slid as then specified (19 markers, same width) —
   which is what prompted the rule-2 refinement above.
6. **Help** (`help/help.html`, the Follow bullet): today it says *"flags, filters and selection are preserved"* —
   which is true and stays as written. Add one clause: *"open graphs re-extract."* (Review F2: the first draft
   misread the preserved list as an updates-live list.)
7. **CHANGELOG ▸ Unreleased**: *"Follow now refreshes open graphs (a chart at full extent grows with the log, one
   pressed to the live edge slides with it, one zoomed into the middle holds); the slider echo no longer resets an
   unpinned zoom on every follow tick; the store is safe to read while follow appends; `graph` gains `refresh`;
   identical `graph` re-sends no longer re-extract on `markers`/`bands`."*

## Effort

D-F0 (both parts + test 2): half a day. D-F1 + D-F6 + D-F7 + D-F8 with tests 1 and 3, help, CHANGELOG: one day.
D-F4 + test 4: half a day. D-F5: not scheduled. **Two days**, against the first draft's one; the extra day is the
conditions and the view rules. Pass 2 added D-F8 and the reason merge without moving the estimate, as the review
judged.

## Review record

| item | source | landed as |
|---|---|---|
| C1 store publishes in the wrong order; `file` not volatile | review | D-F0 part 1 |
| — index arrays read unsynchronised, grown by reallocation | author, checking C1's fix | D-F0 part 2 (read view) |
| C2 no in-flight back-pressure, unbounded pool | review | D-F6 |
| C3 re-extract resets an unpinned zoom | review's "did not check", confirmed by author | D-F7 |
| F1 `refreshed: true` means scheduled | review | D-F4 echo wording |
| F2 help bullet misquoted | review | acceptance 6 |
| F3/F4 acceptance is asynchronous | review | acceptance 1 |
| F5 reports/coverage known stale | review | *Not in scope* + tracker note |
| F6 schema parity is automatic | review | D-F4 |
| poll already O(file) per tick | author | D-F5 cost note |
| C4 slider echo resets an unpinned view every growing tick, before extraction lands | pass 2 | D-F8; C3 paragraph corrected; acceptance 1 drives the full tick |
| C5 tail rule contradicted acceptance 1 | pass 2 | D-F7 rewritten as extend / slide / hold |
| F1 `reason` merge under coalescing | pass 2 | D-F7 DEFINITION-wins; acceptance 3 |
| F2 read view on the `LogStore` interface; carry `length` | pass 2 | D-F0 part 2 |
| F3 name the pool seam | pass 2 | acceptance 3 |
| F4 the hook is the only mover of an unpinned view on a data tick | pass 2 | Q1 preconditions; D-F8 |
| F1 capture order (size + arrays under the lock, `file` after) and the view serves `record(row)` | pass 3 | D-F0 part 2 |
| F2 empty-before → behave as `resetView` | pass 3 | D-F7 rule 0 |
| F3 D-F8 also silences identical programmatic ranges | pass 3 | D-F8 consequence; acceptance 4 |
| F4 the seam lives in `ui`, `core` unchanged | pass 3 | acceptance 3 |
| slide fired when the new point was already in view | M65.3 live proof | D-F7: hold when the new point is already visible (first as a rule-2 clause) |
| B1 extend contracted a zoomed-out view | impl review pass 4 | D-F7 restated with the visibility guard in front (rule 1) |
| F1 filter reads the live index arrays without happens-before on a grown reference | impl review pass 4 | D-F0: the `LogIndex` row arrays are `volatile` |
| F3 a synchronous runner throw left `extracting` set | impl review pass 4 | D-F6: cleared and rethrown |
| nit: that catch was wider than its producer | impl review pass 5 | narrowed to `RejectedExecutionException` |

# Spec — follow refreshes open graphs

**Status:** PROPOSED 2026-09-17 (owner question: *"what is the lowest overhead way of forcing the graph redraw?
should we add something to the plot verb to redraw for new log entries?"*). **Tracker:** [tracker.md](tracker.md) ▸ M65.
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

## Why — the causal chain (verified in source)

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

Cost: one full re-extract per poll tick that carried data — at most one per second, off the EDT, the same work a
dimension change costs today. Superseded runs are dropped by the existing `gen != extractGen` check. Zoom, Fit and
the slider stay cache-only; nothing in M6's smoothness rule changes.

### Q2 — should the `graph` verb get a redraw/refresh option?

**Not as the fix.** A verb flag leaves the *person at the UI* with a stale chart, makes the *agent* responsible for
knowing the chart is stale (it cannot tell — the verb echo does not carry a point count), and is redundant the
moment follow drives extraction. The verb's contract is *"define the graph"*; freshness belongs to the data path.

**Two small verb changes are still warranted**, because they close the inconsistency the investigation exposed:

- **`refresh: true`** — an explicit "re-extract this graph now" for an agent driving an analyser whose follow is
  off, or a log that was appended by something other than follow. Five lines: calls the same `onRecordsAppended`.
  Documented as *"rarely needed; follow does this for you"*.
- **Make the re-extract decision consistent across keys.** Today `series` re-extracts only on a new key while
  `markers`/`bands` re-extract on any presence. Either is defensible; having both is not. Decision: **a re-send
  that changes nothing re-extracts nothing** (cheap, idempotent), for every key — and `refresh` is the one word
  that means "do it anyway".

## Decisions

**D-F1 — follow invalidates open graphs.** `pollFollow` notifies `GraphTabs.onRecordsAppended()` whenever
`added > 0`. Every open `GraphPanel` re-extracts through its existing debounce. This is the fix; everything else
is optional.

**D-F2 — a pinned graph re-extracts but keeps its window.** Pinning fixes *what range the evidence shows*, not
*which records exist*. `reExtract → applyWindow` already honours the pin; no change needed beyond D-F1. The
pinned/unpinned distinction in `onFilterChanged` is untouched.

**D-F3 — the slider, zoom and Fit remain cache-only.** M6's rule stands: a time-only change never re-parses. The
follow tick is not a time-only change; it is new data, and is routed as such.

**D-F4 — `graph` re-sends are idempotent; `refresh: true` is the only forced re-extract.** `setMarkers`/`setBands`
gain the same "only if changed" guard as `addKeys`; `refresh` bypasses all guards. The echo gains
`refreshed: true|false` so an agent can see what happened.

**D-F5 — full re-extract first; incremental only on evidence.** A per-tick full walk is O(store) and is the same
cost the app already accepts for a filter change. An incremental extractor (start at the previous record count,
append to the existing `Series`) is the follow-up **if and only if** a measured log makes the full walk visible
(>~50 ms on the extraction thread, or the debounce coalescing more than one tick in steady state). Recorded
here so the cheaper thing is not built speculatively.

## Not in scope

- A general observer on `LogStore`. Six hand-listed consumers plus one is fine; an event bus is a refactor with
  no user-visible gain and is not this spec's problem.
- Refreshing **reports** or **coverage** on follow. Both may have the same staleness; each deserves its own
  one-line check, and this spec names them as candidates rather than pulling them in.
- S3 and large-file (off-heap) logs: follow does not apply to them today; nothing here changes that.

## Acceptance

1. **Test — `ui/FollowRefreshesGraphTest`** (headless): open a two-record log, bind a `GraphPanel` on a key,
   assert 2 points; append a third record to the file, call the poll (or the store append + the new hook
   directly), pump the debounce, assert 3 points **and** the marker legend count is 3. Repeat with the graph
   pinned: 3 points, window unchanged.
2. **Test — verb idempotence**: `graph {series:[k]}` twice → `refreshed: false` on the second; with
   `refresh: true` → `refreshed: true` and a re-extract observed (extraction generation advanced).
3. **Manual — the bundle loop**: with the audit-analyser-bundle running and follow on, append a CSV row and run
   `./export-audit.sh`; the open graph shows the new point within ~1.2 s (poll + debounce) without touching it.
   Screenshot before/after via the `screenshot` verb.
4. **Help** (`help/help.html`, the Follow bullet): add *"open graphs"* to the list of what updates live — today it
   names flags, filters and selection only, which is true and incomplete.
5. **CHANGELOG ▸ Unreleased**: *"Follow now refreshes open graphs; `graph` gains `refresh`; identical `graph`
   re-sends no longer re-extract on `markers`/`bands`."*

## Effort

D-F1 + acceptance 1, 3, 4, 5: half a day. D-F4 + acceptance 2: a further half day. D-F5: not scheduled.

# Handoff — M65 implementation, `feat/m65-follow-refreshes-graphs`

**For:** the reviewing session · **Author:** the implementing session (working from the bundle repo) · **Date:** 2026-09-17
**Branch:** `feat/m65-follow-refreshes-graphs`, five commits above `102029c3` (the pass-3 review on `main`).
**Spec:** [`docs/specs/spec-follow-refreshes-graphs.md`](../specs/spec-follow-refreshes-graphs.md) — READY after three
passes ([1](review_spec_m65_2026-09-17.md) · [2](review_spec_m65_pass2_2026-09-17.md) · [3](review_spec_m65_pass3_2026-09-17.md)).
**Tracker:** M65 ◧ → all four sub-items closed (.4 dropped by design).

Written so the review judges the code against the spec it already accepted, and so nothing below has to be
re-derived from the diff. Section 5 is the part worth the most attention: the judgement calls the spec did not
make for me, and the one rule I changed **after** review at the owner's instruction.

---

## 1 · What to read, in order

| commit | what | read first |
|---|---|---|
| `812bfe0d` | **M65.0 — D-F0**, the store is publishable while it grows | `parse/HeapLogStore.java` (`appendFrom` order, `readView()`), `index/LogIndex.java` (`rowSpans`), `parse/LogStore.java` (`ReadView` + default), the three extractors, `parse/HeapLogStoreReadViewTest` |
| `6f785402` | **M65.1/.2 — D-F1/2/3/4/6/7/8**, the hook, coalescing, landing rule, echo guard, `refresh` | `ui/GraphPanel.java` (the whole diff, ~166 lines), `ui/ExtractionRunner.java`, `ui/ChartPanel.java` (`viewX`), `ui/GraphTabs.java`, `ui/MainFrame.java` (one line), `ui/ActionExecutor.java`, `llm/VerbSchemas.java`, the two `ui/*Test` classes, `help.html`, `CHANGELOG.md` |
| `1c3d7737` | **M65.3** live proof + the D-F7 rule-2 refinement (§5.1) | `landWindow` in `GraphPanel`, the new test variant, spec D-F7 rule 3 |
| `4ec08cca` | docs only — pass-3 follow-ups folded into the spec | skip unless checking the spec matches the code |

```
git log --oneline 102029c3..feat/m65-follow-refreshes-graphs
git diff --stat 102029c3..feat/m65-follow-refreshes-graphs      # 20 files, +754 −58
```

## 2 · Decision → code map

| decision | where | how |
|---|---|---|
| D-F0.1 text published before rows; `file` volatile | `HeapLogStore.appendFrom` | `this.file = full` moved ABOVE the framing loop; field `private volatile String file` |
| D-F0.2 bounded read view, capture order | `LogIndex.rowSpans()` (new, `synchronized`, returns size + `offset[]`/`length[]` refs); `HeapLogStore.readView()` calls it, THEN reads `file` | `LogStore.ReadView` interface + `default readView()` (live, size fixed at creation) |
| D-F0 consumers | `SeriesExtractor` ×4 loops, `MarkerExtractor`, `SeriesScan` | `var view = store.readView(); for (row < view.size()) … view.record(row)` |
| D-F1 the hook | `MainFrame.pollFollow` → `GraphTabs.onRecordsAppended()` → `GraphPanel.onRecordsAppended()` | one line after `onFilterChanged()`; the panel calls `scheduleExtract(DATA)` (the existing 200 ms debounce) |
| D-F2 pinned re-extracts, keeps window | `landWindow` first branch → `applyWindow()` | no new code beyond D-F1, as the spec claimed |
| D-F3 slider/zoom/Fit cache-only | untouched | — |
| D-F4 idempotent re-sends; `refresh`; `refreshed` | `GraphPanel.setMarkers`/`setBands` early-return on `equals`; `ActionExecutor.doGraph` counts `panel.extractionRequests()` before/after; `VerbSchemas` gains `refresh` | echo is `"scheduled"` or `Boolean.FALSE` |
| D-F5 full re-extract, measure later | unchanged | no measurement taken (§6) |
| D-F6 one in flight | `GraphPanel.extracting` / `dirty`; `startExtraction` / `finishExtraction`; both `extractionRunner.run` callbacks call `finishExtraction()` | the generation check is kept for results |
| D-F7 extend / slide / hold; DEFINITION wins | `GraphPanel.ExtractReason`, `pendingReason`, `merge()`, `landWindow(reason, viewBefore, dataBefore)`; `ChartPanel.viewX()` | `viewBefore`/`dataBefore` captured BEFORE `chart.setSeries` (which `resetView`s) |
| D-F8 echo inert for an unchanged window | `GraphPanel.lastFrom/lastTo`, seeded in `bind`, updated only by `applyFilterWindow()`; `onFilterChanged` else-branch compares before re-windowing | — |
| seam (pass-3 F4) | `ui/ExtractionRunner` (package-private, `Background.run`'s shape); `GraphPanel.extractionRunner = Background::run`; package-private `setExtractionRunner`, `chart()`, `runPendingExtractionNow()`, `isExtracting()`, `markerPointCount()` | `core` unchanged |

## 3 · What was verified

- **`mvn test`: 1442 run, 0 failures, 0 errors, 14 skipped** (pre-existing skips) at `1c3d7737`. Sweep clean
  (the four-term check in CLAUDE.md rule 1 prints nothing).
- **`HeapLogStoreReadViewTest`** (3): a view is bounded when taken and a fresh one sees the appends; the interface
  default over a non-growing delegate is bounded; **a walker taking fresh views while the store is appended to 400
  times never throws** and reads every row's `logTime` correctly. Before D-F0 that walk could throw
  `StringIndexOutOfBounds` or `ArrayIndexOutOfBounds` on its thread; the graph's error path swallowed it.
- **`FollowRefreshesGraphTest`** (7): each tick is driven as `pollFollow` does — `appendFrom`, then
  `filter.setTimeRange(from, to)` with the SAME window (what `extendAbsMax → publish` sends), then the hook — so the
  D-F8 guard is exercised, not bypassed. Variants: full extent → extend; right edge at the old max → slide; zoomed
  into the middle → hold; **past the live edge with the new point already in view → hold, then slide when the next
  point lands outside** (§5.1); pinned → 3 points, window unchanged; the echo alone is inert and a real range change
  (and a programmatic re-send of an already-applied range, pass-3 F3) behaves as specified; a burst behind an
  in-flight extraction coalesces to one follow-up and a definition change wins the merge (view resets).
- **`GraphRefreshVerbTest`** (1): new graph → `"scheduled"`; identical `series` + `markers` re-send → `false`;
  `bands: []` over empty → `false`; changed markers → `"scheduled"`; `refresh: true` alone → `"scheduled"`.
- **M65.3 live, on the branch jar with the bundle following** (screenshots `m65-proof-*.png` in the analyser's
  exchange directory, NOT committed — temp files): extend at full extent (17 → 18 markers, axis to the new time,
  untouched, ~1 s); a zoom reaching past the live edge slid as then specified (19 markers, same width). The second
  is what prompted §5.1.

## 4 · What the reviewer must still check

1. **The D-F0 memory-model argument, end to end.** `ReadView.index()` returns the LIVE `LogIndex`, and the filter's
   `test`/`testExceptTime` read its arrays (`logTime`, `dimId`, …) unsynchronised for rows below the captured size.
   My claim: those rows were written before the `add` that published them released the lock; `rowSpans()` takes the
   same lock, so the reader has happens-before on every element below `size`; a stale array reference still holds
   them because `ensure()` grows by `Arrays.copyOf`. This is the same argument the existing `snapshot()` javadoc
   makes. I believe it; I have not had it checked by anyone else.
2. **`appendFrom` failure mid-frame.** With the text swapped first, an exception inside `RecordFramer.frame` leaves
   `file` new and the index partially extended. Before, `file` stayed old and the index was partially extended —
   which was the WORSE state (rows pointing past the text). I judged the new state strictly better; confirm.
3. **The `refreshed` echo's type.** `"scheduled"` (String) or `Boolean.FALSE` in one map value. JSON-friendly,
   heterogeneous. The spec asked for `"scheduled" | false`; if the manifest/MCP schema tests want a single type,
   say so.
4. **`extractionRequests` semantics.** It counts requests that WILL (re)extract, incremented in both
   `requestExtract` and `scheduleExtract`, before coalescing. So `refreshed: "scheduled"` can be reported for a
   request that D-F6 later merged into an in-flight run — still true ("a re-extract carrying your change is
   scheduled"), but note it.
5. **Swing-thread discipline.** `onRecordsAppended`, `runPendingExtractionNow`, the landing callbacks all assume the
   EDT, as `reExtract` always did. `Background.run` marshals via `invokeLater`; the test runner delivers inline on
   the test thread. Nothing new crosses a thread, but it is worth a read of `startExtraction`/`finishExtraction`
   re-entrancy: `finishExtraction` may call `startExtraction` which calls `extract` which sets `extracting = true`
   before returning — inside the previous run's `finally`. I traced it; a second pair of eyes is cheap.
6. **The debounce-timer guard.** The timer now runs `refreshKeys()` then `if (pendingReason != null) startExtraction()`.
   A structural filter change schedules DEFINITION so it still re-extracts as before; an immediate request
   (`addKeys` etc.) may CONSUME the pending reason first, after which the timer only refreshes keys. Intended;
   confirm no path relied on the timer re-extracting unconditionally.
7. **Build and run the jar.** Rule 4: Swing is not unit-tested. I did (M65.3) on this branch's jar; the reviewer
   should too, at least the extend case — it is a one-minute loop with the bundle following.

## 5 · Judgement calls — mine, not the spec's

### 5.1 The D-F7 rule-2 refinement (post-review, owner's instruction — commit `1c3d7737`)

The reviewed rule slid whenever the view's right edge was ≥ the old maximum. The live proof zoomed a chart so its
window ran PAST the live edge (09:04 → 11:28 against data ending 10:55); the new point landed at 10:56, inside the
window, and the chart slid anyway — nothing had been hidden, yet the view moved. The owner chose the hold. Rule 2 is
now `v1 >= oldMax && v1 < newMax`. Pressed exactly to the old edge still slides (the new point lands past it). Spec
D-F7 rule 3 records the change and why; the test variant pins both halves. **This is a change to a rule three
passes accepted, made after them.** If the review disagrees, the revert is one condition.

### 5.2 The read view carries `offset`/`length` refs, not copies

`rowSpans()` returns the live array REFERENCES (like `snapshot()` does), not copies — O(1) per walk. Correct by the
copy-on-grow argument in §4.1. A defensive copy would be O(n) per tick, which is the cost D-F5 is trying not to pay.

### 5.3 `refreshed` is derived from a counter, not from observing extraction

Alternative was to expose the extraction generation and compare; but a debounced request has not bumped the
generation when the verb returns. Counting requests is what "scheduled" means.

### 5.4 The seam's test hooks are package-private on `GraphPanel`

`chart()`, `setExtractionRunner`, `runPendingExtractionNow`, `isExtracting`, `markerPointCount`. Five package-private
methods on a production class, for tests in the same package — the pattern `GraphTabsBindIsNotAnEditTest` already
relies on for `bind`. `runPendingExtractionNow` deliberately skips `refreshKeys()` (it would hit the real pool).

### 5.5 Process departures, disclosed

- **Branch, not `main`** (CLAUDE.md rule 3 says main only). The owner asked for one feature branch; `feat/` branches
  already exist in the repo.
- **CHANGELOG in the same commit** (rule 2): the M65.0 commit carries no CHANGELOG line of its own; its user-visible
  effect is the second *Fixed* entry in `6f785402`. Judged one entry for one fix rather than two.
- **No `unreviewed-changes.md` ledger entry** — this is a branch for review, not a direct-to-main change.

## 6 · Not done, and why

- **D-F5 measurement** (extraction wall time per tick on the demo log and a ~100k-record log) — not taken. The
  spec gates incremental extraction on it; nothing here needs it to ship. Worth doing before a release if follow on
  large logs is a real use.
- **Acceptance 4's D-F8 verb-level case** is covered at panel level (`FollowRefreshesGraphTest`, a programmatic
  `setTimeRange` re-send), not through `ActionExecutor`'s `filter` verb. Same code path; one layer lower.
- **`RolledLogStore`'s append path** — not read (follow is heap-local-only; the interface default covers it).
- **Reports and coverage under follow** — named as known-stale in the spec's *Not in scope*; not touched.
- **Screenshots not committed** — they show only the bundle's demo symbols, but rule 1 says generated, not taken,
  and these were taken. They live in the exchange directory for the owner to look at.

## 7 · Repro for the reviewer

```bash
git checkout feat/m65-follow-refreshes-graphs
mvn test                                  # 1442, 0 failures
mvn -q package -DskipTests && java -jar target/fluxtion-auditlog-analyser-*.jar --rest
# open the bundle project + log + graph, click Follow, make a graph of rootNode.price;
# in the bundle: echo "XYZ,100.00,500" >> data/input.txt && ./export-audit.sh
# ~1 s later the chart has the point; zoom into the middle and repeat — it holds.
```

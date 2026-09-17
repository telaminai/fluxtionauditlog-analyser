# Review — M65 implementation (`feat/m65-follow-refreshes-graphs` at 64e83562)

**Date:** 2026-09-17 · **Reviewer:** Claude (this session) · **Handoff reviewed:**
[handoff_m65_implementation_2026-09-17.md](handoff_m65_implementation_2026-09-17.md) · **Spec:**
[spec-follow-refreshes-graphs.md](../specs/spec-follow-refreshes-graphs.md) (READY after
[pass 1](review_spec_m65_2026-09-17.md) · [pass 2](review_spec_m65_pass2_2026-09-17.md) ·
[pass 3](review_spec_m65_pass3_2026-09-17.md)). Commits read in the handoff's order: 812bfe0d, 6f785402, 1c3d7737.

## Verdict: NOT READY — one blocker, small; everything else accepted

**B1 — the extend rule contracts a view that was zoomed out.** `landWindow` rule 1 fires whenever the view covered
the whole old data range and sets the right edge to the new maximum *even when the new maximum is inside the
view*. A person who zoomed **out** (room on both sides of the data) has their right edge pulled back to the data
on every tick that carries a point. Reproduced with a probe test on the branch (below): view `[500, 3500]` over
data `[1000, 2000]`, a point lands at 3000 → view becomes `[500, 3000]`; the spec's D-F7 principle, and the
owner's own rule-2 refinement in 1c3d7737, say it should hold. The fix is one guard and one test variant; the
spec's D-F7 rule 1 needs the same clause. Details and the fix under *Conditions*.

The two questions the owner asked for a verdict on are answered in §2 and §3: the D-F0 argument is sound for the
case it states and `rowSpans()` is exactly `snapshot()`'s shape, but the **filter's** reads through
`view.index()` are a third case the argument does not cover (benign, rare, follow-up F1); the rule-2 change is
right and should stay — it is B1 that shows the same principle has to be applied to rule 1.

## 1 · What was verified

`mvn test` on the branch, my run: **1442 run, 0 failures, 0 errors, 14 skipped** (matches the handoff). Sweep
clean. Jar built and driven — see §5 for how far the live loop got.

| Decision | Claim in the handoff | Source at the commit | Result |
|---|---|---|---|
| D-F0.1 | text published before rows; `file` volatile | `parse/HeapLogStore.java` `appendFrom`: `this.file = full` above `RecordFramer.frame(...)`; field `private volatile String file` | **Confirmed.** |
| D-F0.2 | capture order: spans under the lock, then `file` | `HeapLogStore.readView()`: `index.rowSpans()` (a `synchronized` method returning `size` + the `offset`/`length` references) **then** `file` (volatile read); `RowSpans.offset/length` bounds-check against the captured size | **Confirmed** — pass-3 F1 applied exactly. |
| D-F0 consumers | every walk takes a view and reads `view.record(row)` | `SeriesExtractor` (4 loops), `MarkerExtractor`, `SeriesScan` — all `store.readView()`, `view.size()`, `view.record(row)` | **Confirmed.** `LogStore.readView()` default captures `size()` once and bounds-checks; `MappedLogStore`, `RolledLogStore`, `SpiLogStore` untouched. |
| D-F1 | hook after the echo, only on growth | `MainFrame.pollFollow` lines 3106–3112: `if (added == 0) return;` … `extendAbsMax` … `onFilterChanged()` … `graphTabs.onRecordsAppended()`; `GraphTabs.onRecordsAppended` iterates tabs; `GraphPanel.onRecordsAppended` → `scheduleExtract(DATA)` | **Confirmed.** |
| D-F2 | pinned: window fixed, data not | `landWindow` first branch `isPinned()` → `applyWindow()` | **Confirmed** (test `aPinnedChartReExtractsButKeepsItsWindow`). |
| D-F4 | idempotent re-sends; `refresh`; `refreshed` | `setMarkers`/`setBands` early-return on `equals`; `ActionExecutor.doGraph` counts `extractionRequests()` around the call; `refresh` → `onRecordsAppended()` (DATA, so the view is kept); `VerbSchemas` documents `refresh` | **Confirmed**; `GraphRefreshVerbTest` covers all five cases. |
| D-F6 | one in flight; both callbacks finish | `extract`: `extracting = true` before `extractionRunner.run`; success path in `try { … } finally { finishExtraction(); }` (so a stale-generation early return still finishes); error callback `err -> finishExtraction()`; `finishExtraction` drains `dirty` once | **Confirmed.** Re-entrancy traced (§4.5). |
| D-F7 | reason merged DEFINITION-wins across debounce and dirty | `merge()`; `pendingReason` set in both `requestExtract` and `scheduleExtract`, consumed in `startExtraction` | **Confirmed** (test `oneExtractionInFlight_andADefinitionChangeWinsTheMerge`). |
| D-F7 | `viewBefore`/`dataBefore` captured before `setSeries` | `extract` success path: `chart.viewX()` and `chart.dataBounds()` read before `chart.setSeries(merged)` | **Confirmed.** Rule 1 is B1. |
| D-F8 | echo inert for an unchanged window | `lastFrom`/`lastTo` seeded in `bind`, written only by `applyFilterWindow()`; `onFilterChanged` non-structural branch compares before re-windowing; the pinned branch untouched | **Confirmed** (test `theEchoAloneNoLongerMovesAZoomedChart…`, including the pass-3 F3 programmatic re-send). |
| Seam | `ExtractionRunner` in `ui`, `Background.run`'s shape | `ui/ExtractionRunner.java`; `core` unchanged; five package-private hooks on `GraphPanel` | **Confirmed.** |
| 4.6 | the debounce timer only extracts with a pending reason | the only `extractDebounce.restart()` caller is `scheduleExtract`, which always sets `pendingReason`; `bind`/`unbind` only `stop()` it | **Confirmed** — no path relied on the timer re-extracting unconditionally. |
| Tests | the tick is driven as `pollFollow` does | `FollowRefreshesGraphTest.Rig.tick`: `appendFrom` → `filter.setTimeRange(same)` → `onRecordsAppended` → `runPendingExtractionNow` | **Confirmed.** The echo is simulated as `setTimeRange` with the same pair rather than `extendAbsMax`; equivalent, since `publish` sends exactly that pair. |

## 2 · Owner question 1 — is the D-F0 memory-model argument sound?

**For the case the handoff states, yes.** Rows below the captured `size` were written inside `add`'s
`synchronized` block; `rowSpans()` acquires the same monitor, so every element write below `size` — including
writes into an array that `ensure()` had just reallocated — happens-before the reader's reads through the
references `rowSpans()` captured. A stale reference still holds those rows because `Arrays.copyOf` preserves the
prefix. This is `snapshot()`'s argument and `rowSpans()` mirrors it exactly: **references captured under the lock**.

**There is a third case the argument does not cover, and it is the one the filter is in.** `ReadView.index()`
returns the **live** `LogIndex`, and `FilterState.test` / `testExceptTime` read `logTime[row]`, `dimId[row]`,
`threadId[row]`, `flags[row]` … through the live fields, plainly, after the lock was released. If a later `add`
runs `ensure()` and swaps those references, the walker can observe the **new** reference without any
happens-before with the writes that filled it. The JMM then does not guarantee the copied prefix is visible
through that reference. That is not `snapshot()`'s argument — `Snapshot` captures the references and never reads a
newer one.

What it costs in practice: it can only happen at a growth boundary (a doubling: 1024, 2048, …), it can never throw
(the new array is larger, so the row is in bounds), and the worst outcome is one row filtered wrongly in one
extraction, corrected on the next tick. On x86 (TSO) it cannot happen at all; on AArch64 — the owner's machine —
it is permitted. **Not a blocker. Follow-up F1** with two fixes, either of which makes it sound by the JMM:
declare the row arrays in `LogIndex` `volatile` (a reference published by a volatile store carries its contents;
sixteen keywords, a load-acquire per element read on ARM, nothing on x86), or extend `Snapshot` with `length` and
have the read view hand the filter a `Snapshot` instead of the live index (bigger, but the cleaner shape).

## 3 · Owner question 2 — the rule-2 change after review (1c3d7737)

**Keep it.** The reviewed rule slid whenever the right edge was at or beyond the old maximum, which moved a view
that already contained the new point — motion with nothing revealed, the same class of thing pass 2's C4 was
about. The refinement (`v1 >= oldMax && v1 < newMax`) is the correct statement of tail-following: the view moves
only to reveal a point that would otherwise be hidden. The test variant pins both halves.

What the live proof did not show, and B1 does, is that the **same principle applies to rule 1**. Rule 1 still
moves the right edge to `newMax` when `newMax` is already inside the view. The two rules are one rule with the
guard in front:

1. `newMax <= v1` → **hold** (the new point is visible; nothing moves — this also covers the 1c3d7737 case);
2. else `v0 <= oldMin && v1 >= oldMax` → **extend** (right edge to `newMax`, left edge stays);
3. else `v1 >= oldMax` → **slide** (same width, right edge to `newMax`);
4. else → **hold**.

Stated this way the rule needs no "outside the view" clause on either branch, and the D-F7 text in the spec gets
shorter, not longer.

## 4 · Conditions

### B1 — rule 1 contracts a zoomed-out view (`GraphPanel.landWindow`)

Probe run on the branch (not committed — it fails):

```java
// FollowRefreshesGraphTest-style rig: data [1000, 2000], one key, inline runner
panel.chart().setViewWindow(500L, 3500L);      // zoomed OUT: room on both sides of the data
append rec(3000, 3); store.appendFrom(p); filter.setTimeRange(null, null);
panel.onRecordsAppended(); panel.runPendingExtractionNow();
// expected view [500, 3500] (the point at 3000 was already visible) — actual [500, 3000]
```

```
org.opentest4j.AssertionFailedError: hi: the new point at 3000 was already in view, nothing should move
  ==> expected: <3500.0> but was: <3000.0>
```

Fix: in `landWindow`, before rule 1, `if (newMax <= v1) { chart.setViewWindow((long) v0, (long) v1); return; }`
— or restructure as §3 — and add the variant *a chart zoomed out past both ends holds until the live edge
reaches it* to `FollowRefreshesGraphTest` (then a second tick past 3500 must **extend**, not slide, because the
view still covers the whole old range). Update spec D-F7 rule 1 (or adopt §3's four-line form) and the review
record. Zoom-out is unclamped (`ChartPanel.zoomAroundCentre`, `zoom`), so this is an ordinary interaction, not
an edge case.

## 5 · The live loop — how far it got, and what is left for the owner

Done on the branch jar, driven over `--rest` with an isolated `user.home` so the endpoint file of the two analysers
already running on this machine was not overwritten:

- `open` the bundle's exported log → 115 records; `graph {newTab, name, series:["rootNode.price"]}` →
  `refreshed: "scheduled"`, `resolved: ["rootNode.price"]`; `series {expr:"rootNode.price"}` → 40 points;
  `screenshot` → the chart is drawn, the Follow toggle is enabled.
- **Not done: the extend tick itself.** Follow is a toolbar/menu toggle with no verb behind it, and synthetic
  input from this session (`java.awt.Robot` clicks, System Events) is not delivered to the app on this machine
  without an accessibility grant; injecting a call into the running JVM was out of bounds for a review. So
  **the M65.3 live proof stands as the implementer's, not independently reproduced.** The analyser I launched is
  still up with the graph open; one click on *Follow* and one `./export-audit.sh` from the bundle finish the loop.
  Also: my first `run-server.sh` collided on port 8181 with an instance that then disappeared, and a second start
  was made; check `logs/` grows on export before reading the chart.
- **Observed, not attributed:** right after launching the jar with the log as a command-line argument,
  `context` reported the log loaded (115 records) but `graph` answered *"could not open a graph (no log loaded)"*;
  after `open {log}` over the verb the same `graph` call succeeded. M65 did not touch that path; worth one look
  on `main` before assuming it is new.

## 6 · Findings (not blocking)

- **F1 — the filter reads the live index arrays without happens-before** (§2). `volatile` on the row arrays, or a
  `Snapshot` carrying `length` handed to the filter. Note the choice in D-F0 either way.
- **F2 — `appendFrom` failure mid-frame (handoff 4.2): agreed, strictly better for readers**, with one thing to
  write down. After the swap-first order, an exception inside the framing loop leaves `file` new and the index
  partially extended — every indexed row has valid spans, so no reader can throw. Recovery moves, though: the next
  tick sees `full.length() == file.length()` and returns 0, so the unindexed tail is picked up only when the file
  grows again (the framer skips `before` records and adds the rest, so nothing is lost). Before, the retry was
  immediate but readers could throw meanwhile. One comment in `appendFrom`.
- **F3 — `extracting` is set before `extractionRunner.run` and nothing clears it if `run` throws
  synchronously.** `Background.POOL` is a cached pool, so today that is only `RejectedExecutionException` at
  shutdown — but a panel left with `extracting == true` never extracts again; every later request only sets
  `dirty`. Wrap the `run` call: on a synchronous throw, clear `extracting` and rethrow. Two lines.
- **F4 — `refreshed` type and semantics (handoff 4.3, 4.4): accepted as is.** A heterogeneous `"scheduled" |
  false` is fine on the wire and the schema tests pass; counting requests before coalescing is the honest reading
  of "scheduled". No change.
- **F5 — D-F5 measurement not taken** (handoff §6). Agreed it does not gate shipping; the tracker should carry it
  as the open item it is, with the trigger the spec names.

## 7 · Did not check

- `RolledLogStore`'s append path and follow over a rolled set (scoped out by the spec and the handoff).
- The jar on a **real** follow tick end-to-end (§5) — the unit test drives every step of `pollFollow` after
  `appendFrom` except `extendAbsMax` itself, which it simulates with the pair `publish` would send.
- Whether the *"no log loaded"* answer from `graph` after a command-line open is pre-existing.
- The screenshots in the exchange directory named by the handoff (not committed; not needed for the verdict).

## Review record

| Pass | Commit reviewed | Verdict | Conditions |
|---|---|---|---|
| spec 1 | 5cb9121e | CONDITIONAL | C1 store publication, C2 in-flight back-pressure |
| spec 2 | 88f17de7 | CONDITIONAL | C4 echo path defeats the hold, C5 one tail rule |
| spec 3 | d745006c | READY WITH FOLLOW-UPS | F1 capture order (applied in 812bfe0d) |
| **impl 4** | **64e83562** | **NOT READY** | **B1 rule 1 contracts a zoomed-out view; F1 live-index reads; F3 `extracting` on a synchronous throw** |

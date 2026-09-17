# Review — M65 spec, third pass (`spec-follow-refreshes-graphs.md` at d745006c)

**Date:** 2026-09-17 · **Reviewer:** Claude (this session) · **Earlier passes:**
[pass 1](review_spec_m65_2026-09-17.md) · [pass 2](review_spec_m65_pass2_2026-09-17.md)

## Verdict: READY WITH FOLLOW-UPS — no further spec review needed

Both pass-2 conditions and all four follow-ups are folded in faithfully: D-F8 guards the slider echo where the
review recommended, D-F7 is one extend / slide / hold rule with the DEFINITION-wins merge, the read view moved to
the `LogStore` interface and carries `length`, the seam is named, acceptance 1 drives the tick as `pollFollow`
does. The C3 paragraph is corrected. The estimate is unchanged and I agree it should be.

One follow-up (F1) is a wording fix the **implementer must apply** before writing D-F0 part 2, because the spec's
current sentence cannot be implemented as written and the obvious reading reintroduces the race C1 closed. It is
one sentence; it does not need another review pass.

## What was verified this pass

| Premise the revision relies on | Source | Result |
|---|---|---|
| `appendFrom` frames and `index.add`s each new record, then assigns `this.file = full`; the index lock is taken per `add`, never around the file swap | `parse/HeapLogStore.java` `appendFrom` | **Confirmed** — this is why F1 matters: "captured together under the index lock" is not available for `file`. |
| `rawText(row)` = `file.substring(offset, offset + length)`; `record(row)` = parse of `rawText(row)` with `index.offset(row)` | same file | **Confirmed.** The view must serve `record(row)`, not only `size()` (F1). |
| `Background.run(Supplier<T>, Consumer<T>, Consumer<Throwable>)` on a cached pool | `core/Background.java` line 36 | **Confirmed.** The seam is that three-argument shape; it lives in `core`, not `ui` (the spec does not say either). |
| `SeriesExtractor` has four `store.size()` loops | `graph/SeriesExtractor.java` | **Confirmed** (4). |
| `setViewWindow` stores `vx0 = lo; vx1 = hi` with **no x padding** (padding is Y only); `resetView` stores `vx0 = gx0; vx1 = gx1` | `ui/ChartPanel.java` lines 293, 311, 344–345 | **Confirmed.** D-F7's "left edge ≤ oldMin and right edge ≥ oldMax" holds with equality at full extent, so rule 1 fires for a never-zoomed chart. |
| `reExtract` bumps `extractGen`, runs through `Background.run`, drops a stale result, then `setSeries` / `setBands` / … / `applyWindow` on the EDT | `ui/GraphPanel.java` 834–900 | **Confirmed.** The D-F7 landing point and D-F6's success/error callbacks exist as the spec describes. |
| The structural debounce is a Swing `Timer` calling `reExtract()` | `GraphPanel.java` line 106 | **Confirmed.** There is no request object today; the `reason` "riding the request" will be a panel field merged on each request and read when `reExtract` starts (implementation note, not a finding). |
| Programmatic time-range setters exist besides the slider | `ui/ActionExecutor.java` 258, 536; `graph/SeriesScan.java` 55; `report/FilterSnapshot.java` 69 | **Confirmed** — see F3. |

## Follow-ups

- **F1 — required before D-F0 part 2 is written: state the capture order, and that the view serves `record(row)`.**
  The spec says the read view captures `size`, `file` and the `offset`/`length` arrays "under the index lock". The
  arrays and `size` can be (they are `LogIndex` privates, so the capture is a new `synchronized` method on
  `LogIndex`, or `Snapshot` extended with `length`). **`file` cannot**: it is a `HeapLogStore` field and
  `appendFrom` swaps it outside the index lock. The correct form, given D-F0 part 1 (swap `file` first, `volatile`):
  **read `size` and the arrays under the lock, then read `file` after releasing it.** Writer order is `file = full`
  (volatile) → `add(k)` (lock); a reader that sees row *k* under the lock therefore sees a `file` that contains it.
  Reading `file` *before* the lock is wrong — an old string with a new size, the exact C1 race. And the walk must
  call `view.record(row)` / `view.rawText(row)` served from the captured arrays and string; if it still calls
  `store.record(row)` it reads the live arrays unsynchronised, which is the array race part 2 exists to close.
  One sentence for the order, one for the view's surface. No re-review needed; I will check it in code review.
- **F2 — the empty-before case in D-F7.** When the previous extraction produced no points (`vx0` is `NaN`, no
  `oldMin`/`oldMax`), a `DATA` landing should behave as `resetView` (the first data is an "extend from nothing").
  The rule as written has nothing to compare against. One clause.
- **F3 — D-F8 also silences identical programmatic ranges.** `ActionExecutor` (two verbs), `SeriesScan` and
  `FilterSnapshot` (report re-issue) call `setTimeRange` directly. With the `lastFrom`/`lastTo` guard, a verb that
  re-sends the range the chart last applied while the person has zoomed no longer re-windows the chart. That is
  consistent with M6 (a time-only change is a view change, and the view did not change) and an agent that wants
  the window reset has Fit and the zoom verbs — but say so in D-F8 so nobody files it as a regression, and add it
  to acceptance 4's idempotence list.
- **F4 — say where the seam lives.** `Background` is in `core`, not `ui`. The `extractionRunner` field's type is
  the three-argument `Background.run` shape (`Supplier<T>`, `Consumer<T>`, `Consumer<Throwable>`); a
  package-private functional interface in `ui` beside `GraphPanel` keeps `core` unchanged.

## Did not check

- `RolledLogStore` follow behaviour (unchanged from pass 2; the spec scopes it out).
- Whether the reports panel's echo-driven refresh is the *only* reason not to guard in `FilterState` (pass 2 said
  "not without reading every listener"; D-F8's choice makes the question moot).

## Review record

| Pass | Commit reviewed | Verdict | Conditions |
|---|---|---|---|
| 1 | 5cb9121e | CONDITIONAL | C1 store publication, C2 in-flight back-pressure |
| 2 | 88f17de7 | CONDITIONAL | C4 echo path defeats the hold, C5 one tail rule |
| 3 | d745006c | READY WITH FOLLOW-UPS | none; F1 capture order is a required wording fix, applied by the implementer |

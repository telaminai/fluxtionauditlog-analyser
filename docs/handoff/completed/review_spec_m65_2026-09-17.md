# Review — `spec-follow-refreshes-graphs.md` (M65), 2026-09-17

Reviewed `7b9e09cc` on `main`. Independent of the author's session; every claim below was checked against source
at that commit, not inferred from the spec. Design review only — nothing implemented, nothing changed.

## Verdict: **CONDITIONAL** — the diagnosis is right and the fix is the right one; two things the spec does not see must be in it before implementation

## What I confirmed

The causal chain (§ *Why*) is correct at every step, by reading:

- `MainFrame.pollFollow` fans out by hand to the table, the slider (max and histogram), `onFilterChanged`, the
  scroll and the status line; `graphTabs` is absent.
- `GraphPanel.onFilterChanged` computes `structural` from dimensions, text and group mode only; an append lands
  in the `else` branch (`chart.setViewWindow` over the cached series) or, pinned, in nothing.
- `reExtract` is private; its callers are the 200 ms structural debounce, the series-panel edits, `addKeys`
  guarded by `if (added)`, `addExpr`, and `setMarkers`/`setBands`, which re-extract **unconditionally** — the
  accidental cure the investigation saw, exactly as stated.
- `ActionExecutor.doGraph` reuses the tab (`graphForAction`) and calls `addKeys`, so an identical re-send is a
  no-op for `series` and a re-extract for `markers`/`bands`.
- `GraphTabs.refreshFlagRug` is the loop shape the proposed `onRecordsAppended` mirrors.
- `reExtract`'s success path drops a superseded generation, sets series/bands/markers and calls `applyWindow()`,
  which honours the pin — so D-F2 needs no code beyond D-F1, as claimed.

Q1's answer (route the follow tick into the existing debounce; keep zoom/Fit/slider cache-only) is the lowest-
overhead fix available and the correct layering. Q2's answer (no verb flag as the fix; `refresh` as an explicit
extra; make re-sends idempotent) is right.

## Conditions

### C1 · P1 — the extractor walks a store that another thread is appending to, and the store publishes in the wrong order

`SeriesExtractor.extract` loops `for (row = 0; row < store.size(); …) store.record(row)` on the **live** store,
off the EDT (`Background.run`). `HeapLogStore.appendFrom` runs on the EDT (the poll timer) and does, in this
order: `index.add(...)` for each new record, **then** `this.file = full`. `rawText(row)` is
`file.substring(index.offset(row), … + index.length(row))`. So between the index growing and the field swap, a
row exists whose offsets point past the end of the OLD `file` string — a walker that reaches it throws
`StringIndexOutOfBoundsException` on the extraction thread (swallowed by `err -> { /* best-effort */ }`, so the
chart simply fails to update). `file` is a plain field, not `volatile`, so the swap is not guaranteed visible
either. Today this race needs a structural change to coincide with a poll tick; **D-F1 makes the walk happen on
every tick that carried data, so it becomes the steady state.**

Required: make the store's append safe for a concurrent reader before wiring D-F1. The cheapest correct form:
assign `this.file = full` **before** the framing loop that adds index rows (old rows keep their offsets and
lengths — the file is append-only — so a reader with an old `size` reads correctly from the new string), and make
`file` `volatile`. `LogIndex.add` is already `synchronized`, and `size` is an int read. State this in the spec as
its own decision (D-F0: *the store is publishable while it grows*), and pin it with a test that appends while a
walker is mid-log (a latch inside a test extractor, or a store spy).

### C2 · P2 — D-F5's "full re-extract per tick" has no back-pressure, and the pool is unbounded

`Background.POOL` is `newCachedThreadPool`. The generation check drops superseded **results**; it does not stop
superseded **work**. With follow at ~1 Hz and an extraction that takes longer than a second (a large log, or several
formula series with windows), every tick starts another thread walking the whole store, and they accumulate.
The spec's D-F5 threshold (">~50 ms") is the right instinct but is a measurement, not a guard.

Required: coalesce in flight, not only in the debounce — an extraction requested while one is running sets a
`dirty` flag and a single follow-up runs when the current one lands (one line in the success/error callbacks, one
in `onRecordsAppended`). Then D-F5's incremental extractor can stay unscheduled with a clear conscience. Also state
the measurement D-F5 wants concretely: the demo log and one ~100k-record log, extraction wall time per tick, on
the CI machine class.

## Findings, non-blocking

- **F1 — `refreshed: true` means scheduled, not done.** The verb returns synchronously; the re-extract is a
  debounce plus an off-EDT walk. An agent that reads `refreshed: true` and immediately screenshots may still see
  the old chart. Say so in the echo (`refreshed: "scheduled"`) or in the schema text; the `series` verb, which
  re-extracts on every call, is the way to READ fresh values, and the chart is what lags.
- **F2 — Acceptance 4 misquotes the help bullet.** The Follow bullet says flags, filters and selection are
  *preserved* across an append, not that they are what *updates live*. The addition should read "open graphs
  re-extract" and leave the preserved list alone.
- **F3 — Acceptance 1 is asynchronous.** "Pump the debounce" is not enough: the extraction runs on the pool and
  lands via `invokeLater`. The headless test must wait for the generation to land (poll the chart's series count
  with a deadline, or expose the pending generation), or it will be flaky in CI. `GraphTabsBindIsNotAnEditTest`
  shows a headless `GraphPanel` bind is possible.
- **F4 — the marker legend count** (acceptance 1's second assertion) comes from `setMarkers` on the success path,
  so it follows the same landing; assert it after the same wait.
- **F5 — reports and coverage** are correctly named as candidates and left out. A one-line note in M65's tracker
  entry that they are *known stale under follow* would stop the next reader re-discovering it.
- **F6 — schema parity is automatic.** `refresh` added to `VerbSchemas` reaches both the REST manifest and the
  MCP tool list through the existing parity tests; no extra work, worth one sentence so nobody adds it twice.

## What I did not check

I did not run follow against the bundle or measure an extraction; the observation table in the spec is the
author's. I did not read `RolledLogStore`'s append path (follow is heap-local-only, as the spec says). I did not
verify that `ChartPanel.setSeries` under `applyWindow` preserves a user's zoom that is NOT a pin — D-F3 says
zoom stays cache-only, but a re-extract calls `setSeries` then `applyWindow`, and whether an un-pinned zoom
survives that is worth one assertion in acceptance 1 (it may reset to the filter window on every tick, which a
person watching a live chart would notice).

## Effort

The spec's day is right for D-F1–D-F4 once C1 and C2 are folded in; C1 is an hour including its test, C2 a
couple of lines and a test.

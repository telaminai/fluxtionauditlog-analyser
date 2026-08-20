# Graph Artifacts — Pinned Range + Derived Series (Design Spec)

Status: DRAFT v1 · Owner: greg.higgins · Last updated: 2026-08-12

Companion to **[spec-assistant-actions.md](spec-assistant-actions.md)** (the `graph` verb + `GraphSpec`),
**[spec-assistant-actions-mcp.md](../spec-assistant-actions-mcp.md)**, and **[tracker.md](../tracker.md)**
(milestone **M14**).

Two features that turn an LLM-built graph from a *view* into an **artifact** — a chart that keeps meaning
after the investigation moves on:

- **A · Pinned range** — a graph can hold a fixed time window instead of following the shared filter.
- **B · Derived series** — plot a formula over `instanceId.key` values (e.g. `askMakerOrder.price −
  bidMakerOrder.price`), which the UI can't author ergonomically but the LLM emits effortlessly.

Both slot into existing seams — `GraphSpec`, the `graph` verb params, and `SeriesExtractor`. No new
architecture. Together they upgrade the action loop's endgame: an investigation can end with a **pinned,
formula-defined, named** chart ("pick-off exposure, 02:03:40–02:05:00") that survives filter changes,
persists in the profile, and is one `export_promotion` (M11) away from a Grafana panel.

---

## A. Pinned range

**Problem.** A graph today follows the *shared* filter's time window. Set the filter to a 9 ms episode,
build a chart, then reset the filter → the graph rescales to the whole log and the episode is an invisible
sliver. An evidence graph that mutates when the investigation moves on isn't evidence.

**Behavior.**
- `GraphSpec` gains nullable `from` / `to`. **null = follow the shared filter** (today's behavior);
  **an explicit range = pinned** to that window.
- The `graph` verb's `from` / `to` params — already defined in the schema but currently no-ops — now
  mean **"pin to this window."** Sensible default: an LLM-supplied explicit range **pins**; no range
  **follows**.
- The tab shows a **📌 indicator** when pinned; **Unpin** re-links it to the shared filter (clears
  from/to). A UI **Pin to current window** action captures the filter's current range — and when the filter
  is **unbounded** (from/to both null), it captures the **effective data bounds** at that moment (the
  series' min/max time), *not* nulls (which would mean "following"), so the pin is a real fixed window.

**Seam (cheap, thanks to the smoothness work).** A graph already extracts *across all time* and windows via
`ChartPanel.setViewWindow` (the redraw-smoothness fix). So:
- **Following** graph: on a time-only filter change → `setViewWindow(filter.from, filter.to)` (today).
- **Pinned** graph: **ignore** the filter's time change → `setViewWindow(pinned.from, pinned.to)` once, and
  leave it. Structural changes (dimensions/text/keys) still re-extract (across all time) and re-window to
  the pinned range.

**Y-axis:** auto-fit to the points in the pinned window (as `setViewWindow` already does). Optional
Y-pinning is an open question (§9), not v1.

**Persistence:** free — `GraphSpec` already round-trips through `config/ConfigStore`; add `from`/`to`.

---

## B. Derived series (formula over node-log values)

**Problem / asymmetry.** The series this kind of log most wants — `quoted spread =
askMakerOrder.price − bidMakerOrder.price`, `distance-from-fair =
bidMakerOrder.price − contraPositionToHedgeQuantityCalculator.rate` — are painful to author through
combo-boxes and dialogs, but the LLM emits `{"label":"quoted spread","expr":"askMakerOrder.price −
bidMakerOrder.price"}` effortlessly, **and it knows which keys exist because it just read the records.**
Agent-first feature authoring, honestly labeled.

### B.1 A tiny arithmetic grammar — not a scripting engine

LLM-authored formulas executed in-app are a **code-injection surface** if you reach for
JEXL/Nashorn/anything eval-like. Instead, a **~100-line Pratt parser** over:

- **references** `instanceId.key` (the same identity as a `GraphKey`; dots-in-key handled by the parser
  matching against known keys, or by quoting: `` `liverOrder.leavesQuantity` ``),
- **numeric literals**, `+ − × ÷`, **parentheses**, and a small function set: `abs`, `min`, `max`
  (v1) — extensible later.

The lexer **accepts both ASCII and Unicode operators** (`-`/`−` U+2212, `*`/`×`, `/`/`÷`) — the stated
author is an LLM, which will emit the Unicode forms (the spec's own examples use `−`); one line of lexer
tolerance avoids a whole class of one-round parse errors.

This is **hermetic** (no eval), matches the project's zero-dep ethos (like the bespoke `Json` codec), is
**trivially safe**, and — the part that matters downstream — is **serializable and portable**: a persisted
expression string can travel through `export_promotion` to the Grafana pipeline (M11), where the tap plugin
evaluates the same grammar or the dashboard JSON maps it to a Grafana transform. *Code can't make that trip;
a little algebra can.*

### B.2 Reference resolution — LOCF by default (the decision that makes it work)

An event-audit record is one cycle: it logs only the nodes that fired, so two refs in a formula may **not
co-occur** in the same record. Strict per-record eval would make `quoted spread` sparse/empty exactly when
it matters. So the resolution *policy* is first-class:

- **LOCF (default, recommended):** scanning records in **row (event-sequence) order**, carry each ref's
  **last-known value**; a record that updates *any* ref yields a point using last-known values for the
  others. O(1) state per referenced key — not windowing — so it stays v1 and is cheap. This is what makes
  cross-node formulas actually plot.
  - **Carry vs clear (must-spec, review):** a **finite** observation **updates** the carry; an **explicit
    NaN** observation **clears** the carry (that ref becomes "no value" → the derived point is omitted until
    a finite value returns); a **key absent** from the record leaves the carry **unchanged**. This matters:
    `MakingOrderNode` logs `price: NaN` after a cancel — the node is explicitly saying "I have no value."
    Skipping the NaN and carrying the old price would plot the pre-cancel spread straight through the
    no-quote period — **fabricated continuity through exactly the episodes the tool exists to expose.**
  - A ref **never yet seen** → point omitted (existing NaN-as-no-point semantics).
- **strict (opt-in `"resolve":"record"`):** all refs must be present in the *same* record, else the point
  is omitted. Useful when co-occurrence is itself the signal.

*(Iteration order — resolved, §I.2: use **row order**, which is exact for a single-logger file — the audit
log is written by one agent thread in causal order, and same-millisecond records within a burst must not be
reordered by a `logTime` sort. For a multi-processor log (several loggers interleaved), keep LOCF state
**per-logger**, or extract against a single logger.)*

### B.3 Scope — v1 per-record(+LOCF); v2 cross-record

- **v1:** every ref resolves to a **single value** (this record, or last-known via LOCF); the expression is
  pure arithmetic over those. Missing/non-numeric ref → point omitted.
- **v2 (not v1):** cross-record transforms where the interesting series live — `delta(x)`, inter-arrival
  `dt(x)` (the 15-second-cadence discovery is `dt(onMultilevelMarketData)`), `rolling(x, 1m)`. These need
  windowed state in the extractor and ship later as a small set of **named transforms wrapping a series**.
  Don't let them smuggle into v1. §B.6 defines the model.

### B.6 Stateful / windowed functions (v2) — bounded, not a scripting engine

"Stateful" has a cheap version and an expensive version; commit to the cheap one. The rolling window is
**not too complex** *if* it's scoped as a **closed set of pure `Series → Series` transforms applied
top-level**, and it becomes a mess if it's allowed to be arbitrary stateful scripting or to nest inside the
per-record arithmetic. The rules:

- **Transforms are pure functions over an already-extracted series** — a v1 expression produces a point
  array `(t, v)`; a transform maps that array to a new one. No user state, no eval — the same hermetic
  posture as v1.
- **Closed set (v2.0):** `delta(x)` (successive difference), `dt(x)` (inter-arrival Δt), `rate(x, w)`
  (count/window), `rolling(x, w, agg)` with `agg ∈ {avg,min,max,sum,count}`. `w` is a duration
  (`30s`,`1m`,`5m`). Each is O(n) over the time-sorted points with a two-pointer/deque window.
- **Top-level wrapping only (the rule that keeps it simple):** `rolling(askMakerOrder.price, 1m, avg)` is
  fine; **mixing** a transform *inside* arithmetic — `askMakerOrder.price - rolling(bidMakerOrder.price,
  1m, avg)` — is **not v2.0**, because subtracting a per-record scalar from a windowed series forces
  **series alignment** (two series with different time axes → merge + LOCF at the series level). That
  "everything is a series, arithmetic aligns them" model is strictly more powerful and is the natural
  **v2.1**, but it changes the execution model (per-record eval → series algebra) and carries the
  alignment cost, so it is called out separately, not smuggled in.
- **Portable, again:** each transform maps to a **native Grafana transform** (`delta`→Difference,
  `rolling avg`→Moving average, `rate`→derived/rate), so a v2 `GraphSpec` still promotes cleanly (M11).

**Complexity verdict:** moderate and cleanly separable — a handful of O(n) windowing functions over a point
array, unit-testable in isolation. The things that would make it hard (arbitrary user state; transforms
mixed into arithmetic → series alignment) are explicitly deferred, so v2.0 stays a small, safe increment on
v1, not a new engine.

### B.4 Errors name the failing ref

A formula error must be **actionable in one round** (the principle threaded through the whole action
schema): `{"ok":false,"error":"unknown key 'askMakerOrder.prce' in expr 'askMakerOrder.price − …' (did you
mean 'askMakerOrder.price'?)"}`. Parse errors point at the offending token; unknown refs name the ref (and
may suggest the nearest known key).

### B.5 Seam

- `graph/Expr` — the AST (Ref / Num / Unary / Binary / Call) + a `parse(String, Set<knownKeys>)` Pratt
  parser + `eval(Map<GraphKey,Double> refValues)`. Pure, headless, unit-tested. No dependency.
- `graph/SeriesExtractor.extractExpr(store, filter, Expr, label, acrossAllTime, resolvePolicy)` — one
  forward pass; per record, update the LOCF ref map (or gather this-record refs for strict), `eval`, and
  add `(logTime, value)` when finite.
- **Series identity:** a derived series is identified by its **label**, not a `GraphKey`. Give `Series` an
  optional display label (defaults to `key().display()` for raw series) so the legend/CSV show the formula
  label.

---

## C. Action schema extension (the `graph` verb)

Backwards-compatible additions to the existing params (spec-assistant-actions §4.3):

```jsonc
"params": {
  "name": "Pick-off exposure",
  "series": ["bidMakerOrder.price", "askMakerOrder.price"],      // raw keys (as today)
  "exprs":  [{ "label": "quoted spread",
               "expr":  "askMakerOrder.price - bidMakerOrder.price",
               "resolve": "locf" }],                              // derived series (new)
  "from": 1754449420000, "to": 1754449500000,                    // PIN the window (already defined; now honored)
  "style": "step"                                                // newTab omitted → reuse the named graph if
}                                                                //   it exists, else create (the §4.3 default)
```

- `exprs` — zero or more `{label, expr, resolve?}`; each becomes a derived series named `label`.
- `from`/`to` present → the graph is **pinned** to that window; absent → follows the filter.
- Echo (per the resolved-vs-unresolved pattern): `{name, resolved:[…], unresolved:[…],
  exprs:[{label, ok|error}], pinned:{from,to}}` — so a bad key or a parse error is fed back.
- Rename still `{name, rename}`; unchanged.

---

## D. GraphSpec + persistence

`GraphSpec(name, series)` → `GraphSpec(name, series, exprs, from, to)` where `exprs` is a list of
`{label, expr, resolve}`. `ConfigStore` gains `graph.i.from`, `graph.i.to`, and `graph.i.expr.j.{label,expr,resolve}`.
Round-trips exactly like the existing fields (a new `ConfigStore` test locks it). Old profiles (no exprs /
no from-to) load as following, raw-key graphs — fully backward compatible.

---

## E. UI

- **Pinned:** a 📌 on the tab; **Pin to current window** / **Unpin** buttons (Unpin clears from/to → follows).
- **Derived:** a plain **text field** ("f(x): expr", with an optional label) — no formula-builder dialog in
  v1. Honest agent-first: the UI can author simple formulas, the LLM authors the gnarly ones.
- **Formula management (shipped after v1):** a per-graph **formulas combo** lists the active derived
  series with **Edit** (loads the formula back into the fields; Add applies the change, renames included)
  and **Remove**. The f(x) field has **inline autocomplete** over the union of discovered keys and the
  existing formula labels.
- **Formulas can reference other formulas** by label (bare token, or `` `backticked` `` for labels with
  spaces): references are expanded textually (parenthesised) before parsing, chains across passes, a real
  key always wins over a same-named label, and cycles are broken by a pass cap (the leftover label then
  fails with the usual actionable unknown-key error).
- Both are labelled "built by assistant" like other action-built views, and are reversible.

---

## F. Grafana portability (M11 payload)

This is why the grammar is algebra, not code. A `GraphSpec` with `{exprs, from, to}` is the **most valuable
payload** for the research→monitoring promotion: `export_dashboard` (M11) emits (a) the metric allowlist
(the raw keys the exprs reference) for the tap plugin, and (b) a Grafana dashboard JSON where each expr maps
to a **Grafana transform / expression** and the pinned range seeds the panel's default window. An
investigation that ends in a pinned, formula-defined chart is one click from a production panel.

!!! warning "Rescoped — see tracker M11.1 (2026-08-20)"

    Still true, and still the reason the grammar is algebra: a `GraphSpec` with `{exprs, from, to}` is
    the payload. What changed is who renders it. The analyser emits a **neutral promotion manifest** —
    the allowlist, the series definitions, guide thresholds, the pinned window, the rationale — and an
    **agent** maps the exprs onto Grafana transforms. Mapping a foreign schema is the agent's job under
    the same rule that keeps FIX out of the loader (M29) and parquet out of the core (M31).

---

## G. Delivery slices

1. ✅ **DONE** — **Pinned range.** `GraphSpec` + from/to; `GraphPanel` logical name + pin state, windows via
   `setViewWindow` (`applyWindow`); 📌 toggle (pins the filter window, or effective data bounds when
   unbounded) + tab 📌 indicator; graph verb from/to honored + echoed; `ConfigStore` persists it.
   `ConfigStoreTest.savedGraphsRoundTripPinnedWindow`; 127 tests green.
2. ✅ **DONE** — **Expr engine** `graph/Expr`: sealed AST + recursive-descent parser + evaluator (refs,
   arithmetic, abs/min/max, ASCII+Unicode operators, div0/missing→NaN, error-names-the-ref + nearest
   suggestion, `refs()`). Pure/headless. `ExprTest` (9); 136 total green.
3. ✅ **DONE** — **Derived extraction** `SeriesExtractor.extractExpr` (row-order **LOCF** + `STRICT`;
   finite-updates / NaN-clears carry rule; non-finite omitted) + `Series` display label + nullable key.
   `SeriesExtractorExprTest` (4); 140 total green. _(Chart/legend/CSV showing the label is M14.4 UI wiring.)_
4. ✅ **DONE** — **Action wiring** — `graph` verb `exprs` param (parsed against discovered keys, per-expr
   ok/error echo), `GraphPanel` holds derived series (off-EDT parse + gen-guard), legend/CSV by label,
   legend-click removal, `GraphSpec.ExprSpec` + `ConfigStore` persistence, manifest updated.
   `ConfigStoreTest.savedGraphsRoundTripDerivedFormulas`; 141 total green. _(Plain `f(x)` UI field is M14.5.)_
5. ✅ **DONE** — **UI** — 📌 pin/unpin (slice 1) + a plain `f(x)` field (+ label + locf/strict) that parses
   with the same inline error the LLM path returns, then `addExpr`. Agent-first authoring.
6. _(v2.0, later)_ closed-set `Series → Series` transforms `delta` / `dt` / `rate` / `rolling` (top-level
   wrap only), each O(n) and unit-tested. _(v2.1: series-level arithmetic with alignment — separate.)_

---

## H. Testing

- `ExprTest` — parse + eval of literals, `+−×÷` (**ASCII and Unicode operators**), precedence, parens,
  abs/min/max; unknown-ref error names the ref; parse error points at the token; division-by-zero → NaN
  (omitted point).
- `SeriesExtractorExprTest` — `askMakerOrder.price − bidMakerOrder.price` over `sample.yml` under **LOCF**
  (cross-node co-occurrence not required) vs **strict** (only co-occurring records); **an explicit NaN
  observation clears the carry** (the derived point is omitted, no fabricated continuity); never-seen ref →
  omitted; row-order carry is deterministic within a same-millisecond burst.
- `ConfigStoreTest` — a `GraphSpec` with exprs + from/to round-trips; old profile without them still loads.
- Pinned behavior + UI by compile + manual pass (as with the rest of the UI).

---

## I. Open questions

**Resolved:**
- ~~LOCF iteration order~~ → **row order** (event-sequence, exact for a single-logger file; a `logTime`
  sort would reorder same-millisecond burst records and break the causal carry). Multi-logger files →
  per-logger LOCF state or single-logger extraction. See B.2.

**Still open:**
- **Staleness horizon (v1.5):** even with NaN-clears-carry, a ref can go quiet for hours (the Aug-11 feed
  silence) while another keeps updating — LOCF then draws a flat line of hours-old data. An optional
  **`maxAge` per expression** ("omit the point if any ref is older than 5m") bounds how much history a
  point can silently embed. Not v1-blocking, but wanted before this is trusted for anomaly work.
- **Y-pinning:** should a pinned graph also fix the Y-axis (so it doesn't rescale as the window's data
  changes under follow-mode/tail)? Lean: X-pin only in v1; Y auto-fits the pinned window.
- **Ref syntax for dotted keys** — backtick-quote (`` `a.b.c` ``) vs longest-known-key matching. Lean: try
  longest known `instanceId.key` match first; quoting as the escape hatch.
- **Boolean refs in exprs** — a boolean key is ±1 in a raw series; in a formula, keep ±1 or treat as 0/1?
  Lean: ±1 for consistency with the raw plot.

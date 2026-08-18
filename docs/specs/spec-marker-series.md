# Marker Series — events on a value chart (Design Spec)

Status: ACCEPTED v2 (review: docs/handoff/review_m30_m31_m32_specs.txt — the point-snapped mouseover
severed into its own slice [M1], the dangling y-series rule added [M2], the label-disclosure
half-sentence added [M3]) · Owner: greg.higgins · Last updated: 2026-08-18 · Milestone **M32**

Companion to **[tracker.md](tracker.md)** (M32). Subsumes M28's unscheduled **P3** (event markers /
rug strip) and gives M29's **D-F2** ("foreign series are visibly second-class") its natural rendering.
Prompted by the owner: *"plot buys/sells on a price graph and add customer order id with a distinctive
point style."*

## The problem

A price line answers "what was the value"; an investigation almost always also needs "and what
HAPPENED" — the fills, the rejections, the cancels, *on* that line, at their price, carrying their
identity. Today none of that can reach a chart: point style is chart-wide (a price line cannot coexist
with fill dots), notes are time-anchored rules that flood past a handful, and **text values — order
ids, sides, states — are unplottable entirely** (`KV.graphValue()` is numeric/boolean by design).
The workaround is a table open beside a chart and a human cross-referencing timestamps, which is the
job this tool exists to remove.

## The principle

A **marker series** is a first-class series whose points are discrete events: `(time, y, payload)`
rendered as a glyph, never connected, with the payload (an order id, a reason string) carried per
point. It is NOT a line with a different style — it is the one legitimate path for **categorical and
per-event data** onto a value chart. Everything already learned applies: extraction rides the same
record walk as every series (one universe), density is handled honestly (never silently dropped),
text payloads are display cargo and **never** become expression inputs (`Expr` stays numeric — the
M29 D-F3 wall, same brick).

## A — where markers come from (three sources, one model)

| source | shape | example |
|---|---|---|
| **key triple** (audit log) | `{when: "instanceId.key", y: "instanceId.key", payload: "instanceId.key"}` | every record where `fillListener.fillPrice` was logged → marker at that price, payload `fillListener.clOrdId` |
| **condition** (M28 exprs) | `{when: "<expr>", y: "<key or expr>", payload: "…"}` | `when: "askMakerOrder.orderStatus == 1"` (numeric/boolean conditions only — text never enters `Expr`) |
| **external CSV** (M29) | the M29 loader + a `payload` column | agent-parsed FIX fills, order id per row |

All three produce the same thing: a `MarkerSeries {label, glyph, List<(logTime, y, payloadText)>}`.
Extraction uses the existing last-occurrence-per-record walk and the active filter — a marker can
never disagree with a plotted series about what a record contained. `y` may also be pinned to another
series by label (`y: {series: "mid price"}`) for events that have a time but no natural price — the
marker rides the named series' value at that moment (LOCF within the walk), and the echo says so.
**A dangling pin degrades loudly, never silently** (review M2): labels are mutable — series get
removed, renamed, or arrive renamed through a share's replace-by-name merge — so a marker series
whose pinned series is absent renders nothing, keeps its legend row with the reason ("y pinned to
'mid price' — not on this graph"), and the verb echo names it, exactly as `rightAxis` warnings,
M29's "2 of 3 resolved" and M27's partial focus recalls already do. D-M4 makes this load-bearing:
the spec is persisted and SHARED, so a dangling reference arrives on someone else's machine, not
just after a local edit.

## B — the decisions (answers proposed, reviewer should challenge)

- **D-M1 — glyph + colour are per MARKER SERIES; the set is small and fixed.** `triangleUp`,
  `triangleDown`, `circle`, `square`, `diamond`, `x` — declared at creation (`glyph: "triangleUp"`),
  default assigned round-robin. Buys/sells are two marker series (`▲ buys`, `▼ sells`), not one
  series with per-point styling.
  *Rationale:* per-point styling is a rendering DSL nobody should maintain; "one meaning, one series,
  one glyph" is also what makes the legend honest.
  *Alternative rejected:* per-point glyph/colour columns. Grafana-scale machinery for a forensic tool.

- **D-M2 — payloads are DISPLAY CARGO: hover, click, export — never computation.** The payload shows
  in a tooltip on hover, in the marker's legend flyout, and in exports (a markers table under the
  chart in PDF; tooltips can't print). Payload text never enters `Expr`, `series`, `aggregate` or any
  filter. Log-sourced markers keep their `recordIndex`, so **clicking a marker is `goto`** — the
  existing click-plot-to-record affordance extended to the artifact that wants it most. External
  markers have no record and say so on click (M29 D-F2, unchanged).

  **If `UP-FLX-27` lands (a unit and description carried on a logged key), this is its first consumer**
  (review X3): the tooltip and the PDF markers table print the key's *meaning* beside the value, and
  the key-triple picker shows it while you choose. The round-2 POC defect is the case in point —
  `lastQty` means *shelf level after the movement*, not *units sold*, and a marker labelled with the
  bare key name hands a reader exactly the information that produced that bug. Display only: a
  description is cargo like any other payload, never an input (D-M2 unchanged).
  *Rationale:* the moment payloads are queryable, this tool has invented a string-typed column store.
  The record IS the queryable form; the marker is a signpost to it.

- **D-M3 — density degrades to a COUNT GLYPH, never to silence and never to soup.** Markers draw
  individually while legible; when a pixel column holds more than a handful, the column renders one
  aggregate glyph with a count badge ("×23"), and hover lists the first N payloads + "and 15 more —
  filter to see them". The exact thresholds are the implementer's; the contract is that **the
  presence of hidden markers is always visible** (M26's cap-honesty rule, drawn instead of written).
  Labels are hover-first: at most the K nearest-to-cursor labels draw inline; a `labels: "always"`
  opt-in exists for sparse charts headed to a PDF.
  *Rationale:* a day of fills is thousands of points; both failure modes — overplotted mush and
  silent decimation — are lies. The count glyph is the honest middle.
  *Alternative rejected:* always-on labels with collision layout. That is the notes system's
  stacking problem re-fought at 100× the density.

- **D-M4 — markers are persisted as their SOURCE, and ride the graph like everything since M28.5.**
  `GraphSpec` gains `MarkerSpec {label, glyph, source-shape}` — the key triple / condition / CSV
  reference, never the extracted points (M28.6's rule: conditions persist, intervals are data).
  The full share-surface checklist applies: ConfigStore round-trip · project snapshot/restore/clear ·
  SettingsShare ride-along asserted · restore-not-an-edit · **sharing-setups.md row updated in the
  same commit** (`ShareDisclosureContractTest` pattern — this is the fifth artifact to ride the
  Graphs category; the drift class is known and the remedy is standing).
  One new disclosure with teeth: payloads can carry **business data** (order ids). The spec's
  position: payload text is drawn from the log the recipient already has (or a CSV they must also
  have, per M29 D-F5), so sharing a marker spec shares no data the graph didn't — but the
  sharing-setups row must say "marker definitions (not their extracted values)" explicitly. The one
  exception is the `label` (review M3): free text authored by a human or agent, so it CAN carry
  business content into a shared file — the same caveat notes and explanations already carry, and
  the same answer: it is authored consciously, not extracted silently.

- **D-M5 — the rug strip is a marker series with `y: "axis"`.** P3 is not a second feature: a marker
  series may declare `y: "axis"` to render as ticks in a dedicated lane under the time axis — same
  sources, same density rule, same payloads, no y-value needed. Flagged records become one built-in
  rug ("Flags" toggle), closing the loop with the findings workflow.
  *Rationale:* one model, one renderer, one persistence shape; P3 falls out instead of being built.

## C — surface

**Verb** — `graph {markers: [...]}`, REPLACE semantics like guides/bands; malformed entries skipped
AND named in `warnings[]` (M28.5's contract). Echo: markers extracted per series, columns aggregated,
payload key resolution, and the M26.4 ignored-parameter rule throughout.

**UI** — "Add markers…" on the graph panel (key-triple pickers populated from discovered keys);
legend rows show glyph + label with a count; the flags rug is a toggle. Hover/click per D-M2/D-M3.

**Point mouseover (owner ask, generalised to every series — SEVERED into its own slice, review
M1)** — today's chart tooltip reports the CURSOR's coordinates (`pxToX`/`pyToY` — wherever the mouse
happens to be, whether or not data is there). M32.1 replaces it with a **point-snapped** hover:
within a small radius the tooltip snaps to the nearest actual sample and shows
`series label · time · value`; no snap candidate in radius → the coordinate readout remains as the
fallback; on a decimated column the tooltip names the column's min/max rather than pretending one
sample is the truth (the paint-side envelope's honesty). The nearest-sample search already exists
for click-to-record; hover reuses it. **This changes a shipped surface on every existing chart and
is independent of markers** — hence its own slice, changelog line and acceptance; the marker slices
then ADD payload to an already-shipped snapping tooltip ("▲ buys · 13:02:11.412 · 17.2450 ·
ORD-4711 · fillListener") instead of co-delivering it under a slice whose stated risk is glyph
rendering.

**Exports** — PNG draws what the screen shows (count glyphs included); the PDF report adds a markers
table (time, y, payload, source) under the chart, capped with an explicit "N of M shown" note.

## Non-goals / guardrails

- **No per-point styling, no styling DSL** (D-M1).
- **Payloads never computable** — not in `Expr`, not in `aggregate`, not filterable (D-M2). The
  record is the queryable form.
- **No new chart types** — markers overlay the existing time chart; scatter x-vs-y stays out (M28's
  survey said why).
- **Text stays out of `Expr`** — a condition source uses numeric/boolean expressions only; a text
  side field (`side: BUY`) needs either a boolean/numeric companion key in the log or the key-triple
  source with the side as *payload* on two pre-filtered marker series. Stated honestly in the docs.
- `FaqSecurityContractTest` untouched: nothing here mutates or writes beyond existing export paths.

## Acceptance

1. The motivating chart on the demo fixture: mid-price line + `▲ buys` + `▼ sells` marker series from
   key triples, order-id payloads on hover, click-a-marker selects the record (D-M2), one dense
   column showing a count glyph (D-M3) — screenshot via the capture harness for the docs.
2. A shared graph carrying marker specs round-trips (ConfigStore, share apply, project
   snapshot/restore/clear) and the disclosure row names marker definitions (D-M4) — the full
   checklist, each item a named test.
3. The PDF report renders the markers table with the cap note; the PNG shows count glyphs.
4. A rug-strip marker series (`y: "axis"`) and the built-in Flags rug render in the lane; P3 is then
   marked subsumed in the M28 spec's survey (D-M5).
5. A `markers` entry with an unresolvable payload key still plots (payload absent, echo names it);
   a text-valued `y` is refused naming the numeric rule.
6. Mouseover snaps to the nearest sample on ANY series (label · time · value; payload for markers;
   min/max on a decimated column); with no sample in radius the coordinate readout still answers.

## Delivery slices

1. **M32.1** Point-snapped mouseover for ALL series (severed per review M1 — independent of markers,
   shippable first, own changelog line and acceptance #6): the snap SEARCH is pure and
   headless-tested; the tooltip itself is eyeball-verified.
2. **M32.2** Model + extraction (pure): `MarkerSeries`, the key-triple and condition sources over the
   existing record walk, series-pinned `y` with the M2 dangling rule, density aggregation as DATA
   (column → count) so D-M3 is testable headlessly.
3. **M32.3** Rendering: glyphs, count badges, payload on the M32.1 tooltip, click→goto, the axis
   lane (D-M5) + Flags rug. The eyeball-heavy slice — offscreen-render verification like M21.3.
4. **M32.4** Verb + schema + echoes (`graph {markers}`), REPLACE + warnings contract (incl. the M2
   dangling-pin warning).
5. **M32.5** Persistence + share (D-M4 checklist) + exports (PDF table, capture-harness screenshot)
   + docs + changelog. External-CSV source lands here IF M29 has shipped, else it is deferred to
   M29.4 as one line ("the M29 loader is a marker source").

**Effort:** M32.1/M32.2/M32.4 are M28-sized mechanical slices; M32.3 is the real work and the least
testable — budget the offscreen-PNG verification time. **Sequencing:** builds on the anchor model
M30.2 settles (review C1 — recordIndex primary; do not restart that audit here); otherwise
independent of M30/M31, and the external-CSV source is the only M29 coupling, severable (see M32.5).

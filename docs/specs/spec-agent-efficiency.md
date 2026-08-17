# Agent-Efficiency Verbs — the analyser computes, the agent concludes (Design Spec)

Status: DRAFT v1 · Owner: greg.higgins · Last updated: 2026-08-17 · Milestone **M26**

Companion to **[tracker.md](tracker.md)** (M26) and the shipped assistant vocabulary
(`llm/VerbSchemas`, thirteen verbs). Proposed by the originating session after driving the analyser
over MCP on a production log — every item below is a friction actually hit in that session, not a
speculative feature.

## The principle (and why this is mostly a performance spec)

There is a hard asymmetry between the two parties on the socket. The **analyser** is a local Java
process on a capable machine, holding the *entire* log behind a columnar index — scanning 30k records
is a millisecond, and costs nothing. The **agent** is token-metered and context-bounded — every raw
record it pages through costs real money and crowds out reasoning. Today's verb set leans on `read`
(raw text) for anything the index doesn't aggregate, which pushes computation to the expensive side.

**Rule for this and future vocabulary work: any question answerable by an index/series scan should be
a verb, not a paged read.** Tokens should carry questions and conclusions; the JVM does the arithmetic.

Evidence from the motivating session (production log, 30,339 records, 39 MB):
- *"Where does the quoted spread exceed 0.004?"* took **five** `read` calls at hand-estimated record
  indexes (guessing records-per-minute to convert times to indexes), manual subtraction of bid from
  ask per sample, and luck. One indexed scan should answer it in one call. (V1, V2)
- Each `read` returned ~2 KB of raw record text per record when the question needed **two fields** —
  a 10–50× token overhead per look. (V3)
- A `graph` call putting a series in `rightAxis` without also listing it in `series` was a silent
  no-op, caught only by inspecting the echo. (V4)

## Proposals

### V1 — `series` scan: stats and threshold crossings over any key or formula

A read-only verb (or `aggregate` extension — implementer's call, one or the other, not both):

```
series {
  expr:      "askMakerOrder.transformedTargetPrice - bidMakerOrder.transformedTargetPrice",
             // or a bare key: "contraPositionToHedgeQuantityCalculator.rate"
  resolve:   "STRICT" | "LOCF",           // same semantics as graph exprs
  filter:    { from, to, dimensions, text },   // same shape aggregate takes
  stats:     true,                        // → { count, min, minAt, max, maxAt, mean, first, last }
  crossings: { above: 0.004 },            // or { below }, or both
             // → [ { recordIndex, byteOffset, logTime, value }, … ]  capped + "truncated" flag
  buckets:   "minute" | "hour" | null     // optional: stats per bucket instead of whole-window
}
```

- Reuses `SeriesExtractor` + `Expr` wholesale — the machinery exists; this is a transport for it.
- Crossings are **edge events** (entering the region), not every sample inside it, with a hard cap
  (e.g. 200) and an explicit `truncated: true` — no silent bounds (the no-silent-caps rule).
- With this verb, the motivating investigation is: one `series` call (crossings above 0.004) → one
  `read` at the returned anchor → conclude. Three calls total, none estimated.
- Off the EDT, like `coverage` — a formula scan over the whole log is exactly the freeze risk M24
  already solved for.

### V2 — time anchors on `read` and `goto`

`read { at: <epochMillis> }` and `goto { at: <epochMillis> }` resolve to the record at-or-before that
time (the index is time-ordered — this is a binary search). Kills the records-per-minute estimation
arithmetic entirely; agents think in times (they come from aggregate buckets and graph notes), the
index thinks in rows, and the conversion should live on the index's side.

### V3 — field projection on `read`

```
read { at | recordIndex | byteOffset, count, fields: ["bidMakerOrder.transformedTargetPrice",
                                                      "askMakerOrder.*"] }
```

returns compact rows — `{ recordIndex, logTime, event, values: { "instanceId.key": v, … } }` — instead
of raw text. `instanceId.*` wildcards allowed; keys follow last-occurrence-per-record semantics (same
as graphing). Raw text stays the default when `fields` is absent — projection is an opt-in economy,
not a replacement, and the detail/evidence use of `read` (quote the actual record) still wants raw.

### V4 — echo hardening on `graph`

- `rightAxis` (or a `notes[].series`) naming a series that is neither in `series`/`exprs` of this call
  nor already on the graph → the echo carries a `warnings: [...]` entry naming it. Still applies the
  rest — warn, don't reject (the series may be added next call).
- General rule worth adopting alongside: **every verb echo names what it ignored.** The dispatcher
  already never throws; this extends "actionable feedback" to the silently-dropped-parameter case.

## Non-goals / guardrails

- **No new mutating or file-writing surface.** V1–V3 are read-only; V4 is an echo change. The FAQ's
  security answer is untouched and `FaqSecurityContractTest` should not need editing — if it does,
  something here went wrong.
- No general query language. `series` is one expr + one threshold shape, deliberately — the moment it
  grows joins or multi-expr algebra, stop and reconsider (the agent can compose calls).
- `read` raw stays; projections don't strip the header fields agents anchor on (recordIndex/logTime).

## Acceptance

Replay the motivating investigation against the same production log shape: *"where does the quoted
spread exceed 0.004, and why?"* answered in **≤ 3 socket calls** (series crossings → read-at-anchor
→ conclusion), with no record-index estimation and no hand arithmetic. Token cost of the investigation
measurably an order of magnitude below the five-read baseline.

## Delivery slices

1. **M26.1** `series` (stats + crossings, STRICT/LOCF, filter, off-EDT) + schemas via `VerbSchemas`
   (auto-publishes as `analyser_series` through McpTools — no bridge work) + tests against both demo
   fixtures and a formula.
2. **M26.2** time anchors: `at` on `read` and `goto` (index binary search) + tests.
3. **M26.3** `read.fields` projection (+ wildcard) + tests pinning last-occurrence semantics.
4. **M26.4** graph echo warnings + the ignored-parameter echo rule applied to the existing verbs
   where cheap; docs (assistant guide's verb list + manifest prompt) + changelog.

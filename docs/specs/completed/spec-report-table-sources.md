# Spec — report table sources: every verb a table may cite, assembled

**Status:** SHIPPED 2026-08-28 — independent review found F1/F2, both corrected and cross-reviewed;
the full correspondence is [here](../../handoff/completed/review_m33_7_tables.txt). **Milestone:** M33.7
(it is M33's own recorded gap — *"aggregate/coverage/series table sources … stated, not hidden"*,
[tracker.md](tracker.md) ▸ M33.3). **Design it extends:**
[spec-investigation-reports.md](spec-investigation-reports.md) D-I7/D-I8.
**Tracker:** [tracker.md](tracker.md) ▸ M33. **Review focus:** rowWhen (D-T4) and coverage row shape
(D-T3c), both confirmed.

## The proposition

D-I7 promised a table whose rows are **derived from a call** — `read {fields}`, `series {buckets}` or
`{crossings}`, `aggregate`, or `coverage` — and M33.3 shipped `read` with the other three resolving but
rendering *"table source 'aggregate' is not assembled yet"*. That sentence is honest and it has now been
seen by a reader: recording the UAT conversation for the docs on 2026-08-27, a report that should have
carried the coverage figures carried the shrug instead, and the page had to be re-recorded around it.
The same session found that `recordIndex: 0` in a table's call was re-issued as `"0.0"` — a symptom of
the way a call is stored (D-T1), fixed at the symptom (`44b44c9`) and here fixed at the cause.

After this slice, the four verbs D-I7 names all assemble, every table carries the **scalars a reader
needs to judge its rows** (total, ratio, scope, truncation) printed under it, and the words on screen
and in the PDF are the same words (D-I8: one rule). Nothing about what a report *is* changes.

## D-T1 — a call is stored so it can be re-issued exactly

Today `SectionSpec.call` is `Map<String,String>` and the properties store writes it as flat
`call.N.key`/`call.N.val` pairs. Two things cannot survive that: a JSON number (arrives as `Double`,
stringified `0.0` — patched in `callValue`) and a **nested object** — `series {filter: {from, to}}` or
`aggregate {filter: {dimensions: […]}}` is turned into `{from=…}` by `toString` and can never be
re-issued. So:

- **A non-scalar call value is stored as JSON text** in the same flat slot: `call.N.val` holds
  `{"from":1767254400000,"to":…}` for an object or array, and the bare string for a scalar (integral
  numbers rendered whole, as now). Re-issue parses a value that starts with `{` or `[` back to structure;
  anything else is passed as the string it is. **Backward compatible**: every stored report today has
  only scalars, and they parse as themselves.
- `SectionSpec.call` becomes `Map<String,Object>` in memory (the parsed form); the store does the
  flattening at its edge and nowhere else. `ReportVerb.parse` stops flattening.

*Alternative rejected:* changing the store to a JSON document. It is a bigger change to a file that
`KnownKeys` (M38.7, D-C10) already preserves key-family by key-family, and a mixed-version hazard for a
tier that ships in `.fluxtion-settings` files people share.

## D-T2 — one rule for every source: rows are the echo's list, columns are the echo's keys

Each verb already returns exactly one list a table can be: the **row keys are the verb's echo keys**, so
an agent that has seen the echo can author a column spec without learning a second vocabulary — the
same reason `read` tables use `recordIndex, logTime, event, <field>` today.

| Source | Rows | Default columns (echo keys) | Printed under the table (the scalars) |
|---|---|---|---|
| `read {anchor, count/before/after, fields}` | records | `recordIndex, logTime, event, <fields…>` | the 25-record cap note, as now |
| `aggregate {metric, groupBy, filter, limit}` | `buckets[]` | `key, count` (+ `rate_per_min` when the metric asks) | `metric · groupBy · total N · population M records (filter / scan)`; `truncated: K more buckets than limit` when set |
| `series {expr, buckets: minute\|hour}` | `buckets[]` | `key, count, min, max, mean` | `expr · resolve · N points`; `timeOrderNote` when present (D-R4) |
| `series {expr, crossings: {above/below}}` | `aboveEvents[] ∪ belowEvents[]`, time order | `direction, logTime, recordIndex, byteOffset[, file]` | `above N · below M`; the crossings `note` when capped |
| `series {expr}` (neither) | **one row**: the stats | `min, minAt, max, maxAt, mean, first, firstAt, last, lastAt` | `expr · resolve · N points` |
| `coverage {filtered}` | every node in the denominator **and** every excluded node, graph order | `instanceId, class, status, reason` | `declared D · covered C · uncovered U · ratio R · N records · scope`; `excludedNote`; the audit-level line when U > 0 |

Rules that fall out of the table:

- **Empty is a sentence.** Zero rows renders the header and *"no rows — ‹the verb's own reason›"*
  (no buckets in the window; no crossings; every node covered), never a blank grid.
- **Caps are named** (D-M3 in table form): `aggregate.limit` and `series.limit` are the verb's own and
  the truncation line rides; coverage rows cap at **500** with *"and K more"* — a 300-node estate fits, a
  synthetic one does not hide its tail.
- **The scalars line is one string built once** and handed to both `ReportsPanel.table()` and
  `ReportRenderer.table()`; it is not re-derived on either side.

## D-T3 — the three shapes worth a sentence each

**a. `series` picks its row shape from the call, and says which.** Crossings if `crossings` is given,
else buckets if `buckets` is given, else the one-row stats. A call giving **both** `buckets` and
`crossings` is a valid *verb* call but an ambiguous *table* — it is refused at resolution
(`warnings[]`: *"a series table needs one row shape — buckets or crossings, not both"*), and the
section renders that sentence in place, like every other unresolved section.

**b. Crossing rows are records.** Each carries `recordIndex`, so a crossing table is a **navigation
surface** exactly as a `read` table is: clicking a row selects the record (ReportsPanel already does this
for `read` rows via `rowRecords`). Bucket rows and coverage rows have no record and no click.

**c. Coverage rows are the whole ledger, not the gap list.** The echo lists only the uncovered and the
excluded; the report lists **every** node with its `status` — `covered`, `uncovered`, `excluded` — and
for excluded the `reason` the echo already carries (event class · exported service · silent by
construction). *Rationale:* a report is read by someone who was not in the session. "These 3 never
logged" without "these 15 did" is the denominator hidden again, one layer up — M40.2's whole point was
that nothing leaves the denominator silently. The `limit` parameter (an echo-size control) does not apply
to a table; the 500 cap does.

## D-T4 — `rowWhen` stays a rule over a row's **record**; rows without records refuse it

D-I8 defines `rowWhen` as an `Expr` evaluated strictly against each row's own record. Bucket rows
(aggregate, series-buckets) and coverage rows have no record, so the rule **cannot be checked** — and
D-I8's own words for that case are *"a rule that cannot be checked against its own row does not fire on
it."* Silently not firing would be the trap D-I8 exists to avoid (a label printed under a table claiming
a rule that never ran). So: on a source whose rows have no record, a `rowWhen` is **refused at
resolution** with the reason (*"rowWhen evaluates against a row's record; aggregate buckets have none —
highlight by columns is not a thing yet"*), the table renders un-highlighted, the warning rides in the
echo — acceptance 7's existing behaviour for a malformed rule, applied to an inapplicable one.

Crossing rows **do** have records, so `rowWhen` applies to them unchanged.

*Alternative rejected:* letting `rowWhen` mean "over the row's columns" when there is no record
(`count > 100`). Same name, two semantics, and the second one is a formula over a table rather than
over the log — a different feature (call it `cellWhen`, or a `having` on the call) that deserves its own
decision, not a quiet overload. Listed under *not in this slice*.

## D-T5 — the call's own `filter` wins, and is printed

`aggregate`, `series` and `coverage {filtered}` each take a scope. D-I3a's "stored filter vs current
filter" offer governs `read` tables and record sections (they have no scope of their own). A table
whose call carries a `filter` is scoped by **that**, regardless of the current view, and the scalars
line prints it (`population 726 records (filter: 09:00–09:10, dims: MarketDataEvent)`). `coverage
{filtered: true}` is the one call that reads the *current* filter — so its line says `scope: current
filter` exactly as the echo does, and the report header's D-I3a sentence covers the rest.

## Not in this slice, said so the reader does not look for it

- **`cellWhen` / a `having` on the call** — highlighting bucket rows by their own values (D-T4).
- **Topology and series pictures in the PDF** — M33.3's other named gap; a chart is a `chart` section
  and stays one.
- **A `sort` on the column spec** — D-I7 mentions sort; rows come in the verb's order (graph order for
  coverage, time order for crossings, the verb's bucket order otherwise). Sorting is presentation and
  cheap to add later without a model change.
- **Joins** — one call per table. Two facts side by side are two tables.

## Acceptance

1. A report with four table sections — `read`, `aggregate {groupBy: dimension}`, `series {buckets:
   minute}`, `coverage` — against the demo traced log **renders rows in all four** on the Reports tab
   and in the PDF, and `csv: i` exports each (the existing CSV path is `TableData`-based, so it needs no
   change — verify, do not assume).
2. A `series` table with `crossings` lists the crossing records; clicking one selects it; `rowWhen` on it
   highlights and prints the rule (D-T3b, D-T4).
3. `aggregate` with `rowWhen` resolves with a **warning naming the reason**, renders un-highlighted, and
   the echo's `warnings[]` carries the same sentence (D-T4).
4. A call with a nested `filter` object round-trips through the store: save, restart, reopen, the table
   re-runs under the stored scope and the scalars line prints it (D-T1, D-T5). A report saved by
   1.10.0 opens unchanged.
5. `coverage` on the no-audit fixture (`demo-quote-processor-noaudit.graphml`) renders **every** node
   with a status and the *cannot be observed* excluded reason where M40.2b assigns it; no row is missing
   and the ratio line matches the echo (D-T3c).
6. Every scalars line on screen is byte-identical to the one in the PDF for the same section (D-T2).
7. Empty results render the header plus the verb's own reason, for all four sources.
8. The `docs/site/sample-conversations.md` scenario 4 table goes back to `aggregate {groupBy:
   dimension}` — the shape the conversation actually wanted — and the recorded run shows rows; the
   `reports.md` sentence *"(v1: `read {fields}`)"* is replaced by the table in D-T2.

## Delivery slices

1. **M33.7a — the store** (D-T1): `SectionSpec.call` as `Map<String,Object>`, JSON-text for non-scalars
   at the store edge, `callValue` retired, round-trip tests including a 1.10.0-shaped file.
2. **M33.7b — aggregate + coverage** (D-T2, D-T3c, D-T4, D-T5): the two record-less sources, the
   scalars line and its single builder, the refusal path for `rowWhen`, empty-as-sentence.
3. **M33.7c — series** (D-T3a/b): three row shapes, the both-shapes refusal, crossing rows as
   navigation, `rowWhen` on crossings.
4. **M33.7d — docs**: `reports.md`, the sample-conversations scenario 4 regenerated, CHANGELOG. The
   capture harness aborting on a failed call is the acceptance for slice 8 — no hand-typed transcript.

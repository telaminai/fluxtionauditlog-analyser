# Investigation Reports — the account, not just the evidence (Design Spec)

Status: PROPOSED v2 (review: docs/handoff/completed/review_m33_spec_and_1_6_0.txt — F1/F2 folded into D-I3a
as one mechanism, F3 answered in D-I8, F4 declined with a reason) · Owner: greg.higgins ·
Last updated: 2026-08-20 · Milestone **M33**

Companion to **[tracker.md](tracker.md)** (M33), the shipped finding report (`report/PdfDoc`,
`report/FindingReport`, M23) and **[spec-closed-loop.md](spec-closed-loop.md)** §A (M12.1's fix-brief,
which this spec proposes to absorb — see D-I6). Prompted by the owner: *"an explanation for a set of
results or an investigation that the analyser can render and keep in memory or store to disk — a more
general purpose reporting mechanism."*

## The gap

Every surface in this tool produces **evidence**. Nothing produces the **account** of it.

A real investigation is not one record. It is *"across these twelve records, these three nodes and this
window, here is what happened and why"* — and today that narrative exists only in a chat window, which
is ephemeral, unshareable, and anchored to nothing. The shipped `report` verb renders exactly one
record's finding; graph `explanation`/`notes` attach prose to one chart; `flag` writes one diagnosis to
one record. Three partial answers to the same missing thing.

## The principle

**A report is an ordered list of REFERENCES with connective prose — never a free-form document.**

That single constraint is what keeps this a forensic instrument rather than a word processor with a
database attached, and every decision below follows from it. A report *assembles* what other verbs
produced; it does not become a second place where findings are authored, and it never renders a claim
that has stopped being true without saying so.

## A — the section model

A report is `{title, sections[], createdAt, notes}`. Each section is exactly one of:

| section | stored as | rendered from |
|---|---|---|
| `finding` | `recordIndex` | the record's `flag` — never restated |
| `record` | `recordIndex` (+ `file` on a rolled set) | the live log |
| `chart` | graph name | the live graph, re-extracted |
| `topology` | named focus | the live graph, re-rendered |
| `series` | the `series`/`aggregate` call that produced it | re-run against the live log |
| `table` | the call that produced the rows, plus a column spec | re-run, then presented (D-I7) |
| `narrative` | the agent's prose | itself — and marked as such |

Nothing but `narrative` stores its own content.

## B — the decisions (answers proposed, reviewer should challenge)

- **D-I1 — a report INCLUDES findings; it never authors them.** A `finding` section carries a
  `recordIndex` and renders the note and fix that `flag` wrote. The report verb cannot set finding text.
  *Rationale:* `flag` is deliberately the ONE place a finding is written, which is why the same words
  appear in the records table, on the topology callout and in the PDF. A report that could also write a
  diagnosis would let one record carry two accounts of itself that disagree — the drift class this repo
  has now caught five times (the manifest's verb count, `assistant.md`'s tool list, the sharing-setups
  row, the marker legend, a stale `BLOG.md` index row). Every one was two sources of truth for one fact.
  *Alternative rejected:* letting a report override or extend a finding "just for this document". That
  is how the second source of truth always arrives — locally reasonable, globally corrosive.

- **D-I2 — narrative is VISIBLY narrative.** Prose sections render distinctly from evidence sections —
  a different type treatment and a standing label, in the PDF and on screen alike.
  *Rationale:* the tool's whole value is that a claim traces to a record. Unanchored prose is exactly
  where a plausible, wrong sentence lives with nothing to check it against, and an agent will write
  those. This is M29's D-F2 applied one level up: external series are stamped so a foreign line can
  never pass as audit evidence; agent narrative gets the same treatment so an assertion can never pass
  as a record.
  *Alternative rejected:* uniform styling with a byline at the top. A reader scanning a rendered page
  does not carry the byline down the page; the distinction has to survive being read out of order.

- **D-I3 — evidence persists as its REFERENCE, and re-renders live.** Opening a report re-reads the
  records, re-extracts the charts, re-runs the series calls. A reference that no longer resolves is
  reported — *"3 of 5 anchors did not resolve against this log"* — never silently dropped or silently
  stale.
  *Rationale:* M28.6's rule (conditions persist, intervals are data) and M29's D-F5 (degrade out loud),
  both of which exist because a stored artefact that quietly disagrees with the current data is worse
  than one that fails. It also makes a report a **re-runnable claim**: open last month's investigation
  against today's log and find out whether it still holds.
  *Alternative rejected:* snapshotting rendered evidence into the report. Portable and immune to a
  moved file — and it converts the report into a screenshot with extra steps, which cannot be
  re-verified and therefore is not evidence.

  **D-I3a — the authoring CONTEXT is captured, because the dangerous failure arrives resolved
  (review F1/F2).** Re-rendering against the live log is only safe if the report can tell whether it
  is the *same* log and the *same* view. It cannot infer either from its references: `recordIndex 42`
  resolves against **any** log with 43 records, a chart name resolves against project state, a series
  call re-runs anywhere. Against the wrong log every section renders confidently — fresh, plausible,
  wrong evidence sitting under narrative written about different data. The unresolved-anchor path
  above catches *absence*; this failure arrives *present*.

  So a report stores two pieces of **identity** at authoring — not evidence, so references-only
  survives:

  | captured | used for |
  |---|---|
  | log fingerprint — name/root, record count, first and last `logTime` | is this the same log? |
  | the `FilterState` it was written under (or a named-focus reference) | is this the same view? |

  and the renderer applies one rule: **compare, announce, offer.** A fingerprint mismatch is named
  *before any section renders* — "written against `store-audit.yaml` · 582 records · 09:00→09:07; the
  loaded log differs". A filter difference **offers** the stored context (offer-never-act, M20.5 and
  D-R5's pattern); declining renders under the current filter with a standing line saying which filter
  produced what is on the page.

  *Rationale:* without this, the principle sentence at the top of this spec — *never renders a claim
  that has stopped being true without saying so* — is unenforceable, because the worst case never
  dangles. With it, the two cases separate cleanly and both become useful: **same log, moved on** is
  re-verification, which is D-I3's best property; **different log** is misapplication, and the page
  says which one you are looking at. It also closes the drift D-I1 guards elsewhere — narrative and
  evidence silently describing different extractions is one document carrying two accounts of itself.
  *Alternative rejected:* refusing to open a report against a different log. Too strict — comparing a
  finding against a later run is a legitimate and valuable thing to do. Announce, do not forbid.

  **Same content, different name** (review Q1, owner-decided): announces SOFTLY — "the loaded log
  matches on content but is a different file" — because it is still a fact the reader needs, while a
  legitimate copy or rename must not wear the strong different-log banner. Same content under the
  same name stays quiet; the strong banner is reserved for a content difference.

- **D-I7 — a table is a QUERY plus a column spec: the rows are derived, the presentation is
  declared.** A `table` section stores the call that produces its rows — `read {fields}`
  (M26.3 already returns compact `{recordIndex, logTime, event, values{}}` rows), `series {buckets}` or
  `{crossings}`, `aggregate`, or `coverage` — together with an authored **column spec**: which columns,
  in what order, their headings, number format, alignment and sort. Rows re-run under D-I3; the column
  spec persists.
  *Rationale:* this is the same seam every other artefact in the tool is cut along — M28.6's condition
  persists while its intervals are data, M32's marker spec persists while its points are data. It also
  means a table cannot drift from the log: change the filter, reopen the report, and the numbers move
  because they were never copies.
  *Alternative rejected:* storing the rendered rows. It makes a report portable and immediately makes
  it a screenshot of a spreadsheet — unverifiable, and stale the moment the log is re-read.

  **An AUTHORED table — one whose numbers the agent computed itself rather than derived from a call —
  is a `narrative` section and renders as narrative** (D-I2), not as evidence. This is the case worth
  being strict about: a hand-built comparison table is the single most convincing-looking way to put an
  unanchored number in front of a reader, and it must not wear the same styling as rows that came out
  of the log.

  Tables export to CSV per section, reusing the existing `RecordExporter` path rather than growing a
  second writer — a report is then a route *into* a spreadsheet as well as a document, which is the
  point of asking for one.

- **D-I8 — formatting is a declared RULE that is shown, never a per-cell paint.** Tables get a small,
  fixed set of presentation controls, and every one of them that carries *meaning* also carries its
  reason on the page.

  | control | scope | declared as |
  |---|---|---|
  | heading, order, width, alignment | column | literal |
  | number format — decimals, thousands, percent, duration, epoch-as-time | column | literal |
  | emphasis — bold / muted | column | literal |
  | **row highlight** | row | **an `Expr` condition + a label** |
  | **value scale** (cell intensity across a column's range) | column | on/off + the range, printed |
  | totals / row count | table | literal |

  Numbers right-align in tabular figures by default, because a column of prices that does not line up
  is a column nobody reads.

  **`rowWhen` evaluates STRICTLY against the row's own record — no LOCF carry** (review F3). Bands
  carry values across the record walk because a band describes a *regime over time*; a table row is a
  single record a reader is looking at. A row highlighted because of a value carried from an earlier
  record that is not on the page is an emphasis the reader cannot verify from what they can see, which
  is the precise thing this decision exists to prevent. A rule that cannot be checked against its own
  row is not a rule, it is a colour.

  Two absences, two behaviours, deliberately (review R2 + F1): a ref the row did not log makes the
  rule NOT FIRE on that row, quietly — even under negation, because a highlight is a positive claim
  about a row and a row with no `x` supports no claim, not a vacuous one. A WINDOW function in the
  rule is refused loudly and named, because that absence is a fact about the RULE, not the row: a
  row rule is evaluated against its own record alone, so the window would hold one sample and report
  a value it never computed. One is data honestly absent; the other is a rule that cannot mean what
  it says anywhere.

  *Rationale:* a red row says **"this one is bad"**. If that colouring came from an agent's taste it is
  an unanchored judgement wearing evidence styling — D-I2's problem arriving through the back door,
  and harder to spot because it looks like formatting rather than a claim. Making the highlight a
  **condition** means it re-evaluates under D-I3 with everything else, it can be wrong in a checkable
  way, and the reader is told what it means. It also reuses the M28 expression engine rather than
  inventing a second rule language: `rowWhen: "askMakerOrder.price - bidMakerOrder.price > 0.004"` is
  already a thing this tool can parse, test and explain.

  Every visual emphasis already in the analyser states its rule — a guide line is labelled
  (`4bp limit`), a band is labelled (`in breach`), a marker series has a legend row with a count. A
  highlighted table row that says nothing about why would be the only unexplained emphasis in the
  product.

  *Alternative rejected:* per-cell styling — an agent setting colours cell by cell. It is the obvious
  spreadsheet mental model, and it is D-M1's rejected per-point styling one artefact later: a rendering
  DSL nobody should maintain, and the mechanism by which a document stops being reproducible from its
  sources.

- **D-I4 — reports are project-tier state with their OWN share category.** They persist like saved
  graphs and named focuses; the full share-surface checklist applies (ConfigStore round-trip, project
  snapshot/restore/clear, `SettingsShare` ride-along asserted by test, restore-is-not-an-edit,
  `sharing-setups.md` row in the same commit).
  *Rationale:* they are analysis artefacts, so the tier is obvious. The **separate category** is not:
  a shared graph discloses key names and a formula, whereas a shared report discloses *prose an agent
  wrote about your production data*, which is a different kind of cargo and deserves its own consent
  checkbox rather than riding Graphs as the sixth passenger.
  *Alternative rejected:* folding them into the Graphs category. That is precisely how F1 happened —
  named focuses rode Graphs and the export side never said so.

- **D-I5 — writes ride the existing guard, unchanged.** Rendering to disk uses `ExportGuard`: the
  opt-in, the exchange directory, never-overwrite. No new file surface, no new setting.
  *Rationale:* there is exactly one file-exchange boundary and it is already argued, tested and
  documented (M29 D-F4). A second one would double the surface and halve the clarity.

- **D-I6 — M12.1's fix-brief BECOMES a report with a fixed section list.** The closed-loop brief
  (diagnosis, evidence, resolved source targets, replay reference, task, acceptance) is a report whose
  sections are prescribed rather than chosen.
  *Rationale:* two document models in one tool will drift, and shipping a general reporting mechanism
  beside a bespoke brief generator would be an unusually ironic way to prove this spec's own point. It
  also means the brief inherits D-I3 for free — a fix-brief that re-renders is one an agent can
  re-verify after its fix.
  *Alternative rejected:* keeping them separate because the brief targets an agent and the report
  targets a human. The audience differs; the structure does not.

## C — surface

**Verb** — `report {sections: [...], title, name}` builds or replaces a named report; the existing
single-record form stays as sugar (`report {recordIndex, path}` = a two-section report, rendered
straight to disk). Echo follows M26.4: sections accepted, references that did not resolve **named**,
and the M26.4 ignored-parameter rule throughout.

**UI** — a Reports list beside the graph tabs; selecting one renders it in a panel with its sections in
order, each evidence section clickable through to the thing it references (a `finding` section selects
that record, a `chart` opens that graph). A report is a **navigation surface**, not just an output.

**Export** — PDF via the existing `PdfDoc`. HTML is a natural second target and is explicitly out of
scope for v1.

## Non-goals / guardrails

- **No free-form document authoring.** Sections are typed; there is no "arbitrary markdown" section.
- **No finding authorship** (D-I1). `flag` remains the one write site.
- **No snapshotted evidence** (D-I3). References only.
- **No new file-write surface** (D-I5).
- **No cross-log MERGING.** A report belongs to the log it was authored against. Opening it on another
  log is allowed and useful, and is handled by D-I3a's fingerprint comparison — *not*, as an earlier
  draft of this spec claimed, by the unresolved-anchor path, which does not catch it.
- **No authorship attribution on narrative** (review F4, considered and declined). Chart
  `explanation`/`notes` do not distinguish human from agent prose either, and introducing it here alone
  would be a new inconsistency. Revisit for all three surfaces together or not at all.
- `FaqSecurityContractTest` gains the report row only if the file rules change — they should not.

## Acceptance

1. A three-section report (finding → chart → narrative) renders on screen and to PDF, with the
   narrative visibly distinct from the evidence in both (D-I2 demonstrated, not documented).
2. The finding section's text is byte-identical to what `flag` wrote, and the report verb **cannot**
   change it (D-I1 pinned by test).
3. A report whose chart was deleted and whose record index is out of range opens, renders what remains,
   and states what did not resolve (D-I3).
4. A report round-trips through ConfigStore, project snapshot/restore/clear and a share export/import,
   and `sharing-setups.md` names the new category in the same commit (D-I4, the F1 checklist).
5. A path outside the exchange directory is refused with the existing message (D-I5).
6. A `table` section over `read {fields}` renders with its declared columns, re-runs when the filter
   changes, and exports to CSV; an authored table renders with the narrative treatment rather than the
   evidence treatment (D-I7, both halves pinned).
7. A table with `rowWhen` highlights the matching rows **and prints the rule that selected them**;
   changing the underlying data changes which rows are highlighted, because the rule re-evaluates
   (D-I8). A malformed rule is named in `warnings[]` and the table still renders.
8. The M12.1 fix-brief is expressible as a report with a fixed section list (D-I6 demonstrated by
   building it that way, not by asserting it).
9. A report opened against a **different log** names the fingerprint mismatch **before any section
   renders**, and does not refuse (D-I3a).
10. A report opened under a **different filter** offers its stored context; declining renders under the
    current filter with the difference stated on the page (D-I3a).

## Delivery slices

1. **M33.1** Model + section resolution, headless: `Report`, `Section`, reference resolution against a
   live store, the unresolved-anchor report, and **D-I3a's authoring context** — log fingerprint and
   filter capture, comparison, and the announce/offer decision as data. Full D-I1/D-I3/D-I3a tests
   before any rendering; the amendments are cheapest here and unaffordable later.
2. **M33.2** Rendering: extend `FindingReport` from its fixed `Evidence` record to a section list, with
   D-I2's narrative treatment and D-I7/D-I8's table layout (column widths, tabular figures, row-rule highlighting with its
   printed rule, and page-breaking a long table with its header repeated). `PdfDoc` is unchanged.
3. **M33.3** Verb + echo (`report {sections}`), the single-record form kept as sugar; per-section CSV
   export through `RecordExporter`.
4. **M33.4** Persistence + share (D-I4 checklist) + the Reports panel + docs/changelog.
5. **M33.5** Fold M12.1's fix-brief onto the model (D-I6) — after the closed-loop precondition
   (journal ↔ audit-log pairing) is resolved, not before.

**Effort:** smaller than it looks. `PdfDoc` (354 lines) is already a general renderer — pages, faces,
images, flow control, widow handling. `FindingReport` (329 lines) holds the fixed shape: an `Evidence`
record with named fields. **The rendering is largely done; the work is replacing that record with a
section list**, plus a verb, a tier and a category. M33.4 is where the review attention belongs, because
it is the share surface.

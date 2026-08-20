# Source Adapters — the same instrument over other execution engines (Design Spec)

Status: PROPOSED v1 · Owner: greg.higgins · Last updated: 2026-08-20 · Milestone **M34**

Companion to **[tracker.md](tracker.md)** (M34) and **[spec-log-source-plugins.md](spec-log-source-plugins.md)**
(M31, whose reader SPI this extends). Prompted by the owner: *"make the app a general purpose solution
by identifying the Fluxtion-specific elements and making them plugins… we could write a plugin for
LangGraph etc that extracts their data and transforms it into our audit log format, then we are using
all the exact same tools."*

## The proposition

The analyser's data model is not Fluxtion-shaped. It is:

> an ordered sequence of **cycles**, each triggered by an **event**, each recording **which components
> ran, in what order, and what each logged** — with a static **graph of components** alongside.

LangGraph, Temporal, Airflow, Dagster and OpenTelemetry all produce something within reach of that
description. M31 already made *containers* pluggable — parquet, Chronicle, a DB holding Fluxtion
records. **M34 makes the ENGINE pluggable**: the records need not have come from Fluxtion at all.

If that holds, every feature above the boundary — filters, charts, formulas and windows, markers,
findings, M33 reports, the MCP verb surface, the shared human/agent surface — arrives for free on
every engine an adapter exists for.

## The principle

**M31 draws the boundary at "how bytes become records". M34 draws it one level out: "how a RUN
becomes records and a graph."** The core keeps exactly one model; adapters translate into it and
declare honestly what they could not supply.

## A — the asymmetry, made a first-class decision

This is the finding that shapes the whole spec, and it is not a detail.

**The audit log generalises cleanly. The topology does not.** Some engines can hand over a *declared*
set of components — the graph as authored, before anything ran. Others can only offer what was
*observed* — the paths that happened to execute. Every one of the analyser's most differentiated
features depends on which side an engine falls:

| feature | needs | LangGraph | Temporal | OTel traces |
|---|---|---|---|---|
| records, filters, charts, formulas, markers, findings, M33 reports | records only | ✅ | ✅ | ✅ |
| topology view, focus, routes | *a* graph | ✅ declared | ⚠ inferred | ⚠ per-trace |
| intra-cycle step-through | ordered components per cycle | ✅ | ✅ | ✅ |
| **node coverage** | a **declared** set to subtract observed from | ✅ | ❌ | ❌ |
| replay diff | a replay mechanism + capture | ⚠ | ✅ native | ❌ |

*(Confidence: the shapes are researched, the current API specifics are not. Per CLAUDE.md rule 6 each
adapter must be written against the live source of truth, not this table.)*

**Coverage is the sharp case, and it is worth stating plainly: it is "declared minus observed".** With
no declared set there is nothing to subtract from. The feature that found 54 dead nodes in the
supermarket POC cannot exist for an engine that only knows what ran — and *that finding is the single
most persuasive thing this tool has produced*. An adapter cannot conjure it.

- **D-A1 — an adapter DECLARES which of these it can supply, and the core degrades loudly per
  capability.** Not lowest-common-denominator, not silent absence: a source without a declared graph
  disables coverage and *says why* — "this source reports what ran; it cannot report what did not."
  *Rationale:* M31's D-P4 already chose loud capability degradation over pretending; this extends the
  same rule to the features that make the tool differentiated rather than merely useful. The
  alternative — inferring a "declared" graph from observed history — would manufacture the one number
  a reader would most want to trust, and coverage computed against an inferred denominator is not a
  finding, it is a tautology.
  *Alternative rejected:* shipping coverage everywhere by treating the union of observed nodes as the
  declaration. It always reports 100%.

- **D-A2 — a graph is either DECLARED or INFERRED, and the view says which.** An adapter supplying a
  graph marks its provenance. An inferred topology renders with a standing label and disables the
  features that assume completeness (coverage; "nodes that did not run" shading).
  *Rationale:* the topology's whole value is that absence means something. M27 already refuses to let a
  filtered view pass as complete — "14 node(s) of this cycle ran OUTSIDE this view". An inferred graph
  is the same claim at a larger scale, and it needs the same honesty.

## B — the SPI shape

M31's `AuditLogReader` stays exactly as it is for containers. M34 adds a sibling that produces a
**run**, not a byte stream:

```
interface RunAdapter {
    String engineId();                      // "langgraph", "temporal"
    String displayName();
    boolean canOpen(Path source);
    TimeBase timeBase();                    // MANDATORY, as M31 D-P1 established
    Capabilities capabilities();            // M31's flags…
    GraphSupport graphSupport();            // …plus: NONE | INFERRED | DECLARED   (D-A1/D-A2)

    void read(Path source, Consumer<String> recordText) throws IOException;
    Optional<DeclaredGraph> graph(Path source) throws IOException;
}
```

- **D-A3 — adapters emit the CANONICAL RECORD TEXT, exactly as M31's readers do.** An adapter renders
  each cycle into the standard `eventLogRecord` shape; the core builds the index, the store, the filter
  columns and everything above.
  *Rationale:* M31's D-P2 already decided this and the reasoning is unchanged — half this tool's
  surfaces are text-shaped (raw read-through, the text filter, copy-prompt quoting, reports quoting
  evidence). One record model, one set of consumers. It also means an adapter is testable as a pure
  function from a foreign run to text.
  *Consequence, stated honestly:* the rendering is a *translation*. Byte anchors are a text-container
  feature and stay unavailable (M31 D-P2), so foreign sources anchor by `recordIndex` — which M30's
  D-R2 already made the primary anchor.

- **D-A4 — the graph is handed over in the core's own vocabulary**, not the engine's: nodes with
  `{id, label, className, kind}` and edges with `{id, source, target}` — the existing
  `ProcessorTopology` shape, which is already engine-neutral. An adapter maps its concepts onto
  `NODE`/`EVENT`/`EVENT_HANDLER`/`EXPORT_SERVICE` or declines a kind.
  *Alternative rejected:* a richer generic graph model with engine-specific attributes. Every consumer
  above would then need to know which engine it was looking at, which is the coupling this spec exists
  to remove.

- **D-A5 — GraphML stays the Fluxtion adapter's business, not the core's.** Today `GraphMlParser` is
  core; under M34 it becomes what the *Fluxtion* adapter uses to satisfy `graph()`. The core knows
  `DeclaredGraph`, never GraphML.
  *Rationale:* this is the actual "make the Fluxtion-specific elements plugins" move, and it is the
  test of whether the boundary is real. If the built-in adapter cannot be expressed through the same
  SPI a third party would use, the SPI is decoration — the same argument M31 made for refactoring the
  text parser behind its reader interface.

## C — the format question

Adapters translate *into* the audit-log format, which makes that format a public interface whether or
not it is published as one.

- **D-A6 — publish the format openly; hold the NAME.** A written specification, a conformance fixture
  set, and the analyser as the reference implementation. The name is the thing to protect; the layout
  is not protectable in any useful sense and attempting it would suppress the adoption the strategy
  depends on.
  *Rationale:* an adapter ecosystem only exists if emitting the format is safe and obvious. The
  defensibility was never the schema — it is the reference tool's accumulated behaviour (M21–M33), the
  disciplines baked into it (one write site, references not content, degrade out loud), and the shared
  human/agent surface. Those are judgement, not layout, and judgement is what does not copy.
  *Alternative rejected:* a proprietary or licence-restricted format. It prevents nothing a
  determined implementer wants to do, and it removes the reason anyone would emit it.

## Non-goals / guardrails

- **No engine-specific code in the core, ever** (D-A4/D-A5). If a feature needs to know it is looking
  at LangGraph, it belongs in the adapter or nowhere.
- **No inferred coverage** (D-A1). Absent a declared graph the feature is disabled and says why.
- **No merging runs from different engines.** One run, one adapter, per open.
- **No write path.** Adapters read; nothing here mutates a foreign system.
- The M31 plugin trust boundary applies unchanged: a jar you install is arbitrary code execution, said
  in those words, parent-first for the SPI package and child-first for everything else.

## Acceptance

1. The **Fluxtion** path runs entirely through `RunAdapter` — records via M31's reader, graph via
   GraphML inside the adapter — and the full suite passes unchanged (D-A5, the seam demonstrated on
   the engine that matters most, exactly as M31.1 did for the text parser).
2. One foreign adapter, built **out of tree** against the published SPI, opens a real run: records,
   filters, step-through, a chart, a finding and an M33 report all work.
3. A source declaring `graphSupport = NONE` disables coverage with a message naming the reason, and
   every other feature still works (D-A1).
4. A source declaring `INFERRED` renders the topology with its provenance label and without
   "did not run" shading (D-A2).
5. The format specification and its conformance fixtures are published, and the built-in Fluxtion
   adapter passes them (D-A6).

## Delivery slices

1. **M34.1** `RunAdapter` SPI + `DeclaredGraph` + `GraphSupport`; the Fluxtion path refactored behind
   it; suite green unchanged. The published SPI artifact question inherits M31's D9 (deferred pending
   a multi-module decision).
2. **M34.2** Capability degradation wired through: coverage, "did not run" shading, replay-diff
   availability — each disabled loudly, none silently.
3. **M34.3** The format specification + conformance fixtures (D-A6).
4. **M34.4** The first foreign adapter, out of tree — **LangGraph first**, because it is the one with a
   declared graph and therefore exercises the hardest parts of the boundary rather than the easiest.

**Sequencing note:** M34.4 is the experiment that decides whether any of the rest is worth building,
and it is the cheapest step here. Consider running it as a spike against the CURRENT code before
M34.1 — if a foreign run cannot be made legible by the existing tool, no amount of SPI improves that,
and the spike costs one adapter rather than a re-plumbing.

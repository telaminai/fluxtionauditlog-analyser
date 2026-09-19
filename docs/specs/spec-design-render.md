# Design render — the Spring XML on the canvas (Design Spec)

**Status:** PROPOSED 2026-09-19 (owner-directed). **Milestone:** M49. **Tracker:** [tracker.md](tracker.md).
**Builds on:** [`spec-shared-evidence-canvas.md`](spec-shared-evidence-canvas.md) (the thesis this serves),
[`spec-authoring-modes.md`](spec-authoring-modes.md), the `open` / `source_root` / `spotlight` verbs, and
the compiler's Spring-authoring runbook (fluxtion-compiler `design/spring-authoring/spec-2-…`, revision 4:
`validate.sh` → `target/fluxtion-validation.json`; `regenerate` → `target/fluxtion-reconciliation.json`).

Owner, 2026-09-19: *"the analyser is the canvas with context; the LLM connects to the analyser to render
decisions/proof; the LLM authors with Spring XML; the user talks to the design-partner LLM and reviews the
evidence in the canvas. We already hold source-root pointers, so this is render, and a verb/parameter to tell
the analyser what file to display."*

## The proposition

The canvas already shows three of the four artefacts a design conversation is about: the **log** (what
happened), the **generated processor** (what the compiler built), the **node source** (what the developer
wrote). It does not show the fourth — the **design** the LLM authored, the Spring XML that the compiler turned
into the other three. So when the design partner says *"I declared `riskStatusBook` as a push target of
`gate` — here is why, and here is the record that proves the edge fired"*, the human can see the record, the
node and the dispatch line, but not the declaration. The explanation points at three of its four subjects.

M49 puts the design on the canvas as a first-class artefact, **read-only**, with the same three properties the
other artefacts have: it is **navigable** (bean ↔ node ↔ record ↔ source), it is **spotlightable** (the LLM
can light a bean declaration and say why, and the caption is marked as testimony), and it is **addressable
from evidence** (a compiler refusal names a bean; the canvas shows the line). Nothing here executes,
validates or edits the XML — the runbook's utilities own that, and B0/B3 own what the XML may change.

## What exists, and what this adds

| Today | M49 |
|---|---|
| `source_root` grants reading of `.java` files under configured roots | a root also grants reading of the project's **design files** (`*.xml`, later `*.yaml`) beneath it |
| Source tab: `PROCESSOR` / `NODE` / `SPLIT` modes, Java highlighting, scroll-to-offset, back navigation | a **`DESIGN`** mode: XML highlighting, scroll-to-line, bean-id index, the same back stack |
| `open {log, graphml, processor, …}` puts the session's artefacts in force | `open` gains **`design`**: the path of the design file (project-relative to a root, or absolute under a root); it rides posture/handoff like the others |
| `spotlight` targets: `tab:source`, `records:row:n`, `topology:node:id`, … | new targets **`source:design`**, **`source:design:bean:<id>`**, **`source:design:line:<n>`** |
| log → source navigation via the processor's field declarations | **bean ↔ node**: under Spring authoring the bean id *is* the node's instance id (`nodeBeans` → `addNode(bean, id)`), so `detail:node:<id>` and `topology:node:<id>` gain "show declaration", and a bean line gains "show node / show records" |
| — | **evidence → design**: a `SPRING_*` diagnostic in `target/fluxtion-validation.json` / the compiler sidecar carries `element.beanName` (and, once required, `sourceRef`); the canvas resolves it to the bean's line |

## D-1 · The verb: `source`

One new verb, read-only with respect to data, view-changing like `goto`:

```
analyser_source {
  file?:   string   // a design file or any .java under a configured root; project-relative or absolute
  bean?:   string   // a bean id in the OPEN design file → scroll to its declaration
  line?:   integer  // 1-based line in `file` (or the open design file when `file` is absent)
  fqn?:    string   // existing: a node class → NODE mode
  method?: string   // existing: with fqn
}
```

Rules:

- `file` must resolve **under a configured source root or to the `design` path the session opened** —
  the same boundary `source_root` already draws; anything else is refused with the root list in the echo.
  Read-only: the verb never writes, and the file is re-read on each call (the LLM is editing it between calls).
- `bean` without `file` addresses the open design; with `file` it opens that file first. An unknown bean id
  echoes the ids that exist (the same courtesy `open.discover` gives for graphml).
- Echo: `{file, mode, line, bean?, nodeId?, hasRecords: n, hasSource: bool}` — the cross-links the LLM can
  name in its next sentence ("the node has 41 records in this log; its class is …").
- `open.design` is the *session* setting (what the canvas considers the design); `source` is the *view*
  action. A `source {file}` on a different XML does not change the session's design.

Why a verb and not more `open` parameters: `open` declares what the session is about and is expensive to
call casually (it re-plans the canvas); `source` is a glance, called many times in a conversation, exactly as
`goto` is for records. The existing `openFqn`/`openFqnAtMethod` behaviour becomes `source {fqn, method}` so
the LLM has one way to point at any text.

## D-2 · Rendering

- The Source tab's `DESIGN` mode renders the XML with an `XmlHighlighter` beside the existing
  `JavaHighlighter`; a bean-id gutter/index (`<bean id="…">`, plus `FluxtionSpringConfig`'s
  `nodeBeans` / `eventHandlers` / `serviceBindings` lists) so "bean" is a first-class anchor.
- **Follow** applies: when the design file changes on disk (the LLM ran `regenerate`, or edited the XML),
  the render refreshes and the current spotlight is re-anchored by bean id, not by line number.
- Nothing is evaluated. The XML is text with an index; no Spring context is created in the analyser.

## D-3 · Spotlight vocabulary

Add to `SpotlightVocabulary`: `source:design` (the whole design view), `source:design:bean:<id>`,
`source:design:line:<n>`. Reveal selects the Source tab in `DESIGN` mode and scrolls; the callout is the
LLM's caption and is shown as testimony, as today. A relation across artefacts — *"this declaration (1),
this node (2), this record (3)"* — is the case this exists for and is within `MAX_LIT`, but it crosses tabs,
so per the existing rule it is lit in sequence, not together. **Decision needed (Q1):** whether to allow a
split view (design left, records/topology right) so a declaration and its evidence can be lit together.

## D-4 · Evidence → design

`target/fluxtion-validation.json` and `target/fluxtion-reconciliation.json` (compiler Spec 2 §5.2) and the
compiler sidecar all carry the shared diagnostic envelope. When a diagnostic's `element` is a
`SPRING_BEAN` / `SPRING_SERVICE_BINDING` / `SOURCE_MEMBER`, the Reports/Assistant surfaces offer "show in
design", which is `source {bean}` (or `{file, line}` when `sourceRef` is present). This is what turns *"the
compiler refused FLX-1009 on riskStatusBook"* into a lit line in the XML with the fix text beside it.

**Compiler-side requirement filed with this spec:** make `sourceRef` (file, line, column) **required** on
every `SPRING_*` diagnostic in the A1b registry — it is optional today (`xpathHint`), which is why the
canvas would have to fall back to bean-id search. The analyser tolerates its absence; it should not have to.

## D-5 · What is deliberately out

- No editing, no validate, no generate from the analyser. The runbook owns the write side; the canvas is
  where the result is read. This keeps the analyser's read-only/root boundary exactly where it is.
- No Spring evaluation, no bean instantiation, no classpath. Text and an index.
- YAML designs (`compileFromReader`) follow the same shape later; the verb and targets are named
  `design`, not `xml`, for that reason. Not in M49.
- Design-time graph *without* a run (GraphML metadata default ON in the compiler) is a separate enrichment;
  the canvas already draws the graph the run produced.

## Acceptance

1. `analyser_source {file: "src/main/fluxtion/designer/application-context.xml"}` on a project under a
   configured root renders the XML in the Source tab; the echo names the file and mode; a file outside every
   root is refused and the echo lists the roots. `tools/list` shows the verb with read-only annotations.
2. `analyser_source {bean: "riskStatusBook"}` scrolls to the declaration; the echo carries `nodeId`,
   `hasRecords` from the open log and `hasSource`; an unknown id echoes the ids present.
3. `spotlight {target: "source:design:bean:riskStatusBook", caption: "…"}` lights the declaration; a
   `screenshot` shows it lit; the caption is rendered as testimony.
4. From a record of node `riskStatusBook` in the detail view, "show declaration" lands on the same line;
   from the lit line, "show node" selects `topology:node:riskStatusBook`.
5. A `SPRING_UNKNOWN_BINDING_NODE` finding loaded from `target/fluxtion-validation.json` offers "show in
   design" and lands on the binding's bean line.
6. Editing the design file on disk while it is shown re-renders within the Follow interval and keeps a
   spotlight anchored to its bean.
7. The verb never writes: a read-only filesystem containing the project passes 1–6.

## Open questions

- **Q1** split view for cross-artefact spotlights (D-3).
- **Q2** whether `source` should accept `xpath` for anchors that are not beans (an `eventHandlers` entry,
  a `serviceBinding`) — cheap once the index exists; deferred until a conversation needs it.
- **Q3** the `sourceRef`-required change is a compiler-side A1b edit; owner to confirm it rides Spec 2 A1.

# Design render — the Spring XML on the canvas (Design Spec)

**Status:** IMPLEMENTED ON MAIN, UNRELEASED, **revision 2** (2026-09-19) — revised after the independent review
[`review_spec_design_render_71e50ee5.md`](../../handoff/review_spec_design_render_71e50ee5.md) (NOT READY, R1–R4;
all accepted). Revision 2 author-checked, then independently re-reviewed in
[`review_spec_design_render_232c846a.md`](../../handoff/review_spec_design_render_232c846a.md). Implementation
accepted at `b7c82f5` and merged on main with owner approval on 2026-09-19; unreleased. Current status and
producer-delivery limits are in the tracker. **Milestone:** M66. **Tracker:** [tracker.md](../tracker.md).
**Builds on:** [`spec-shared-evidence-canvas.md`](../spec-shared-evidence-canvas.md) (the thesis this serves),
[`spec-authoring-modes.md`](../spec-authoring-modes.md), the `open` / `source_root` / `spotlight` verbs, the
public Spring-authoring documents ([`contract.md`](https://fluxtion-playground.dev/spring-authoring/contract.md),
[`skill.md`](https://fluxtion-playground.dev/spring-authoring/skill.md), the project `RUNBOOK.md` those documents
introduce), and the compiler diagnostics contract (`diagnosticsVersion 1.0`; per-code pages under
[telaminai.github.io/fluxtion/troubleshooting/errors](https://telaminai.github.io/fluxtion/troubleshooting/errors/FLX-1009)).

Owner, 2026-09-19: *"the analyser is the canvas with context; the LLM connects to the analyser to render
decisions/proof; the LLM authors with Spring XML; the user talks to the design-partner LLM and reviews the
evidence in the canvas. We already hold source-root pointers, so this is render, and a verb/parameter to tell
the analyser what file to display."*

Milestone correction: the original spec and review called this M49. M49 is already Runtime performance
in the archived tracker; this work is M66. Historical review filenames and commit subjects are unchanged.

## The proposition

The canvas shows three of the four artefacts a design conversation is about: the **log** (what happened), the
**generated processor** (what the compiler built), the **node source** (what the developer wrote). It does not
show the fourth — the **design** the LLM authored, the Spring XML the compiler turned into the other three. So
when the design partner says *"I declared `riskStatusBook` as a push target of `gate` — here is why, and here is
the record that proves the edge fired"*, the human can see the record, the node and the dispatch line, but not
the declaration.

M66 puts the design on the canvas as a first-class artefact, **read-only**, with the properties the other
artefacts have: **navigable** (bean ↔ node ↔ record ↔ source), **spotlightable** (the LLM lights a declaration
and says why; the caption is testimony), and **addressable from evidence** (a producer diagnostic names a
location; the canvas shows it). And with one property the first revision left implicit and the review made
explicit: the canvas **states the relationship** between the text on screen and the evidence beside it, and
never lets a shared name pass for proof (D-5).

Nothing here executes, validates or edits the XML. The runbook's utilities own the write side.

## D-1 · Verbs and parameters

### `open` gains `design` and `diagnostics`

- `open {design: <path>}` — the **session's** design file. Project-relative to an authorised root, or absolute
  under one; refused otherwise, with the root list in the echo. Rides posture/handoff like `log` and `graphml`.
  Switching project (a new `open.log` or `open.design` outside the current project root) **clears** it.
- `open {diagnostics: <path>}` — explicit intake of one **producer result** (review R3). Same root boundary.
  Supported wrappers, detected by shape, never guessed: `ValidationResult` (`diagnosticReport.diagnostics[]`),
  `ReconciliationReport` (`diagnosticReport.diagnostics[]`), compiler sidecar `DiagnosticReport`
  (`diagnostics[]`). Unknown `schemaVersion`/`diagnosticsVersion`, unreadable file or unrecognised wrapper →
  **refused**, and the previous result — if any — is **cleared, not retained** as if current. A new `open.diagnostics`
  **replaces** the previous. `open {discover: "diagnostics"}` lists candidate result files under the project's
  `target/` **without loading one** (the same rule as `discover: graphml`: auto-selecting is the convenience that
  reintroduces the defect discovery exists to prevent). Works with **no log open**.

### `source` — the glance

```
analyser_source {
  file?:   string   // a design file or any .java under an authorised root; project-relative or absolute
  bean?:   string   // a bean id → its declaration in the SESSION design (or in `file` when given and it is XML)
  line?:   integer  // 1-based line in `file`, or in the session design when `file` is absent
  fqn?:    string   // a node class → NODE mode
  method?: string   // with fqn
}
```

- Read-only with respect to data; changes the Source tab view like `goto` changes the records view. Re-reads
  the file on every call (the LLM edits between calls).
- **Selectors are exclusive families** (review): `{file}`, `{file, line}`, `{bean}`, `{file, bean}` (XML only),
  `{line}` (session design), `{fqn}`, `{fqn, method}`. Any other combination — `bean`+`line`, `file`+`fqn`, `method` alone — is **refused**
  with the accepted shapes in the echo. No precedence games.
- `source {file: B}` does **not** change the session design; `spotlight source:design:*` always resolves against
  the **session** design (`open.design`), and the echo names the resolved file so the LLM cannot mistake the
  glance for the session (review: "pin A"). The same bean id in both files is the test.
- Echo: `{file, mode, line?, bean?, nodeId?, records?: n, source?: bool, relationship}` — `relationship` is
  D-5's state, present on every design-side echo.
- An unknown bean id echoes the ids present; a bean id present twice is **ambiguous**: the echo lists both
  lines and nothing is selected or spotlit.

### Publication and root policy (re-review G2/G3)

`source` is the sixteenth verb, distinct from `topology`'s existing `source` boolean. It lands in
`VerbSchemas`, `ActionDispatcher`, `ActionExecutor`, `AppControl`, MCP `tools/list` with `readOnlyHint`,
the built-in assistant manifest, the assistant user guide and source-navigation guide. The manifest's
verb/parameter inventory test and spotlight vocabulary equality test must pass. The three design families
are exercised by `tools/check-design-render.py`; `tools/verify-m64-spotlight.py` retains its M64 scope.
No canonical guided-start or point-at-the-fault skill is changed in M66, so no playground skill re-vendor
is owed by this implementation.

The **configured source roots** authorise Java, XML and producer JSON reads beneath them. This widens the
previous Java-only description of the grant. The project root supplies a relative-path base, not an implicit
grant: XML or JSON inside a project but outside every authorised root is refused. Include a containing root
for `target/` to read findings and receipts. `open.design`/`open.diagnostics` and `source.file` resolve
project-relative paths without rewriting a project profile, following M38's path-anchor rule.

## D-2 · Rendering

- Source tab gains a **`DESIGN`** mode beside `PROCESSOR` / `NODE` / `SPLIT`: `XmlHighlighter`, a bean index
  (`<bean id>` plus `FluxtionSpringConfig`'s `nodeBeans` / `eventHandlers` / `serviceRegistrations` entries), the
  existing back stack.
- The design view has its **own Follow eligibility** (review): it refreshes on a change to the design file
  whether or not a log is open or followable (`MainFrame.setFollowing` today requires a followable log store;
  the design watcher does not). A malformed intermediate write (the LLM mid-edit) renders the last good
  revision with a *"parse error at line n"* banner and does not clear anchors; the next good write replaces it.
- Every render carries a **document revision** (sha256 of the bytes). Anchors (spotlights, the `line` echo)
  are stamped with it — D-5 says what happens when it changes.
- No Spring context, no bean instantiation, no classpath. Text and an index.

## D-3 · Spotlight vocabulary

`source:design` · `source:design:bean:<id>` · `source:design:line:<n>`. Reveal selects the Source tab in `DESIGN`
mode and scrolls; the callout is the LLM's caption, shown as testimony. Cross-tab relations (declaration, node,
record) are lit **in sequence**, as the existing rule requires; a request whose targets cannot be visible
together is refused whole, not partly honoured (review, Q1 stays optional). On a document revision change: a
`bean` spotlight whose bean **still exists** is re-anchored to its new position and its echo/callout gains
*"(design edited since this caption)"*; a `bean` spotlight whose bean **disappeared or became ambiguous** goes
out and `wentOut` names it — it is **never rebound** to a nearby declaration; a `line` spotlight goes out on any
revision change (a line number is not a stable identity).

## D-4 · Evidence → design: the location-resolution table (review R1)

A producer diagnostic's `element` is a tagged union; "any `SPRING_*` names a bean" was false. Resolution goes
**offending location first, referenced bean second**, and every row has an *unavailable* outcome that keeps the
diagnostic and says why navigation is not offered:

| `element.kind` (codes) | Offending location | Fallback | Unavailable → UI says |
|---|---|---|---|
| any, with `sourceRef` | `sourceRef` resolved through the **report's `sourceRoot`** mapped to an authorised local root; XML → `DESIGN` at line; `.java` → `NODE` at line | — | *"location outside authorised roots"* |
| `SPRING_SERVICE_BINDING` (`SPRING_UNKNOWN_BINDING_NODE`, `…_INCOMPLETE`, `…_DUPLICATE_…`) | the **binding declaration** (the `serviceRegistrations` entry) — even when the target bean exists, and even when `beanName` names a bean that does *not* exist | the `FluxtionSpringConfig` block, labelled *approximate*; several matching bindings → *ambiguous*, both listed, none selected | *"binding not found in this design"* |
| `SPRING_BEAN` (`…_DUPLICATE_BEAN_ID`, `…_DANGLING_BEAN_REF`, `…_ROLE_CONFLICT`, `…_SERVICE_DECLARED_AS_BEAN`, `…_WIRED_AS_DEPENDENCY`, `SPRING_HANDLER_MISMATCH`, `SPRING_BEAN_NOT_SELECTED`) | `<bean id="beanName">`; for a dangling ref, the **referencing** `<ref>`/`ref=` site first, the missing target is by definition absent | the config list entry naming it | *"bean not declared in this design"* |
| `SPRING_CONFIG` (`…_LEGACY_CONFIG_FALLBACK`, `…_LOG_LEVEL_CONFLICT`, `…_STRICT_SERVICE_BINDINGS_EMPTY`) | the `FluxtionSpringConfig` bean(s) | document start | — |
| `SPRING_DOCUMENT` (`…_XML_NOT_WELL_FORMED`) | `sourceRef` line/column when present | document start with the parse message | — |
| `SPRING_TYPE` / `EVENT` / `SERVICE` (`SPRING_EVENT_SERVICE_TYPE_COLLISION`, `…_UNKNOWN_HANDLER_EVENT`, `…_UNKNOWN_SERVICE_TYPE`, `…_UNUSED_SERVICE_TYPE`) | the `eventTypes` / `serviceTypes` list entry naming the FQCN | the config block, *approximate* | *"type not listed in this design"* |
| `NODE` (compiler codes such as **`FLX-1009`**, `FLX-1001`, `FLX-1008`) | under Spring authoring `nodeName` **is** the bean id → `<bean id="nodeName">`; the `element.nodeClass` also offers `source {fqn}` | — | *"no bean with this node name — not a Spring-authored node?"* |
| `SOURCE_MEMBER` (`SPRING_RECONCILE_CONFLICT`, `…_WRONG_CONSTRUCT`) | this is a **Java** location: `sourceRef` if present, else `source {fqn: className}` — `member` may be a method, a field (`field:`) or an interface use (`implements:`), so it selects the class and scrolls by name when it can, else opens the class | the `xmlDeclaration` text is shown in the finding, and the bean whose declaration it quotes is offered as a *secondary* design anchor | *"class not under an authorised root"* |

Rules: the finding is **always retained** whether or not navigation resolves; the UI action is *"show"* only
when a location resolved, *"show (approximate)"* for fallbacks, and a greyed reason otherwise. The producing
stages this table must cover: validation (`fluxtion-validation.json`), reconciliation
(`fluxtion-reconciliation.json`), and the compiler sidecar — all three, not the sidecar alone.

**Upstream requirement, accepted in A1 (review R1.4; contract commit `29ea9ab`):** the diagnostics registry
requires an accurate `sourceRef`
**where the producer knows the originating document and position** (the XML validator does, for every
`SPRING_*` it raises from a parsed document; the reconciler does, for the Java member it refuses), and keeps an
explicit *unavailable* case otherwise — never an invented file or column. `xpathHint` stays a separate element
property. Report-relative paths resolve through authorised local roots. The authoring contract assigns this
to A1, including diagnostic goldens carrying `sourceRef`. **Acceptance 5's exact-location integration remains
gated on producer delivery**, not just this contract change. M66 verifies explicit-location fixtures and the
fallback rows; no released producer satisfying the new requirement has been exercised.

## D-5 · Relationship state — a name is not evidence (review R2)

The design file is a **working copy**. The log was produced by *some* build of *some* revision of it; a result
file describes *some* attempt. Bean-id equality lets the canvas **navigate** between them; it does not establish
that the declaration on screen produced the records beside it. So every design-side echo, the design view's
header and each "show in design" action carry one of:

| `relationship` | Meaning | Established by |
|---|---|---|
| `unverified` | working copy; matched by name; relationship to this run **unknown** | the default — and, in M66, the **only state a loaded log can reach**: there is no run/model identity carrier linking an arbitrary log to the build that produced it (tracker M48.12, still open). M66 ships with this stated, not hidden |
| `input-current` | this XML **is the input** the named result file describes | the authoring **run receipt** (`target/fluxtion-run.json`): the stage's `outputs.xmlHash` when outputs exist, otherwise `inputs.xmlHash` equals the document revision on screen — **and only that**: an unchanged XML after a Java edit is still `input-current` for the XML and says nothing about the build; the receipt's `sourceHash`/`recordHash` and `build.compilerRan`/`outcome` are surfaced beside it so the human sees *which* inputs match |
| `input-stale` | the result file describes an earlier revision of this XML | receipt `xmlHash` ≠ document revision, or the result's own `inputHash` ≠ |
| `unknown` | no receipt, no hashable result | a compiler sidecar alone; a project without the authoring record |

Consequences: a `records: n` in the `source {bean}` echo is labelled `unverified` unless a future carrier
upgrades it; a stale or failed-build result is never presented as current (`open.diagnostics` of a sidecar with
a receipt saying `build.compilerRan: false` shows *"sidecar predates the last attempt"*); a caption survives a
bean's edit only with the *"(design edited since this caption)"* mark (D-3). **The combined case the review
names — unchanged XML, edited Java, an old log, a failed latest build — reads:** design `input-current` for
the XML with `sourceHash` mismatch shown; validation result `input-current`; build receipt `outcome: failed`;
sidecar *predates the last attempt*; log relationship `unverified`. Each stated; none inferred. Producer hashes use `sha256:<hex>`; document revisions use the same SHA-256
hex over the original UTF-8 bytes, with the prefix normalised for comparison.

A design hash in the runtime descriptor (`DescriptorSupport.Meta`) would let a *log* be tied to an XML revision
later; that is a public-API contract change owned elsewhere and is **not** an M66 requirement.

### Implementation bounds (2026-09-19)

File reads and parsing run outside the UI thread; requested/completed facts pass through the existing
Fluxtion session graph. New requests or project changes supersede late reads. Follow is independent of logs.
Files are UTF-8, at most 2 MiB. A malformed first open renders inert text without an index; Follow retains a
last good parsed revision when one exists.

`source {bean}` reports a bounded record preview: `recordsScanned` and `recordsExact` qualify `records`.
“Show records” searches off the UI thread and opens the first matching record. Java/authoring-record hashes
and the receipt are read at explicit diagnostic intake; their timestamp is visible and the UI asks the user
to reopen diagnostics after source/build changes. XML comparison follows the rendered revision. Receipt
`outputs` supersede `inputs` when present; effective stage `options.sourceRoot` controls the source scan.
A scan exceeding 10,000 files is unavailable, never truncated into a matching hash.

## D-6 · What is deliberately out

No editing, validating or generating from the analyser. No Spring evaluation or classpath. YAML designs follow
the same shape later (the verb and targets say `design`, not `xml`). Design-time graph without a run is a
separate enrichment. The run/model identity carrier (M48.12) is a separate milestone; M66 states its absence.

## Acceptance

1. `source {file}` renders a design under an authorised root, echo names file, mode and `relationship`; a file
   outside every root is refused with the root list; `tools/list` shows the verb read-only; manifest verb/parameter inventory and spotlight vocabulary equality tests pass.
2. `source {bean}` scrolls to the declaration with `nodeId`, `records` (labelled `unverified`), `source`;
   unknown id echoes the ids present; a duplicated id is ambiguous (both lines echoed, nothing selected).
   Mixed selectors are refused with the accepted shapes.
3. `spotlight source:design:bean:x` lights the declaration; caption as testimony; `screenshot` shows it.
   `open.design = A; source {file: B}; spotlight source:design:bean:x` lights **A**'s bean and echoes A.
4. From a record of node `x`, "show declaration" lands on `<bean id="x">`; from that line, "show node"
   selects `topology:node:x`; both echo `unverified`.
5. **Location table** (gated as D-4 says): `open.diagnostics` of a `ValidationResult` with
   `SPRING_UNKNOWN_BINDING_NODE` whose target does not exist → the **binding** line; an incomplete binding with
   no node → the binding line; malformed XML → document start with the message; a `SOURCE_MEMBER` conflict →
   the **Java** class under an authorised root; `FLX-1009` (`NODE`) → the bean with that node name; a report
   whose `sourceRoot` differs from the project root → resolved through the authorised root or *"outside
   authorised roots"*. Every unresolved case keeps the finding and shows the reason.
6. Follow with a design and **no log**; with a log that cannot be followed; a malformed intermediate write
   (last good revision + banner, anchors kept); a removed bean (spotlight goes out, `wentOut` names it, no
   rebinding); a duplicated bean (ambiguous); lines inserted above a `line` spotlight (goes out).
7. Intake: a design and a failed validation result with **no log open**; each of the three wrappers; replace
   a result with a corrected one (old one gone); switch project (design and diagnostics cleared); an
   unsupported schema / unreadable file refused **without** retaining the previous result.
8. **Relationship**: edit an edge keeping the bean ids with an old log open → `unverified`, records still
   navigable, caption marked edited; a sidecar surviving a failed build that never ran the compiler →
   *predates the last attempt*; the combined case in D-5 reads as D-5 states.
9. The verb never writes: a read-only filesystem holding the project passes 1–8.

## Open questions

- **Q1** split view for cross-artefact spotlights — optional, not for M66.
- **Q2** `xpath` anchors in `source` — deferred; D-4's table covers the shapes the registry emits today.
- **Q3 — resolved (2026-09-19):** authoring-contract commit `29ea9ab` assigns the `sourceRef` requirement and
  diagnostic goldens to A1 and names M66 as a consumer of the existing run-receipt fields. Producer-delivery
  verification remains the D-4 gate. The log↔build identity carrier is a separate public-API change outside
  A1; M66 does not wait for the design-hash-in-`Meta` idea.

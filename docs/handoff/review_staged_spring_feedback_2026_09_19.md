# Review of the staged Spring authoring feedback — 2026-09-19

Decision: improve evidence correctness before adding more presentation targets. The session supports
the core authoring workflow, but exposes misleading analyser outputs and a weak route from authoring
to a hosted, data-driven run. Record-order charts and entity grouping are the strongest next feature
requests. Several suggested mechanisms need correction before implementation.

Scope: all 578 lines of ANALYSER-FEEDBACK.md (issues 1–21, both addenda and corrections), all 271 lines
of AUTHORING-DOCS-FEEDBACK.md, all nine screenshots, selected implementation/specification reads, and
independent probes on the saved logs and local scaffold endpoint. The 60 preserved files, including both reports,
are preserved in the [second evidence packet](evidence/spring-authoring-feedback-2026-09-19-round2/README.md).
The earlier snapshot remains unchanged. This review introduces no product fixes.

## Evidence and corrections that change the decision

1. **Marker counts reproduced exactly.** The standalone log contains 5 positive and 6 negative quantity
   records; current extraction produces 11 buy and 10 sell markers. Hosted runs produce 9 and 10.
   The bare price key correctly produces 8. The probe lists extra fires on PriceUpdate records and,
   in the standalone run, two LifecycleEvent records. `MarkerExtractor.java:79–101` carries the last
   quantity and evaluates it on every record. This is not random counting or a parser error: it is
   an insufficiently exposed distinction between event markers and carried-state conditions.
2. **The business comparison holds.** With timestamps and thread labels normalized, the 19 business
   records match between the standalone and both hosted runs. Only full-log rows 0 and 3 differ
   between hosted runs, both listener identity strings. Correct event counts are 11 trades (5 buys,
   6 sells), 8 prices. The earlier 12/7 claim and associated provenance were participant mistakes.
3. **Headless scaffolding already works.** The local `/start/scaffold?template=fluxtion-spring-mongoose`
   endpoint returned the 22-file template without a browser or source-tree bundling. See
   `web/src/routes/(marketing)/start/scaffold/+server.ts:1–31`. Improve discovery and examples;
   do not duplicate the TypeScript template generator in starter-core. A fully offline generator is
   a separate, currently unproven requirement.
4. **Same-path reopen works, per the addendum.** Issue 1 is accepted as snapshot/freshness reporting,
   not as evidence that reopen serves a stale cache. Existing Follow behaviour must remain an explicit
   user choice; a blanket prohibition on refresh would conflict with existing functionality.
5. **nodeTypes is selection-scoped.** `MainFrame.java:5546–5550` passes `selectedRecords` to
   `PromptBuilder.nodeTypes`, which walks only those records. Issue 10 is an unclear scope, not proof
   that RootNode failed source resolution. Name the scope; do not make every context call resolve
   every graph class merely to fill this map.
6. **The staged dependency version is a fixture limitation.** The launcher deliberately retained a
   cached BOM while overriding builder/starter with local test artifacts. The copied README's runtime
   claim can disagree with this sample. Correct the demo provenance and generated README from effective
   versions; do not change the shared released BOM based on this fixture.

## Disposition of all analyser/starter issues

P1 means evidence correctness to address before claiming the full loop is trustworthy. P2 is the next
capability or substantial usability work; P3 is polish. This prioritization is not owner merge/release
approval, and does not supersede existing compiler/playground review dispositions.

| Issue | Decision | Evidence / required improvement |
|---|---|---|
| 1 | Accept, narrow; P1, analyser | Source confirms `currentLogFileInfo()` reads live `Files.size` with cached store counts/times (`MainFrame.java:2830–2842`). Capture coherent loaded facts and separately report changed/missing on-disk artifacts, with reopen guidance. Respect Follow. Same-path reopening is already a working workaround. |
| 2 | Accept; P1, analyser | Inspected screenshot shows source cut-outs over the header. `DesignSourcePanel.java:80–87` returns a line rectangle without checking the text viewport. Resolve the whole requested set after reveals/layout and refuse non-co-visible anchors. |
| 3 | Accept for reproduction with 2; P1 | Negative height is in the participant's quoted echo. `inOverlay` rejects an initially empty rectangle, but that does not establish the final exported/clipped rectangle is valid. Test the entire geometry/echo path; exact negative-height cause not independently reproduced. |
| 4 | Accept for reproduction with 2; P1 | Before/after screenshots and reported echo support lost lights. Reveal, selection clearing, queued layout and relighting interact (`MainFrame.java:2409–2435`). Preserve stable IDs and account for departures, including deferred ones. A single root cause for 2–4 is not established. |
| 5 | Defer feature | A changeset needs captured previous text and revision identity, not just hashes. First make existing source targets trustworthy; later design a bounded diff/read surface and navigation. No new verb by convenience. |
| 6 | Accept consistency gap; P2, compiler + playground | `Reconciler.java:133–151` creates a plain class; handler stubs return true without audit values (`603`, `628`). Initial starter audit templates differ. New owned classes should follow an explicit audit policy. Existing classes must not be re-parented. Trace entries already show execution; absence of values is not absence of the node. |
| 7 | Accept; P3, compiler | Constructor names `argN` are explicit at `Reconciler.java:1056`; method formatting overlaps F5/G13/F10. Improve names/formatting and explain event shells while preserving ownership hashes and no-op reconciliation. |
| 8 | Accept; P3, compiler | `Starter.java:243` prints the long link by default. Emit a short path or opt-in link/verbose output. Keep machine-readable diagnostics separate and available. |
| 9 | Accept freshness gap; P1, analyser | `DesignWorkspace.diagnostics()` captures receipt/source checks at intake; ProducerResult uses those stored checks. The UI already says “at intake”/“reopen”, so not every field falsely claims current evaluation. Add explicit file/receipt supersession and qualification of build verdicts. Hash large trees off the EDT with bounds, not unconditionally on every context call. |
| 10 | Reframe; P3, analyser docs/schema | Selection-scoped map confirmed in source; explain scope and provide a deliberate path to wider resolution. Do not report a missing-type defect. |
| 11 | Accept feature; P2, analyser | Existing raw-series schema has no entity split. Per-symbol application keys are a working but unscalable workaround. Add same-record value/entity grouping with explicit occurrence rules, cardinality cap and truncation evidence to graph and series. Never carry an entity label from a different record. |
| 12 | Accept semantic correction; P1, analyser | Independent probe reproduces 11/10/8. Expose event-vs-carried-state resolution, default new event markers to same-record evaluation, and disclose/migrate old saved definitions deliberately. Retain an explicit carried-state option and record-index drill-down. |
| 13 | Accept configurable enhancement; P2 | Probe confirms MSFT's final value is 100 before log end; `ChartPanel.java:566–584` draws only between samples. Add an explicit held-tail option with a visible held/observed distinction, scope end and NaN/staleness rules. Step styling alone must not assert indefinite state validity for every metric. |
| 14 | Accept; P2, analyser rendering | Screenshots and `ChartPanel.java:780–818` confirm explanation/note text inside the plot and clipping. Reserve layout space and wrap text in the shared screen/export layout. Verify small widths and long notes. |
| 15 | Accept discoverability gap; P2, schema/docs | Formula syntax and error positions already exist (`Expr.java:152,237,294,317`; `docs/site/user-guide/graphs.md:20–64`). Link/expose a concise capability reference through existing tools, including unsupported text operands and absent-key semantics. No need for a new general verb or a second parser. |
| 16 | Accept; P3, runtime | Normalized comparison isolates two control records. `EventLogControlEvent.java:103–108` appends the listener object. Use a stable logical descriptor or omit process identity; a lambda class name can also contain unstable `/0x…` text, so removing only the trailing identity hash is insufficient. |
| 17 | Accept feature, first chart capability; P2 | Saved run has many records sharing a millisecond, and the screenshot visibly compresses the transitions. Preserve record identity in series/markers and offer record-order x-axis. Do not pace the application to make a chart readable. Record/file order must not be advertised as global causality in concurrent logs. |
| 18 | Accept silent-loss issue; P2, revise remedy | `ActionExecutor.java:1233–1237` converts recordIndex to time; `GraphSpec.NoteSpec` stores only time/text/series. Retain anchor kind and originating-log identity, and report unresolved/out-of-window notes. Never silently bind index 26 in an unrelated run: cross-run mapping must be explicit or verified. |
| 19 | Fold into 12 | Independent probe confirms 9/10 in both hosted runs despite the same 19 business records. This is additional evidence for the same semantic issue, not another implementation task. |
| 20 | Accept; P2, analyser | `ActionExecutor.java:966–975` stores the design echo before loading diagnostics. Assemble the returned state after the combined request applies, retaining honest per-step failures. Source-confirmed; no independent live reproduction performed. |
| 21 | Fold into 14; P2 | Inspected paced screenshot shows the final rise obscured by the legend. Reserve space outside the data rectangle; do not fix note placement while leaving the legend collision. |

## Authoring and hosting decisions

| Request | Decision and owner |
|---|---|
| One re-entry path | Accept now: playground-generated docs plus local harness. Short orientation, obvious runbook pointer, and task-specific links. Put silent failure rules (reference direction, owned code, audit before init, mutable collections, time) on that path. Avoid requiring a thousand-word preload or renaming files without compatibility links. |
| Data-driven example | Accept: a CSV-driven example with expected outputs, explicit ordering and a repeatable completion condition. Keep the small Java assertion harness for focused semantics; a plain main is valid and Mongoose is not mandatory for every test. Distinguish bare main, DataFlowConnector and Mongoose. |
| Headless template creation | Document and test the existing `?template=` endpoint first. The browser's Copy curl and local endpoint already support it. No Java generator port; no new authoring server. |
| Re-host existing project | Start with a tested migration recipe listing dependency/classpath, host boot, supplier, config, mapper, module opens and audit ownership. Introduce an automatic rehost command only after the recipe and conflict policy are demonstrated. |
| Hosted audit listener | Fix in the playground template: downloaded supplier calls setAuditLogProcessor, then Mongoose's `ServerConfigurator.java:126` replaces it before init. Wire the intended listener at `bootServer(reader, listener)` or the documented server capture boundary. Test lifecycle and business records at the actual host boundary. |
| Feed order / reset | Document one ordered mixed-event stream when cross-type order matters. Mongoose `FileEventSource.java:102` starts EARLIEST at zero; COMMITED uses `<file>.readPointer` (`93–112`). A broad `market.csv.*` cleanup is not the correct generic reset recipe. Test strategies individually. |
| Completion / replay | A quiet audit file for two seconds is not proof that all inputs completed. Use expected input/output counts or an explicit completion acknowledgement. Separate deterministic business results, replay support/provider requirements, and byte-identical audit text. Use injected time in domain behaviour; do not call a diagnostic host timestamp itself a determinism bug. |
| Logging for analysis | Accept: numeric/boolean values, event/entity identity, sparse samples and useful state transitions must be designed up front. Until grouping exists, label fixed per-entity keys as a limited workaround. Preserve trace-only versus value evidence. |
| Dense runbook | Reformat receipt verification into short checks without weakening source/record freshness or compilerRan rules. Keep initial design and existing-project re-entry clearly separated. |

Resolve the reported contradictions by testing and scoping them, not by deleting one side arbitrarily:

- The blanket AOT SinkPublisher prohibition is too broad for the successfully generated Spring sample.
  Pin working Spring and imperative/service-injection examples against the carrying compiler version,
  then narrow the warning to the configurations it actually excludes.
- `SingleNamedNode` extends `EventLogNode` (`runtime/node/SingleNamedNode.java:24`): these are compatible
  alternatives. Explain naming and audit capabilities; do not require a single superclass everywhere.
- XML declares event identity/bindings, not general business payload fields. Say clearly that shell
  payloads are implemented after generation; introducing an event-schema language is a separate design.
- Resolve version prose from the effective project. The local cached-BOM rehearsal is not the public
  template's dependency contract. Distinguish local, custom HTTP and remote generation prerequisites.

## Delivery order and acceptance

1. **Evidence correctness:** existing DX-02 pairing-membership correction plus feedback 1/9/20,
   source spotlight 2–4, and marker 12/19. Separate reviewable fixes, not one large UI rewrite.
   Acceptance must exercise loaded snapshots overwritten in place, combined-action echoes,
   scrolled source views, and the saved standalone/hosted marker fixtures. New STRICT markers must
   show 5/6/8; explicit legacy/carried semantics must be labelled and tested. Capture real screenshots
   and inspect them, including a refusal case. Every new context fact needs its human surface and docs.
2. **Authoring route and host boundary:** short orientation/read order, headless scaffold link,
   CSV example, correct hosted audit listener, feed/reset/completion documentation, and audited
   reconcile-add consistency. Use another fresh client with only the generated project's entry point.
   It should locate the template without source bundling and execute the data without invented timing.
3. **Analysis capabilities:** record-order x-axis first, then entity grouping. Test repeated timestamps,
   filters, record selection, markers, notes, exports and label/cardinality limits. The 19 inputs should
   be examinable at full host speed without changing node logging for each chosen entity.
4. **Presentation and maintenance:** annotation/legend layout, explicit note remapping and held tails,
   source formatting, quieter CLI output, stable control-event text. Keep the existing review's
   ownership/no-op requirements and report wording honest. Changeset spotlights remain deferred.

Cross-repo source was read at the revisions in the packet manifest; the runtime/Mongoose checkouts
may differ from the sample's resolved binaries. Runtime log claims above are backed by the saved
sample logs; source references explain candidate causes and must be checked against the release
version when implementing. No paid compilation, replay licence use, publication, browser-preview
witness, full gate rerun or modifications to the participant's live session were performed here.

The prior Java/MkDocs gates do not cover these user-level failures. Passing them is not grounds to
close this feedback. Publication and G14 remain separate gates. Before calling the Spring evidence
loop ready, require the first two delivery slices and a fresh-session check of their intended effect.

## Third addendum — report export, issues 24–27

The participant subsequently appended issues 22–28. The five points in the owner's follow-up map to
24, 25 and three parts of 26; issue 27 additionally covers clipping and a missing boolean band.
Keep that numbering; do not invent five new issue numbers. The updated participant document and an
independent probe are in the [third packet](evidence/spring-authoring-feedback-2026-09-19-round3/README.md).
This addendum supersedes the earlier delivery order where report integrity is concerned: **24 joins
the first evidence-correctness slice; 25 and 26 should precede optional chart presentation work.**

### Verified, not just accepted from the report

- Read both exported PDFs and inspected every rendered page: 9 pages in the first draft, 8 in v2.
  Neither contains a topology picture or its fallback. The first draft page 1 says “The graph above”;
  v2 changes that account to point to the app. Both show seven expected-behaviour notes with
  `WHAT IS WRONG` headings. First-draft page 6 prints the text-comparison parse warning; page 8
  prints the series assembly stub. Its title is clipped; both PDFs clip `WRITTEN AGAINST`.
- Ran `ReportFeedbackProbe.java` against the current built jar and saved desk log, without changing
  the live application. A focus accepted by `ReportResolver` resolves with no warning. With the exact
  fallback-content shape that MainFrame supplies, its text vanishes from the PDF; the series fallback
  survives. A finding containing “Expected behaviour” receives `WHAT IS WRONG`.
- Repeated `Expr.parse("orderGateway.decision == \"REJECT\"")`: it produces the reported “not a duration”
  error. A projected read-table request centred on record 69 with count 6 returns 67–72, including
  the intervening signal records. A **table** sourced from `series {expr: "pnlCalculator.totalPnl"}`
  produces one statistics row with no notes: this works and is distinct from a **series section**.
- Checked boolean typing: unquoted `true` is BOOLEAN and graphable as 1; quoted `"true"` is TEXT and
  not graphable. That distinction is deliberate, not a defect to remove for the missing-band claim.

### Decisions and acceptance

| Issue | Priority / decision | Change and acceptance |
|---|---|---|
| 24 — silent topology omission | **P1, confirmed defect** | `MainFrame.java:1284–1291` supplies `monoLines` as an explicit fallback, but `ReportRenderer.java:156–163` only consumes picture/table for TOPOLOGY. `ReportResolver.java:155` verifies focus existence, not export support. At minimum render an in-place limitation and return a warning with the section/focus identity. Full closure of the capability request requires rendering that saved focus, not whichever topology happens to be selected. Test a valid focus, missing focus and unavailable render; assert every requested section is rendered or explicitly accounted for in the PDF and echo. Preserve following sections and leave the user's view unchanged. |
| 25 — expected behaviour framed as defects | **P2, confirmed semantic mismatch** | `ReportRenderer.java:138–143` and `FindingReport.java:100` hard-code defect language; `Finding` has no category. Use neutral observation wording by default, with an explicit defect/observation/evidence category chosen at the existing flag write site. Persist it and render consistently in records, topology and both PDF paths. An evidence category is an author classification, not proof the interpretation is true. Keep note text unchanged and keep the narrative banner. Specify a compatibility policy for existing flags; no report-local second copy of the finding. Test all categories and an old saved flag. |
| 26a — text row predicates | **P2, confirmed missing capability** | Quoted Expr tokens are parsed as duration literals (`Expr.java:324–328`), so text equality cannot work. Add typed equality/inequality under an explicit common expression contract; define missing values, case sensitivity, quoting, repeated-key resolution and no implicit number/string coercion. Row rules remain same-record/STRICT. The first draft already prints a warning and leaves rows unhighlighted: preserve that honesty. Acceptance highlights only REJECT rows and excludes intervening records missing the key, with the evaluated rule printed. |
| 26b — series PDF stub | **P2 capability; disclosure belongs with 24** | `MainFrame.java:1292–1297` unconditionally creates a stub for a resolved SERIES section. Reuse the existing series computation/assembly rather than introduce another calculator, or explicitly warn and document the supported `table`/`chart` alternative until implemented. A resolved key alone must not imply evidence was exported. Acceptance compares PDF values and scope with the corresponding series result, including empty, unresolved and capped results. |
| 26c — arbitrary record selection in tables | **P2, accept with narrower wording** | Read-backed tables use `ReadService`'s contiguous range (`ReportVerb.java:252–289`, `ReadService.java:91–132`). Tables can already use aggregate, series statistics/crossings and coverage, so “every table is a contiguous read” would be wrong. Add a bounded, derived selection from explicit record anchors or a declared predicate/marker source through existing surfaces. Preserve log identity, deterministic ordering, repeated-record policy, caps/omitted counts, and per-row highlight anchors. Do not accept arbitrary caller-written evidence rows. Acceptance selects 67/69/70 without 68/71/72 and includes a multi-order record without silently losing its distinct occurrences. |
| 27a — title and provenance clipping | **P2 provenance, P3 title** | `ReportRenderer.java:347,371` clips to measured width, not a fixed character count. Wrap the title and provenance or provide a full provenance block elsewhere in the PDF; provenance must remain readable after export. Test a long identity and title with pagination, not just a string assertion. Both original PDFs substantiate this. |
| 27b — boolean band absent | **Needs exact reproduction before defect classification** | The addendum gives no band declaration, key or record. Quoted boolean text is intentionally not a boolean; bare booleans are supported. Obtain the exact saved definition and inspect the parsed value, condition and scope. Require an explicit unresolved/type explanation for unsupported operands, but do not coerce all quoted strings or claim the boolean renderer is broken from this evidence. |

The archived M33 author report (`completed/report_feat_m33_reports.txt:98–100`) says topology and
series gaps are printed in place. The topology claim is false at the current head; the new probe
catches the missed renderer integration. The existing renderer tests cover unresolved references,
but this case is a **resolved reference with no assembled picture**. Add that integration case when
fixing it. Unsupported sections also need export warnings, not merely a clean reference count.

Practical interim use: exported named charts and their marker tables already carry real content;
`kind: table` with a `series` call provides statistics/buckets/crossings. They are explicit alternatives,
not grounds to report the topology or series-section defects as closed. Do not embed a screenshot of
the wrong focus merely to avoid a warning.

### Other newly appended issues: intake, not independent closure

- **22 — generated propagation defaults:** the compiler source does contain `return true` stubs
  (`Reconciler.java:603,628`). The participant's signal mutant and double-booking result were read,
  not re-run. Escalate to the compiler author for a propagation-contract regression. A blanket rule
  that all handlers with dependents return false is also unsafe: this same scenario intentionally
  evaluates on `hedgingOn`. Make the propagation choice conspicuous in stubs and verification;
  any new generation default needs an explicit semantic decision and ownership/hash tests.
- **23 — callback type documentation:** accept the request for a boolean type, accepted values and
  actionable diagnostic as upstream intake; no fresh compiler validation run is claimed here.
- **28 — reopening a project:** accept an explicit “restore last session” offer as a workflow proposal,
  not a reason to remove the project session boundary. Persisted candidate state needs stale/missing
  artifact checks, project identity and an honest list of what can be restored. No automatic restore
  or cross-project reuse. This reset was reported by the participant; it was not reproduced against
  their live session.

No app/compiler fixes, full gate rerun, paid compilation, project reopen or new business-model
validation occurred in this review. The original PDFs and current desk artifacts contain literal
exchange names; their hashes/locations are recorded, but those originals are not copied into this
public repo. The neutral updated feedback and probe are preserved. Prior snapshots remain intact.

## Fourth addendum — four use cases and the validation pack

The latest change is **159 appended lines in AUTHORING-DOCS-FEEDBACK.md**, beginning “The analyser as
a shared canvas”; ANALYSER-FEEDBACK.md is unchanged from round3. Read the entire addition. The
[fourth packet](evidence/spring-authoring-feedback-2026-09-19-round4/README.md) preserves it and an
independent comparison-scope probe. Verdict: **accept the direction, revise the mechanisms and several
claims before implementation.** This is a proposal review, not approval to add verbs or a task runner.

### What the reframing improves

Authoring, maintenance, support and evidence walkthroughs are useful acceptance scenarios. They explain
why entity grouping, sequence charts, general record tables and validation-oriented findings matter.
They fit the existing [shared evidence canvas thesis](../specs/spec-shared-evidence-canvas.md), whose
status remains proposed and whose shipped/target distinction matters. They do not establish that every
support/production claim in the participant's account was verified by this trial.

Separate correctness from capability expansion. Misplaced spotlights, misleading freshness and silent
export loss remain defects in every use case. Grouping and record-order charts are enhancements with
observed demand. Issue 25 is a limitation of investigation-only framing, not a broken PDF string;
its priority remains P2. Investigation itself can contain expected observations, so not every support
finding should be forced to describe a defect either. Likewise, record-order axes and entity grouping
can help support; the four scenarios are not four mutually exclusive product modes. Conversation
cuts across the other three.

### Decisions on the new proposals

| Proposal | Decision and implementation boundary |
|---|---|
| Echo equals shared evidence | Accept as the first priority, with asynchronous states explicit: `accepted`/`scheduled` is not `visible`/`complete`. Record the view revision and final omissions/refusals when layout or data refresh finishes. Do not make pending rendering look complete or require all work to block the EDT. This reinforces issues 1/2–4/9/12/19/20/24. |
| Design-first graph preview | Accept a declared XML/reference view, clearly labelled as declarations. The analyser's `DesignDocument` is an inert XML index; it cannot establish the graph the compiler *would* produce, dispatch, effective Spring values or reachability by reimplementing compiler rules. Authoritative preview requires a builder-produced model with input identity. Changeset views still require previous text/revisions (issue 5). |
| Two-log comparison | High-value next investigation, **not “only fix #16”**. Reuse existing typed value comparisons and guarded scoring where their scopes fit. First specify input alignment, dropped/extra/reordered events, repeated node/key occurrences, output streams, expected differences and declared normalization. Preserve original anchors and expose ignored fields. Clock/thread normalization must not erase domain values, causal distinctions or the evidence for a mismatch. No new verb is approved by this review. |
| Make posture useful | Keep existing research/authoring state and attribution; improve discoverability and optionally use it to suggest workflow pointers/default presentation. It already changes `context.handoff` and the Project panel (`CanvasHandoff.java:174–193`, `ProjectModel.java:85–96`, `VerbSchemas.java:262–270`). “Nothing the LLM can see” is false; “does not adapt report framing” is accurate. Finding category and saved-report purpose should be explicit, not retroactively changed by a session posture switch. |
| Validation pack | **Accept as the strongest new packaging proposal.** Begin with one project-owned manifest/entry document naming spec, scenario feeds, oracle/validator, mutation suite and result artifacts. Existing runbook pointers can expose that entry point today. Add typed result intake only after its schema and freshness contract are reviewed; the current compiler-diagnostics shape is not automatically a validation-result schema. |
| A verb that runs the validation pack | Do not adopt. The agent, developer or external harness runs commands using its own tools. The analyser reads and renders the resulting evidence. This preserves the explicit runbook and server boundaries in ONBOARDING, portable-context D-C2 and the canvas thesis. A bounded built-in comparison is different from executing a project-supplied Python command. |
| Invariants first | Useful for a small declared rule set, not arbitrary prose or Python executed by the analyser. Specify same-record versus temporal rules, required observations, scope and PASS/FAIL/UNKNOWN/NOT-RUN outcomes. Missing logging cannot prove an invariant holds. Existing `ExpectationScorer` provides scoped outcome comparisons, not a general invariant language. Avoid building another unrestricted expression engine. |
| Scenario expected-versus-observed report | Accept as a later result projection. Ingest externally computed per-step expectations/outcomes with scenario/run identities, record mappings and evidence references. Show checked, failed, unknown and skipped counts. Keep model testimony distinct from tool comparisons; agreement with an oracle does not prove the specification matches the user's intent. Reuse report tables and findings once their correctness gaps are fixed. |
| Recover a feed row from a bad record | Narrow to **navigate an explicit input mapping**. Audit `eventToString` is optional and comes from `event.toString()` (`runtime/audit/LogRecord.java:315–325`), not a reversible event serialization. Reproduction may require the preceding input sequence, initial state, service returns and time. Require feed identity/row IDs or a proper capture provider; otherwise return unknown. One bad record is not generally a self-contained reproducer. |
| Saved spotlight tour | Prototype using existing saved analyses before creating a new artifact family. `AnalysisSpec` already stores parameterized ordered analyser actions and stops at refusal. A polished walkthrough still needs explicit next-step control, captions, final-layout readiness, log/model anchoring and unresolved-step treatment; it must not race all spotlights to the last one or silently rebind them onto a new run. Do not conflate this with the separately queued M67 extension-tour programme. |
| Let the person point back | Accept as an experiment. Existing selection/flags already reach the agent; add an explicit question anchor only if a fresh task demonstrates the remaining gap. Distinguish human intent from an ordinary selection or an agent-caused cursor move. Node/record/series/interval identity is preferable to screen pixels; do not automatically send data or trigger an LLM call on a click. |
| Safe withdrawal | Retain upstream ownership rules. B3 already removes untouched owned stubs, demotes implemented members and refuses conflicting/adopted-code changes. Remaining unused classes or explicit cleanup limits do not mean removal is wholly unimplemented. Extend tests/docs around retirement and G12 rather than deleting developer code to make the workflow symmetrical. |

### Comparison evidence: useful parts exist, their scopes differ

Ran `score.ScoreCommand` with natural dialect on the two saved hosted logs: **PASS, 11 scored events,
11 figures**. The run contains 19 business records, so this is not a 19-input equivalence result.
`ExpectationScorer.java:78–102` defaults to config/tick/rate/trade; its natural reducer compares numeric
state, with carry-forward and tolerance, rather than complete events, booleans/text, every emission or
dispatch order. Its guards should be reused, not presented as proof beyond that scope.

The new probe changes every PriceUpdate event name and still gets PASS 11/11. This is an explicit witness
for the event-selection boundary, not a newly discovered violation of the scorer's declared contract.
Similarly `DiffBuilder` is an existing two-record last-value comparison with exact typed equality. The
probe compares repeated writes `[1,2]` with `[2]` and finds no differences: it cannot establish identical
dispatch traces or intermediate outcomes. Fixing a lambda identity string alone supplies none of the
missing alignment, normalization or scope policy.

### Minimum validation-result contract to review

Keep the declaration (what was intended), invocation (what was run), result (what was checked), and
interpretation (what the author concludes) distinct. A proposed result should carry:

- pack/schema and checker identity/version; hashes of spec, scenario, oracle/checker and relevant build;
- actual log and output-artifact identities, with explicit model/run linkage or UNKNOWN;
- completion and input-damage status, comparison scope/normalization and checked/failed/skipped counts;
- per-step/rule outcome with stable expected/observed references and resolvable record/output anchors;
- mutation results as a separate bounded coverage claim, including survivors and errors;
- freshness qualifications in `context`, the human panel and exported reports over the same model.

Hash agreement establishes input identity, not independent authorship or correctness. A reference model
written first in another language is helpful separation, but a shared misunderstanding can still be in
both implementations. Fifteen killed mutants support detection of those mutants, not universal regression
safety. Reframe “the change is safe” as “these stated checks passed over these identified inputs.”

### Revised sequencing and predictions to test

1. Finish the already identified truthfulness/report fixes. Neither the new framing nor an attractive
   validation pack lowers their priority.
2. Package the existing desk assets behind one project entry point, without new analyser execution.
   Prediction: a fresh client can locate the intended spec, inputs, check command and latest result
   without the transcript, and correctly name what has not been checked. Record failures before expanding
   `context` with more material.
3. Run a bounded two-run comparison experiment and review its contract alongside result intake.
   Prediction: it distinguishes an equal rerun, changed output, missing/extra/reordered input, repeated
   write, corrupt/truncated input and stale result; unalignable runs produce UNKNOWN, not a green score.
   Choose the smallest useful support and maintenance examples. This does not displace the confirmed
   record-axis/grouping needs or authorize a large diff implementation first.
4. Add record-order/grouped charts and complete validation-friendly reports. Test another authoring
   scenario plus an investigation to show the extension preserves support behaviour.
5. Trial walkthrough replay and human question anchors after spotlight geometry/settling is trustworthy.
   Prediction: the user can identify the exact evidence being discussed and a changed run makes stale
   anchors visible instead of silently relocating the story.

Corrections from earlier reviews still apply: headless template generation is already available; issue 1
is not evidence of a stale graph; flags cannot infer whether an unlogged node ran; generated handler
propagation needs declared intent. The document's statement that *every* wrong narrative came from a bad
`ok` also overreaches: its own earlier 12-trade/7-price count was an author mistake. Preserve evidence for
both tool and interpretation errors.

Verification this round: document diff, source/spec reads, one existing CLI comparison and two in-memory
scope probes. No new feature, live UI change, upstream mutation run, replay/capture test, full gate rerun,
or proof of production support outcomes. Review artifacts remain uncommitted.

## Owner clarification — execution stays outside the analyser

The owner confirmed the division of responsibility after this review: **the LLM uses runbooks and
bootstrap documentation; the analyser must not take on application processing/execution logic.**
This is a settled boundary, not an open implementation option in the validation-pack proposal.

The project entry path should orient a fresh LLM across Fluxtion, the analyser, audit logs, Mongoose
and Mongoose plugins, then lead it to the task-specific runbook. The LLM uses its external tools to
generate/build, host, feed, test and, where supported, replay the application. Compiler, runtime, host
and test harness remain the authorities for their respective outputs.

The analyser reads the resulting artifacts, checks identity/freshness, queries and compares recorded
evidence within stated scopes, and renders the same evidence for the person and LLM. It does not
execute a project's oracle or mutation suite, orchestrate the application's lifecycle, or implement
business rules to predict the application's behaviour. Domain invariants and expected outcomes belong
in the application's tests/external validator; importing their results does not make the analyser the
executor or the authority on business correctness.

Accordingly, “validation pack” means a documented, portable bundle of intent, scenarios, checks and
results. The first experiment tests whether a fresh LLM can use that bundle through the runbooks and
present trustworthy evidence. It does not justify adding an analyser workflow engine. Preserve the
participant's original proposal unchanged; this clarification governs our disposition of it.

## Fifth addendum — vendor components, issues 29–35

Read the restructured master index and all seven vendor findings, the complete vendor predictions/results,
and the relevant source/artifacts. **29 is a new required correctness fix for the dependency-component
authoring route.** The earlier local-workflow reviews do not establish that this newly exercised route is
safe. 30 is a material dependency-identity gap; 34 is a compiler/exporter metadata gap surfaced by the
analyser, not evidence that its renderer discarded a supplied edge.

The entire project is preserved privately at
`/Users/greg/fluxtion-local-evidence/spring-demo-20260919T214833Z/project`, with 252 entries verified
against the original during copying and zero concurrent changes. This includes all four documents,
evidence, desk, vendor source/jars and local provisioning. It is outside the public repo; parent
directories are owner-only. The [fifth public packet](evidence/spring-authoring-feedback-2026-09-19-round5/README.md)
contains the reviewed report, predictions, inspected topology screenshot and selected probe results.

### Independent checks and their limits

- **29 reproduced without a build/key.** On a disposable copy, added `acmeRisk` to the outer `nodeBeans`
  list and ran the shipped starter jar's `regenerate`. Exit 0 created an empty public
  `com.acmerisk.RiskEngine`, added `ownership["com.acmerisk.RiskEngine"] = {}`, and recorded
  validate/regenerate `ok`. Reconciliation lists the class but all member-change lists are empty.
  Compiled the new shell with JDK 21 and loaded it ahead of the genuine vendor jar: its code source is
  the compiled shell directory and it has zero declared methods. The dependency jar itself was not
  overwritten. The participant's subsequent all-green compiler build was read, not repeated.
- **30's missing input identity confirmed.** `RunReceipt.Inputs` has only XML/source/record hashes;
  `Starter` and `BuildWorkflow.java:117–125` supply those three. After stabilizing the disposable
  project's record, changed the jar bytes by adding a harmless ZIP entry and repeated regenerate.
  Jar SHA changed; receipt inputs were identical. This independently tests validate/regenerate
  identity coverage, not a new tampered build/run. The original tamper is preserved in participant
  evidence: rerunning their saved checker gives 11 rows/zero mismatches on the genuine log (exit 0),
  versus 9 mismatches on the tampered log (exit 1).
- **34 traced across three artifacts.** Saved GraphML contains `MarketPrice → priceBook` and
  `Quote → acmeQuoteFeed`, but no `MarketPrice → acmeQuoteFeed`. The generated MarketPrice handler
  explicitly calls `acmeQuoteFeed.onQuote(typedEvent)`; all nine MarketPrice records in the saved
  combined run contain that vendor node. The screenshot agrees with the missing graph relationship.
  `JgraphGraphMLExporter.java:223–240` builds event edges from declared handler dispatch types; it
  does not add the concrete subtype route shown by generated dispatch. No live processor was run.
- **33/35 checked against the template and docs, not just the participant's scripts.** Playground
  `src/lib/starter/authoring.ts:251,265,274` writes the classpath in setup and reuses it in build/preflight.
  The participant's run-server script also reads the cached file. A vendor-library page already exists
  in `src/routes/(marketing)/vendor-libraries/+page.svelte`, including an author contract and consumer
  example. The gap is completeness and bootstrap discovery, not the absence of any vendor page.

### Dispositions

| Issue | Decision / owner | Required improvement and acceptance |
|---|---|---|
| 29 — generated shell shadows dependency | **P1, starter/compiler boundary; independently reproduced** | Before creating source, distinguish an absent project type from a type supplied by the effective dependency classpath. Keep dependency-provided types foreign and report their origin; never silently own or shadow them. Resolve metadata without initializing arbitrary classes, and retain keyless XML-only validation. An absent/unresolved classpath must not authorize a skeleton by default. Regression: a small runtime-only jar, listed and referenced routes, with/without properties, and both missing/stale classpaths; no duplicate source or ownership, vendor handlers preserved. Detect conflicting class origins in this workflow; a universal ban on every source/jar FQCN overlap needs a separately reviewed compatibility policy. |
| 30 — dependencies absent from receipt | **P1 for dependency provenance; builder/scripts produce, analyser presents** | Record effective resolved artifacts/byte identities and relevant classpath precedence for each phase, including local jars and transitive dependencies. Tie those inputs to the built processor identity, and separately capture effective runtime inputs and model/run identity. Acceptance replaces a jar at the same path/version: the prior result becomes superseded or an explicit approved-digest policy refuses the build. A newly successful receipt can honestly describe changed inputs; it is not automatically “uncertified.” A hash proves bytes, not vendor approval, signature authenticity or which binary produced an unlinked log. Do not advertise certification from three source hashes or merely from adding a dependency hash. |
| 31 — accessibility failure arrives in generated javac | **P2, compiler diagnostic; source/read evidence, original failure not rerun** | Validate constructibility from the actual generated package against the compiler's effective construction plan. Name the node, inaccessible class/constructor and remediation before emitting unusable code. Do not mandate one Java shape for every factory/serialization route. Test inaccessible class, inaccessible chosen constructor and a supported construction route. Preserve the final javac check. |
| 32 — names and collisions | **P2 naming contract/docs; instability/collision not reproduced** | Indexed discovered names are visible; lost Spring naming occurs on the workaround route that avoids nodeBeans. Fix 29 first and retest both routes. Document stable naming and component-instance namespaces; a vendor prefix alone does not solve two instances of the same vendor component. Define precedence and clear duplicate-name refusal before promising stability across graph changes. Test graph/audit key agreement and two instances. |
| 33 — stale resolved classpath | **P2 scripts/runbook reliability** | Refresh or verify classpath inputs when effective dependency configuration changes, with an explicit offline failure if resolution is unavailable. Coordinate setup, preflight, build and launch; their compile/runtime scopes can legitimately differ, so do not promise byte-identical classpaths. Test an added/removed/replaced dependency after setup and ensure launch uses the intended artifact. The participant's run script is custom, but the cached-path mechanism is also in the emitted authoring scripts. |
| 34 — supertype route absent from graph | **P1 evidence contract, compiler/exporter first; analyser consumer** | Carry declared handler types and effective known concrete dispatch relationships (or authoritative assignability metadata) with unambiguous edge semantics. The analyser must not manufacture compiler dispatch edges from observations or by running application classes. Test one host event implementing a vendor interface, another implementor and filtering/dispatch qualifications; generated calls, exported facts and the displayed route must agree. Until metadata supports it, show the limitation rather than imply complete input reachability. |
| 35 — redistributable component guidance | **P2 bootstrap/runbook documentation** | Extend and link the existing vendor-library page with tested class/constructor accessibility, stable instance naming, dependency identity, configuration, audit setup, event interfaces and lifecycle/hosting examples. Interface-typed inputs are a useful composition pattern, not the only valid pattern: customers cannot always modify their event classes, and adapters may be necessary. Explicitly distinguish event sharing from bridging host state; the desk's positions were not connected and its vendor VaR stayed zero. Include that limitation in examples. |

### Corrections to the new master index

The A/B/C/D grouping is useful navigation; its compressed descriptions should not override the reviewed
evidence or create new promises:

- Put 29 at the front of the component-authoring work. Scope 30 to dependency/build/run identity;
  “certified” is unestablished in this unsigned, system-scope fixture.
- Move 34's producer ownership to compiler/exporter, with an analyser disclosure/consumption follow-up.
  This preserves the owner's rule: application mechanics are upstream, the analyser renders facts.
- Keep the earlier corrections: 6 concerns audit scaffolding/values, not proof of no execution or no
  possible trace; 22 requires an explicit propagation decision, not universally false handlers;
  2–4 do not yet have one established root cause; 1 is loaded/on-disk freshness, not failed reopen.
- Issue 16 still does not stand alone between us and a trustworthy two-log diff. The round4 scope
  probes refute that simplification. Tables have sources beyond contiguous read, and headless template
  generation already exists. Retain these corrections in any implementer's brief.
- P9's setter-only alternative and P11's declared-vendor-event shell clause were not tried. The
  “15 of 17 confirmed” headline is the participant's row-level assessment, not 15 fully tested compound
  predictions. Interface routing was observed; Maven coordinate resolution, runtime-version conflict,
  naming collisions, state bridging and certification were not.

Recommended next work: **29 first; dependency identity and classpath lifecycle (30/33) together as a
reviewed producer contract; dispatch metadata (34) alongside existing evidence correctness.** Improve
the bootstrap/runbook/vendor guide in parallel with the relevant fixes. Retain the confirmed analyser
freshness, spotlight, marker and export priorities. Do not turn these cross-repo findings into analyser
application execution, a dependency resolver, or a second compiler.

No source fixes, upstream commits, paid compilation, host restarts or full gates were performed. The
participant's live project is unchanged. The full private snapshot is durable; the public review intake
remains uncommitted.

### Companion document: the additional documentation advice

The fifth packet also preserves the companion's 157-line **More advice on the docs** addition.
Accept the concrete discovery improvements: a short orientation with addressable runbook links,
copyable hosted-feed examples in the downloaded project, typed property tables, task-specific
construction/reference/hosting decisions, and executable examples with checked outcomes. Include a
documented failing mutation and an external checker so the reader sees how evidence detects a mistake.
Keep useful standalone tests; clearly label their purpose instead of deleting every `main` example.

Treat the participant's claims about how *every* model or human responds as hypotheses, not measured
population results. Freeze cold-start tasks, record wrong turns and correctness as well as build cycles,
and include a client without repository-source access. Deterministic snippet checks belong in ordinary
CI; repeated model trials need a separate bounded evaluation budget, not mandatory paid runs for every
wording edit. A cross-language oracle reduces some shared implementation mistakes; it is not automatically
independent or correct merely because its language differs.

Correct the proposed messaging before using it: order alone does not prove causality; equal logs under
an explicit comparison contract support tested behaviour, not universal correctness; interface-typed
events do not remove every adapter; construction and naming rules need the qualifications above.
The vendor guide already exists. Fix 29 before promising the suggested “drop in a jar” tutorial route.
Version the generated project documentation against its actual tools, while keeping public guides free
of speculative release literals. Generated references still need executable semantic checks; sharing a
generator does not make contradictory claims impossible. The advanced-API omission list is participant
evidence, not an independently audited inventory of every published machine-readable guide.

## Sixth addendum — the analyser's own session audit

The new 20-line note is preserved in the [sixth packet](evidence/spring-authoring-feedback-2026-09-19-round6/README.md).
**Accept a bounded, explicit diagnostic snapshot export.** This is the analyser exposing its own
recorded decisions, fully consistent with keeping application processing outside it. It is useful
supporting evidence for the existing fixes, not a prerequisite that should delay their reproductions.

What exists: `SessionDriver` attaches `SessionAuditSink` before initialization and exposes `auditSink()`;
the sink keeps up to 2,000 records, counts dropped records and sink failures, and has `export(Path)`.
Its output is a fixed audit-log snapshot, separate from the business log. There is no current action/UI
export wiring: source search finds only the driver's accessor outside the sink and tests. The Java
export does **not** include its dropped/failure counters in the file, so simply exposing that method
would lose an important qualification.

Ran existing `SessionAuditRecordTest` and `DesignSessionAuditTest` with JDK 21: **7 tests, zero failures,
errors or skips**. They exercise bounded capture, sink failure accounting, decision/outcome separation,
immutable export, reader round-trip with operation IDs, stale design reads and project clearing.
They do not witness an MCP/UI export or diagnose the participant's original live session.

The proposed diagnoses need these distinctions:

- **1:** the current graph carries opened/observed/closed facts, not an observed-on-disk revision
  contract. A filesystem adapter can emit revision facts, with session nodes owning the resulting
  freshness state; filesystem reads remain outside those nodes. The independent live size read in
  `MainFrame.currentLogFileInfo()` is confirmed. No audit record can report a file change the adapter
  never observed; add observation/comparison tests rather than assuming the history is complete.
- **20:** the source-confirmed defect is `ActionExecutor` storing the design payload before reading
  diagnostics. Session audit can expose update ordering, but current design events record generation,
  revision or producer stage, **not the outgoing echo or which receipt it quoted**. An incorrect echo
  beside correct graph state may be an adapter/response assembly bug. Preserve request/response plus
  state revision/correlation; do not declare the session graph's decision logic faulty from the symptom.
- **28:** the reframing is right and agrees with the earlier review. Preserve the project boundary;
  capture a restore candidate before destructive clearing, then offer it after successful transition.
  Restore is explicit, freshness-checked and can partially refuse with reasons. It must never leak
  another project's state. The audit of a completed close does not reconstruct all cleared chart state.

Minimum export contract for review:

1. Freeze records and counters together on the session-owning thread; write the immutable copy off the
   EDT under existing export/path rules. Export must not open the snapshot, switch projects or clear the
   ring. Inspect the result in a separate analyser instance when preserving the live session matters.
2. Bundle analyser version, session-model fingerprint/graph identity, capture boundary, retained/total/
   dropped counts and sink failures/completeness. Include bounded operation/request correlation and
   the relevant response/state revisions where available. Distinguish not-recorded from unknown.
3. Keep session diagnostics separate from customer application evidence. Use explicit local export,
   expose the same snapshot/completeness information to the human and MCP client, and provide review/
   redaction before public attachment: session paths, names and errors can identify private projects.
4. Test ring overflow, sink failure, export failure, a pending load with a stale completion, project
   transition and combined design/diagnostics open. After export, mutate the session and assert the
   artifact is unchanged and reopens as one record per captured dispatch. Include actual request/echo
   evidence for the echo defect and screenshots for geometry; session logging cannot witness pixels.

`analyser_session {export}` is a suggested spelling, not an approved seventeenth verb. Review the fit
with existing report/export surfaces and preserve the pinned action inventory unless the owner adopts
a deliberate API expansion. Similarly, attaching a snapshot to a local defect bundle is not permission
to automatically upload it or turn every report into an export of private session history.

This is a concrete extension of the evidence-driven development loop: observe analyser decisions,
reproduce with its session harness, fix the owning node or adapter, and verify the emitted evidence.
It does not promise automatic self-repair or add execution of the investigated application.

## Seventh addendum — focus management and saved walkthrough proposal

The [latest packet](evidence/spring-authoring-feedback-2026-09-20-tours/README.md) preserves participant
36–39, recurring 6 and proposal P1. Concurrent numbering is reconciled: Show all is 37 (earlier intake 36),
topology caption overlap is 40 (earlier intake 37). Historical packets are unchanged.

Accept P1 for design exploration: a saved, ordered, attributed explanation of a named focus addresses a
reported repeated task. Do not silently persist ordinary spotlights or claim that a structural caption is
true across runs simply because its target resolves. A graph can retain names/edges while its code or
configuration changes. Bind structural commentary to known model/build context, record unknown identity,
and bind observations to their original run/record or link existing report evidence. A coarse report
fingerprint does not prove byte identity: LogFingerprint compares counts/time endpoints, then qualifies
name/provenance. Reuse disclosure, not a stronger guarantee than its implementation provides.

Keep missing/ambiguous steps visible in order; do not silently drop or remap them. Define focus rename/
delete/reference semantics and explicit save/recall before adding playback. Start with a structural
walkthrough and references to existing findings/reports; this is not approval for another reporting system
or an application runner. The current spotlight spec's never-saved rule needs an explicit reviewed extension.

36 is specifically MCP lifecycle parity: the UI already deletes a named focus. The reported file-edit
workaround is unsafe in general because ProjectSession.close flushes dirty memory before closing.
38 is participant restart evidence, not an extension of our earlier close/reopen probe. 39 confirms the
echo cannot be a visibility oracle: negative height remains invalid even if the pixels happen to be right.
6's second occurrence supports an audit policy for new owned nodes without proving absence of execution
or authorising re-parenting foreign/implemented classes. Influence-set exclusions describe the supplied
graph, not proof that unmodelled state or missing dispatch edges cannot affect an output.

Verification here is source reading and document preservation; no fresh UI or compiler run is claimed.

**Participant clarification:** focus persistence is already observed; the missing persistence is only
the ordered caption payload. P1 is an optional extension to a named focus, preserving existing behaviour
when captions are absent. The concrete use case mixes two structural captions with four observations
from one run. The structural-first suggestion above is a possible delivery slice, not a replacement for
that requested use case. Reuse written-against disclosure with its identity limitations stated, preserving
historical qualification when the current run differs or cannot be verified. No new focus-save route or
repeat proof of focus persistence is requested.

**Latest participant feature request 40:** preserved verbatim in the packet; this is the same proposal as
P1, not a second feature. It supersedes the temporary intake assignment of 40 to callout overlap, now
tracked descriptively under the annotation-placement family (14). No further rewriting of the participant's
live document was done. The authoring session has demonstrated real retyping friction, but this record
does not establish cross-model frequency or priority. Rank it below current correctness/restore issues;
test resumption by the original and a fresh reader before expanding its scope.

Keep three qualifications with the proposal: structural captions are not automatically valid after a
model/code change; `writtenAgainst` disclosure using current report fingerprints cannot establish exact
identity; and focuses are not the only durable/shareable view state (saved charts and reports also exist).
UI focus deletion exists today, so “write-only lifecycle” is too broad: the concrete gap is MCP lifecycle
operations plus optional caption persistence. Neither correction weakens the demonstrated caption gap.

## Eighth addendum — participant system assessment and EOD qualification

Source: the owner's pasted cold-start assessment and subsequent EOD qualification, 2026-09-20.
This is an intake assessment, not a new independent execution or release verdict. The participant reports
19 matching business records across hosts; 784 desk values and 33 sink messages checked against a Python
oracle; 15/15 injected defects detected; vendor predictions 15/17 and independent VaR checks 11/11;
and three byte-identical runs. Retain these denominators and the tested scope. Prior independent checks
in this review have their own narrower scope; this intake does not upgrade the remaining claims to verified.
“39 defects” is the participant's count of filed issues, not 39 independently confirmed product defects.

The strongest product hypothesis is that explicit design, generated dispatch, independent checks and
inspectable evidence let a fallible author detect and correct mistakes. “Author reliability is irrelevant”
is too strong: an author can write the wrong requirement, share a mistake with the oracle, leave behaviour
unlogged or miss an input class. Fifteen killed mutants establish sensitivity to those mutations, not
exhaustive correctness. Determinism supports repeatable comparisons under controlled inputs, initial
state, dependencies and external effects; it does not make every log a sufficient test or establish
non-execution from silence. Keep measurements separate from marketing conclusions.

The EOD follow-up materially narrows the initial positive verdict. Unchanged desk comparisons support
non-regression for those observations on that feed, not a general proof of inertness or correct reporting.
Two exercised trigger paths and a few hand-checked totals leave report arithmetic, grouping and boundaries
largely untested. DATA references constrain propagation, not Java object mutability; a false handler result
is not a general no-side-effects guarantee. The participant's discovered zero accepted-order count is
evidence that output plausibility caught something the automated checks did not.

**Next validation experiment, owned by the project runbook/harness:** freeze EOD predictions before edits,
extend the independent feed-based oracle with per-book/per-symbol report expectations, and compare both
structured audit summaries and the rendered report against them. Include both trigger paths, empty state,
threshold below/equal/above, multiple books/symbols and repeated requests. Inject isolated errors in mark
selection, multiplier, threshold, grouping and accepted-order count; each must fail a named assertion.
Re-run the existing desk comparison as a separate non-regression check. Preserve inputs, code/build
identities, expected/actual results and mutation outcomes. The LLM runs this externally; the analyser
renders the evidence and never becomes the application executor. This experiment has not been run here.

**Priority disposition:** retain dependency shadowing (29) and dependency identity (30/33) as producer
correctness work, alongside analyser freshness/echo/count/export integrity. A dependency hash records
identity; detecting an unauthorized replacement requires an independently trusted expected digest or
manifest and an explicit comparison policy. Hashing the replacement and accepting its new value does
not certify it. Bind actual resolved classpath order and build/run evidence to the recorded identities.

Next, exercise the documented entry journey from an empty directory without repository-source access
or owner coaching. The existing headless endpoint means “cannot generate without a browser” is too broad;
the gap is discovery and parity with the required authoring download. Hosted replay/audit/sink defaults
need explicit capability, storage and ordering contracts rather than blanket activation. Document input
ordering and reset/replay scope in the Mongoose runbook without promising cross-feed determinism.

Two-log comparison remains worthwhile but is not one text-format fix away: alignment, repeated values,
missing/extra events, initial state and normalization all need a contract (see the fourth addendum).
Keep optional presentation features below these correctness and journey checks. No new MCP route,
compiler fix, product guarantee or release approval follows from the participant's enthusiasm.


## Ninth addendum — chart feedback 41–43 (2026-09-20)

The complete participant document is preserved in
[evidence round 6](evidence/spring-authoring-feedback-2026-09-20-round6/ANALYSER-FEEDBACK.md), with a byte
manifest. Source inspection used analyser base `3ed21ea`; current journey edits do not change these paths.
These findings do not require application execution inside the analyser.

### Disposition and qualifications

- **41 — accept as high-priority evidence-scope visibility work, linked to restore.** `GraphPanel.bind`
  clears a pin, but `GraphTabs.doRestore` reapplies the saved definition's pin (line 313). Thus the
  participant's observation is consistent with saved-definition restoration, not proof that bind itself
  retains an old window. `ActionExecutor` already returns `applied.pinned` (lines 488–493); the missing
  information is disjointness/current extraction scope, not complete absence of the bounds. The tab's pin
  indicator also exists. `refreshed: scheduled` means asynchronous work was requested, not that data is
  visible. Preserve those distinctions while adding a visible reason for zero in-window points and
  separate dimension/text/window restrictions. Do not silently discard a deliberate pin. A written-against
  identity is useful provenance but does not replace comparing the actual window with available input.
  The exact two-chart/20-of-76 interaction remains participant-observed, not independently UI-replayed here.
- **42 — split three concerns.** `graph` is described globally as create/append, and `panel.addKeys`
  confirms additive series; the per-parameter description needs the same clarity. MCP has no raw-series
  removal operation. The human Series list DOES have removal (`GraphPanel.removeSelectedSeries`, line 503),
  so this is a surface-parity gap rather than wholly write-only chart state. Keep existing additive calls
  compatible when designing explicit replacement/removal. The axis defect is independently reproduced:
  `ChartPanel.resetView` partitions by axis; `setViewWindow` (line 360) scans every series into the left
  range, ignoring the assignment and leaving the right range unchanged. With small values 10–20 and large
  values 1,000,000–1,250,000, the left range changes from 9.5–20.5 to -62,489.5–1,312,499.5 after windowing.
  This confirms cross-axis contamination; it does not independently reproduce identical tick labels on
  both axes in the participant's screenshot. Regressions should cover pin/filter windows, refresh and
  restoration, including an empty side and guides/markers.
- **43 — accept target/creation compatibility defect.** `GraphTabs.addGraph` accepts the name; the
  `SpotlightTarget` parser refuses a colon within the chart portion (lines 144–147). The supplied target
  was independently passed to that parser and refused. Choose an unambiguous target representation with
  a compatibility rule for existing saved names; rejecting only new names leaves those projects broken.
  The current-tab `graph:note:<n>` form is a workaround after explicitly selecting the intended chart.
- **Pin clear — accept schema/documentation mismatch.** `containsKey` and nullable conversion in the
  executor let explicit null clear the bounds, while `VerbSchemas` declares integers. Make the eventual
  contract express clear/follow without relying on a caller bypassing schema validation; test through MCP.

The participant's successful mutually-invisible record-target refusal is a positive regression case.
Preserve it while extending visibility checks to other surfaces. No fix is claimed by this intake.

### Evidence and next acceptance

`JourneyChartProbe.java` and its output beside the snapshot are the independent headless probe, run
against compiled classes. They exercise the actual chart axis/window code and spotlight parser, not the
staged application. No screenshot was produced or inspected in this intake.

For 41, use two logs with disjoint times and a persisted pin, then independently add a dimension filter
that removes otherwise visible points. Human and MCP must identify each active restriction, distinguish
no records/no key/no points in window from failed extraction, and provide explicit clear/restore choices.
Include overlapping runs and an intentionally empty view so a warning does not become an automatic reset.
Keep 42's correctness fix above optional lifecycle additions; retain 43's compatibility check in the
spotlight workstream. The ongoing starter journey inherits the restoration regression, not every chart
feature request as a prerequisite.

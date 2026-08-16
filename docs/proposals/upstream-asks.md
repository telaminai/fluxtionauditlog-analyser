# Proposals to other repos — what the analyser needs from upstream

Ideas that belong in **another** repo, raised from work done here. This file is the holding pen: an item
lives here until it is filed upstream, then the entry records where it went.

Everything below is written from something that actually happened while building the analyser. Where an
item says *measured*, it was checked against generated code, a real audit log, or the live source — not
inferred. That distinction matters: this repo's own history (`CLAUDE.md` rule 6) is a list of defects
caused by inferring framework behaviour instead of reading it, so an ask built on inference is worth
less than no ask at all.

| Field | Meaning |
|---|---|
| **ID** | stable handle, quote it in the upstream issue |
| **Target** | which repo it belongs to |
| **Evidence** | what makes this a fact rather than a preference |
| **Cost to us if unfixed** | the workaround the analyser carries today |

Status: ☐ not filed · ◐ filed · ☑ landed.

---

## 1 · Fluxtion compiler — rejection codes and machine-readable diagnostics

The owner is adding rejection codes (2026-08-16). These are the asks that would have saved time in this
repo, in priority order.

### UP-FLX-01 ☐ Structured, coded diagnostics with a machine-readable form

**Target** `fluxtion` (compiler) · **Priority** high

An agent authoring a graph needs to *recover* from a rejection, not just read it. Today the message names
the symptom; the rule and the fix are in prose elsewhere, so a model guesses — and guessing at framework
semantics is exactly the failure mode this repo has documented.

What I hit, verbatim:

```
cannot find matching constructor for:Field{name=riskMonitor, fqn=com.acme.demo.node.Nodes.RiskMonitor, …}
failed to match for these fields:[orders, limit]
```

I had written `RiskMonitor(OrderTracker, int, String)`, passing the `SingleNamedNode` name as the third
argument. The message lists the mapped fields but never says *why* `name` is not one of them
(`@FluxtionIgnore` on the parent field), so nothing rules out "drop `limit`" as readily as the correct fix.

Proposed shape — the `why` line is the load-bearing part:

```
FLX-1001  constructor does not match mapped fields
  node      riskMonitor : com.acme.demo.node.Nodes.RiskMonitor
  mapped    [orders: OrderTracker, limit: int]
  found     RiskMonitor(OrderTracker, int, String)
  rule      Constructor parameters must correspond 1:1 to mapped fields, in order.
  why       'name' (SingleNamedNode) is @FluxtionIgnore'd, so it is not a mapped field and must be
            supplied by the subclass, not taken as a parameter.
  fix       RiskMonitor(OrderTracker orders, int limit) { super("riskMonitor"); … }
  see       https://fluxtion.dev/errors/FLX-1001
```

Plus `-Dfluxtion.errorFormat=json` (or a sidecar `target/fluxtion-diagnostics.json`) emitting
`{code, severity, element, rule, suggestedFix, sourceRef}`. This is the highest-leverage single item:
it turns "read English that changes between versions, guess, retry" into a deterministic loop.

**Evidence** measured — cost me one full build cycle on 2026-08-16.
**Cost to us if unfixed** none directly (we consume logs, not the compiler), but every agent authoring a
processor for the analyser to read pays it.

### UP-FLX-02 ☐ Codes for rules that are currently prose-only

**Target** `fluxtion` (compiler) · **Priority** high for the first two

| Suggested code | Rule | Why it matters |
|---|---|---|
| `FLX-1002` | `@ExportService` interface methods must return `void` | doc-only today; I had to read `claude.txt` to learn it |
| `FLX-1008` | `auditLog` used on a node in a graph built without `addEventAudit(…)` | **silent** today: builds fine, logs nothing, and the analyser then shows a node that "did not log" for a reason no one can see |
| `FLX-1003` | handler/trigger missing `boolean` return | exists as `failBuildIfMissingBooleanReturn`; give it a code |
| `FLX-1004` | `@OnTrigger(dirty=false)` with no sibling on the same parent | usually a mistake — warning, not error |
| `FLX-1005` | duplicate node name, or a name that collides with a generated identifier | |
| `FLX-1006` | `@Inject` service unresolved at build time | |
| `FLX-1007` | dependency cycle, printed as the path `a → b → c → a` | |

`FLX-1008` is the one with a direct analyser consequence: it produces a log that is *misleading* rather
than merely absent, and no downstream tool can distinguish it from a node that legitimately stayed quiet.

---

## 2 · Fluxtion runtime — metadata the audit log should carry

The analyser's hardest problems are all "the log does not say". Each item below is something the runtime
knows at write time and the analyser has to guess at afterwards.

### UP-FLX-10 ☐ Mark a re-dispatched record as re-dispatched

**Target** `fluxtion` (runtime, `EventLogManager`) · **Priority** high — biggest single win

**Measured, 2026-08-16.** A node calling `processReentrantEvent` queues the event; the generated
processor then runs

```java
afterEvent();                                  // publishes the audit record
callbackDispatcher.dispatchQueuedCallbacks();  // only now is the queued event dispatched
```

so the re-dispatch is **not** extra rows on the causing cycle — it is a **separate `eventLogRecord`**,
same thread, stamped with a normal `eventTime` (an exported call is stamped `-1`), carrying nothing that
says it came from inside. The graphml is no help: the raised event is an ordinary `EVENT` node with no
edge from the node that raises it.

Ask: a header field on the record, e.g.

```yaml
eventLogRecord:
    event: RiskBreachEvent
    origin: reentrant          # external | reentrant | newEventCycle | exportedCall | lifecycle
    causedBy: riskMonitor      # the node that raised it, when origin is reentrant
```

**Cost to us if unfixed** the analyser can only link effect to cause by parsing the **processor source**
for `processReentrantEvent`/`processAsNewEventCycle` call sites and correlating with record adjacency —
inference, so it must be presented as a guess or not at all. Fixture and two pinning tests live at
`src/test/resources/topology/demo-quote-audit.yaml` + `RealProcessorPairTest`.

### UP-FLX-11 ☐ Traced-regime marker in the record header

**Target** `fluxtion` (runtime) · **Priority** high · *(carried over: reviewer follow-up F2)*

Whether a record lists **every node invoked** or **only nodes that called `auditLog`** changes the meaning
of absence completely — it is the difference between "did not run" and "we cannot say". Today the analyser
infers it (`AuditTrace.tracesEveryInvocation`: every entry carries `thread` **and** `method`), which is
sound but fragile — one node logging a business key called `method` would break it, and a cycle where no
node logged is unclassifiable.

Ask: `auditLevel: TRACE` (or `tracesInvocations: true`) in the record header.

**Cost to us if unfixed** an inference on every record, and no answer at all for empty cycles.

### UP-FLX-12 ☐ Populate `ProcessorDescriptor.Meta`

**Target** `fluxtion` / `mongoose` · **Priority** medium · *(carried over: reviewer follow-up F2)*

`sourceFingerprint()` and `graphmlResource()` are both emitted as `null` today. Populating them would let
the analyser pair a log with the right graphml automatically instead of asking the user to find the file,
and would let it say *this graph is not the build that produced this log* with certainty rather than by
counting unmatched instance ids. The reviewer noted `sourceFingerprint` is also the natural carrier for a
provenance stamp.

### UP-FLX-13 ☐ One spelling for exported-call signatures

**Target** `fluxtion` (runtime/compiler) · **Priority** medium

Two forms are in the wild for the same fact:

```
public boolean com.acme.VenueMonitor.onConnected(com.acme.ConnectedEvent)   ← declaring class present
@Override
public void suspendQuoting(String arg0)                                       ← method name only
```

The second names no class, so there is nothing to match a node against. The analyser now falls back to
"the sole authored exported service", which is inference-free only while there is exactly one.

Ask: always emit the declaring class, and drop the embedded newline (a raw `\n` inside a YAML scalar makes
every downstream reader handle a multi-line field for no gain).

**Cost to us if unfixed** entry-point resolution is ambiguous for any graph with two or more exported
services — see `EntryPointResolver.addSoleExportedService`.

### UP-FLX-14 ☐ Carry lifecycle/annotation facts into the GraphML

**Target** `fluxtion` (compiler, graphml emission) · **Priority** medium

GraphML carries no annotations, so the analyser cannot tell that a node's callback is `@AfterEvent`,
`@Initialise` or `@Start` — all of which **fire without upstream propagation**. "Something downstream
logged, therefore this ran" is unsound for exactly those nodes and the analyser has no way to detect them
(recorded in `docs/ONBOARDING.md`).

Ask: a style or data key per node for the callback kinds it declares. Even a coarse
`lifecycleOnly="true"` would let the view stop reasoning about propagation for those nodes.

---

## 3 · svc-admin-web — what it could take from the analyser

Reviewed 2026-08-16: `visualiser/scaffold-filter.js`, `replay/replay-engine.js`,
`replay/eventlog-parser.js`, `visualiser/graph-parser.js`. These flow *from* this repo rather than to it.

### UP-WEB-01 ☐ Execution classification, not just "active nodes"

**Priority** high — this is the substantive one

`replay-engine.js` highlights the nodes present in `nodeLogs` and nothing else, so a node that **ran and
logged nothing** is drawn exactly like a node that **did not run**. Under a sparse audit level that is
most of the graph. The analyser's `ProcessorTopology.classifyCycle` distinguishes LOGGED /
RAN_SILENTLY / MAY_HAVE_RUN / OFF_PATH / DID_NOT_RUN, gated on whether the record traces every
invocation, and draws MAY_HAVE_RUN **dashed** so the picture never states more than the log does.

This was the correction that produced the `fix/m21-execution-vs-logging` branch here; the same
misreading is live in the web UI.

### UP-WEB-02 ☐ An entry position, and regime-aware wording

**Priority** medium

`replay-engine.js` starts at `stepIndex = 0` — the first row. The analyser's `StepCursor` has an explicit
**ENTRY** position before the first row, which is where the entry point is marked before any node
highlights, and `prev()` from an entry lands on the *previous record's last row* so stepping reads the
same in both directions. Its labels also say which regime is in force: `row 3 / 8 (logged nodes)` versus
`invocation 3 / 16`. `step 2/5` alone invites reading 5 as "the nodes that ran".

### UP-WEB-03 ☐ Widen the scaffolding filter

**Priority** low

`SCAFFOLD_CLASS_NAMES` matches by class name only, so framework **EVENT** nodes (`ClockStrategyEvent`,
`EventLogControlEvent`, `SinkRegistration`, …) stay visible — they usually carry no `class` attribute at
all. The analyser's `Scaffolding` also matches by package prefix and by node id, hiding 10 of 20 in the
demo graph against roughly 7 for the JS filter.

### UP-WEB-04 ☐ Re-dispatch (see UP-FLX-10)

Neither svc-admin-web nor fluxtion-visualiser handles re-dispatch today. If UP-FLX-10 lands, both UIs get
the causal link for free; if it does not, both will keep showing internally-raised events as though they
arrived from outside.

---

## 4 · Shared assets

### UP-SHARED-01 ☐ A published fixture pack

**Target** `fluxtion` · **Priority** medium

`examples/fixture-generator/` in this repo produces a byte-reproducible, paired set: a compiler-emitted
`.graphml` plus audit logs from running **that** processor, at two audit levels, now including an exported
service and a re-dispatch. Every consumer of audit logs needs exactly this and they should not each invent
one — a hand-written fixture drifts from the log it claims to describe and still renders perfectly.

Ask: publish it (or its equivalent) as a small artefact from the fluxtion repo so svc-admin-web, the
visualiser and the analyser test against identical bytes.

### UP-SHARED-02 ☐ Error-code doc anchors

**Target** `fluxtion` docs · **Priority** medium — pairs with UP-FLX-01

Every rejection code should carry a stable URL (`fluxtion.dev/errors/FLX-1001`). That is what makes the
semantics in `claude.txt` reachable **at the moment of failure**, rather than requiring an agent to have
read them up front — which is precisely where inference creeps in.

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

**STATUS UPDATE 2026-08-29 — the §5/§6 gate opened and three of these asks are DONE.** The §H
harness landed in the M19 bench, the M19 bundle work then consumed these asks as a real client, and:
**UP-MNG-01 ☑ landed and RELEASED** in `mongoose-plugins` 1.0.39; **UP-MNG-03 ◐** declaration half
landed in the same release (enforcement half still waits on the UP-MNG-02 decision); **UP-PG-01 ☑**
landed; **UP-PG-03 ☑** landed and released 2026-08-30 (M19.5 — fetch a template by id; the analyser
half remains). UP-MNG-02 (D-05) and
UP-MNG-04 remain owner decisions, UP-PG-02's capability now exists but
its catalogue field does not, and UP-RDR-01 is untouched. The paragraph below is the round-4 framing,
kept because its reasoning about the gate is what produced this outcome.

**Raised 2026-08-25 (round 4), NOT yet filed — and gated:** §5–§7 are the server, playground and
reader halves of the **agent-brokered dev loop** (`spec-agent-brokered-dev-loop.md`, ACCEPTED v2, M18
closed in its favour): **UP-MNG-01…04** (`telaminai/mongoose` — endpoint registry file, MCP admin
tool, declared dev/prod environment, audit-sink descriptor), **UP-PG-01…02** (the two catalogue fields
that survived reading the live index; four were withdrawn and are recorded so they stay withdrawn),
**UP-RDR-01** (the Chronicle reader). None starts before the spec's §H conformance harness has a home
in the M19 bench — the dependency on another repo is acceptable because of that harness and not
otherwise. Until now the tracker said these asks "belong in upstream-asks.md" and none was here; a
session opened in the mongoose repo had the spec but no brief.

**Raised 2026-08-17 (round 3), NOT yet filed:** UP-FLX-25…27 (§2b) come from specifying M28 (rolling
windows) and M29 (external series) — the first asks here raised from making the log's own **clock**
load-bearing, and from putting a second clock beside it. UP-FLX-28…31 (§2c) answer the owner's
proposal to add backward-compatible GraphML attributes, and settle which of them belong on **edges**
rather than vertices. All seven are facts the compiler or runtime already holds at write time. §2c
opens with a **probe graph compiled 2026-08-17** that measures what actually survives into the
GraphML (answer: nothing — every edge is identical but for `id`/`source`/`target`), then the
**verified** backward-compatibility contract and the one constraint it carries. UP-FLX-26 carries a
**name collision** with the owner's proposed record header `source` that should be settled before
either is filed.

**Filed 2026-08-16 (round 2):** UP-FLX-20…22 are open as [telaminai/fluxtion issues 14–16](https://github.com/telaminai/fluxtion/issues). They come from the supermarket POC's *maintenance* round — injecting new subsystems upstream of working ones — and are the first asks here raised from changing a graph rather than building one.

**Filed 2026-08-16:** all nine fluxtion-owned asks are open as [telaminai/fluxtion issues 8–13](https://github.com/telaminai/fluxtion/issues) (some grouped: 11 covers UP-FLX-11+12, 12 covers UP-FLX-14+SHARED-01, 8 covers UP-FLX-01+SHARED-02). The four `svc-admin-web` asks (UP-WEB-01…04) belong to `telaminai/mongoose-plugins` and are **not yet filed** — see below.

---

## 1 · Fluxtion compiler — rejection codes and machine-readable diagnostics

The owner is adding rejection codes (2026-08-16). These are the asks that would have saved time in this
repo, in priority order.

### UP-FLX-01 ◐ Structured, coded diagnostics with a machine-readable form

_Filed: https://github.com/telaminai/fluxtion/issues/8_

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

### UP-FLX-02 ◐ Codes for rules that are currently prose-only

_Filed: https://github.com/telaminai/fluxtion/issues/9_

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

## 1b · Fluxtion compiler — the maintenance manoeuvre

Raised 2026-08-16 from the **supermarket POC round 2**: nine new subsystems injected *upstream* of ten
nodes that already worked (23 → 44 application nodes, 44 → 95 edges, 31 → 90 distinct dispatch paths).
That exercise is the first time this repo has evidence about **changing** a Fluxtion graph rather than
building one, and it produced two asks that greenfield work could not have surfaced.

### UP-FLX-20 ◐ Warn when a new parent is a data reference, not a trigger

_Filed: https://github.com/telaminai/fluxtion/issues/14_

**Target** `fluxtion` (compiler) · **Priority** high — this is the sharpest edge in the maintenance path

**Evidence — measured.** `PurchaseOrderRaiser` was given a `SupplierScorecard` reference so its purchase
orders could *quote* a reliability score. A plain field reference makes the referenced node a **parent**,
and a parent firing runs the child's `@OnTrigger`. So a scorecard update — which happens on every
delivery — fired the order raiser with no reorder decision behind it, and the app emitted:

```
PO,null,0,unknown,score=1.0
```

A purchase order for no product, from a node whose own logic had not changed. The build was clean, the
subsystem tests were green, and it took reading the output CSV to notice.

`@NoTriggerReference` is the correct answer and it works. The problem is that **the default is the wrong
way round for this manoeuvre**: when you inject a reference into working code you usually want the data
and not the trigger, and the quiet path silently gives you both. Five of this round's ten injections
turned out to be data-only.

**The ask.** A build warning when a node's `@OnTrigger` method body does not reference a field that is a
graph parent:

```
FLX-1020 WARN  purchaseOrders: 'suppliers' is a trigger parent but @OnTrigger raise() never reads it.
               If it is a data reference, mark it @NoTriggerReference.
```

Detectable statically — it is "does the trigger method touch this field" — and it would have caught the
defect above at build time rather than in a CSV. Not an error: a node may legitimately want to recompute
on a parent it does not read.

**Cost to us if unfixed.** The analyser can show that a node ran when it should not have, but only after
a log exists and only if someone steps the trace. This class is cheap to catch at build time and
expensive to catch afterwards, because the symptom appears in output rather than in behaviour anyone is
testing.

### UP-FLX-21 ◐ Regenerate before compiling, or fail with a message that says what happened

_Filed: https://github.com/telaminai/fluxtion/issues/15_

**Target** `fluxtion` (maven plugin) · **Priority** medium · supersedes the round-1 note on F6

**Evidence — measured, twice, and it scales with the size of the change.** The generated processor is
checked in and compiled as ordinary source, but the plugin regenerates it at `process-classes` — *after*
`compile`. So any change to a node constructor breaks the build against the stale generated file before
regeneration can run. Round 1 hit this with **one** changed constructor. Round 2 changed **ten** and got
ten errors, all inside generated code:

```
StoreProcessor.java:[131,7] constructor MaintenanceScheduler cannot be applied to given types;
  required: FridgeMonitor,CompressorHealth,ModelDrift
  found:    FridgeMonitor
```

The messages are accurate and point at the generator's output, so they read like a generator bug rather
than a stale artefact. The fix — delete the generated file and rebuild — is not discoverable from them.

**The ask**, in preference order:

1. Bind generation *before* `compile` when the output directory is a source root, so a constructor change
   simply regenerates.
2. Failing that, detect the case and say so: `FLX-1021: generated processor is stale — it was built
   against a different signature of com.acme.store.equipment.MaintenanceScheduler. Delete
   src/main/java/…/StoreProcessor.java and rebuild.`

**Cost to us if unfixed.** One wasted iteration per structural change, every time, for anyone who
checks generated source into their repo — which the golden-path examples encourage.

### UP-FLX-22 ◐ Give the dirty contract and reference-kind an artefact

_Filed: https://github.com/telaminai/fluxtion/issues/16_

**Target** `fluxtion` (compiler + GraphML) · **Priority** medium · **extends round 1's finding**

Round 1's assessment ended: *"a node's dirty contract has no artefact… it is the one interface element
between subsystem author and orchestration author with nothing machine-readable behind it."* Round 2
makes that concrete and adds a second field to it.

**Evidence — measured.** The most expensive bug of round 2 was a **getter's meaning**, not its type.
`StockLedger.lastQty()` returns the shelf level *after* the movement; a new subsystem read it as "units
just sold". Both are `int`, both read plausibly at the call site, and the result was demand predicted as
a function of stock on hand — a feedback loop that took the milk reorder point to 908 units and raised
264 purchase orders in a day.

That is not something a compiler can check. But two adjacent facts **are** machine-readable and would
have narrowed it:

- **Reference kind** — is this parent a trigger or a data reference? Already known to the compiler
  (`@NoTriggerReference`); not currently in the GraphML, so no downstream tool can show it.
- **Dirty contract** — a node's `@OnTrigger` returns `true` under conditions stated only in prose. An
  optional `@DirtyWhen("a reorder is needed")` carried through to the GraphML would let the analyser
  render, on the edge, *why* propagation happens.

**The ask.** Emit reference kind as a GraphML edge attribute now (free — the compiler has it), and
consider a declarative dirty-contract annotation.

**Cost to us if unfixed.** The analyser draws every edge identically, so a data reference and a trigger
reference are indistinguishable in the topology — including in the two-view finding report. A reader
cannot tell "this node ran because of that one" from "this node read that one" without opening source.

### UP-FLX-23 ◐ Replay cannot serialise record events

_Filed: https://github.com/telaminai/fluxtion/issues/17_

**Target** `fluxtion` (builder/replay) · **Priority** high — it is silent until the worst moment

**Evidence — measured.** `YamlReplayRecordWriter` serialises inbound events through SnakeYAML's JavaBean
representer, which needs `getX()`. A record exposes `x()`. A 309-node application with 24 record event
types throws on the first event:

```
YAMLException: No JavaBean properties found in com.acme.store.event.StoreEvents$TariffChanged
```

**Why it is worse than a missing feature.** The two capabilities pull against each other. Records are the
right way to write Fluxtion events *because* their generated `toString()` lands readably in the audit
log — which is what a reader sees in `eventToString` on every record. So an event vocabulary optimised
for the **audit log** cannot be recorded for **replay**, and nothing says so until you try. The recorder
is inert while its target writer is unset, so the failure surfaces the first time someone records a
production incident.

**Cost to us if unfixed.** The analyser's pitch — and three rounds of assessment in this repo — assume
the audit log plus a replay log is a complete, re-runnable account of a run. For any application written
in modern Java that pair is currently unavailable, so "replay it locally with a debugger" is advice that
does not work for the apps most likely to be written today.

### UP-FLX-24 ◐ Replay omits exported-service calls, so a replayed run diverges

_Filed: https://github.com/telaminai/fluxtion/issues/18_

**Target** `fluxtion` (runtime + builder/replay) · **Priority** highest of the replay asks

**Evidence — measured.** An `Auditor` tape records events; an `@ExportService` call is an entry point
that dispatches identically but is not an event, so it is never recorded. Replaying a 309-node
application's trading day:

```
replay: 582 live cycles, 574 replayed, 295 divergent
```

**295 of 574 replayed cycles produce different node output** — because three `setPrice` calls never
replayed, the price book was empty, and every downstream figure was computed from nothing. The wrong
numbers are plausible (`stockAtCost: 262.9`) and nothing throws.

**Why it matters here.** This repo's whole argument is that the audit log plus a replay log is a
complete, re-runnable account of a run. It is not, for any application with an operator surface — and
"what did the operator do" is usually the regulatory question. A replay that yields a believable
alternative history is worse than no replay, because it presents as evidence.

**Cost to us if unfixed.** The analyser cannot tell a replayed log from a live one, and has no basis to
warn that a log it is showing came from an incomplete replay.

---

## 2 · Fluxtion runtime — metadata the audit log should carry

The analyser's hardest problems are all "the log does not say". Each item below is something the runtime
knows at write time and the analyser has to guess at afterwards.

### UP-FLX-10 ◐ Mark a re-dispatched record as re-dispatched

_Filed: https://github.com/telaminai/fluxtion/issues/10_

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

### UP-FLX-11 ◐ Traced-regime marker in the record header

_Filed: https://github.com/telaminai/fluxtion/issues/11_

**Target** `fluxtion` (runtime) · **Priority** high · *(carried over: reviewer follow-up F2)*

Whether a record lists **every node invoked** or **only nodes that called `auditLog`** changes the meaning
of absence completely — it is the difference between "did not run" and "we cannot say". Today the analyser
infers it (`AuditTrace.tracesEveryInvocation`: every entry carries `thread` **and** `method`), which is
sound but fragile — one node logging a business key called `method` would break it, and a cycle where no
node logged is unclassifiable.

Ask: `auditLevel: TRACE` (or the owner's preferred minimal spelling, `trace: true|false`) in the
record header. **Per record, not per file**, deliberately: tracing is compiled in at build time but
gated at runtime (`EventLogControlEvent`), so one file can legitimately contain both regimes — a
file-level flag would lie across the switch.

**Owner endorsement + consumer contract (2026-08-17):** the owner independently proposed exactly this
("state trace:[true|false] in the header — this allows the analyser to either draw with certainty or
say the non-log is a potentially missing step"). On landing, the analyser will: (1) prefer the declared
flag over the `AuditTrace` heuristic wherever the header carries it, keeping the heuristic only for
legacy logs; (2) render classification language as DECLARED fact rather than inference —
`trace:true` → an absent node is drawn as **did not run** with certainty; `trace:false` → absence is
drawn as a **potentially missing step**, never as absence of execution; (3) resolve the currently
unclassifiable case (a cycle where no node logged) that the heuristic cannot answer at all.

**Cost to us if unfixed** an inference on every record, and no answer at all for empty cycles.

### UP-FLX-12 ◐ Populate `ProcessorDescriptor.Meta`

_Filed: https://github.com/telaminai/fluxtion/issues/11_

**Target** `fluxtion` / `mongoose` · **Priority** medium · *(carried over: reviewer follow-up F2)*

`sourceFingerprint()` and `graphmlResource()` are both emitted as `null` today. Populating them would let
the analyser pair a log with the right graphml automatically instead of asking the user to find the file,
and would let it say *this graph is not the build that produced this log* with certainty rather than by
counting unmatched instance ids. The reviewer noted `sourceFingerprint` is also the natural carrier for a
provenance stamp.

### UP-FLX-13 ◐ One spelling for exported-call signatures

_Filed: https://github.com/telaminai/fluxtion/issues/13_

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

### UP-FLX-14 ◐ Carry lifecycle/annotation facts into the GraphML

_Filed: https://github.com/telaminai/fluxtion/issues/12_

**Target** `fluxtion` (compiler, graphml emission) · **Priority** medium

GraphML carries no annotations, so the analyser cannot tell that a node's callback is `@AfterEvent`,
`@Initialise` or `@Start` — all of which **fire without upstream propagation**. "Something downstream
logged, therefore this ran" is unsound for exactly those nodes and the analyser has no way to detect them
(recorded in `docs/ONBOARDING.md`).

Ask: a style or data key per node for the callback kinds it declares. Even a coarse
`lifecycleOnly="true"` would let the view stop reasoning about propagation for those nodes.

---

## 2b · Fluxtion runtime — what the log must say to be correlated with anything else

Round 3, raised 2026-08-17 while specifying **M28** (rolling windows over the log's own clock) and
**M29** (`spec-external-series.md` — plotting a foreign CSV, e.g. a FIX log an agent parsed, beside the
audit-derived series). Both make the audit log's *time base* load-bearing in a way it has never been:
M28 computes `mean(x, "5m")` and `rate(x, "1m")` against `logTime`, and M29 puts a second clock on the
same axis. These three asks are the facts the log would need to carry for that to be sound rather than
assumed.

### UP-FLX-25 ☐ Declare the log's time base — source, zone and resolution

**Target** `fluxtion` (runtime, record header) · **Priority** high — blocks honest cross-log correlation

**Measured, 2026-08-17.** A record carries a bare number and nothing about what produced it:

```yaml
eventTime: 1767258000080
logTime:   1767258000090
```

(from `src/test/resources/topology/demo-quote-audit.yaml`). The analyser assumes **epoch
milliseconds** in six files — `Instant.ofEpochMilli` in `TimeFormat`, `TimeRangeSlider`, `SeriesScan`,
`AggregateService`, `SessionFacts` and `RecordExporter` — and `SeriesScan:211` additionally hardcodes
`ZoneOffset.UTC` when it buckets by minute or hour. Nothing in the log confirms any of that; if a
processor ever emitted micros, every timestamp in the app would be silently wrong by a factor of 1000
and no test anywhere would fail.

Ask: a once-per-file (or per-record-header) declaration, e.g.

```yaml
timeBase:
    epoch: millis            # millis | micros | nanos
    zone: UTC                # the zone the writer's clock was reporting
    source: wallClock        # wallClock | monotonic | injected(ClockStrategy)
```

`source` matters as much as the unit: a log written under an injected/simulated `ClockStrategy` (a
replay, a test) must not be silently aligned against a real venue log, and today nothing distinguishes
the two.

**Consumer contract (added 2026-08-17, review F2 — what the analyser will do the day this lands):**
(1) `TimeFormat` renders in the DECLARED zone for declared logs, retiring the display-decision UTC
default there (undeclared logs keep it, labelled as an assumption); (2) `SeriesScan` buckets in the
declared zone, retiring the hardcoded `ZoneOffset.UTC` at SeriesScan:212; (3) M29's external-series
echo upgrades from "declared offset shown" to a verdict — **"clocks comparable"** / **"NOT
comparable: audit log is monotonic, CSV declares wall-clock"**; (4) a `source: injected` log draws a
visible replay banner so simulated time is never silently aligned with venue time; (5) rolled sets
(M30) compare per-file declarations and refuse a set that mixes epochs or sources louder than any
timestamp heuristic could.

**There is now exactly one place to receive it (added 2026-08-18, after M31 shipped).** The reader SPI
makes `timeBase()` a **mandatory** declaration on every log source, and the built-in YAML reader
answers it at `spi/YamlAuditReader.timeBase()` — today returning `TimeBase.wallClockMillisUtc()` with
a comment saying, in as many words, that the native log declares nothing and this is the analyser's
long-standing assumption *stated in one place instead of six*. The day this ask lands, that single
method reads the header instead of asserting, and all five consumer behaviours above become true
without touching anything else. A plugin reader for parquet or Chronicle already declares its epoch
unit truthfully, so **the native format is currently the least self-describing source the analyser
can open** — which is the sharpest form of the argument.

**Cost to us if unfixed** M29 pushes the whole burden onto the user — the CSV's format and zone are
*required* inputs (D-F1: never inferred) precisely because the analyser cannot state its own side of
the comparison. It can show a declared offset but can never say whether the two clocks are actually
comparable. A `rate(x, "1m")` computed against an unlabelled clock is a number with no unit.

### UP-FLX-26 ☐ Identify the record's origin — process, host, processor instance

**Target** `fluxtion` (runtime, record header) · **Priority** medium

Nothing in a record says which process wrote it. A file that concatenates two processors' logs is
indistinguishable from one processor's, so every time-ordered operation the analyser performs —
the `at` anchor's binary search (M26.2), rolling windows (M28.3/.4), band intervals (M28.6) — silently
assumes one monotonic time base. That assumption was accepted explicitly as a stated boundary in the
review of `feat/b-m20-3-m26-m27` (assumption A2) rather than defended, because there is nothing in the
log to defend it with.

Ask: `writer: {host, pid, processorId}` in the header, or once per file.

!!! warning "Name collision — resolve before either is filed"

    This ask originally proposed the key `source`. The owner independently proposed (2026-08-17) a
    `source` property on the record header meaning **what caused this cycle** — external event,
    re-dispatch, exported service call — which is UP-FLX-10's `origin` enum arrived at from the other
    direction, and confirms it is the right shape. Two different facts cannot both be `source`.
    Suggested split: **`origin`** = why this cycle ran (UP-FLX-10's enum, plus `causedBy`), and
    **`writer`** = which process emitted the record (this ask). Both are header fields, both are known
    at write time, and they would file naturally as one issue.

**Cost to us if unfixed** the analyser cannot detect a merged multi-logger file, so it cannot warn;
it just interleaves and produces a plausible, wrong picture. Detection is the whole ask — the analyser
does not need to *handle* merged files, only to stop pretending they are single-source.

### UP-FLX-27 ☐ Let a logged key carry its unit and meaning

**Target** `fluxtion` (runtime, `EventLogger` API) · **Priority** medium · *audit-log twin of UP-FLX-22*

UP-FLX-22 asks for reference-kind and dirty-contract on the **GraphML edges**, from the round-2 finding
that `StockLedger.lastQty()` means *shelf level after the movement* and was read as *units just sold*.
The same ambiguity exists on the **value** side of the log and is now more expensive, because M28 lets a
formula compute over that number (`mean(stockLedger.lastQty, 10)`) and M29 will plot it against an
external series. A window over a misunderstood quantity is a confident wrong answer.

**REVISED 2026-08-17 (owner challenge: "how would the extra key information be added? on every
call? I don't see how it works").** The challenge is correct — the first draft put the metadata on
the logging CALL, which is the wrong transport: a key's unit and meaning are **static facts about
the key**, not about the observation, so carrying them per call would repeat an unchanging string on
every record (log bloat proportional to record count for zero information gain) and would let two
call sites disagree about what the same key means. **Per-call carriage is rejected.** The metadata
must be **declared once**. Owner's chosen shape (2026-08-17): the runtime transport, as

  ```java
  auditLog.keyMap("lastQty", "units — shelf level AFTER the movement");   // in @Initialise
  ```

**with a freeze-at-init semantic: `keyMap` calls are LIVE only during the lifecycle phase
(@Initialise/@Start); once event processing begins, further calls are ignored (no-ops).** That one
rule is what makes the transport sound end to end:

- the dictionary is **complete before record 1**, so the `EventLogger` can emit it as a single
  `keyMap:` block at the head of the file — parsers read it once, no mid-file scanning;
- a key's meaning is **constant for the run** — no call site can redefine `lastQty` between record
  100 and 101, so a tooltip, legend or LLM prompt quoting the description is quoting something that
  held for every record it describes;
- log rolling re-emits the header block at the top of **each rolled file**, so every file of a set is
  self-describing on its own (exactly what M30's rolled-set loader wants — no reaching back to file
  1 for meanings);
- ignored post-init calls are cheap to make debuggable (one debug-level "keyMap after init ignored"
  line) without ever affecting the log's content.

Covers runtime-computed key names and log-only sessions by construction; costs one header line per
described key per file, never per record.

*Complement, not the ask:* the same dictionary could ALSO be emitted compile-time into the GraphML
node payload from an annotation (a vertex-shaped fact riding §2c's verified additive mechanism) —
useful for graphml-first tooling, but secondary: it cannot cover dynamic keys, and the log-side
declaration wins on any disagreement (it is closer to what actually executed).

Surfaced by the analyser in the key picker, the series legend, marker/point tooltips (M32) and the
LLM prompt — the places where a plausible name and a bare number currently reproduce the original
defect.

**Cost to us if unfixed** the analyser presents `stockLedger.lastQty` as a bare label and an LLM
diagnosing from the chart has exactly the information that produced the original defect: a plausible
name and a number. The javadoc that would disambiguate it exists in source the log never references —
and in the round-2 case that getter was the only one of three with no javadoc at all.

---

## 2c · GraphML — richer edges and nodes, backward compatibly

Owner proposal, 2026-08-17: *"add backward compatible attributes to the graphml edge definitions such
as push edge. Maybe data only nodes."* The asks below answer that and the follow-up *"what else would
be useful?"*.

### MEASURED — a probe graph compiled 2026-08-17

Rather than reason about the emitter, a throwaway processor was compiled (`Fluxtion.compile(...)` with
`generateDescription(true)`, fluxtion-bom 1.0.64) containing one of each construct: a plain handler, a
`filterString="ACME"` handler, a `FilterType.defaultCase` handler, an `@OnEventHandler(propagate=false)`
handler, a node with **one trigger parent and one `@NoTriggerReference` parent**, an
`@ExportService(propagate=false)` service with a `@NoPropagateFunction` method, and a DataFlow
`.push(pushTarget::setPushed)`.

**Result: the emitted GraphML declares exactly two keys — `vertex_label` (node) and `edge_label`
(edge) — and EVERY edge in the file is identical but for `id`, `source` and `target`.** The
`edge_label` payload is `<jGraph:ShapeEdge/>`: pure rendering, zero semantics. Node payload is a
`<jGraph:label text="…">` holding `id:`/`class:` plus a stereotype, and `<jGraph:Style properties="…">`
holding the kind.

So these four edges are byte-identical in structure, though no two mean the same thing:

```
<edge id="17" source="PriceEvent" target="rawFeed">        always fires
<edge id="19" source="PriceEvent" target="acmeFeed">       only when filterString == "ACME"
<edge id="20" source="PriceEvent" target="fallbackFeed">   only when NOTHING else matched
<edge id="21" source="PriceEvent" target="quietFeed">      fires, but never propagates
```

as are these two, which are the trigger/data distinction itself:

```
<edge id="2" source="rawFeed" target="consumer">           TRIGGER parent
<edge id="3" source="dataBag" target="consumer">           @NoTriggerReference DATA parent
```

**And `.push()` is worse than unmarked — it is currently unrenderable.** The push materialises as a
chain of three framework nodes:

```
rawFeed → nodeToFlowFunction_8 → mapRef2RefFlowFunction_9 → pushFlowFunction_10 → pushTarget
```

all three of class `com.telamin.fluxtion.runtime.flowfunction.function.*`, which matches the analyser's
framework-package prefix. Running the real parser and `Scaffolding.authoredNodes` over the probe
(21 nodes → 10 authored) gives:

- **Scaffolding hidden (the DEFAULT):** all four push edges are dropped, because each touches a
  framework node. `pushTarget` survives as an authored node with **zero edges** — a disconnected box on
  the canvas, and the `rawFeed → pushTarget` relationship is *completely invisible*.
- **Scaffolding shown:** the relationship appears as four ordinary edges through three plumbing nodes,
  implying a propagating dependency chain — when the whole point of `.push()` is that downstream does
  not see the effect.

Neither view is correct, and the analyser has no way to produce a correct one from this file. That is
the concrete cost behind UP-FLX-28 and UP-FLX-29 together: the marking (`propagates`) and the
identification (`framework="true"`) are both needed, because with only the second, the push chain is
hidden and the relationship vanishes.

### The backward-compatibility contract — VERIFIED, with one constraint

**Measured against `topology/GraphmlParser.java`, 2026-08-17.** The analyser's reader takes `id` from
each `<node>`, `source`/`target`/`id` from each `<edge>`, and then hunts descendants for two attribute
*names*: `text` (the label) and `properties` (the style, from which node `Kind` is inferred). It
validates nothing else and there is no key whitelist — so **new `<key>` declarations and new `<data>`
children are invisible to today's build and cannot break it**. Backward compatibility is real.

**The one constraint:** `firstDescendantAttribute` returns the first non-empty value of the named
attribute on **any** descendant. A new element that carries an attribute literally called `text` or
`properties` on a node's subtree could therefore be picked up as that node's label or style. New data
must not reuse those two attribute names. Everything else is free.

*(Aside: node `Kind` being sniffed from a style string is itself a weakness. An explicit
`kind="EVENT|HANDLER|NODE|EXPORT_SERVICE"` data key would retire the sniffing — cheap, and covered by
the same additive mechanism.)*

### UP-FLX-28 ☐ Edge attributes: `propagates` and `refKind`

**Target** `fluxtion` (compiler, graphml emission) · **Priority** high · *implements the owner's "push
edge", extends UP-FLX-22*

Two **independent** facts, and the distinction matters — they are not the same attribute:

- **`refKind = trigger | data`** — does this parent *fire* the child, or is it only read? Already known
  to the compiler (`@NoTriggerReference`); this is UP-FLX-22's edge half. Measured in POC round 2,
  where an injected data reference silently became a trigger (`PO,null,0,unknown`).
- **`propagates = true | false`** — the **push edge**. Verified against the framework reference
  (`docs/claude.txt`): *"`.push()` is for side effects observable outside the graph (logging, sinks,
  metrics)"*, and of sinks, *"they fire externally to the graph dispatch, so **downstream nodes won't
  see the effect**"*. So a push edge carries data but **not propagation** — the opposite direction of
  the data-reference case, which carries propagation-relevance but no trigger.

**Verified against the runtime source, 2026-08-17** (`fluxtion-runtime/.../runtime/annotations/`):
non-propagation is already declarable at **four** granularities, none of which reach the GraphML:

| carrier | level | wording |
|---|---|---|
| `@ExportService(propagate = false)` | service (type-use) | *"permanently remove the event handler method from the execution path"* |
| `@NoPropagateFunction` | exported method | *"Marks an exported function as non propagating"* |
| `@OnEventHandler(propagate = false)` | handler method | overrides the handler's boolean return |
| `FilterType.matched` | edge condition | *"Only matching filters allow event propagation"* |

So this ask is "emit what you already know", not "invent a concept".

**On "data only nodes" — the name is already taken, and means something else.** `@FluxtionDataOnly`
exists, and it is a **lint-suppression marker**: *"The annotated type is intentionally not a Fluxtion
event-handling node — it is a data class, DTO, event type, helper or launcher"*, and explicitly *"has
no effect on Fluxtion source-generation, dispatch or runtime behaviour"*. It marks classes that are
**not in the graph at all** — the opposite of a graph vertex that carries data without triggering.
Emitting a GraphML node flag called "data only" would therefore collide head-on with an existing
annotation of the same name and the opposite meaning.

Independently of the name: the edge attribute is the primitive and a node classification is derivable
from it — a node whose every outgoing edge is `refKind="data"` is data-only. Deriving it cannot
disagree with the edges, which a separately-emitted node flag eventually would. **Recommend edges
only**, and if a node-level convenience is ever wanted, do not call it data-only.

**Cost to us if unfixed** the analyser draws every edge identically, so it will present a push target
as though it participates in propagation — a *wrong* picture, not merely an imprecise one, in exactly
the "did this node run because of that one?" question the topology tab exists to answer.

### UP-FLX-29 ☐ Mark framework-generated nodes as such

**Target** `fluxtion` (compiler, graphml emission) · **Priority** high — cheapest big win here

**Measured, 2026-08-17.** `topology/Scaffolding.java` classifies framework plumbing with three
heuristics over *names*: a hardcoded id list (`FRAMEWORK_EVENT_IDS`), framework package prefixes, and a
hardcoded simple-name list (`SinkRegistration`, `SinkDeregister`, `LifecycleEvent`, …). That is this
repo guessing at what the compiler generated, and it is wrong in both directions — a user class in a
matching package is hidden, a new framework type is shown until we notice and add its name.

Ask: `framework="true"` (or `role="scaffold"`) on every node the compiler generates.

The probe above shows the second half of the cost: name-matching happens to *succeed* on the DataFlow
plumbing (`…runtime.flowfunction.function.*` matches a package prefix), and succeeding is what makes
`pushTarget` an orphan. Identification alone is not enough — it must arrive with UP-FLX-28's `propagates`,
or hiding the plumbing silently deletes a real relationship.

**Cost to us if unfixed** the **Scaffolding** checkbox — which hides 10 of 20 nodes in the demo graph,
half the picture — is driven by a name list that must be maintained in this repo, in lockstep with a
framework it does not ship with. Every new framework node type is a silent defect here until someone
spots it.

### UP-FLX-30 ☐ A build fingerprint in the GraphML itself

**Target** `fluxtion` (compiler, graphml emission) · **Priority** medium · *pairs with UP-FLX-12*

UP-FLX-12 asks the runtime to populate `ProcessorDescriptor.Meta.sourceFingerprint()`. The graphml
side of the same fact would let the analyser answer *"is this graph the build that produced this log?"*
from **the two files alone**, with no running processor — which is the normal forensic case: someone
sends you a log and a graphml.

Ask: graph-level `<data>` for a build fingerprint, generator version and build timestamp.

**Cost to us if unfixed** the analyser answers the question by counting unmatched instance ids — a
heuristic that cannot distinguish "wrong build" from "this cycle simply didn't touch those nodes".

### UP-FLX-31 ☐ Filtered handler edges — the filter value and the match strategy

**Target** `fluxtion` (compiler, graphml emission) · **Priority** high

**Verified against the runtime source, 2026-08-17.** `annotations/FilterType.java` is documented as the
*"Filter match strategy for an `@OnEventHandler`"* with two values: **`matched`** — *"Only matching
filters allow event propagation"* — and **`defaultCase`** — *"Invoked when no filter match is found,
acts like a default branch in a case statement."* `annotations/FilterId.java` *"Marks a field as a
default filter for all event handlers in that class"* (field-level, so a per-class default).

So an event→handler edge is frequently **conditional**, and some edges are *default branches* that fire
only when every other edge did not. **Confirmed by the probe above**: the unfiltered, `"ACME"`-filtered,
`defaultCase` and `propagate=false` handler edges are byte-identical in the emitted file. The analyser
draws all four as though they always fire.

Ask: on the edge, the filter value and its `FilterType`; on the node, the class-level `@FilterId`
default when one is declared.

**Cost to us if unfixed** the topology cannot answer *"why didn't this handler run?"* — the commonest
question after *"why did it?"* — for any filtered graph. Worse, the answer it currently implies is
wrong: an unconditional arrow that did not fire reads as a defect in the processor rather than a filter
working correctly. A `defaultCase` edge is misleading in the opposite direction, appearing to be an
ordinary path when it is a fallback.

*Process note: this ask was drafted, then dropped, on 2026-08-17 because the framework reference
(`docs/claude.txt`) does not mention filters and this file does not accept asks built on inference. That
was the wrong call — absence from a curated LLM-facing reference is not absence from the framework, and
the rule forbids **inferring**, not **checking**. Reinstated after reading the annotation sources
directly, which is what should have happened first.*

### Where these properties belong — edges vs vertices

Owner question, 2026-08-17: *"a vertex may have a few properties: Filter, Reference, Propagation,
Push. Maybe more?"* Three of those four are **edge** facts, and putting them on the vertex would lose
information:

| fact | carrier | why |
|---|---|---|
| **Reference** (trigger \| data) | **edge** | the same node is commonly triggered by one parent and merely read by another — one value per vertex cannot express that |
| **Propagation** (does the effect continue downstream) | **edge** | `@NoPropagateFunction` is per exported *method*; `FilterType.matched` gates propagation per handler |
| **Push** (data flows against the dependency arrow) | **edge** | a property of the relationship, not of either end |
| **Filter** value + match strategy | **edge** | one handler node may take event `E` under several filter values via different edges |
| **Filter** *default* (`@FilterId`) | **vertex** | genuinely class-level — the default for all handlers in that class |

`Push` and `Propagation` are related but not the same axis: a push edge is non-propagating **and**
reverses the data direction, whereas `@NoPropagateFunction` is non-propagating without reversing
anything. Modelling them as `propagates` plus a direction flag keeps them orthogonal; collapsing them
into one attribute would make "push" unrecoverable. *(Worth the owner confirming how a `.push()` target
is emitted today — whether it appears as an edge at all is not something this repo can verify.)*

The genuinely **vertex**-shaped properties are a different list: `kind` (retiring the style-string
sniffing), `framework` (UP-FLX-29), declared lifecycle callbacks (UP-FLX-14 — note `@AfterTrigger`
exists alongside `@AfterEvent`, `@Initialise`, `@Start`), the `@FilterId` default, the exported
interface and its method signatures with per-method `noPropagate`, the dirty contract (UP-FLX-22), and
optionally source location and dispatch-order index.

### Also worth considering (not yet asks — no measured need in this repo)

- **Dispatch order index per node.** The analyser lays out by layers it recomputes; the compiler knows
  the real invocation order. Would make step-through ordering authoritative rather than reconstructed.
- **Declaring source location** (file + line) per node, so source navigation is exact rather than an
  FQN lookup — and can say *"generated, no source"* honestly instead of failing to find one.
- **Exported-service interface and method signatures** per node — pairs with UP-FLX-13's "one spelling
  for exported-call signatures"; the analyser currently matches exported calls to nodes by name.

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

### UP-SHARED-01 ◐ A published fixture pack

_Filed: https://github.com/telaminai/fluxtion/issues/12_

**Target** `fluxtion` · **Priority** medium

`examples/fixture-generator/` in this repo produces a byte-reproducible, paired set: a compiler-emitted
`.graphml` plus audit logs from running **that** processor, at two audit levels, now including an exported
service and a re-dispatch. Every consumer of audit logs needs exactly this and they should not each invent
one — a hand-written fixture drifts from the log it claims to describe and still renders perfectly.

Ask: publish it (or its equivalent) as a small artefact from the fluxtion repo so svc-admin-web, the
visualiser and the analyser test against identical bytes.

### UP-SHARED-02 ◐ Error-code doc anchors

_Filed: https://github.com/telaminai/fluxtion/issues/8_

**Target** `fluxtion` docs · **Priority** medium — pairs with UP-FLX-01

Every rejection code should carry a stable URL (`fluxtion.dev/errors/FLX-1001`). That is what makes the
semantics in `claude.txt` reachable **at the moment of failure**, rather than requiring an agent to have
read them up front — which is precisely where inference creeps in.

---

## 5 · Mongoose — the agent-brokered dev loop

_Raised 2026-08-25 from [`spec-agent-brokered-dev-loop.md`](../specs/spec-agent-brokered-dev-loop.md)
(ACCEPTED v2). M18 — the analyser linking to a server itself — is **closed**; the adopted design puts
every server-side capability in **Mongoose's own repo**, reached by an agent over MCP, and the
analyser opens files. These four asks are that design's server half._

**THE GATE IS MET — these are ready to file (2026-08-26).** This section previously read "none of them
starts until the §H conformance harness has a home in the M19 bench". That harness shipped:
`tools/bench/loop-bench.py` (M19.6, merged 2026-08-25) plays §C3 steps 3–7 and asserts every one by
name, and `tools/bench/mongoose-stub.py` implements UP-MNG-01 and the export half of UP-MNG-02 **and
nothing else**. So the mongoose-side work has an executable definition of done that exists today:
*make the real server pass what the stub passes.* Pointed at a real `~/.mongoose/servers/`,
`loop-bench.py --registry <dir> --server <name>` **is** the acceptance test for UP-MNG-01/02 — no new
harness, no new agreement about what "done" means.

**These are no longer speculative.** The loop described here is in **daily use debugging a live trading
application**: an agent starts, stops and deploys across environments, pulls logs from remote sites,
drives the analyser, and a fix is proved by re-running and re-reading the record — hours of work
becoming minutes. A **support team** uses the same path to answer questions about a deployed system
they did not build, and reports the same collapse in time-to-answer. Everything below is already being
done; what is missing is that the server half is glued together with bespoke scripting per site
instead of being a capability of the server. That is the gap these asks close, and it is why the
priorities below are ordered by *what the humans are currently working around*.

_**Overtaken for two of the four (2026-08-29):** UP-MNG-01 and UP-MNG-03's declaration half were
implemented and RELEASED in `mongoose-plugins` 1.0.39 without ever being filed as issues — the M19
bundle needed them, so they were built and proved against a real server instead. UP-MNG-02 and
UP-MNG-04 are still unfiled and still owner decisions._

_Still **NOT FILED** as issues in `telaminai/mongoose`. The nine fluxtion-owned asks were filed as
issues 8–16 and moved; these have a spec, a bench and now production evidence, and no issue._

_Target repo for all four: `telaminai/mongoose` (the server runtime and its admin web service plugin —
the owner decides which module). Evidence throughout is **measured**: the `svc-admin-web` capability
check recorded in `spec-closed-loop.md` §B.2, the live playground read of 2026-08-21 (spec §C2), and
the analyser's own endpoint-file mechanism, in daily use by every script in `tools/`._

### UP-MNG-01 ☑ Each running server publishes an endpoint file — `~/.mongoose/servers/<name>`

**LANDED AND RELEASED — `mongoose-plugins` 1.0.39 (2026-08-29).** Implemented in `svc-admin-web`
(`ServerRegistryFile` + `WebAdminService` wiring), merged to `release`, released, and verified public:
the released jar carries `ServerRegistryFile` and the `publishRegistry` / `serverName` / `environment`
/ `registryDir` config. Config knobs also include a JVM-wide `mongoose.servers.dir` override so tests
never touch a developer's real `~/.mongoose`. One deviation from the ask as written, decided by the
implementation: removal hooks `Service.stop()`, **not** `tearDown()` — a live run proved Mongoose's
clean shutdown calls only `stop()`, so removal in `tearDown()` would never have run. A crash still
leaves a dead-pid entry, as specified.

**Verified end to end**, not just unit-tested: the analyser's `loop-bench.py` passes every
registry-side step against a real server, and the M19 P3 clean-machine run drove a generated bundle
through publish → export → clean stop with the entry removed. 96 module tests green.

**Target** `mongoose` (runtime or admin web service) · **Priority** highest — this is the discovery
glob; without it every other leg of the loop needs hand configuration, which is the M18 shape again

**The ask.** On startup, when the admin web service is enabled, write one file per server, mode 600:

```
~/.mongoose/servers/<name>
{
  "name":        "risk-engine",
  "home":        "/Users/dev/work/risk-engine",
  "url":         "http://127.0.0.1:8081",
  "token":       "…",
  "authMode":    "TOKEN",
  "environment": "dev",                                   ← UP-MNG-03
  "pid":         41221,
  "startedAt":   "2026-08-21T09:14:02Z",
  "processors":  [{"group":"main","name":"RiskProcessor",
                   "graphml":"/api/processors/main/RiskProcessor/graphml"}]
}
```

Remove it on clean shutdown; on a crash it stays, and `pid` is how a reader tells a stale file from a
live one — document that, do not build a cleaner. `processors[].graphml` is the **runtime** home of the
"where is this server's GraphML" fact (the catalogue field that was withdrawn in favour of this, §C2).

**Evidence.** The analyser publishes `~/.fluxtion-analyser/rest-endpoint` (`{url, token, pid,
startedAt}`, mode 600 via `PosixFilePermissions`) and every capture and drive script in `tools/` finds
the app that way with zero configuration — this is the same mechanism, one directory over. The
alternatives were rejected in the spec: a registry *inside the analyser* re-acquires the coupling M18
was closed to remove; MCP client config is static and cannot absorb a server deployed mid-session.

**Concrete consumer, 2026-08-28.** The local `mongoose-hosted-fluxtion` starter validation (M19.1a)
needs this file before it can run the existing `loop-bench.py` against a real server. A project-local
skill may read the registry and fall back to its YAML for one-project inspection, but it must not create
or repair it; that fallback is not conformance evidence. This ask therefore survives regardless of the
separate UP-MNG-02 decision.

**Trust model, stated because these tokens gate restarts, not renders (spec review F5).** Mode 600,
readable by any process running as the user — the same posture as the analyser's file. It is only
sufficient because UP-MNG-03 puts the real refusal server-side: a non-dev deployment declines admin
control regardless of who holds the file.

**Acceptance.** A server generated from a `mongoose` starter and run with its `addRunScript` writes
the file before the admin port answers; two servers → two files; `kill -9` → a file whose `pid` is
dead; the §H harness reads it at step 3–4 with no configuration. **Cost to us if unfixed:** step 4 of
the loop is a human typing a URL, and the "deploy a second server, it just appears" property is gone.

**Measured cost, 2026-08-26.** Teams already run this loop; without the file, each site hand-maintains
its own answer to "which servers are running and where" — the coupling M18 was closed to remove,
rebuilt per site in shell. The first thing support does with an unfamiliar system is find its log, and
that is exactly the step no one can automate portably today.

### UP-MNG-02 ☐ An MCP admin tool, in the Mongoose repo — audit level, export, restart

**Target** `mongoose` (a new module, or the admin web service plugin) · **Priority** high — it is the
deploy leg (§C3 step 9) and the moved M18.3/18.4. **`mongoose_export_audit` is the single most valuable
tool here**: it is the first move of every support investigation and every agent-driven diagnosis, and
it is the one currently done by hand

**Disposition is independent of UP-MNG-01 (recorded 2026-08-28).** Local scripts/skills may prove a
project-local start/stop workflow, but they neither remove the need for registry discovery nor decide
whether the Mongoose owner should provide MCP export/restart/audit-level tools. Before filing or
withdrawing this ask, record an explicit **retain / defer / withdraw** owner decision and the evidence
behind it. M19.1a's optional analyser-client MCP comparison is not evidence either way.

**The ask.** An MCP server (stdio, like the analyser's bridge) whose tools address a server by the
`name` in its registry file (UP-MNG-01), each mutation approved per call by the MCP client — the
transport's own permission model, not a hand-built dialog (D-B3):

| tool | does | notes |
|---|---|---|
| `mongoose_servers` | lists the registry — name, url, environment, pid liveness, processors | read-only |
| `mongoose_audit_level {server, level}` | sets the audit level **and returns the previous one** | today `EventLogControlEvent` is not a REST endpoint and has **no GET companion** (M18.3a) — the tool needs a read-back to record a baseline and to restore it. Capture-and-restore was M18.3's whole point |
| `mongoose_export_audit {server, format, path}` | writes the audit log to a file the analyser can open | `format: yaml` today; the analyser then does `open {log, provenance}` with the server's `name` as provenance (§E) |
| `mongoose_restart {server}` | dev restart | **its description must say, in its own words, "export the audit log first — a restart can roll or truncate the evidence" (D-B8)**, because the agent reads the tool, not the spec. `destructiveHint: true` |

Every mutation is **journaled by the server** to its own log (D-B5, free tier): who, what, when,
from which tool. A chat transcript is not an audit trail.

**Evidence.** The `svc-admin-web` check (spec-closed-loop §B.2) found three gaps a client cannot
paper over: no lifecycle endpoint (stop/start commented out), audit level reachable only through a
registered command with no read-back, and no audit-sink discovery (UP-MNG-04). The analyser's MCP
bridge (`McpTools`, 14 verbs, destructive hints on `open`/`source_root`/`screenshot`/`report`) is the
in-house precedent for the shape.

**Acceptance.** From a fresh Claude Code session with both MCP entries configured: `mongoose_servers`
→ the server; `mongoose_audit_level` up, investigate, restore → the server's log shows three
journal lines; `mongoose_restart` prompts in the client and its description mentions export-first;
the analyser's Follow picks up the fresh log. **Cost to us if unfixed:** the flagship cycle's deploy
leg does not exist and M18.3/18.4 stay deleted rather than moved.

### UP-MNG-03 ◐ The server declares whether it is a dev instance

**DECLARATION HALF LANDED — `mongoose-plugins` 1.0.39 (2026-08-29).** `WebAdminService` takes a
declared `environment` (default `dev`) and carries it into the UP-MNG-01 registry file, so an
exporting agent has an authoritative value instead of a hand-typed one — which is the correctness
half of this ask. The M19 bundle sets it explicitly, and `bundle-bench` asserts it.

**STILL OPEN: the ENFORCEMENT half.** Nothing yet refuses admin control outside `dev`. That is
deliberate: server-side refusal presupposes the UP-MNG-02 admin surface, whose retain/defer/withdraw
is still an unmade owner decision (D-05). Until it is made, `environment` is a declared fact only —
useful for provenance, not a licence boundary. Do not read the landed half as the paid line being
enforceable.

**Target** `mongoose` (server config) · **Priority** high — two independent reasons now: it makes the
free/paid line enforceable (D-B7), **and it is a correctness requirement for anyone answering questions
across environments**

**The ask.** A declared `environment` in the server's own configuration (`dev` | `prod` — the owner
names the values), carried into the endpoint file (UP-MNG-01) and honoured by the admin surface:
**outside `dev`, admin control (level changes, restart) is refused without a licence token — by the
server.** A licence check inside a readable client is an `if` anyone can delete; a server that
refuses has nothing to patch on the customer's side.

**Why declared and not inferred.** Non-loopback is the obvious signal and is too crude — a developer
on a remote box is not production. Declared-never-inferred is the rule this codebase applies to
graphs (D-A2), order (D-A1a) and provenance (§E); the environment is the same kind of fact.

**The correctness half, added 2026-08-26 from production use.** Support answer questions about live
systems from exported logs, across several environments. **Two environments running the same build
emit logs that are identical in shape and usually identical in filename** — the only thing that can
separate them is something a human or a script *declared* at export time. The analyser already carries
this the whole way (§E provenance in the status bar, report headers, and a mismatch banner that can say
*"same content — a different system"*), but it can only report what it was told. With `environment` in
the endpoint file, the exporting agent has an authoritative value to pass instead of a hand-typed one,
and it comes from the server rather than from whoever wrote the script.

Without it the failure is silent and expensive in exactly the way this project cares about: an answer
that is correct about UAT, read as production. Nothing errors, the log looks right, and the report
names a filename. This is the same class as the producer diagnostics the analyser added on 2026-08-25 —
a wrong answer with no symptom — and unlike those, the analyser cannot detect it, because both logs
are perfectly well-formed.

**Acceptance.** A starter-generated server declares `dev` by default; flipping it to `prod` makes
`mongoose_restart` return a refusal that names the reason; the free dev MCP pointed at a `prod` server
gets the same refusal. **Cost to us if unfixed:** the free dev MCP can be pointed at production and
the paid line evaporates — or the line is drawn client-side, where it cannot hold.

### UP-MNG-04 ☐ Describe the audit sink — its type and location — instead of assuming a file

**Target** `mongoose` (admin web service) · **Priority** medium — the export path (UP-MNG-02) covers
today's need; this is what lets UP-RDR-01 delete the export beat

**The ask.** `GET /api/audit/sink` (or a field in the endpoint file) → `{"type": "file" | "chronicle" |
"kafka" | "jdbc", "location": "…"}`. The audit writer is pluggable and **typed** (the `LogRecordListener`
seam): a file sink has a path a reader can open; a Chronicle sink has a directory; kafka and jdbc have
neither. Discovery that assumes a file path is wrong for three of the four.

**Evidence.** spec-closed-loop §B.2: "no audit-sink endpoint … the sink is pluggable and typed";
the `mongoose` starters already write `auditBackend: "chronicle"` (live read, spec §C2).

**Acceptance.** A chronicle-backed server answers `{type: chronicle, location: ./audit}`; a reader
that cannot open that type says so rather than failing on a path. **Cost to us if unfixed:** the
Chronicle reader (UP-RDR-01) has to guess where the store is.

---

## 6 · Playground — two catalogue fields, and four that were withdrawn

_The catalogue at <https://fluxtion-playground.dev/starter-templates/index.json> already exists and
is already agent-readable; spec §C2 was corrected twice by reading it. **Recorded here so nobody
re-raises the four that were withdrawn**: `mongoose.adminRest` (encoded by `type: mongoose` + the
`web-admin` tag + the generated services block), `analyser.sourceRoot` (already
`adminWebService.config.sourceRoots`), `run` (`addRunScript: true`), `analyser.graphml` (a runtime
fact — moved into UP-MNG-01's `processors[].graphml`). Two stand._

### UP-PG-01 ☑ `catalogue: 1` — a version integer on the index

**LANDED (2026-08-29).** `static/starter-templates/index.json` carries top-level `"catalogue": 1`,
pinned by a test in `templates-disk.test.ts`. The gallery loader already accepted the object form and
ignores unknown keys, so the change is additive for every existing consumer.

**Target** `fluxtion-playground` (starter-templates) · **Priority** high — D-B4 governs additive
evolution *of a field that does not exist*

**The ask.** Add a top-level `"catalogue": 1` to `index.json`. Fields may then be added within a
version; none removed or retyped; a breaking change increments the integer and the old file stays
served. **Evidence (live, 2026-08-21):** the only top-level key today is `templates`. Agents parse
this file — the playground's own `/build-with-ai` says so — and an unversioned schema read by agents
is rule 6 pointed outward: we would be the party shipping the breaking revision. **Cost to us if
unfixed:** D-B4 is a rule about nothing.

### UP-PG-02 ☐ `agentBootstrap` — where the generated project's agent instructions live

**The CAPABILITY now exists; the CATALOGUE FIELD does not (2026-08-29).** M19 bundles ship
`CLAUDE.md` + an `AGENTS.md` mirror, generated from the single bundle model and declaring the
contract version, the keyless-run and key-regeneration rules and the resolved MCP route. So the
thing the field would advertise is real. What is still missing is the advertisement: nothing in
`index.json` tells an agent, BEFORE generating, whether a template's project will ship agent
instructions. That is the ask, and it is unchanged — only now it is a one-line addition describing
shipped behaviour rather than a request for behaviour that does not exist.

**Target** `fluxtion-playground` (starter-templates + `/start`) · **Priority** medium

**The ask.** One field per template naming the `CLAUDE.md` (+ `AGENTS.md` mirror) stack the generated
project ships — the layered bootstrap M19.1 specifies (`spec-onboarding-example.md`: thin
example-specific file at generation, over the maintained
<https://fluxtion-playground.dev/CLAUDE.md>). It is the one M19.1 need with no equivalent in the
live catalogue. **Cost to us if unfixed:** step 1 of the loop cannot tell an agent whether the project
it is about to generate will know Fluxtion.

### UP-PG-03 ☑ Fetch a template by id, and two catalogue facts a picker needs

**ALL THREE LANDED AND RELEASED (2026-08-30)**, `fluxtion-web` `994e82a`, verified against the
deployed site: `GET /start/scaffold?template=<id>[&artifact=&group=&basePackage=]` returns the real
zip, the catalogue carries `tags: ["onboarding"]` and `keyNeed: "none"`, and (c) turned out to be a
**live bug** rather than a latent one — the gallery was badging `analyser-bundle` "Key once at build"
when it needs no key to build. Contract as shipped:
[handoff_30_aug_2026_1.txt](../handoff/handoff_30_aug_2026_1.txt).

**Raised 2026-08-30**, from the owner's ask that the analyser let you *choose a template inside the
app* ([spec-template-from-analyser.md](../specs/spec-template-from-analyser.md), M19.5). Three parts,
smallest first; all additive under `catalogue: 1`.

**Target** `fluxtion-playground` (`/start/scaffold` + starter-templates) · **Priority** medium

**(a) `GET /start/scaffold?template=<file>[&artifact=&group=&basePackage=]`.** The endpoint already
generates a real zip server-side and is already Worker-safe — but its only input is an lz-string spec
token from the `/start` page's "Copy curl" button. A Java client cannot reasonably produce one, and
reimplementing lz-string to talk to our own service would be absurd. Resolving a named template from
the directory the catalogue already indexes is ~20 lines in a 38-line file, reusing `validate()` and
`buildStarterZip()` unchanged. The optional overrides preserve the factoring
`spec-agent-brokered-dev-loop.md` §C2 established — *the template is a shape, the project is generated
to the user's names* — and stop this degrading into a fixed zip. Unknown template → 404 naming the
catalogue URL; both `s` and `template` → 400. **Cost to us if unfixed:** the analyser cannot acquire a
project at all, and M19.5 stops at the hop the owner actually cares about.

**(b) `tags: ["onboarding"]` on the templates worth showing a support engineer.** The catalogue's 14
entries are written for the playground gallery — someone learning to *build*. The analyser's audience
is someone diagnosing a system that already exists, and "Fluxtion DataFlow DSL" is noise to them. The
alternative is an allowlist inside the analyser, a second source of truth that drifts on the first
rename — the exact failure §C2 spent two corrections avoiding. `tags` already exists and is already
optional (3 entries carry it), so this adds a value, not a key. **Cost to us if unfixed:** the picker
either shows everything or drifts.

**(c) Separate "builds keylessly" from `mode`.** §C2 states the rule *"`interpreted` is keyless…
anything else is AOT and needs a subscribed compiler key."* True when written; **now false for exactly
one template.** `analyser-bundle` is `mode: aot` **and builds with no key**, because M19 commits the
generated processor and moves the `fluxtion-maven-plugin` scan behind `-Pgenerate-fluxtion`. A picker
deriving a "needs an API key" warning from `mode` would therefore show a **wrong warning on the one
template the tutorial recommends** — the first thing a new user sees. Building and regenerating are now
different facts and `mode` cleanly carries neither. **Cost to us if unfixed:** either the picker says
nothing about keys (today's workaround, spec §D-2) or it says something false at the front door.

---

## 7 · Out-of-tree readers on the shipped SPI

### UP-RDR-01 ☐ A Chronicle audit reader — open `./audit` directly, and delete the export beat

**Target** wherever Chronicle already is a dependency — `mongoose` or `mongoose-plugins` (not this
repo: it must not add Chronicle to the analyser's classpath; M31 D-P3 is the plugin directory for
exactly this) · **Priority** high — the spec calls it "the sleeper": it makes the analyse leg *live*

**The ask.** An `AuditLogReader` plugin: `formatId "chronicle"`, `canOpen` by the store directory,
`capabilities {follow: true, byteAnchors: false, randomAccess: …, ordering: TOTAL}` (a Fluxtion
processor's order is compiler-derived), `timeBase` declared from Chronicle's clock — never sniffed
(review X2), `graph(source)` optional (the registry's `processors[].graphml` is the better source).
Records are handed over as canonical text; **it passes the M34.3 conformance suite**
(`docs/site/format-spec.md`, `src/test/resources/conformance/`) — that suite is the contract, and
"passes it" is what conformant means.

**What it buys.** §C3 step 5 (`export?format=yaml` → temp file → open) disappears: the agent opens
`./audit` through the reader and **Follow tails the live store**. Edit → approve restart → watch the
log move. The spec notes this is a design that gets *simpler* over time through a mechanism the
analyser shipped in 1.5.0 — M18 could never have inherited that.

**Evidence.** The `mongoose` starters write `auditBackend: "chronicle"` (live read); the SPI shipped
in 1.5.0 and its `ServiceLoader` path was exercised end to end by the M34.2 probes; `M31.4r` (the
playground example reader) is the same kind of artefact and the two should share a build recipe.
**Cost to us if unfixed:** every cycle carries an export beat and a temp file, and the analyse leg is
a snapshot rather than a tail.

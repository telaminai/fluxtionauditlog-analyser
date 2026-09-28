# Evidence bundle replay — the second delivery (record at the processor, replay into the recipient's build, compare)

**Status: r1 (2026-09-28), DRAFT, not reviewed.** Grounded in the replay spike,
[`tools/spikes/replay-bundle/README.md`](../../tools/spikes/replay-bundle/README.md) (commits `6965aee5`, `5b78ec4d`,
`04f14401` and the one that lands this spec). It builds on the first delivery,
[`spec-evidence-bundle-packaging.md`](spec-evidence-bundle-packaging.md), shipped in 1.27.0, which deferred replay
(its D-0) and says so on every surface: *"no replay: this bundle shows an investigation; it does not reproduce or fix
it"*.

**R0 done (2026-09-28).** On a DEMO processor generated with the recorder compiled in, the spike's results hold with
no hand-called recorder: inputs only, per-event clock, byte-identical; recording everything duplicates the
graph-raised event (8 → 9); a ticking clock shows the recorder's second read. See the spike README ▸ *R0*.

**Owner decisions this spec builds on (2026-09-28), not open for review:**
- **R-D1. Demo scope: YAML only.** Replay records are Fluxtion's YAML replay format. Other encodings, and a link to a
  log stored in the cloud instead of a member, come later. "We can't replay every scenario, but this is not the goal."
- **R-D2. Replay into the processor, not into Mongoose.** The recipient does not need the sender's Mongoose setup;
  the recorded inputs are fed straight into a fresh instance of the processor.
- **R-D3. The replay clock replaces the wall clock, and the times are identical.** Replay installs a data-driven clock
  strategy, so every reading the business logic takes is the recorded one.
- **R-D4. Recording is not a Mongoose-wide setting.** Mongoose is multithreaded. Replay records are written at the
  single-threaded point of consumption: the processor, or the agent that drives it. A Mongoose-wide service may be
  the sink the records are written to.
- **R-D5. Service invocations are dealt with separately.** A service call is a serialised method call, recorded and
  replayed in the same stream as the events. That is its own piece of work (UP-FLX-24, M50.7), and this delivery
  only names the gap (§3.4).
- **R-D6** *(superseded by R-D8, then R-D10)*. The redispatch match was to go through a hook the dispatcher offers.
- **R-D8. Our own replay writer and reader** fix the clock read and the redispatch duplicate, with no Fluxtion core
  release (§3.2).
- **R-D9. The serialiser supports exactly the event types the processor handles**, known statically (§3.2).
- **R-D10. Record by identity at the consumption point; replay by plain injection** (§3.2). The caller names each
  input, and only that object is recorded; graph-raised events are never recorded, whatever their type.
- **R-D7. R0 goes ahead** (the compiler key through the plugin's build). **Licensing is deferred**; the owner will
  decide it.

## 1. What the second delivery is for

The first delivery lets a recipient see an investigation. The second lets them **check it**: they replay the run's
inputs into their own build of the processor, and the analyser tells them whether the replayed audit log is the one in
the bundle.

**The claim a replay may make, and no more:**

> The recorded inputs, replayed into this build of the processor, reproduce the bundled audit log: N of N records
> agree, apart from the fields replay cannot know (§6).

It is **not** "the incident is reproduced" in general. A processor that reads anything the replay records do not carry
(a service's return value, UP-FLX-24; a file; the network) replays only as far as that read is deterministic, and §3.4
makes the capture say so. It is also **not** "the fix works". Replaying a *changed* build and showing where it diverges
is the natural next step (M12's diagnose → fix → prove loop), and this delivery is its prerequisite, not it.

**The demo, added to the first delivery's, about 90 seconds:**

| step | what may be claimed |
|---|---|
| the recipient runs the replay against their build | the build's graph is the bundle's graph (§5.2), or the replay refuses by name |
| the analyser compares | N of N records agree, and each difference is named by record and field |
| a wrong build (a changed node) | the comparison names the first record and field that diverge |

## 2. What is measured, and what is not

**Measured** (the spike, on the DEMO processor, `examples/fixture-generator`):

| result | evidence |
|---|---|
| Inputs recorded with the processor's receipt instant and replayed with data-driven time reproduce the audit log **byte for byte**, when the clock does not move within a cycle | mode `per-event`: 8 of 8 records, byte-identical |
| With a clock that ticks within a cycle, every input's `eventTime` and `logTime` still match; `endTime` differs on every record, and so do the time fields of a record the graph raised itself | mode `per-read-shared` |
| Recording every audited event, not just inputs, **breaks** replay: an event the graph raises on itself is injected from the recording and raised again | mode `record-all`: 8 records become 9 |
| A whitelist of the processor's input types avoids it (the design now matches instead, §3.2) | mode `record-all-whitelist`: byte-identical |
| **The shipped `YamlReplayRecordWriter` records a second clock read**, not the receipt instant | `RecorderClockProbe.java`: the cycle ran at 1000, the recorder wrote 1001 |
| The YAML writer needs JavaBean events; a Java `record` fails. **A property of the writer, not of replay** (owner): `ReplayRecord`, the runner and the data-driven clock take any event | UP-FLX-23 (filed) |

**Read from the source, not run** (rule 6: read, never infer, and say which is which):
- **The auditor path.** The spike calls the recorder itself. That a recorder *installed* as an auditor sees graph-raised
  events is read from the generated dispatcher (`processReentrantEvent` → `queueReentrantEvent` → `onEventInternal`
  → `handleEvent` → `auditEvent`). **R0 ran it:** a generated recorder duplicates the graph-raised event (8 → 9).
- **Auditors are a generation-time choice.** The runtime `DataFlow` has no `addAuditor`; only the builder's
  `EventProcessorConfig.addAuditor(auditor, name)` does. A processor generated without a recorder cannot be recorded
  later without regenerating it.
- **Mongoose already replays `ReplayRecord`s.** `EventQueueToEventProcessorAgent.doWork` passes a `ReplayRecord` to
  `processEvent(event, wallClockTime)`. `AbstractEventToInvocationStrategy` then installs a per-processor synthetic
  clock (`setClockStrategy(atomicLong::get)`) and sets it before dispatch. So R-D2 is a choice, not a limit:
  replaying into Mongoose works too, but needs the sender's Mongoose setup.
- **Mongoose already binds a server-wide service to a per-processor auditor.** `MongooseServer`
  `applyProcessorNameToPerfMonAuditor` finds the `perfMon` auditor by id, names it after the processor and binds it to
  the processor's own `clock`. A replay recorder can be bound to a server-wide sink service the same way (R-D4).

## 3. Recording (the producer's side)

### 3.1 Where: an auditor compiled into the processor

The recorder is an auditor in the generated processor, `cfg.addAuditor(recorder, "replayRecorder")`, constructed with
the processor's own `clock`.

This is the single-threaded point of consumption (R-D4). An auditor hears each event on the processor's thread, in
dispatch order, after the clock has fixed the cycle's instant. Every processor has its own recorder and so its own
record stream. The order of that stream is the order to replay.

**The agent-level alternative, and why it is not the demo's.** Recording in `EventQueueToEventProcessorAgent.doWork`,
before `processEvent`, would need no regeneration and would never see graph-raised events. But:
- one agent drives a **group** of processors, and each filters what it accepts (`isValidTarget`), so the agent's
  stream is not any one processor's input;
- the agent reads the time itself, a separate read from the processor's. That is exactly the gap finding 7 measures,
  unless the agent also pins the processor's clock for every live event, which changes live behaviour.

It stays open (§10) as the route for a processor that cannot be regenerated.

**The sink (Mongoose-wide, R-D4).** Where the records go is a server-wide service: one file per processor, named after
the processor. Mongoose binds the recorder's target writer to that service at startup, as it binds `perfMon`. Outside
Mongoose, including the DEMO, the producer sets the target writer itself. The binding is an upstream ask to Mongoose,
filed when R0 has run.

### 3.2 Our own writer and reader (owner, R-D8): no Fluxtion release needed

The clock read (UP-FLX-53) and the redispatch duplicate (UP-FLX-54) are both fixed on our side, with a writer and a
reader of our own. The asks stay open as improvements, and neither blocks this delivery.

**Record only what arrives from outside, by identity, at the consumption point (owner, R-D10).**
- The code that calls `onEvent` (R-D4: the processor's caller, or the Mongoose agent) names each input to the
  writer just before dispatching it: `writer.expect(event)`.
- The writer, `ReplayCapture`, an `Auditor` compiled into the processor (`cfg.addAuditor`, as R0 proved works),
  records **only that exact object**, stamped with `clock.getProcessTime()`, the instant the cycle ran at (§3.3).
- Everything else it hears during the call was raised by the graph, **whatever its type**, and is never recorded.
  That includes a type that also arrives from outside, which a type-based whitelist cannot handle: include the type
  and the internal copy duplicates on replay; exclude it and the external one is lost.
- **Why the caller has to name it.** The writer cannot tell from its own callbacks. The graph's own event arrives
  *after* its input's `processingComplete`, exactly like a new input (spike, `NestingProbe`).

**The codec is the processor's handled event types, known statically (owner, R-D9).**
- The builder reads them from the nodes' `@OnEventHandler` methods, and the generator compiles the set into the
  processor.
- A handled type the writer cannot encode **fails the build**, by name.
- The writer encodes a record's components itself, so the JavaBean constraint is a choice of encoding, not a rule of
  replay. It keeps Fluxtion's `ReplayRecord` YAML shape.

**The reader: plain injection.**
- Every record is an input. The runner sets the data-driven clock to the record's instant and calls `onEvent`, and
  the graph raises its own events again by itself.
- The reader resolves event types **only from the recipient's own build's handled set**. A replay naming anything
  else is refused and never loaded, so an untrusted bundle cannot make the recipient instantiate an arbitrary class.
- **No matcher.** The audit-log comparison (§6) already names a build that behaves differently: the changed build's
  missing breach is its first difference.

**Measured (spike R2, on a clock that ticks per read, with a `RiskBreachEvent` sent in from outside as well):**
- capture: 8 replay records for 9 audit records;
- the same build replays 9 of 9, with only `endTime` differing;
- the changed build (risk limit 3) replays 8 of 9, and the comparison shows the missing breach.

*Superseded the same day:* R1's matcher, observe mode and `raisedByGraph` declaration. R1 showed them working; R2
shows none of them is needed.

### 3.3 When: the instant the cycle ran at

The recorder must stamp `clock.getProcessTime()`, the instant the clock fixed on receipt, which the audit log's
`eventTime` and `logTime` also show. **The shipped writer stamps a fresh `getWallClockTime()` read** (spike finding 7,
UP-FLX-53). At millisecond resolution the gap is usually 0, so it passes casual testing; with nanosecond timestamps it
is almost never 0.

`ReplayCapture` (§3.2) stamps the receipt instant. R0 reproduced the fault on a generated processor: the cycle ran
at `…140` and the shipped writer wrote `…150`.

### 3.4 What the recording cannot carry

- **Service calls and their returns** (UP-FLX-24, filed): an `@ExportService` call is not an event and is not
  recorded, and a consumed service's return value is not either (M50.7). **This is in our own demo.** The DEMO
  processor exports `QuoteControl`, and the shipped DEMO log (`src/main/resources/demo/demo-quote-audit.yaml`, 10
  cycles) holds 2 such calls, `suspendQuoting` and `resumeQuoting`. A replay of that run loses both, and with them the
  suspended state the later cycles depend on.
  - **They are visible in the log.** Each call is a record with `event: ExportFunctionAuditEvent`, and its
    `eventToString` gives the signature (`public void suspendQuoting(String arg0)`) but not the arguments, so the log
    cannot supply them either.
  - **So the capture counts them.** It writes `replay.serviceCalls: K` into the manifest (§4.2). When K > 0, it says
    before the author sends anything: *"this log holds K exported-service calls; the replay does not carry them, so
    it will diverge from the first cycle that depends on one"*. It does not refuse, because the divergence is itself
    true evidence. The comparison (§6) counts a missing call record as a divergence, never as an exception.
  - **The demo's replay run makes no service calls** (R1). Replaying the shipped 10-cycle log is the witness that the
    limit is named, not the demo.
- **Record events, in YAML** (UP-FLX-23, filed): the YAML writer needs JavaBean events, or a custom representer.
  This is the writer's constraint, not replay's, and another writer lifts it (R-D1's later encodings).
- **State before the first recorded event.** A recording that starts mid-run cannot rebuild the state the processor
  had when it started. In the demo, recording starts with the processor.

## 4. The bundle (capture, in the analyser)

### 4.1 A new member, paired by content

```
replay/<file>                       the run's replay records, byte for byte, under the name the author's file had
```

The capture's author points at the replay file (`report {bundle: {…, replay: <path>}}`). The `evidenceCapture` node
decides whether it goes in (rule 9: the file is read as a fact, and the node decides). The path itself never enters
the bundle (first delivery, r4).

**Pairing, because a replay file from another run is the likeliest mistake** (built in R2, `ReplayPairing`). The
frame observes it and the node decides.
- **The rule.** A replay holds the run's inputs only (R-D10), each stamped with its cycle's instant (§3.3). So each
  replay record must match a log record with the same event name and the same `eventTime`, **in order, within the
  log**. The log records in between are the ones the graph raised itself, and service calls.
- **Refused, naming the first record that has no match.** A replay from another run fails at record 0. A replay
  re-stamped by a writer that read the clock again (UP-FLX-53) fails at the first record it moved. Records out of order
  fail too. *r1 said the replay lines up one to one with the log minus service calls; that was written before R-D10,
  and is wrong once the graph's own events are not recorded.*
- **Nothing is loaded from the file.** Only each record's class name and time are read. Any document that is not a
  replay record refuses the file, which, with the pairing, is what stands between an arbitrary file named as a replay
  and the bundle it would be packed into.
- **A limit, deliberately.** A replay that also recorded the graph's own event still pairs: that event is in the log
  at that instant. Replaying it would raise the event twice, and that is the comparison's to find (§6), not the
  pairing's. Pairing asks only whether the replay belongs to this log.
- **A second limit, found by the demo driver (R5).** Pairing is by content, so a *different run* whose inputs are the
  same events at the same instants pairs too. The short DEMO **test fixture** (`src/test/resources/topology/demo-quote-audit.yaml`, not the jar's copy) and the
  recorded run are exactly that: the same
  input script on the same clock, the DEMO log adding two service calls at the end. That is honest by construction:
  those records ARE the replay's inputs. The capture then counts the service calls and warns, and `--replay-compare`
  shows where the logs differ.
- **A third limit (review N4): the log names an event type by its SIMPLE class name**, so pairing matches on it:
  `a.Foo` and `b.Foo` are indistinguishable here. The runner is not affected, since it resolves the full name
  against the build's own handled types.
- **What a replay does not carry of its own types** (review S1). A replay cut short still pairs, because each record
  it has IS one of the log's. So the pairing counts the log's records of the replay's own event types that it does
  not carry, and the node says so: they are events the graph raised itself, or inputs the replay is missing. It does
  not refuse, because an event the graph raises can share a type with an input.
- **The copy is held to the paired bytes.** The pairing digests the file in the same pass that reads it. The writer
  copies the file later, off the event thread, and refuses a copy whose digest differs: *"the replay file changed
  after it was paired with the log; nothing was written"*.

**Also refused, by name:** a replay with a window (§4.3), and a replay while Follow is on, whether or not growth has
been seen yet (second review S4: a producer still writing could add records between the pairing and the copy),
because the bundle would then hold only what was read so far.

**What the author is told** (the node's lines, when it is written): *"replay: the run's N recorded inputs, paired
with the log in order…"*, and, when the log has any, *"replay: the log holds K exported-service call(s) the replay
does not carry, so a replay diverges from the first cycle that depends on one"*. `--verify` prints the same facts.

### 4.2 Manifest (format 2)

```json
"replay": {"member": "replay/demo-quote-recorded.replay.yaml", "records": 7, "serviceCalls": 0}
```

- `format` is 2 **only** when a replay is present. Every other bundle stays format 1, byte for byte, so a
  first-delivery reader still reads every bundle it could before.
- A format-2 manifest must name its `replay/` member, and list it; a format-1 manifest may not state a replay. So a
  replay can be neither claimed without its member nor carried without its claim.
- `limits` replaces its "no replay" line with:

> *"replay: the recorded inputs reproduce this log only on a build whose graph matches, and only as far as the
> processor reads nothing the records do not carry"*

*r1 listed `processor` and `inputTypes` here as well. `inputTypes` went with R-D10, since nothing in the bundle needs
to classify records any more. The processor's identity is the graph member's, for the runner (§5.2).*

### 4.3 Excerpts and a replay

A time-window excerpt (first delivery, §13.4) and a replay do not combine simply. The processor's state at the
window's start depends on every earlier input (spike finding 4), and `YamlReplayRunner.betweenTimes` skips those.
**For the demo, a replay goes only with a whole-log bundle.** A capture with a window and a replay refuses by name:
*"a replay needs the whole run; drop the window or the replay"*.

Replaying from the start and comparing only the window is the right later design. It carries the whole replay but
only an excerpt of the log, so it is a size decision, and it is deferred. Checkpoints come after that.

## 5. Replay (the recipient's side, outside the analyser) — built in R4

### 5.1 The runner

`tools/replay/ReplayBundle.java`, one file, run with JBang (the install) or plain `java` with the Fluxtion runtime on
the classpath:

```
jbang tools/replay/ReplayBundle.java --bundle run.fexp \
      --processor com.acme.demo.generated.DemoQuoteRecordedProcessor --cp <your build> --out replayed-audit.yaml
```

1. It reads the bundle's `replay/` and `graph/` members.
2. It checks the graph (§5.2).
3. It resolves each replay record's event type **only from your build's handled types**: the event types the
   generated processor's `handleEvent` overloads take, found by reflection, so this works for any generated processor.
   A type outside them is refused and never loaded.
4. It feeds each record into a fresh instance of your processor with a data-driven clock set to the record's instant.
   The graph raises its own events again by itself.
5. It writes the processor's audit log, framed as the producer's is, without the runner's own set-up records, and
   prints the `--replay-compare` command to run next.

It refuses, by name, exit 1: a build that is not the bundle's processor; a bundle with no replay records; a processor
not on the classpath; a record type the build does not handle; an output file that exists. Exit 2 is usage.

**Why outside the analyser.** Replay runs the recipient's code, their build on their classpath. The analyser has
never executed a user's processor, and the placement rule (first delivery, §3.1) does not require it to. The runner's
output is untrusted input that the analyser then checks. The comparison is what the recipient must trust, so the
comparison is the analyser's (§6).

**Found building it:** `fluxtion-runtime` is not on Maven Central, so the runner's JBang header names the repository
the analyser's own build resolves it from (`//REPOS … repsy-fluxtion-public`). The first header had none and failed
on a clean resolve. `ReplayRunnerEndToEndTest#theRunnerResolvesWhereTheAnalyserDoes` holds the header to the root
pom's version and repository.

### 5.2 Is this the same processor?

Before replaying, the runner compares your build's own GraphML, which the generator writes as `<Class>.graphml`
beside the class, with the bundle's `graph/` member, by **node ids and edges**, never bytes: GraphML is not byte-stable
across generator versions (§10.4). It refuses naming the difference, *"your build's graph is not the bundle's:
node(s) [replayCapture] missing"*. `--skip-graph-check` replays anyway, and says *"graph: NOT checked"*. The XML is
parsed with DOCTYPEs and external entities disabled, since the bundle's is untrusted.

A build with the **same graph but different behaviour** passes this check and replays. That is the case the
comparison exists for: in the end-to-end test, a build whose risk limit is 3, not 2, replays and diverges at record 6,
the risk monitor's own record, before the breach it no longer raises.

## 6. The comparison (in the analyser) — built in R3

`--replay-compare <bundle.fexp> <replayed-audit.yaml>` (`ReplayCompare`) verifies and unpacks the bundle into a
temporary working copy first, then compares the bundled log with the replayed one record by record, and prints one
verdict. Both logs are read with the analyser's own reader, so how each file frames its records does not matter.

**The rule, measured: every record, and every line of it, is exact, except `endTime` and `thread`.** Both say when
and where a cycle ran, never what it computed.
- `endTime` is a live read when the cycle ends, and replay pins the clock at the recorded instant (R1/R2 spikes,
  generator 1.0.75).
- `thread` is the name of the thread the cycle ran on, and a recipient's replay runs on its own. **Found by the R4
  end-to-end test**: the spike's recording and replay ran on one thread, so it never saw it. R3 shipped the rule
  with `endTime` only; R4 corrected it.
- Both exceptions are by position and key: a record whose excepted line is missing on one side, or has moved, or
  stands where the other has a different key, still differs.
- Nothing else is excepted: an input's `eventTime`, a graph-raised record's times, every node's values.
- *r1 carried a second exception, for a graph-raised record's time fields. It came from the 2026-08-16 processor,
  which gave a queued event a fresh clock reading. On 1.0.75 a queued event keeps its input's instant in production
  and on replay (R1), so there is no such exception, and with it went the need for `inputTypes` to classify records.*
- A record count that differs is a divergence, named at the first record one side has and the other does not.
- Anything else that differs is a divergence, named by record, event and YAML key path, first difference first,
  with both values.

**Refused, with nothing compared:** a bundle that fails verification; one that carries no replay records; one whose
log is an excerpt; a replayed log that cannot be read.

**Output and exit codes:**
- `replay: AGREES, 8 of 8 records (endTime and thread excepted, differing on 8: when and where a cycle ran, which a
  replay cannot know)`, exit 0;
- `replay: DIVERGES at record 1 (OrderUpdateEvent): eventLogRecord.nodeLogs.orderTracker: '{ orderId: ord-1,
  live: 1}' ≠ '{ orderId: ord-1, live: 7}'`, then how many records before it agree, exit 1;
- a refusal on stderr, exit 1; usage, exit 2.

Either verdict states the limits from §4.2, and names the service calls the replay records cannot carry when the log
holds any.

**Measured on a real replay.** The fixture generator now also commits what its fresh processor wrote replaying the
recorded run (`demo-quote-recorded.replayed-audit.yaml`). It agrees 8 of 8, with `endTime` excepted on all 8.

In the UI the replayed log opens beside the bundled one and the walk can step onto the first divergence. That is the
follow-up; the demo is the CLI verdict.

## 7. Acceptance (each needs a regression and a wrong-result witness; rule 8)

| id | acceptance | wrong-result witness |
|---|---|---|
| RB-1 | a DEMO run recorded with `ReplayCapture` replays to N of N agreeing, graph-raised records matched and never injected | the same with one node's logic changed diverges, named at its first record |
| RB-2 | an event raised by the graph is never recorded, even when an input of the same type arrives from outside | the external `RiskBreachEvent` is recorded and replays; recording by type instead duplicates the graph's own (spike `record-all`, R0) |
| RB-3 | the exceptions are only those in §6 | a replayed log that differs in an input's `eventTime` is a divergence, not excepted |
| RB-4 | a replay file from another run is refused at capture, naming the first unmatched input | a correctly paired file passes |
| RB-5 | a window together with a replay is refused by name | a whole-log capture with a replay succeeds |
| RB-6 | the runner refuses a build whose graph differs, naming the difference | the matching build replays |
| RB-7 | the recorder stamps the receipt instant | `ReplayCodecRoundTripTest#theWriterStampsTheReceiptInstant`: the DEMO's `ReplayCapture` on a clock that ticks per read records the cycle's instant, never a later read; control `rq-stamps-the-receipt-instant`. (`RecorderClockProbe` shows Fluxtion's own writer does not, by hand.) |
| RB-8 | a format-1 bundle still verifies and unpacks, unchanged | `EvidenceBundle` refuses a format-2 bundle with its `replay` member removed but still in the manifest |
| RB-9 | a log holding exported-service calls is captured with `serviceCalls: K` and the warning | `ReplayRunnerEndToEndTest#aLogWithServiceCallsDivergesAtTheFirstCall`: the short DEMO test fixture pairs (its 7 inputs are the recorded run's) with `serviceCalls: 2`, observed for real; the runner replays it and `--replay-compare` DIVERGES at record 8, the first `ExportFunctionAuditEvent`, never AGREES; control `rp-counts-the-logs-service-calls`. (r1 named the jar's DEMO log, which since R0b no longer pairs: second review finding 1.) |

Every acceptance runs in `mvn test` from committed fixtures. The runner's end-to-end run joins
`tools/evidence-bundle-demo.py`.

## 8. Plan

| slice | what | needs |
|---|---|---|
| **R0** ☑ | Prove the auditor path. Generate the DEMO processor with the DEMO recorder installed, record a run, and replay it: the spike's `record-all-whitelist` result, with no hand-called `eventReceived` | done: builder 1.0.71 (the root pom's), runtime 1.0.16 |
| R1 ☑ | `ReplayCapture` (identity recording, the static codec) and the injecting reader into the DEMO; commit the recorded fixture, the log, the replay and the graph from one real run with no service calls (§3.4) | R0; M70.R0b if the fixtures are regenerated |
| R2 ☑ | The `replay` member: the `evidenceCapture` node's pairing, the whole-log rule and manifest format 2 | R1 |
| R3 ☑ | `--replay-compare` and its rule (§6) | R1 |
| R4 ☑ | The runner (§5), and the demo driver's replay leg (the driver leg moves to R5) | R2, R3 |
| R5 | The docs site's *Evidence bundles* section gains *Replay*; CHANGELOG | R4 |
| — | UP-FLX-53, UP-FLX-54 filed as improvements, not blockers (R-D8); the Mongoose sink binding asked for after R0 | the owner files them |

## 9. Not in this delivery

- Other encodings (a binary replay format, Chronicle); a replay stored in the cloud and linked, not carried (R-D1).
- Windowed replay, and checkpoints (§4.3).
- Service-call recording (R-D5): a serialised method call in the replay stream, its own work (UP-FLX-24, M50.7).
  Until it lands the bundle names the gap and does not fix it.
- Replaying into Mongoose (R-D2).
- The UI side-by-side, and "prove the fix", which replays a changed build (M12).
- Signing. A bundle is still unsigned (first delivery, D-3).

## 10. Open questions

1. **Licensing on the recipient's side.** The Fluxtion guide calls replay a commercial compiler feature. In 1.0.16,
   `YamlReplayRunner` ships in `fluxtion-builder-api-all-java8`, and the jar states no licence terms of its own (its
   `LICENSE.txt` belongs to a bundled dependency). Can a recipient without a Fluxtion licence run the replay?
   **An owner question; it decides whether the demo can say "anyone can check it".**
2. **Settled by R-D10:** there is no redispatch match. The open part is whether a match
   means the event's type and equality, or its type and position only. Equality needs the event's `equals`, which
   beans often lack.
3. **Processor or agent?** R-D4 allows either. This spec uses the processor (§3.1) because it needs no Mongoose change
   and records the right instant. The agent route serves processors that cannot be regenerated. Is that a case worth
   designing for now?
4. **Is GraphML byte-stable across generations of the same source?** If it is, §5.2 can compare digests. To measure
   in R0: generate twice and compare. **One data point (R0):** the DEMO GraphML generated on 2026-08-16 and the one
   generated by 1.0.75 on 2026-09-28 **differ**: 12,653 bytes became 24,958, with new graph keys including
   `fluxtion.sourceFingerprint` and `fluxtion.toolchainVersion`. *(r1 first said "byte-identical"; that compared the
   file with its own build copy.)* So GraphML is **not** byte-stable across generator versions. §5.2 keeps the
   topology comparison, and `fluxtion.sourceFingerprint` is a candidate identity to measure.
5. **How does the recipient get the processor?** In the demo the recipient compiles it: the DEMO processor's
   generated source ships in the analyser's jar as a resource (`src/main/resources/demo/`), and it compiles against the
   Fluxtion runtime with no compiler key (the spike's `run.sh` does exactly this). For a real incident, is the build named in the bundle (a Maven coordinate and a
   commit), and does the runner fetch it?

## 10a. The pre-review (2026-09-28): what it found, and what was done

An independent agent reviewed the whole range (`v1.27.0…feat/evidence-bundle-replay`) before the PR's review.
Every finding was checked against the code before anything was changed:

| finding | disposition | its check (rule 8) |
|---|---|---|
| R1 a null String replayed as `"ul"` | fixed: `null` written bare, a String must be quoted, one left-to-right unescape | `ReplayCodecRoundTripTest`; `rq-null-is-written-bare` |
| R2 strings with line endings, `char ','`, `BigDecimal` | fixed: controls and U+0085/2028/2029 escaped; chars quoted; the encodable set is exactly what the readers decode | round trip through BOTH readers; `rq-escapes-every-line-ending`, `rq-encodable-is-what-the-readers-read`, `rn-refuses-an-unquoted-string` |
| R3 a CRLF log never agreed | fixed: a trailing `\r` dropped per line | `ReplayCompareTest#aCrlfLogAgrees`; `rc-a-crlf-log-is-the-same-log` |
| R4 the `replay` read was unconfined | fixed: `ExportGuard.resolveRead`, as every verb read | `ReplayConfinementTest`; `ax-the-replay-read-is-confined` |
| R5 painted screenshots | open, before merge: native regeneration needs Screen Recording | the PR's checklist |
| S1 a replay cut short paired as the whole run | fixed as a statement, not a refusal: the uncarried records of its own types are counted and named (a graph-raised event may share an input's type) | `ReplayPairingTest#aReplayCutShortStillPairs…`; `rp-counts-what-a-replay-does-not-carry` |
| S2 a cut-off last record, mis-worded | fixed: named as cut off | `ReplayPairingTest#aLastRecordCutOffIsNamedAsCutOff` |
| S3 the pairing reads the file on the event thread | **not fixed, recorded**: it streams in bounded memory, so a large replay stalls the window for its read time. Moving it off the thread needs the capture request to become asynchronous (observe, then submit the fact), a rule 9 change of its own | tracker M70.R2a |
| S4 the runner held every member, and checked no digest | fixed: only `replay/` and `graph/` kept, each held to the manifest | `ReplayRunnerEndToEndTest#whatCannotBeReplayedIsRefused`; `rn-holds-members-to-the-manifest` |
| S5 format-1 listing a `replay/` member verified | fixed: the member rule enforced on read both ways | `ReplayBundleTest#theMemberRuleHoldsOnRead`; `eb-format1-lists-no-replay-member` |
| S6 audit-level changes mid-run | fixed as a warning from the runner | `ReplayRunnerEndToEndTest#aLevelChangeInTheLogIsWarnedAbout`; `rn-warns-of-a-level-change` |
| N1 text said only `endTime` | fixed | read |
| N2 a nested `thread` value was excepted | fixed: only the record's own fields, at its field indent | `ReplayCompareTest#aNestedThreadValueIsCompared`; `rc-only-the-records-own-fields-are-excepted` |
| N3 a byte-order mark refused a replay | fixed, in the pairing and both readers | `ReplayPairingTest#aByteOrderMarkIsAccepted`, `ReplayCodecRoundTripTest#aBomAndCrlfAreRead` |
| N4 simple class names | recorded as a limit (§4.1) | — |
| N5 a bad `--level`; one exit code for refused and diverges | `--level` fixed (usage, exit 2). **Exit 1 for both kept**: every bundle command uses 1 for "not accepted", and a script reads the `DIVERGES`/`REFUSED` line | `ReplayRunnerEndToEndTest` |
| N6 the member's name | fixed in §4.1 | read |
| (rule 9 nit) the frame composed "no log is open" | fixed: with no log the frame observes nothing, and the node refuses in its own words | — |

**Found while closing them:** the mutation harness's JSON-lines reader split rows on U+2028 (`splitlines()`), so a
test message carrying one crashed the gate. `GateLauncher` now escapes U+0085/2028/2029 and the reader splits on
`\n` only; the engine's self-test passes. Two controls first survived because their test *threw* instead of
asserting (the gate counts only a named assertion); both tests now assert.

## 10b. The second pre-review (2026-09-28, a different model): what it found, and what was done

It re-ran the evidence (2850/0/0/170; demo 57/57; 29/29 sampled controls) and judged §7 item by item:

| finding | disposition | its check |
|---|---|---|
| 1 RB-9's witness was dead: the jar's DEMO log no longer pairs (R0b), and the only service-call test fed the count by hand | fixed: RB-9 now runs on the DEMO test fixture, which pairs, with the count observed and a real replay DIVERGING at the first call; §4.1's "short log" now names the test fixture | `aLogWithServiceCallsDivergesAtTheFirstCall`, `theTestDemoLogPairs_andItsServiceCallsAreCountedForReal`; `rp-counts-the-logs-service-calls` |
| 2 RB-7 had no check CI runs | fixed: the DEMO `ReplayCapture` itself, on a ticking clock | `theWriterStampsTheReceiptInstant`; `rq-stamps-the-receipt-instant` |
| 3 the runner bounded only the two members it keeps | fixed: every member bounded (`BoundedStream`) | `everyMemberIsBounded`; `rn-bounds-every-member` |
| 4 a replay under Follow was refused only once growth was seen | fixed: refused whenever Follow is on | `aReplayUnderFollowIsRefusedBeforeGrowthIsSeen`; `rp-refuses-a-replay-of-a-growing-log` (re-anchored) |
| 5 a divergence may be non-determinism, not the build | fixed as a statement on every DIVERGES | `MainBundleTest#replayCompareExitsByVerdict` |
| 6 a `\u` escape's bound was off by one | fixed in both readers | read |
| 7 mkdocs unverified there; painted screenshots | mkdocs passes here; screenshots open before merge (and M70.R0c, now decided: option a) | — |

## 11. Revision history

- **r1 (2026-09-28):** first draft, from the spike and the owner's decisions R-D1…R-D4. The same day it took two
  owner corrections: the JavaBean constraint is the YAML writer's, not replay's; and graph-raised events are matched
  at the redispatch queue in replay mode (§3.2), rather than only excluded by a whitelist. A third decision, R-D5:
  service invocations are a separate piece of work, as serialised method calls. Then R-D6 (a hook, since superseded) and R-D7 (R0
  approved, licensing deferred), and R0 done. Then R-D8, our own writer and reader (§3.2 rewritten), and a
  correction: GraphML is not byte-stable across generator versions (§10.4).

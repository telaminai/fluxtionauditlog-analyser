# Evidence bundle replay — the second delivery (record at the processor, replay into the recipient's build, compare)

**Status: r1 (2026-09-28), DRAFT, not reviewed.** Grounded in the replay spike,
[`tools/spikes/replay-bundle/README.md`](../../tools/spikes/replay-bundle/README.md) (commits `6965aee5`, `5b78ec4d`,
`04f14401` and the one that lands this spec). It builds on the first delivery,
[`spec-evidence-bundle-packaging.md`](spec-evidence-bundle-packaging.md), shipped in 1.27.0, which deferred replay
(its D-0) and says so on every surface: *"no replay: this bundle shows an investigation; it does not reproduce or fix
it"*.

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
| A whitelist of the processor's input types fixes it | mode `record-all-whitelist`: byte-identical |
| **The shipped `YamlReplayRecordWriter` records a second clock read**, not the receipt instant | `RecorderClockProbe.java`: the cycle ran at 1000, the recorder wrote 1001 |
| The YAML writer needs JavaBean events; a Java `record` fails. **A property of the writer, not of replay** (owner): `ReplayRecord`, the runner and the data-driven clock take any event | UP-FLX-23 (filed) |

**Read from the source, not run** (rule 6: read, never infer, and say which is which):
- **The auditor path.** The spike calls the recorder itself. That a recorder *installed* as an auditor sees graph-raised
  events is read from the generated dispatcher (`processReentrantEvent` → `queueReentrantEvent` → `onEventInternal`
  → `handleEvent` → `auditEvent`). Slice R0 runs it.
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

### 3.2 What: inputs only for the demo; everything, matched at the redispatch queue, as the design

**The design (owner, 2026-09-28): make redispatch a choice in replay mode, and match at the redispatch queue.**
- The recorder records every event it hears, graph-raised ones included, in dispatch order.
- In replay mode, when the graph queues a re-entrant event (`processReentrantEvent` → `queueReentrantEvent`), the
  replay compares it with the **next recorded event**.
  - If they match, the recorded one is consumed and the event is dispatched **once**, at the recorded instant.
  - If they do not, the replay has **diverged at that point**, and it says so by name: the first event this build
    raised that the recorded run did not, or the reverse.
- Graph-raised events therefore stop being a hazard and become a check. A changed build is caught at the cycle
  where it first behaves differently, not later in the audit comparison.
- **Expected, not yet measured:** because the matched event is dispatched at its recorded instant, the second
  exception in §6 (a graph-raised record's time fields) would go.
- The simpler variant, where replay mode ignores redispatch and injects recorded events only, also stops the
  duplicates. But it would hide a build that no longer raises the event, so it is not the design.
- This needs a Fluxtion change, UP-FLX-54 (revised): a replay mode on the callback dispatcher.

**The demo, until that lands: the processor's inputs, and nothing else.** The recorder names its processor's **input types** in `classWhiteList`. Getting that list wrong fails in two ways, and
both are detectable:
- **too wide** (a type the graph also raises): the event is injected from the recording and raised again, so the
  replay has extra records. The comparison names the first one (§6);
- **too narrow** (an input left out): the replay never sees it, so the replay diverges from the first cycle that
  needed it. The comparison names that one too.

For the demo the list is written by hand. If the redispatch match is delayed, the fallback source for the list is the
compiler, which knows the processor's input types (M50.8, the compiler-derived capture set).

### 3.3 When: the instant the cycle ran at

The recorder must stamp `clock.getProcessTime()`, the instant the clock fixed on receipt, which the audit log's
`eventTime` and `logTime` also show. **The shipped writer stamps a fresh `getWallClockTime()` read** (spike finding 7,
UP-FLX-53). At millisecond resolution the gap is usually 0, so it passes casual testing; with nanosecond timestamps it
is almost never 0.

Until UP-FLX-53 lands, the DEMO producer installs its own recorder: an `Auditor` of about twenty lines that writes the
same YAML (`ReplayRecord`, `wallClockTime`, `event`) stamped with `getProcessTime()`. The format is Fluxtion's, so
`YamlReplayRunner` reads it unchanged. This is not a fork: it is one line different, and it goes when the upstream
fix does.

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
replay/<processor>.replay.yaml      the processor's replay records, byte for byte
```

The capture's author points at the replay file (`report {bundle: {…, replay: <path>}}`). The `evidenceCapture` node
decides whether it goes in (rule 9: the file is read as a fact, and the node decides). The path itself never enters
the bundle (first delivery, r4).

**Pairing, because a replay file from another run is the likeliest mistake.** The node checks, in order, that the
replay's records correspond one to one to the log's **input records**: the log's records whose event type is on the
replay's whitelist. The check compares event type and position. It **refuses** when they do not correspond, naming
the first record that does not, because a replay from another run would produce a confident, wrong comparison.

It also compares times, and **reports rather than refuses** on a mismatch: *"recorded instants differ from the log's
on K of N inputs, by at most X"*. That is UP-FLX-53 measured on the author's own data, and the recipient sees it
before replaying.

### 4.2 Manifest (format 2)

```json
"replay": {"member": "replay/demo-quote-processor.replay.yaml",
           "processor": "com.acme.demo.generated.DemoQuoteProcessor",
           "inputTypes": ["com.acme.demo.event.Events$MarketDataEvent", "com.acme.demo.event.Events$OrderUpdateEvent"],
           "inputs": 7, "serviceCalls": 0}
```

`format` becomes 2 only when a replay is present, so a first-delivery reader still reads every bundle without one.
`limits` replaces its "no replay" line with:

> *"replay: the recorded inputs reproduce this log only on a build whose graph matches, and only as far as the
> processor reads nothing the records do not carry"*

### 4.3 Excerpts and a replay

A time-window excerpt (first delivery, §13.4) and a replay do not combine simply. The processor's state at the
window's start depends on every earlier input (spike finding 4), and `YamlReplayRunner.betweenTimes` skips those.
**For the demo, a replay goes only with a whole-log bundle.** A capture with a window and a replay refuses by name:
*"a replay needs the whole run; drop the window or the replay"*.

Replaying from the start and comparing only the window is the right later design. It carries the whole replay but
only an excerpt of the log, so it is a size decision, and it is deferred. Checkpoints come after that.

## 5. Replay (the recipient's side, outside the analyser)

### 5.1 The runner

A small runner, `tools/replay/replay-bundle.java`, run with JBang, which is already the install. It:
1. reads the unpacked bundle's manifest;
2. checks the graph (§5.2);
3. feeds `replay/<processor>.replay.yaml` into a fresh instance of the recipient's processor through
   `YamlReplayRunner.newSession(reader, processor).callInit().callStart().runReplay()`;
4. writes the replayed audit log beside the working copy.

**Why outside the analyser.** Replay runs the recipient's code: their build of the processor, on their classpath.
The analyser has never executed a user's processor, and the placement rule (first delivery, §3.1) does not require
it to. The runner's output is untrusted input that the analyser then checks. The comparison is what the recipient
must trust, so the comparison is the analyser's (§6).

### 5.2 Is this the same processor?

Before replaying, the runner compares the recipient's graph with the bundle's `graph/` member. It refuses by name
when they differ: *"your build's graph is not the bundle's: node X added"*. The comparison uses the analyser's own
topology model, node ids and edges, not bytes, because whether GraphML is byte-stable across two generations of the
same source is not yet measured (§10).

The same check read the other way is the "fix" demo of the future: a build that differs on purpose, replayed and
compared.

## 6. The comparison (in the analyser)

`--replay-compare <bundle.fexp> <replayed-audit.yaml>` verifies the bundle first (as `--verify` does), then compares
the bundled log with the replayed one record by record, and prints one verdict. **The measured rule** (spike):
- **Every record and field must agree exactly**, except:
  - **`endTime`, on every record.** It is a live read when the cycle ends, and it is pinned on replay.
  - **The time fields of a record the graph raised itself mid-cycle.** In production it takes a fresh reading;
    replay keeps the time of the input that triggered it. Such a record is known by its event type not being on the
    manifest's `inputTypes`: the whitelist doubles as the classifier. With the redispatch match (§3.2), this
    exception is expected to go. **`ExportFunctionAuditEvent` is the one
    exception to the classifier.** A service call's record is not graph-raised, and it is never excepted (§3.4).
- A record count that differs is a divergence, named at the first extra or missing record.
- Anything else that differs is a divergence, named by record index and field, **first difference first**.

Output: `replay: 8 of 8 records agree (endTime excepted on 8; graph-raised time fields excepted on 1)`, or
`replay: DIVERGES at record 5 (OrderUpdateEvent): nodeLogs.riskMonitor.exposure 1200 ≠ 1100`, with the limits line
from §4.2 on either.

In the UI the replayed log opens beside the bundled one and the walk can step onto the first divergence. That is the
follow-up; the demo is the CLI verdict.

## 7. Acceptance (each needs a regression and a wrong-result witness; rule 8)

| id | acceptance | wrong-result witness |
|---|---|---|
| RB-1 | a DEMO run recorded with the input whitelist replays to N of N agreeing | the same with one node's logic changed diverges, named at its first record |
| RB-2 | a recording with a graph-raised type on the whitelist is caught | the comparison names the duplicated record (spike `record-all`) |
| RB-3 | the exceptions are only those in §6 | a replayed log that differs in an input's `eventTime` is a divergence, not excepted |
| RB-4 | a replay file from another run is refused at capture, naming the first unmatched input | a correctly paired file passes |
| RB-5 | a window together with a replay is refused by name | a whole-log capture with a replay succeeds |
| RB-6 | the runner refuses a build whose graph differs, naming the difference | the matching build replays |
| RB-7 | the recorder stamps the receipt instant | `RecorderClockProbe`: the shipped writer, with a ticking clock, records a different instant; the DEMO recorder does not |
| RB-8 | a format-1 bundle still verifies and unpacks, unchanged | `EvidenceBundle` refuses a format-2 bundle with its `replay` member removed but still in the manifest |
| RB-9 | a log holding exported-service calls is captured with `serviceCalls: K` and the warning | the shipped 10-cycle DEMO log replays to a divergence the comparison names, and it never reads as agreeing |

Every acceptance runs in `mvn test` from committed fixtures. The runner's end-to-end run joins
`tools/evidence-bundle-demo.py`.

## 8. Plan

| slice | what | needs |
|---|---|---|
| **R0** | Prove the auditor path. Generate the DEMO processor with the DEMO recorder installed, record a run, and replay it: the spike's `record-all-whitelist` result, with no hand-called `eventReceived` | the Fluxtion compiler key, through the fixture generator's plugin build: **the owner's go** |
| R1 | Commit the recorded fixture: the log, the replay and the graph from one real run with no service calls (§3.4) | R0 |
| R2 | The `replay` member: the `evidenceCapture` node's pairing, the whole-log rule and manifest format 2 | R1 |
| R3 | `--replay-compare` and its rule (§6) | R1 |
| R4 | The runner (§5), and the demo driver's replay leg | R2, R3 |
| R5 | The docs site's *Evidence bundles* section gains *Replay*; CHANGELOG | R4 |
| — | UP-FLX-53, UP-FLX-54 filed; the Mongoose sink binding asked for after R0 | the owner files them |

## 9. Not in this delivery

- Other encodings (a binary replay format, Chronicle); a replay stored in the cloud and linked, not carried (R-D1).
- Windowed replay, and checkpoints (§4.3).
- Service-call recording (UP-FLX-24, M50.7). The bundle states it; it does not fix it.
- Replaying into Mongoose (R-D2).
- The UI side-by-side, and "prove the fix", which replays a changed build (M12).
- Signing. A bundle is still unsigned (first delivery, D-3).

## 10. Open questions

1. **Licensing on the recipient's side.** The Fluxtion guide calls replay a commercial compiler feature. In 1.0.16,
   `YamlReplayRunner` ships in `fluxtion-builder-api-all-java8`, and the jar states no licence terms of its own (its
   `LICENSE.txt` belongs to a bundled dependency). Can a recipient without a Fluxtion licence run the replay?
   **An owner question; it decides whether the demo can say "anyone can check it".**
2. **The redispatch match: where does it live?** Choices: the generated callback dispatcher (a replay flag on
   `queueReentrantEvent`), or the runner through a hook the dispatcher offers. What counts as a match: the event's
   type and equality, or its type and position only? Equality needs the event's `equals`, which beans often lack.
3. **Processor or agent?** R-D4 allows either. This spec uses the processor (§3.1) because it needs no Mongoose change
   and records the right instant. The agent route serves processors that cannot be regenerated. Is that a case worth
   designing for now?
4. **Is GraphML byte-stable across generations of the same source?** If it is, §5.2 can compare digests. To measure
   in R0: generate twice and compare.
5. **How does the recipient get the processor?** In the demo the recipient compiles it: the DEMO processor's
   generated source ships in the analyser's jar as a resource (`src/main/resources/demo/`), and it compiles against the
   Fluxtion runtime with no compiler key (the spike's `run.sh` does exactly this). For a real incident, is the build named in the bundle (a Maven coordinate and a
   commit), and does the runner fetch it?

## 11. Revision history

- **r1 (2026-09-28):** first draft, from the spike and the owner's decisions R-D1…R-D4. The same day it took two
  owner corrections: the JavaBean constraint is the YAML writer's, not replay's; and graph-raised events are matched
  at the redispatch queue in replay mode (§3.2), rather than only excluded by a whitelist.

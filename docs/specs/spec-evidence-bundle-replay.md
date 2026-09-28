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
- **R-D6. The redispatch match is made through a hook the dispatcher offers** (§3.2), not inside the generated
  dispatcher. The replay runner supplies the matcher.
- **R-D8. Our own replay writer and reader** fix the clock read and the redispatch duplicate, with no Fluxtion core
  release (§3.2). This replaces R-D6's dispatcher hook: the match is made in our runner.
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

**The writer: `ReplayCapture`, an `Auditor` compiled into the processor** (`cfg.addAuditor`, as R0 proved works):
- It records **every** event it hears, graph-raised ones included, in dispatch order.
- It stamps each record with `clock.getProcessTime()`, the instant the cycle ran at (§3.3). The generator injects
  the processor's own clock (`@Inject`, as it does into `YamlReplayRecordWriter`).
- It writes Fluxtion's replay YAML (`ReplayRecord`, `wallClockTime`, `event`), so a record stays readable by
  `YamlReplayRunner` too, when the events are beans. Our reader does the reading, so the JavaBean constraint is a
  choice of encoding, not a rule of replay (R-D1's later encodings).

**The reader: our runner, which matches graph-raised events instead of injecting them** (R-D6's matcher, with no
dispatcher hook needed):
- The runtime has no `addAuditor`, so the runner cannot attach an observer of its own. The writer is already compiled
  into the recipient's build, so **the runner switches it into observe mode**. It then reports each event it hears to
  the runner instead of writing it.
- For each recorded record the runner does one of two things:
  - **If the graph raised events during the last injected input** (the observer heard them inside that `onEvent`
    call, since queued events drain within it), the record must be the **next of those, in order**. Equal type and
    fields: consumed as a match, never injected. Anything else: the replay **diverged here**, named by record.
  - **Otherwise it is an input:** the runner sets the data-driven clock to the record's instant and calls `onEvent`.
- A raised event with no recorded record left to match it is a divergence too: this build raised something the
  recorded run did not.
- So graph-raised events stop being a hazard and become a check. A changed build is caught at the first cycle where
  it behaves differently.
- **Measured (R1 spike):** replayed into the same build, only `endTime` differs; replayed into a changed build (risk
  limit 3), the runner names record 7, *"the recorded run raised RiskBreachEvent[…]; this build raised nothing"*.
  §6's second exception does not arise on generator 1.0.75, because a queued event keeps its triggering input's
  instant in production too.
- **Which records are raised** is known from the input types the processor gives the writer, which marks the others
  `# raised` (a YAML comment, so Fluxtion's parser still reads the file). The writer cannot see where an outside
  `onEvent` call starts. A wrong declaration is still caught: a raised type declared as an input is injected and
  duplicated, which the comparison names; an input declared as raised is never injected, which the matcher names.

**Why not a whitelist.** Recording inputs only also avoids the duplicate (spike, R0), but the list is hand-written,
it fails silently when wrong, and it throws the check away. It stays as the fallback if observe mode fails R1.

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
replay/<processor>.replay.yaml      the processor's replay records, byte for byte
```

The capture's author points at the replay file (`report {bundle: {…, replay: <path>}}`). The `evidenceCapture` node
decides whether it goes in (rule 9: the file is read as a fact, and the node decides). The path itself never enters
the bundle (first delivery, r4).

**Pairing, because a replay file from another run is the likeliest mistake.** The node checks, in order, that the
replay's records correspond one to one to the log's records, in order. `ReplayCapture` records every event the
processor hears, and each audit record is one received event, so the two line up, apart from service-call records
(`ExportFunctionAuditEvent`, §3.4), which the replay does not carry. The check compares event type and position. It **refuses** when they do not correspond, naming
the first record that does not, because a replay from another run would produce a confident, wrong comparison.

It also compares times, and **reports rather than refuses** on a mismatch: *"recorded instants differ from the log's
on K of N records, by at most X"*. With `ReplayCapture` it should be 0; a non-zero K means the replay was written by
another writer (UP-FLX-53), and the recipient sees it before replaying.

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
3. feeds `replay/<processor>.replay.yaml` into a fresh instance of the recipient's processor with our reader
   (§3.2): inputs injected at their recorded instants, graph-raised records matched, never injected;
4. writes the replayed audit log beside the working copy.

**Why outside the analyser.** Replay runs the recipient's code: their build of the processor, on their classpath.
The analyser has never executed a user's processor, and the placement rule (first delivery, §3.1) does not require
it to. The runner's output is untrusted input that the analyser then checks. The comparison is what the recipient
must trust, so the comparison is the analyser's (§6).

### 5.2 Is this the same processor?

Before replaying, the runner compares the recipient's graph with the bundle's `graph/` member. It refuses by name
when they differ: *"your build's graph is not the bundle's: node X added"*. The comparison uses the analyser's own
topology model, node ids and edges, not bytes: GraphML is **not** byte-stable across generator versions (§10.4).

The same check read the other way is the "fix" demo of the future: a build that differs on purpose, replayed and
compared.

## 6. The comparison (in the analyser)

`--replay-compare <bundle.fexp> <replayed-audit.yaml>` verifies the bundle first (as `--verify` does), then compares
the bundled log with the replayed one record by record, and prints one verdict. **The measured rule** (spike):
- **Every record and field must agree exactly**, except:
  - **`endTime`, on every record.** It is a live read when the cycle ends, and it is pinned on replay.
  - **The time fields of a record the graph raised itself mid-cycle, on an older generator only.** On 1.0.75 there
    is no such exception (R1 spike): a queued event keeps its input's instant in production and on replay. On the
    2026-08-16 processor, in production it took a fresh reading;
    replay keeps the time of the input that triggered it. Such a record is known by its event type not being on the
    manifest's `inputTypes`, the types the producer declares it feeds from outside. They classify records for this
    exception only; they no longer decide what is recorded (§3.2). This exception probably stays (§3.2). **`ExportFunctionAuditEvent` is the one
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
| RB-1 | a DEMO run recorded with `ReplayCapture` replays to N of N agreeing, graph-raised records matched and never injected | the same with one node's logic changed diverges, named at its first record |
| RB-2 | a build that no longer raises the breach, or raises a different one, is named at that record by the runner's matcher | the recorded run replayed into its own build matches every raised event; the shipped reader, injecting everything, duplicates the breach (spike `record-all`, R0) |
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
| **R0** ☑ | Prove the auditor path. Generate the DEMO processor with the DEMO recorder installed, record a run, and replay it: the spike's `record-all-whitelist` result, with no hand-called `eventReceived` | done: builder 1.0.71 (the root pom's), runtime 1.0.16 |
| R1 | `ReplayCapture` (the writer, with observe mode) and the matching reader; commit the recorded fixture, the log, the replay and the graph from one real run with no service calls (§3.4). Measure whether §6's second exception stays | R0; M70.R0b if the fixtures are regenerated |
| R2 | The `replay` member: the `evidenceCapture` node's pairing, the whole-log rule and manifest format 2 | R1 |
| R3 | `--replay-compare` and its rule (§6) | R1 |
| R4 | The runner (§5), and the demo driver's replay leg | R2, R3 |
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
2. **The redispatch match: what counts as a match?** It is made in our runner (R-D8). The open part is whether a match
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

## 11. Revision history

- **r1 (2026-09-28):** first draft, from the spike and the owner's decisions R-D1…R-D4. The same day it took two
  owner corrections: the JavaBean constraint is the YAML writer's, not replay's; and graph-raised events are matched
  at the redispatch queue in replay mode (§3.2), rather than only excluded by a whitelist. A third decision, R-D5:
  service invocations are a separate piece of work, as serialised method calls. Then R-D6 (the hook) and R-D7 (R0
  approved, licensing deferred), and R0 done. Then R-D8, our own writer and reader (§3.2 rewritten), and a
  correction: GraphML is not byte-stable across generator versions (§10.4).

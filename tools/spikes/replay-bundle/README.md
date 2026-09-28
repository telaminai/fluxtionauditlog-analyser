# Replay spike — does replaying the recorded inputs reproduce the audit log? (2026-09-28)

**Question.** For an evidence bundle to carry a replay: if a run's inputs are recorded as YAML replay records, and
replayed with data-driven time into a fresh processor, is the replayed audit log the same as the captured one? And
if not, exactly what differs?

**How.** `run.sh`, on the analyser's DEMO processor (`examples/fixture-generator`, generated with Fluxtion 1.0; its
source is committed, so no compiler key is involved).
1. **Capture:** one run writes both the audit log and the replay records. The replay records go through
   `YamlReplayRecordWriter(Clock)`, called for each input before it is dispatched.
2. **Replay:** a fresh processor is fed only the replay YAML, by
   `YamlReplayRunner.newSession(reader, processor).callInit()…callStart().runReplay()`, with its own audit log.
3. **Compare:** byte for byte, then through the analyser's own reader and record digests.

It runs in three clock modes (all three record inputs only):
- `per-event`: time moves only between events;
- `per-read`: time ticks on every read, as a wall clock does, with the recorder reading the clock on its own;
- `per-read-shared`: the same ticking clock, but the recorder records the instant the processor's clock fixes on
  receipt, as a recorder stamping `getProcessTime()` would. The shipped `YamlReplayRecordWriter` does not (finding 7).

Two more modes test what an installed recorder RECORDS:
- `record-all`: the inputs and the event the graph raises on itself. The generated dispatcher passes both through
  `auditEvent`: `processReentrantEvent` queues it, then `onEventInternal` → `handleEvent` → `auditEvent`.
- `record-all-whitelist`: the same, with `classWhiteList` naming only the processor's input types.

## Result

| capture clock | replayed audit log | analyser: records, digests equal |
|---|---|---|
| `per-event` | **byte-identical** | 8 and 8, **8 of 8** |
| `per-read` | differs in `eventTime`, `logTime` and `endTime` in every record: the recorder read a different instant than the processor did. **Not only a harness artefact: the shipped writer does this** (finding 7). | 8 and 8, 0 of 8 |
| `per-read-shared` | **every input's `eventTime` and `logTime` identical.** Only `endTime` differs (every record), plus the time fields of the one record the graph raised itself. | 8 and 8 |
| `record-all` | **diverges:** 9 records instead of 8, because the breach appears twice (raised by the graph, AND injected from the recording) | 8 and 9 |
| `record-all-whitelist` | **byte-identical**: 7 replay records (inputs only), and the graph reproduces the breach itself | 8 and 8 |

The 8 records are 7 inputs plus a `RiskBreachEvent` the graph raises on itself. Replay reproduced it from the
graph's own logic; it was not fed in.

**So the comparison rule is measured, not assumed.** Replay replaces the wall clock with the recorded time. So the
time an input's cycle runs at, and everything the business logic reads through the injected `Clock`, is identical.
Two readings are live measurements that replay cannot know:
- **`endTime`:** the live clock when the cycle ends. It is pinned on replay, so it differs by the cycle's processing
  time, usually 0 at millisecond resolution.
- **A record the graph raises itself mid-cycle:** production gives it a fresh live reading; replay keeps the time of
  the input that triggered it.

Compare records exactly, except for those. With a clock that does not move within a cycle, the replay is
byte-identical.

*Correction, the same day:* the first write-up said "exclude `eventTime`, `logTime` and `endTime`". That came from the
`per-read` harness, whose recorder read the clock on its own. The owner's point that the replay clock strategy should
make the times identical was right for every input; `per-read-shared` measures it.

*Second correction, the same day:* `per-read-shared` models a recorder that records the processor's receipt instant.
**The shipped `YamlReplayRecordWriter` does not** (finding 7): it takes a fresh reading. So today the identical-times
result holds only when the clock does not move between the processor's reading and the recorder's. The replay
side is right; the recording side needs the one-line change in finding 7.

## Findings

1. **The YAML writer needs JavaBean events.** This is a property of the writer, not of the replay framework (owner):
   `ReplayRecord`, the runner and the data-driven clock take any event. The YAML is written and read with SnakeYAML's
   bean representation, so an event needs a no-argument constructor and getters and setters. A Java `record` fails
   with "No JavaBean properties found". The DEMO events are records, so the spike swaps in bean-shaped copies
   (`Events.java`, spike-only). A producer that wants YAML replay uses beans, a custom representer, or another writer.
2. **The 1.0 API differs from the guide, which is written for 0.9.**
   - The classes are in `com.telamin.fluxtion.builder.replay`, in `fluxtion-builder-api-all-java8`; `ReplayRecord`
     and `ClockStrategy` are in the runtime.
   - `YamlReplayRunner` takes a `CloneableDataFlow`, and has `afterTime`, `beforeTime` and `betweenTimes`: a built-in
     time window.
   - A replay record's time field is `wallClockTime`, where the guide says `eventTime`.
3. **Internally raised events: an installed recorder sees them, and recording them breaks replay.** The generated code
   shows the path: a graph-raised event reaches `auditEvent` exactly as an input does. Recorded and replayed, it is
   injected AND raised again: 9 records, a duplicate breach. **Whitelisting the input types fixes it**, and the replay
   is byte-identical. For the demo, a recorder therefore names its processor's inputs. The design (owner) is to record everything and, in
   replay mode, match at the redispatch queue: an event the graph queues is checked against the next recorded one and
   dispatched once, and a mismatch is where the replay diverged. That needs a Fluxtion change, UP-FLX-54.
4. **A time-windowed replay is not a replay of the window.** `betweenTimes` skips earlier events, but the processor's
   state at the window's start depends on them. A replayable excerpt must replay from the start, or from a checkpoint.
5. **Licensing: unresolved.** The guide calls replay a commercial compiler feature. In 1.0 the runner ships in
   `fluxtion-builder-api-all-java8`; its own licence terms are not stated in the jar (the `LICENSE.txt` there is a
   bundled dependency's).
6. **Where the recorder belongs** (owner, 2026-09-28). Mongoose is multithreaded, so replay recording is not a
   Mongoose-wide setting. It is installed at the processor, or the agent that drives it: the single-threaded point of
   consumption, whose order is the order to replay. A Mongoose-wide service may be the WRITER (the sink the records go
   to); the recording point is per processor.

7. **The shipped recorder records a second reading, not the receipt instant.** Read from the 1.0.16 bytecode, and
   measured by `RecorderClockProbe.java`:
   - The processor's `Clock` is a `FirstAfterEvent` auditor. On receipt it fixes `processTime` with one read.
   - The audit log's `logTime` is `getProcessTime()` and its `eventTime` is `getEventTime()`, both that instant.
     `endTime` is `getWallClockTime()`, a live read, which is why it differs on replay.
   - `YamlReplayRecordWriter.eventReceived` stores `clock.getWallClockTime()`: **a live read, taken after the
     clock's**. Even with the processor's own clock passed in, the probe's ticking clock gives `processTime = 1000`
     and a recorded `wallClockTime: 1001`.
   - On replay the data-driven clock returns the recorded value, so each input's cycle runs at the recorder's instant,
     not at the one the log shows. At millisecond resolution the gap is usually 0; with nanosecond timestamps (the
     next release) it is almost never 0.
   - **Fix:** record `clock.getProcessTime()`. This is an upstream ask to Fluxtion. Until it lands, a producer can
     install its own recorder, which is about twenty lines: an `Auditor` that writes a `ReplayRecord` stamped with
     `getProcessTime()`.

## R0, 2026-09-28: the recorder compiled in, on a generated processor

R0 (owner: "R0 is good") generates the DEMO processor with `YamlReplayRecordWriter` installed through `cfg.addAuditor`,
then records and replays with **no hand-called `eventReceived`**. It ran in a scratch copy of
`examples/fixture-generator`, with the spike's bean events and two builders: record everything, and inputs only. The
key was used only by the plugin's own build. The generator injects the processor's own `clock` into the writer
(`@Inject`), and `auditEvent` calls `clock`, then the recorder, then the event log.

| recorder | clock | replay records | audit records, captured → replayed | replayed log |
|---|---|---|---|---|
| everything | per-event | 8 | 8 → **9** | the breach twice (finding 3, now on the real path) |
| everything | per-read | 8 | 8 → **9** | the same |
| inputs only | per-event | 7 | 8 → 8 | **byte-identical** |
| inputs only | per-read | 7 | 8 → 8 | `eventTime`, `logTime` and `endTime` differ on all 8 records: the cycle ran at `…140`, and the recorder wrote `…150` (finding 7, now on the real path) |

A positive control ran in the same harness: the committed processor writes 2 audit records for 2 events.

**A wrong turn, corrected the same day.** The first R0 run regenerated with the fixture generator's own toolchain:
BOM 1.0.64, which gives `fluxtion-builder` **1.0.64**, against the hosted generator **1.0.75**. The output had empty
`auditEvent` methods and re-entrancy compiled out, so it wrote no audit log. I read that as a generator regression and
drafted an upstream ask (UP-FLX-55). The owner said it had to be something in how I ran it, because otherwise audit
logging would break everywhere. It was the client builder. The analyser's own `SessionProcessor`, generated by the
same 1.0.75, audits normally with `fluxtion-builder` **1.0.71** (the root pom's `fluxtion.builder.version`).
Pinning 1.0.71, and runtime 1.0.16, in the scratch pom fixed it. The draft ask is withdrawn.

**Consequence for the repo (M70.R0a, fixed):** `examples/fixture-generator` now pins the root pom's builder
(1.0.71) and runtime (1.0.16), with no BOM, and `FixtureGeneratorToolchainTest` holds the two poms together. The
committed DEMO sources compile against it.

**Regenerating the committed fixtures is a separate step (M70.R0b).** With the right toolchain, the audit logs change:
- the generator reads the clock once per cycle, so `eventTime == logTime`;
- the GraphML carries the new graph vocabulary, and grows from 12,653 to 24,958 bytes.

Against the regenerated fixtures, `CoverageScopeTest` fails 2 of its assertions: coverage names 5 nodes that cannot
log where the test expects 4, and the extra one is `QuoteControl`, the exported-service interface. The fixtures were
restored.

## Rerun

```
mvn -o -q compile                     # the analyser's classes, for the digest comparison if wanted
tools/spikes/replay-bundle/run.sh     # prints every mode's verdict
```

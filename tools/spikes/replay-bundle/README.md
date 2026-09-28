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

It runs in three clock modes:
- `per-event`: time moves only between events;
- `per-read`: time ticks on every read, as a wall clock does, with the recorder reading the clock on its own;
- `per-read-shared`: the same ticking clock, but the recorder records the instant the processor's clock fixes on
  receipt, as a `YamlReplayRecordWriter` installed as an auditor does.

Two more modes test what an installed recorder RECORDS:
- `record-all`: the inputs and the event the graph raises on itself. The generated dispatcher passes both through
  `auditEvent`: `processReentrantEvent` queues it, then `onEventInternal` → `handleEvent` → `auditEvent`.
- `record-all-whitelist`: the same, with `classWhiteList` naming only the processor's input types.

## Result

| capture clock | replayed audit log | analyser: records, digests equal |
|---|---|---|
| `per-event` | **byte-identical** | 8 and 8, **8 of 8** |
| `per-read` | differs in `eventTime`, `logTime` and `endTime` in every record. **A harness artefact:** the recorder read a different instant than the processor did. | 8 and 8, 0 of 8 |
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

## Findings

1. **Replay records need JavaBean events.** The YAML is written and read with SnakeYAML's bean representation, so an
   event needs a no-argument constructor and getters and setters. A Java `record` fails with "No JavaBean properties
   found". The DEMO events are records, so the spike swaps in bean-shaped copies (`Events.java`, spike-only). A
   producer that wants replay has to use beans, or a custom representer.
2. **The 1.0 API differs from the guide, which is written for 0.9.**
   - The classes are in `com.telamin.fluxtion.builder.replay`, in `fluxtion-builder-api-all-java8`; `ReplayRecord`
     and `ClockStrategy` are in the runtime.
   - `YamlReplayRunner` takes a `CloneableDataFlow`, and has `afterTime`, `beforeTime` and `betweenTimes`: a built-in
     time window.
   - A replay record's time field is `wallClockTime`, where the guide says `eventTime`.
3. **Internally raised events: an installed recorder sees them, and recording them breaks replay.** The generated code
   shows the path: a graph-raised event reaches `auditEvent` exactly as an input does. Recorded and replayed, it is
   injected AND raised again: 9 records, a duplicate breach. **Whitelisting the input types fixes it**, and the replay
   is byte-identical. So a recorder must name its processor's inputs, never `recordAll()`.
4. **A time-windowed replay is not a replay of the window.** `betweenTimes` skips earlier events, but the processor's
   state at the window's start depends on them. A replayable excerpt must replay from the start, or from a checkpoint.
5. **Licensing: unresolved.** The guide calls replay a commercial compiler feature. In 1.0 the runner ships in
   `fluxtion-builder-api-all-java8`; its own licence terms are not stated in the jar (the `LICENSE.txt` there is a
   bundled dependency's).
6. **Where the recorder belongs** (owner, 2026-09-28). Mongoose is multithreaded, so replay recording is not a
   Mongoose-wide setting. It is installed at the processor, or the agent that drives it: the single-threaded point of
   consumption, whose order is the order to replay. A Mongoose-wide service may be the WRITER (the sink the records go
   to); the recording point is per processor.

## Rerun

```
mvn -o -q compile                     # the analyser's classes, for the digest comparison if wanted
tools/spikes/replay-bundle/run.sh     # prints every mode's verdict
```

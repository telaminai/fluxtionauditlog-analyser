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

It runs in two clock modes: time moving only between events (`per-event`), and time ticking on every read, as a wall
clock does (`per-read`).

## Result

| capture clock | replayed audit log | analyser: records, digests equal |
|---|---|---|
| `per-event` | **byte-identical** | 8 and 8, **8 of 8** |
| `per-read` | differs **only** in `eventTime`, `logTime`, `endTime` (16 lines each) | 8 and 8, 0 of 8 |

The 8 records are 7 inputs plus a `RiskBreachEvent` the graph raises on itself. Replay reproduced it from the
graph's own logic; it was not fed in.

**So the comparison rule is measured, not assumed:** compare each record with `eventTime`, `logTime` and `endTime`
excluded, and everything else must match. A real producer's clock moves within a cycle, so only a data-driven or
synthetic clock gives byte identity.

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
3. **Internally raised events.** Here only external inputs were recorded, as a caller would feed them. A
   `YamlReplayRecordWriter` installed as an auditor sees every event. If that includes events the graph raises on
   itself, replaying them would inject them twice. **Unverified:** it needs a processor generated with the auditor
   installed.
4. **A time-windowed replay is not a replay of the window.** `betweenTimes` skips earlier events, but the processor's
   state at the window's start depends on them. A replayable excerpt must replay from the start, or from a checkpoint.
5. **Licensing: unresolved.** The guide calls replay a commercial compiler feature. In 1.0 the runner ships in
   `fluxtion-builder-api-all-java8`; its own licence terms are not stated in the jar (the `LICENSE.txt` there is a
   bundled dependency's).

## Rerun

```
mvn -o -q compile                     # the analyser's classes, for the digest comparison if wanted
tools/spikes/replay-bundle/run.sh     # prints both modes' verdicts
```

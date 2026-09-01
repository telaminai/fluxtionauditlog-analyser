# Build: a fleet-telemetry monitoring engine

This describes **behaviour only**. The number of nodes, their names and their dependencies are entirely
your design. Keep or change the package and class names as you like.

## What arrives

- **Telemetry** — `vehicleId, metric, value, timestampMs`
- **Service** — `vehicleId, timestampMs` (a completed service visit)
- **FleetRoster** — `vehicleId, active` (reference data)
- **Limit** — `metric, threshold` (reference data; the current limit for a metric)

## What must be true

**P1 — fleet state.** The most recent telemetry value per vehicle and metric is the current one. A
Service event records that vehicle's most recent service time.

**P2 — limits.** The most recent `Limit` for a metric is the one in force.

**P3 — detectors arrest propagation.** A detector must not propagate when its condition does not hold.
In a cycle where no detector trips, the alert-handling nodes must not run at all.

**P4 — the severity gate.** An alert whose triggering reading has `value` **below 100** is suppressed
rather than raised.

**P5 — reference data is not activity.** A `FleetRoster` or `Limit` event whose values are unchanged
must not cause any detector to evaluate or any alert to be raised.

## Detectors

- **E1 overheat streak** — three or more *consecutive* telemetry readings for the same vehicle and
  metric are above that metric's limit, all within 60000ms of each other. It trips on the reading that
  completes the third. A reading at or below the limit resets the streak.
- **E2 stale service** — a telemetry reading arrives from a vehicle whose most recent service is more
  than 100000ms before that reading. A vehicle with no recorded service at all does **not** trip E2.
- **E3 inactive reporting** — a telemetry reading arrives from a vehicle whose roster entry has
  `active = false`.

## Evidence: use the framework's own audit log

Do not build a log format of your own. Enable the framework's audit log and write it out as it comes.
Two one-line conventions so it can be read mechanically:

- every detector logs its verdict: `auditLog.info("detector","E1").info("tripped", tripped);`
- the alert gate logs the outcome: `auditLog.info("raised", n).info("suppressed", m);`

## Running it

`Main` takes two arguments and must work with **any** scenario file in this format:

```
java -cp <classpath> <your.Main> <scenario-file> <output-log-path>
```
```
TELEM,vehicleId,metric,value,timestampMs
SERVICE,vehicleId,timestampMs
ROSTER,vehicleId,true|false
LIMIT,metric,threshold
```
One event per line, `#` comments ignored. **Do not hardcode a scenario in `Main`.** Your engine will be
run against a scenario written by someone else, which you will never see, and its audit log checked
against expected results. Correct behaviour on unseen input is the only thing scored.

## Deliverables

1. The graph, built with a `FluxtionGraphBuilder`. Report your main class's fully-qualified name.
2. `Main` as above, writing the framework's audit log to the given path.
3. **JUnit tests**; `mvn clean test` green. Tests that would fail if a detector stopped firing.

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

## What it must produce

Alongside whatever else you build, write a plain results file — one line per alert your engine decides
on, in the order it decides them:

```
<eventNumber>,<detector>,<raised|suppressed>
```

`eventNumber` is the 1-based position of the triggering event in the scenario file. `detector` is
`E1`, `E2` or `E3`. Nothing else goes in this file. It exists so the engine's decisions can be checked
by someone else; how you produce it is entirely up to you.

## Running it

`Main` takes two arguments and must work with **any** scenario file in this format:

```
java -cp <classpath> <your.Main> <scenario-file> <results-file>
```
```
TELEM,vehicleId,metric,value,timestampMs
SERVICE,vehicleId,timestampMs
ROSTER,vehicleId,true|false
LIMIT,metric,threshold
```
One event per line, `#` comments ignored. **Do not hardcode a scenario in `Main`.** Your engine will be
run against a scenario written by someone else, which you will never see, and its results file checked
against expected results. Correct behaviour on unseen input is the only thing scored.

## How to build it

There is **no event-processing framework available and none may be added** — the JDK only. There is no
required structure, no required number of classes, and no required design. Write it however you would
naturally write it.

## Deliverables

1. The engine, and a `Main` as above.
2. **JUnit tests**; `mvn clean test` green. Tests that would fail if a detector stopped firing.

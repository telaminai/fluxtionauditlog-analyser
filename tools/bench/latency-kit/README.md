# Fluxtion latency kit

Builds a Fluxtion processor from a **separately compiled node jar** — the generator sees the nodes
only as bytecode, which is the vendor-library case — and measures it against a hand-rolled flat
equivalent across any set of JVMs, plus native-image with and without PGO.

```bash
./run.sh --compiler-cp "<fluxtion-builder + generator-core + velocity + runtime>" \
         --jvm "temurin-21=/path/to/21/bin/java" \
         --jvm "graalvm-25=/path/to/graalvm/bin/java" \
         --native /path/to/graalvm/home \
         --iters 200000000 --warm 5000000
```

Prints one row per runtime: `generated` against `hand-rolled`, ns/event.

## What it is careful about

Round 59 produced **five** figures that looked like framework costs and were properties of the
measuring program. The kit is built to avoid them, and the shape is deliberate:

- **The processor is constructed inside the method that runs the loop** and never escapes it.
- **Nothing sits between the constructor and the loop.** The caller does the timing; a
  `System.nanoTime()` placed there costs 3.3×, because an opaque call between an allocation and its
  use blocks the escape proof.
- **`Runner.loop` is called directly**, not through `warm()`/`timed()` wrappers — that indirection
  measured 6.2 against 1.6 for the identical loop.
- **A `ClockStrategy` is supplied.** Without it the `Clock` auditor reads the system clock on every
  event: 20.6 ns against 6.5 on the same JVM.
- **PGO profiles every arm.** An arm in the image but absent from the profile is *slower* than
  building with no profile at all.
- **`--release 17`** so one build runs on every JVM under test.

## What it does not settle

The generated arm reaches 1.57 ns in several other harnesses and **6.2 in this one**, with the
inlining directive applied and confirmed read. That instability is
[oracle/graal#14387](https://github.com/oracle/graal/issues/14387). Treat the kit's numbers as *what
this program measures*, not as the floor.

For a gated comparison — output equivalence asserted before any timing is believed — run
`../dispatch-bench.py` against `app.Bench`.

Evidence: `docs/experience/runs/round-58/NOTES.md` and `round-59/NOTES.md`.

## The three-event-type graph

The single-type graph measures dispatch down a straight line. `MultiNodes` measures what a real system
does: **three event types taking three different paths**, converging on a shared tail.

```bash
# 1. generate            -DsrcDir= -DresDir= -Dpkg=  →  app.GenerateMulti
# 2. PROVE THEY AGREE — every field, after every event, bit-exact
java -cp <cp> -Devents=200000 app.CorrectnessMulti
# 3. only then measure
java -cp <cp> -Darm=generated -Diters=100000000 app.BenchMulti
java -cp <cp> -Darm=hand      -Diters=100000000 app.BenchMulti
```

**Step 2 is not optional and it is not the same check as the throughput harness's.** `BenchMulti`
prints five values at the end of a run; `CorrectnessMulti` compares fourteen fields after every one of
200,000 interleaved events. An ordering error that cancels out by the final event passes the first and
fails the second.

**The hand-rolled arm's firing order was read off the generated source.** Two orderings are not what a
person writes by hand — the tick path fires `mid, ewma, spread, notional, vol`, and the shared tail
puts `limit` last — so writing `HandMulti` from the graph definition rather than from the generated
dispatch would have produced a plausible, silently different answer.

Measured on this machine, mixed stream, outputs identical, 3 of 3 native cycles landed:

| | JIT | native + PGO |
|---|---|---|
| generated | 5.541 | 1.681 (595 M/s) |
| hand-rolled | 2.680 | 1.372 |

The generated cost is the same as the single-type graph's (1.666); the hand-rolled floor is what drops.
